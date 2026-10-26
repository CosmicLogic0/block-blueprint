package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.MaterialMap;
import dev.buildblueprint.core.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Creative mode: places the whole blueprint (or one layer) for you, bottom layer first, a few hundred blocks per tick.
 * Singleplayer places straight into the world. On a server it sends /setblock commands, so you need to be an operator.
 * Blocks that are in the way are replaced, except chests and other blocks that hold items. Can be undone.
 */
public final class AutoBuild {
    private AutoBuild() {}

    private record Job(BlockPos pos, BlockState state, String text) {}

    private static final int PER_TICK_LOCAL = 400, PER_TICK_SERVER = 20;
    private static final int FLAGS = 2 | 16 | 32;   // update clients | known shape | no drops

    private static List<Job> queue = new ArrayList<>();
    private static int next = 0, total = 0;
    private static boolean undoing = false;
    private static List<Job> undo = new ArrayList<>();   // what was there before the last auto-build
    private static boolean ticking = false;

    public static boolean running() { return next < queue.size(); }
    public static boolean canUndo() { return !undo.isEmpty() && !running(); }
    public static int done() { return next; }
    public static int total() { return total; }

    static void init() {
        if (ticking) return;
        ticking = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("auto-build", () -> tick(mc)));
    }

    private static boolean creative() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (!mc.player.isCreative()) { Chat.err("Auto-build only works in Creative mode."); return false; }
        return true;
    }

    /** onlyLayer = -1 for the whole build, else one layer (0-based). */
    public static void start(int onlyLayer) {
        Session s = BuildBlueprintClient.SESSION;
        Minecraft mc = Minecraft.getInstance();
        if (!PlayerInfo.inWorld()) return;
        if (!s.loaded()) { Chat.warn("Load a blueprint first."); return; }
        if (running()) { Chat.warn("Already building - /buildblueprint autobuild stop to stop."); return; }
        if (!creative()) return;
        Blueprint bp = s.blueprint();
        Map<String, BlockState> parsed = new HashMap<>();
        List<Job> jobs = new ArrayList<>(), before = new ArrayList<>();
        int kept = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = 0; y < bp.sy; y++) {
            if (onlyLayer >= 0 && y != onlyLayer) continue;
            for (int z = 0; z < bp.sz; z++) for (int x = 0; x < bp.sx; x++) {
                String st = bp.stateAt(x, y, z);
                if (st == null) continue;
                BlockState want = parsed.computeIfAbsent(st, AutoBuild::parse);
                if (want == null) continue;
                p.set(s.ox + x, s.oy + y, s.oz + z);
                BlockState here = mc.level.getBlockState(p);
                if (V.blockId(here.getBlock()).equals(MaterialMap.blockId(st)) && (st.indexOf('[') < 0 || dev.buildblueprint.core.Orient.ok(st, here.toString()))) continue; // already right (and the right way round)
                if (V.hasBE(here)) { kept++; continue; }   // don't wipe out chests, furnaces...
                BlockPos at = p.immutable();
                jobs.add(new Job(at, want, V.stateString(want)));
                before.add(new Job(at, here, V.stateString(here)));
            }
        }
        if (jobs.isEmpty()) { Chat.ok(kept > 0 ? "Nothing to place (" + kept + " spots have chests or similar in the way)." : "Nothing to place - it's all built already!"); return; }
        if (!mc.hasSingleplayerServer()) Chat.warn("On a server this uses /setblock, so you need to be an operator.");
        queue = jobs; next = 0; total = jobs.size(); undo = before; undoing = false;
        Chat.ok("Auto-building " + total + " blocks" + (onlyLayer >= 0 ? " on layer " + (onlyLayer + 1) : "") + "..."
                + (kept > 0 ? " (skipping " + kept + " spots with chests or similar in the way)" : ""));
    }

    public static void undoLast() {
        if (running()) { Chat.warn("Wait for the auto-build to finish (or stop it) first."); return; }
        if (undo.isEmpty()) { Chat.warn("Nothing to undo."); return; }
        if (!creative()) return;
        queue = new ArrayList<>(undo); next = 0; total = queue.size(); undo = new ArrayList<>(); undoing = true;
        Chat.ok("Undoing the last auto-build (" + total + " blocks)...");
    }

    public static void stop() {
        if (!running()) { Chat.say("Not auto-building right now."); return; }
        Chat.ok("Stopped after " + next + " of " + total + " blocks.");
        queue = new ArrayList<>(queue.subList(0, next));
    }

    private static BlockState parse(String st) {
        try { BlockState b = V.parseState(st); return b.isAir() ? null : b; }
        catch (Exception e) { return null; }
    }

    private static void tick(Minecraft mc) {
        if (!running()) return;
        if (mc.player == null || mc.level == null) { queue = new ArrayList<>(); next = 0; return; }
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            var server = mc.getSingleplayerServer();
            var dim = mc.level.dimension();
            int end = Math.min(queue.size(), next + PER_TICK_LOCAL);
            List<Job> batch = new ArrayList<>(queue.subList(next, end));
            next = end;
            server.execute(() -> {
                ServerLevel lvl = server.getLevel(dim);
                if (lvl == null) return;
                for (Job j : batch) lvl.setBlock(j.pos, j.state, FLAGS);
            });
        } else {
            int end = Math.min(queue.size(), next + PER_TICK_SERVER);
            for (int i = next; i < end; i++) {
                Job j = queue.get(i);
                V.command(mc.player, "setblock " + j.pos.getX() + " " + j.pos.getY() + " " + j.pos.getZ() + " " + j.text + " replace");
            }
            next = end;
        }
        if (next % 2000 < (mc.hasSingleplayerServer() ? PER_TICK_LOCAL : PER_TICK_SERVER) || !running())
            Chat.bar((undoing ? "Undoing... " : "Auto-building... ") + next + " / " + total);
        if (!running()) {
            Chat.ok(undoing ? "Undo finished." : "Auto-build finished! " + total + " blocks placed. Use Undo in the Page Up menu (or /buildblueprint autobuild undo) to take it back.");
            if (!undoing) BuildBlueprintClient.SESSION.locked = true;   // it's built now: don't let it slide away
            undoing = false;
            Materials.refresh();
        }
    }
}
