package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.BlueprintCodec;
import dev.buildblueprint.core.sync.Proto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Builds on the server (when the server has the Build Blueprint plugin or server mod): your placed build is shared with
 * the other players who have the mod, theirs show up for you (Build with friends lists them, with Join), and you can
 * invite a player in game. On a server without it, nothing here does anything.
 */
public final class SyncClient {
    private SyncClient() {}

    /** Set by this Minecraft version's network part; null = this version can't talk to the server part yet. */
    public static volatile java.util.function.Consumer<byte[]> sender;
    public static volatile java.util.function.BooleanSupplier canSend;

    /** A build another player shares. */
    public static final class Other {
        public final String owner, id, code, name; public final int x, y, z, sx, sy, sz, parts;
        Other(String owner, String id, String code, String name, int x, int y, int z, int sx, int sy, int sz, int parts) {
            this.owner = owner; this.id = id; this.code = code; this.name = name; this.x = x; this.y = y; this.z = z; this.sx = sx; this.sy = sy; this.sz = sz; this.parts = parts;
        }
    }

    private static Object conn;              // the connection we said hello on
    private static int helloTries;
    private static boolean available;        // the server answered: it has the server part
    private static String kind = "";
    private static final Map<String, Other> OTHERS = new LinkedHashMap<>();
    private static final Map<String, Proto.Assembler> INCOMING = new HashMap<>();
    private static final List<String[]> INVITES = new ArrayList<>();   // {from, id}
    private static List<String> players = new ArrayList<>();
    private static String sharedKey, sharedId;   // what we share now (key = which build, where)
    private static int tick;

    public static boolean available() { return available; }
    public static String serverKind() { return kind; }
    public static List<Other> others() { return new ArrayList<>(OTHERS.values()); }
    public static List<String> players() { return players; }
    public static List<String[]> invites() { return INVITES; }

    public static void init() { net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("server builds", () -> tick(mc))); }

    /** Every client tick. */
    public static void tick(Minecraft mc) {
        Object c = mc.getConnection();
        if (c == null) { if (conn != null) reset(); return; }
        if (c != conn) { reset(); conn = c; helloTries = 0; tick = 0; }
        if (sender == null) return;
        tick++;
        if (!available) {   // say hello now and then until the server answers (or give up after a few tries)
            // ask even when the server hasn't announced the channel: older Paper and Fabric servers only do that after we do,
            // and a server without the server part just ignores the message
            if (helloTries < 5 && tick % 100 == 20) { helloTries++; send(Proto.hello("client")); }
            return;
        }
        if (tick % 20 == 0) shareNow();
    }

    private static void reset() { conn = null; available = false; kind = ""; OTHERS.clear(); INCOMING.clear(); INVITES.clear(); players = new ArrayList<>(); sharedKey = null; sharedId = null; }

    private static void send(byte[] d) { try { if (sender != null) sender.accept(d); } catch (Exception e) { System.out.println("[Build Blueprint] couldn't send to the server: " + e); } }

    /** Share the placed build (or stop sharing) so it matches what you have now. */
    private static void shareNow() {
        var s = BuildBlueprintClient.SESSION;
        String key = Settings.shareBuilds && s.loaded() && s.locked ? System.identityHashCode(s.blueprint()) + "@" + s.ox + "," + s.oy + "," + s.oz + "/" + Coop.parts : null;
        if (java.util.Objects.equals(key, sharedKey)) return;
        if (sharedId != null) send(Proto.unshare(sharedId));
        sharedKey = key; sharedId = null;
        if (key == null) return;
        sharedId = Long.toString(System.nanoTime() & 0xFFFFFFFFL, 36);
        String code = "BBCOOP:" + s.ox + "," + s.oy + "," + s.oz + "," + Math.max(1, Coop.parts) + " " + Copier.appCode(s.blueprint());
        List<byte[]> parts = Proto.split(Proto.gzip(code));
        for (int i = 0; i < parts.size(); i++) send(Proto.share(sharedId, i, parts.size(), parts.get(i)));
    }

    /** Asks the server who else has the mod (the answer comes a moment later). */
    public static void askPlayers() { if (available && !"demo".equals(kind)) send(Proto.playersReq()); }

    /** Invites a player to your placed build. Returns a message for the screen. */
    public static String invite(String name) {
        if (!available) return "This server doesn't have the Build Blueprint server part.";
        var s = BuildBlueprintClient.SESSION;
        if (!s.loaded() || !s.locked) return "Place your build first (Delete), then invite.";
        if (!Settings.shareBuilds) return "Turn on Share my build (Settings > Extras) to invite.";
        shareNow();
        if (sharedId == null) return "Couldn't share your build.";
        send(Proto.invite(name, sharedId));
        return "Invited " + name + "!";
    }

    /** Load another player's build in their spot, as a co-op build. */
    public static void join(Other o) {
        CoopScreen.joinCoop(o.code);
        INVITES.removeIf(i -> i[0].equals(o.owner));
    }

    public static Other byOwner(String owner, String id) { return OTHERS.get(owner + "/" + id); }

    /** A message from the server (on the client thread). */
    public static void receive(byte[] raw) {
        Proto.Msg m;
        try { m = Proto.decode(raw); } catch (Exception e) { return; }
        switch (m.type) {
            case Proto.HELLO:
                available = true; kind = m.a;
                System.out.println("[Build Blueprint] this server has the Build Blueprint server part (" + m.a + ")");
                break;
            case Proto.PLAYERS: if (!"demo".equals(kind)) players = m.names; break;
            case Proto.BUILD: {
                String key = m.a + "/" + m.b;
                try {
                    if (m.part == 0) INCOMING.put(key, new Proto.Assembler(m.total));
                    Proto.Assembler a = INCOMING.get(key);
                    if (a == null) break;
                    byte[] all = a.add(m.part, m.data);
                    if (all == null) break;
                    INCOMING.remove(key);
                    String code = Proto.gunzip(all);
                    int[] h = CoopScreen.parseHeader(code);
                    if (h == null) break;
                    Blueprint bp = BlueprintCodec.decode(SavedBuilds.parsePasted(code));
                    String name = bp.name == null || bp.name.isBlank() ? "a build" : bp.name;
                    OTHERS.put(key, new Other(m.a, m.b, code, name, h[0], h[1], h[2], bp.sx, bp.sy, bp.sz, h[3]));
                } catch (Exception e) { INCOMING.remove(key); System.out.println("[Build Blueprint] couldn't read " + m.a + "'s build: " + e); }
                break;
            }
            case Proto.REMOVED: OTHERS.remove(m.a + "/" + m.b); break;
            case Proto.INVITED: {
                INVITES.removeIf(i -> i[0].equals(m.a));
                INVITES.add(new String[]{m.a, m.b});
                Chat.ok(m.a + " invited you to build together! Open the menu > Build with friends to join.");
                Chat.ding(false);
                break;
            }
            default: break;
        }
    }

    /** Screenshot tour only (UiTest): example players, builds and an invite, so the pictures show what the screens look like on a busy server. */
    static void demo(boolean on) {
        if (!on) { reset(); return; }
        available = true; kind = "demo";
        players = new ArrayList<>(java.util.Arrays.asList("Alex", "Steve", "Sam"));
        OTHERS.clear();
        OTHERS.put("Alex/a", new Other("Alex", "a", "", "Castle Keep", 120, 64, -40, 31, 24, 31, 2));
        OTHERS.put("Sam/b", new Other("Sam", "b", "", "Treehouse", -60, 70, 200, 15, 20, 15, 1));
        INVITES.clear();
        INVITES.add(new String[]{"Alex", "a"});
    }

    /** The other players' builds you're standing in or next to (for the corner list). */
    public static Other near(Minecraft mc) {
        if (mc.player == null || OTHERS.isEmpty()) return null;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        for (Other o : OTHERS.values())
            if (px > o.x - 16 && px < o.x + o.sx + 16 && pz > o.z - 16 && pz < o.z + o.sz + 16 && py > o.y - 32 && py < o.y + o.sy + 32) return o;
        return null;
    }
}
