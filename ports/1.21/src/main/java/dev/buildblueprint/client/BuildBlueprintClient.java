package dev.buildblueprint.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.BlueprintException;
import dev.buildblueprint.core.ChunkAssembler;
import dev.buildblueprint.core.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** Build Blueprint: paste a build from the desktop app, see what you need, and nudge it into place. */
public class BuildBlueprintClient implements ClientModInitializer {
    public static final Session SESSION = new Session();
    public static final ChunkAssembler ASSEMBLER = new ChunkAssembler();

    private static final String[] DIRECTIONS = {"up", "down", "left", "right", "forward", "back", "north", "south", "east", "west"};

    @Override
    public void onInitializeClient() {
        Cmd.register(BuildBlueprintClient::registerCommands);
        Modules.init();
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(Cmd.literal("buildblueprint")
            .executes(ctx -> { openMenuOrList(); return 1; })

            .then(Cmd.literal("load")
                .then(Cmd.argument("id", StringArgumentType.word())
                    .then(Cmd.argument("data", StringArgumentType.greedyString())
                        .executes(ctx -> load(ctx, StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "data"))))))

            .then(Cmd.literal("move")
                .then(Cmd.argument("direction", StringArgumentType.word())
                    .suggests((c, b) -> { for (String s : DIRECTIONS) b.suggest(s); return b.buildFuture(); })
                    .executes(ctx -> move(StringArgumentType.getString(ctx, "direction"), 1))
                    .then(Cmd.argument("amount", IntegerArgumentType.integer(-512, 512))
                        .executes(ctx -> move(StringArgumentType.getString(ctx, "direction"), IntegerArgumentType.getInteger(ctx, "amount"))))))

            .then(Cmd.literal("rotate")
                .then(Cmd.literal("right").executes(ctx -> rotate(1)))
                .then(Cmd.literal("left").executes(ctx -> rotate(-1)))
                .then(Cmd.literal("180").executes(ctx -> rotate(2))))

            .then(Cmd.literal("here").executes(ctx -> here()))
            .then(Cmd.literal("ground").executes(ctx -> { if (ready()) snapToGround(); return 1; }))
            .then(Cmd.literal("mirror").executes(ctx -> { if (ready()) mirror(); return 1; }))
            .then(Cmd.literal("stats").executes(ctx -> { if (ready()) Chat.say("Building time " + Stats.time(Stats.activeMs()) + " · " + Stats.placedByYou() + " blocks placed · " + Math.round(Stats.perMinute()) + " a minute"); return 1; }))
            .then(Cmd.literal("place").executes(ctx -> { if (ready()) setLocked(true); return 1; }))
            .then(Cmd.literal("lock").executes(ctx -> { if (ready()) setLocked(true); return 1; }))
            .then(Cmd.literal("unlock").executes(ctx -> { if (ready()) setLocked(false); return 1; }))
            .then(Cmd.literal("autobuild")
                .executes(ctx -> { AutoBuild.start(-1); return 1; })
                .then(Cmd.literal("layer").executes(ctx -> { AutoBuild.start(Math.max(0, Materials.layer())); return 1; }))
                .then(Cmd.literal("stop").executes(ctx -> { AutoBuild.stop(); return 1; }))
                .then(Cmd.literal("undo").executes(ctx -> { AutoBuild.undoLast(); return 1; })))
            .then(Cmd.literal("copy")
                .executes(ctx -> { Copier.start(); return 1; })
                .then(Cmd.literal("corner").executes(ctx -> { Copier.corner(); return 1; }))
                .then(Cmd.literal("cancel").executes(ctx -> { Copier.cancel(); return 1; }))
                .then(Cmd.literal("save").then(Cmd.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> { Copier.saveNow(StringArgumentType.getString(ctx, "name")); return 1; }))))
            .then(Cmd.literal("materials").executes(ctx -> { materialsInChat(); return 1; }))
            .then(Cmd.literal("menu").executes(ctx -> { openMenuOrList(); return 1; }))
            .then(Cmd.literal("show").executes(ctx -> { SESSION.visible = true; Chat.ok("Preview shown."); return 1; }))
            .then(Cmd.literal("hide").executes(ctx -> { SESSION.visible = false; Chat.ok("Preview hidden."); return 1; }))
            .then(Cmd.literal("layer")
                .then(Cmd.literal("all").executes(ctx -> layer(-1)))
                .then(Cmd.argument("n", IntegerArgumentType.integer(1, 512)).executes(ctx -> layer(IntegerArgumentType.getInteger(ctx, "n") - 1))))
            .then(Cmd.literal("status").executes(ctx -> { status(); return 1; }))
            .then(Cmd.literal("clear").executes(ctx -> { SESSION.clear(); ASSEMBLER.reset(); Chat.ok("Blueprint cleared."); return 1; }))
            .then(Cmd.literal("help").executes(ctx -> { help(); return 1; })));
    }

    private static boolean ready() {
        if (!PlayerInfo.inWorld()) return false;
        if (!SESSION.loaded()) { Chat.warn("No blueprint loaded. Paste the lines from the app first (see /buildblueprint help)."); return false; }
        return true;
    }

    private static int load(CommandContext<FabricClientCommandSource> ctx, String token, String data) {
        if (!PlayerInfo.inWorld()) return 0;
        try {
            ChunkAssembler.Progress p = ASSEMBLER.accept(token, data);
            if (p.done() == null) {
                Chat.say("Got part " + p.have() + " of " + p.total() + (p.have() < p.total() ? " received so far. Keep pasting the rest." : "."));
                return 1;
            }
            Blueprint bp = p.done();
            try { // keep every pasted build on this computer (Page Up > Saved builds)
                String joined = p.data();
                if (joined != null && SavedBuilds.list().stream().noneMatch(e -> e.data().equals(joined))) SavedBuilds.save(bp.name == null || bp.name.isBlank() ? "My build" : bp.name, joined);
            } catch (Exception ignored) {}
            SESSION.load(bp, PlayerInfo.x(), PlayerInfo.y(), PlayerInfo.z(), PlayerInfo.yaw());
            Chat.ok("Loaded \"" + bp.name + "\": " + bp.blockCount() + " blocks, " + bp.sx + " x " + bp.sy + " x " + bp.sz + ".");
            Chat.say("It's shown in front of you, fully built. Move it into place, then press Delete (or Page Up > Place it) to start building layer by layer.");
            if (Modules.keysActive) Chat.say("Keys: arrows move it (Up = forward, Down = back, Ctrl+Up/Down = up/down), Page Down rotates, Page Up opens the materials menu.");
            return 1;
        } catch (BlueprintException e) {
            ASSEMBLER.reset();
            Chat.err(e.getMessage());
            return 0;
        }
    }

    /** Placed builds can't be moved until unlocked. */
    private static boolean unlocked() {
        if (!SESSION.locked) return true;
        Chat.warn("The blueprint is placed (locked). Unlock it first: Page Up > Unlock, or /buildblueprint unlock.");
        return false;
    }

    static void setLocked(boolean on) {
        if (!SESSION.loaded()) return;
        SESSION.locked = on;
        Materials.refresh();
        if (on && Settings.startLayer1 && SESSION.layer < 0) SESSION.layer = Layers.firstUnfinished();   // placed: start building from the bottom
        if (!on) SESSION.layer = -1;                                                                     // moving it again: show the whole thing
        Materials.refresh();
        SessionStore.dirty();
        Chat.bar(on ? "Placed! " + (SESSION.layer >= 0 ? "Layer " + (SESSION.layer + 1) + " of " + SESSION.blueprint().sy + " - Home/End to change layer" : "It won't move or rotate now")
                : "Unlocked - move it with the arrow keys, then press Delete to place it again");
    }

    /** Moves the blueprint up or down so its bottom sits on the ground under it. */
    static void snapToGround() {
        if (!SESSION.loaded() || !unlocked()) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        Blueprint bp = SESSION.blueprint();
        java.util.Map<Integer, Integer> votes = new java.util.HashMap<>();
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int z = 0; z < bp.sz; z++) for (int x = 0; x < bp.sx; x++) {
            if (bp.stateAt(x, 0, z) == null) continue;
            int wx = SESSION.ox + x, wz = SESSION.oz + z;
            for (int y = SESSION.oy + 8; y > SESSION.oy - 64; y--) {
                var st = mc.level.getBlockState(p.set(wx, y, wz));
                if (!st.isAir() && !st.canBeReplaced() && st.getFluidState().isEmpty()) { votes.merge(y + 1, 1, Integer::sum); break; }
            }
        }
        if (votes.isEmpty()) { Chat.bar("No ground found under the blueprint"); return; }
        int best = votes.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).get().getKey();
        int d = best - SESSION.oy;
        SESSION.oy = best;
        Chat.bar(d == 0 ? "Already sitting on the ground" : "Moved " + (d > 0 ? "up " : "down ") + Math.abs(d) + " to sit on the ground");
    }

    static void mirror() {
        if (!SESSION.loaded() || !unlocked()) return;
        SESSION.mirror();
        Chat.bar(SESSION.mirrored ? "Mirrored (flipped left <-> right)" : "Back to normal (not mirrored)");
    }

    private static int move(String dir, int n) {
        if (!ready() || !unlocked()) return 0;
        if (!SESSION.move(dir, n, PlayerInfo.yaw())) {
            Chat.err("Unknown direction \"" + dir + "\". Use: up, down, left, right, forward, back, north, south, east, west.");
            return 0;
        }
        Chat.ok("Moved " + dir + " " + n + ". Corner is now at " + SESSION.ox + ", " + SESSION.oy + ", " + SESSION.oz + ".");
        return 1;
    }

    private static int rotate(int quarter) {
        if (!ready() || !unlocked()) return 0;
        SESSION.rotate(quarter);
        Chat.ok("Rotated. Corner is now at " + SESSION.ox + ", " + SESSION.oy + ", " + SESSION.oz + ".");
        return 1;
    }

    private static int here() {
        if (!ready() || !unlocked()) return 0;
        Blueprint bp = SESSION.blueprint();
        SESSION.ox = PlayerInfo.x(); SESSION.oy = PlayerInfo.y(); SESSION.oz = PlayerInfo.z();
        Chat.ok("Blueprint corner moved to where you stand (" + SESSION.ox + ", " + SESSION.oy + ", " + SESSION.oz + "). Size " + bp.sx + " x " + bp.sy + " x " + bp.sz + ".");
        return 1;
    }

    private static int layer(int y) {
        if (!ready()) return 0;
        SESSION.layer = y;
        Chat.ok(y < 0 ? "Showing every layer." : "Showing layer " + (y + 1) + " of " + SESSION.blueprint().sy + " only.");
        return 1;
    }

    private static void status() {
        if (!ready()) return;
        Blueprint bp = SESSION.blueprint();
        Chat.say("\"" + bp.name + "\": " + bp.blockCount() + " blocks, " + bp.sx + " x " + bp.sy + " x " + bp.sz + ", rotated " + SESSION.turns() * 90 + " degrees.");
        Chat.say("Corner at " + SESSION.ox + ", " + SESSION.oy + ", " + SESSION.oz + "; far corner at "
            + (SESSION.ox + bp.sx - 1) + ", " + (SESSION.oy + bp.sy - 1) + ", " + (SESSION.oz + bp.sz - 1) + ".");
    }

    private static void help() {
        Chat.say("/buildblueprint  - open the materials menu");
        Chat.say("/buildblueprint load <id> <data>  - the lines the app gives you; paste them all");
        Chat.say("/buildblueprint move <up|down|left|right|forward|back> [amount]");
        Chat.say("/buildblueprint rotate <left|right|180>   /buildblueprint here   /buildblueprint layer <n|all>");
        Chat.say("/buildblueprint materials | status | show | hide | clear");
        Chat.say("/buildblueprint mirror | stats  - flip the blueprint left/right, show your build time");
        Chat.say("/buildblueprint place | unlock  - lock the blueprint where it is so keys can't move it");
        Chat.say("/buildblueprint autobuild [layer|stop|undo]  - Creative only: places the blueprint for you");
        Chat.say("/buildblueprint copy  - copy a build from your world (or look at it and press Insert; arrows resize the box)");
        if (Modules.keysActive) Chat.say("Keys: Up = forward, Down = back, Left/Right, Ctrl+Up/Down = up/down, Page Down = rotate, Page Up = materials menu.");
    }

    static void openMenuOrList() {
        if (!PlayerInfo.inWorld()) return;
        if (!Modules.openMenu()) { if (ready()) materialsInChat(); }
    }

    /** Needed vs. carried, in chat. Works even without the menu screen. */
    static void materialsInChat() {
        if (!ready()) return;
        Map<String, Integer> need = SESSION.blueprint().materials();
        Map<String, Integer> have = PlayerInfo.inventoryCounts();
        List<Map.Entry<String, Integer>> rows = new ArrayList<>(need.entrySet());
        rows.sort((a, b) -> Integer.compare(b.getValue() - have.getOrDefault(b.getKey(), 0), a.getValue() - have.getOrDefault(a.getKey(), 0)));
        Chat.say("--- " + SESSION.blueprint().name + ": you have / you need ---");
        int missingTypes = 0;
        for (Map.Entry<String, Integer> e : rows) {
            int h = have.getOrDefault(e.getKey(), 0), n = e.getValue();
            String line = PlayerInfo.pretty(e.getKey()) + ": " + h + " / " + n;
            if (h >= n) Chat.ok("OK  " + line);
            else { missingTypes++; Chat.err("NEED " + (n - h) + " more  " + line); }
        }
        if (missingTypes == 0) Chat.ok("You have everything.");
    }
}
