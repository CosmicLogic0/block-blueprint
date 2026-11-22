package dev.buildblueprint.core.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server part, the same for the Paper plugin and the Fabric/Forge/NeoForge server mods: it remembers which players
 * have the mod, keeps the builds they share while they're online, passes them to the others and delivers invites.
 * Call it from the server thread only (or keep calls to one thread).
 */
public final class Relay<P> {
    /** What the platform does for the relay. */
    public interface Bridge<P> {
        void send(P player, byte[] msg);
        String name(P player);
        UUID id(P player);
        P byName(String name);
        void log(String s);
    }

    private static final class Build { final UUID owner; final String ownerName, id; final byte[] data; Build(UUID o, String on, String i, byte[] d) { owner = o; ownerName = on; id = i; data = d; } }

    private final Bridge<P> bridge;
    private final String kind;
    private final Map<UUID, P> modded = new LinkedHashMap<>();
    private final Map<String, Build> builds = new LinkedHashMap<>();         // owner uuid + "/" + id
    private final Map<String, Proto.Assembler> uploads = new HashMap<>();

    public Relay(Bridge<P> bridge, String kind) { this.bridge = bridge; this.kind = kind; }

    public void receive(P from, byte[] raw) {
        Proto.Msg m;
        try { m = Proto.decode(raw); } catch (Exception e) { bridge.log("bad message from " + bridge.name(from) + ": " + e); return; }
        UUID me = bridge.id(from);
        switch (m.type) {
            case Proto.HELLO: {
                boolean isNew = modded.put(me, from) == null;
                bridge.send(from, Proto.hello(kind));
                for (Build b : builds.values()) if (!b.owner.equals(me)) sendBuild(from, b);
                if (isNew) bridge.log(bridge.name(from) + " has Build Blueprint");
                break;
            }
            case Proto.PLAYERS_REQ: {
                List<String> names = new ArrayList<>();
                for (Map.Entry<UUID, P> e : modded.entrySet()) if (!e.getKey().equals(me)) names.add(bridge.name(e.getValue()));
                bridge.send(from, Proto.players(names));
                break;
            }
            case Proto.SHARE: {
                if (!modded.containsKey(me)) return;
                String key = me + "/" + m.a;
                try {
                    if (m.part == 0) uploads.put(key, new Proto.Assembler(m.total));
                    Proto.Assembler a = uploads.get(key);
                    if (a == null) return;
                    byte[] all = a.add(m.part, m.data);
                    if (all == null) return;
                    uploads.remove(key);
                    int mine = 0;
                    for (Build b : builds.values()) if (b.owner.equals(me) && !b.id.equals(m.a)) mine++;
                    if (mine >= Proto.MAX_BUILDS) return;
                    Build b = new Build(me, bridge.name(from), m.a, all);
                    builds.put(key, b);
                    for (Map.Entry<UUID, P> e : modded.entrySet()) if (!e.getKey().equals(me)) sendBuild(e.getValue(), b);
                } catch (IllegalArgumentException e) { uploads.remove(key); bridge.log("build from " + bridge.name(from) + " refused: " + e.getMessage()); }
                break;
            }
            case Proto.UNSHARE: {
                Build b = builds.remove(me + "/" + m.a);
                if (b != null) for (Map.Entry<UUID, P> e : modded.entrySet()) if (!e.getKey().equals(me)) bridge.send(e.getValue(), Proto.removed(b.ownerName, b.id));
                break;
            }
            case Proto.INVITE: {
                P to = bridge.byName(m.a);
                if (to != null && modded.containsKey(bridge.id(to))) bridge.send(to, Proto.invited(bridge.name(from), m.b));
                break;
            }
            default: break;
        }
    }

    /** A player left: forget them and take their builds away from everyone else. */
    public void quit(P p) {
        UUID me = bridge.id(p);
        if (modded.remove(me) == null) return;
        uploads.keySet().removeIf(k -> k.startsWith(me + "/"));
        List<Build> gone = new ArrayList<>();
        builds.values().removeIf(b -> { if (b.owner.equals(me)) { gone.add(b); return true; } return false; });
        for (Build b : gone) for (P o : modded.values()) bridge.send(o, Proto.removed(b.ownerName, b.id));
    }

    private void sendBuild(P to, Build b) {
        List<byte[]> parts = Proto.split(b.data);
        for (int i = 0; i < parts.size(); i++) bridge.send(to, Proto.build(b.ownerName, b.id, i, parts.size(), parts.get(i)));
    }

    public int players() { return modded.size(); }
    public int buildCount() { return builds.size(); }
}
