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
 * Creative mode: places the whole blueprint (or one layer) for you, bottom layer first.
 * Singleplayer places straight into the world, as many blocks per tick as the game keeps up with. On a server it sends
 * commands, so you need to be an operator: blocks of the same kind next to each other on a layer go in one /fill
 * (up to 32,768 blocks each), so even builds of millions of blocks go up in minutes instead of hours.
 * Blocks that are in the way are replaced, except chests and other blocks that hold items. Can be undone.
 */
public final class AutoBuild {
    private AutoBuild() {}

    private record Job(BlockPos pos, BlockState state, String text) {}

    private record Cmd(String text, int blocks) {}

    private static final int CMDS_PER_TICK = 60, BLOCKS_PER_TICK_SERVER = 12_000, FILL_MAX = 32_768;
    private static int perTickLocal = 400;                 // grows or shrinks so a batch takes about 25 ms of the game's time
    private static volatile boolean batchPending = false;  // singleplayer: the game hasn't placed the last batch yet
    private static volatile long lastBatchNs = 0;
    private static List<Cmd> cmds = null;                  // server: the commands for what's left in the queue
    private static int nextCmd = 0, barTick = 0;
    private static final int FLAGS = 2 | 16 | 32;   // update clients | known shape | no drops

    private static List<Job> queue = new ArrayList<>();
    private static int next = 0, total = 0;
    private static boolean undoing = false;
    private static List<Job> undo = new ArrayList<>();   // what was there before the last auto-build
    private static boolean ticking = false;
    // A block that needs a neighbour (a torch, a door's top half...) can fail the first time: one quiet second pass picks those up.
    private static int recheckIn = 0, lastLayer = -1;
    private static boolean secondPass = false;

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
    public static void start(int onlyLayer) { secondPass = false; start(onlyLayer, false); }

    private static void start(int onlyLayer, boolean quiet) {
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
                if (MaterialMap.sameBlock(V.blockId(here.getBlock()), MaterialMap.blockId(st)) && (st.indexOf('[') < 0 || !V.blockId(here.getBlock()).equals(MaterialMap.blockId(st)) || dev.buildblueprint.core.Orient.ok(st, here.toString()))) continue; // already right (and the right way round)
                if (V.hasBE(here)) { kept++; continue; }   // don't wipe out chests, furnaces...
                BlockPos at = p.immutable();
                jobs.add(new Job(at, want, V.stateString(want)));
                before.add(new Job(at, here, V.stateString(here)));
            }
        }
        if (jobs.isEmpty()) { if (quiet) return; Chat.ok(kept > 0 ? "Nothing to place (" + kept + " spots have chests or similar in the way)." : "Nothing to place - it's all built already!"); return; }
        if (!quiet && !mc.hasSingleplayerServer()) Chat.warn("On a server this uses /setblock, so you need to be an operator.");
        if (quiet) before.addAll(undo);   // the second pass: undo still takes everything back (what was there first goes back last, so it wins)
        queue = jobs; next = 0; total = jobs.size(); undo = before; undoing = false; lastLayer = onlyLayer; cmds = null; batchPending = false;
        if (quiet) return;
        Chat.ok("Auto-building " + total + " blocks" + (onlyLayer >= 0 ? " on layer " + (onlyLayer + 1) : "") + "..."
                + (kept > 0 ? " (skipping " + kept + " spots with chests or similar in the way)" : ""));
    }

    public static void undoLast() {
        if (running()) { Chat.warn("Wait for the auto-build to finish (or stop it) first."); return; }
        if (undo.isEmpty()) { Chat.warn("Nothing to undo."); return; }
        if (!creative()) return;
        queue = new ArrayList<>(undo); next = 0; total = queue.size(); undo = new ArrayList<>(); undoing = true; cmds = null; batchPending = false;
        Chat.ok("Undoing the last auto-build (" + total + " blocks)...");
    }

    public static void stop() {
        if (!running()) { Chat.say("Not auto-building right now."); return; }
        Chat.ok("Stopped after " + next + " of " + total + " blocks.");
        queue = new ArrayList<>(queue.subList(0, next));
        cmds = null;
    }

    /** On a server: blocks of the same kind side by side on a layer become one /fill, so big builds need far fewer commands. */
    private static List<Cmd> commands(List<Job> list) {
        List<Cmd> out = new ArrayList<>();
        Map<Long, Job> at = new HashMap<>();
        List<Job> group = new ArrayList<>();
        int y = Integer.MIN_VALUE;
        for (Job j : list) {   // jobs come a layer at a time (bottom first); the same spot twice starts a new group, so the order still holds
            long k = key(j.pos.getX(), j.pos.getZ());
            if (j.pos.getY() != y || at.containsKey(k)) { layer(group, at, y, out); group = new ArrayList<>(); at = new HashMap<>(); y = j.pos.getY(); }
            group.add(j); at.put(k, j);
        }
        layer(group, at, y, out);
        return out;
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    private static boolean same(Map<Long, Job> at, java.util.Set<Long> used, int x, int z, String text) {
        long k = key(x, z);
        Job j = at.get(k);
        return j != null && !used.contains(k) && j.text.equals(text);
    }

    private static void layer(List<Job> group, Map<Long, Job> at, int y, List<Cmd> out) {
        java.util.Set<Long> used = new java.util.HashSet<>();
        for (Job s : group) {
            int x0 = s.pos.getX(), z0 = s.pos.getZ();
            if (used.contains(key(x0, z0))) continue;
            int x1 = x0, z1 = z0;
            while (x1 - x0 + 2 <= FILL_MAX && same(at, used, x1 + 1, z0, s.text)) x1++;   // as far as it goes along the row...
            int w = x1 - x0 + 1;
            grow:
            while ((long) (z1 - z0 + 2) * w <= FILL_MAX) {                                 // ...then as many rows as match all the way
                for (int x = x0; x <= x1; x++) if (!same(at, used, x, z1 + 1, s.text)) break grow;
                z1++;
            }
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) used.add(key(x, z));
            int n = w * (z1 - z0 + 1);
            out.add(new Cmd(n == 1 ? "setblock " + x0 + " " + y + " " + z0 + " " + s.text + " replace"
                    : "fill " + x0 + " " + y + " " + z0 + " " + x1 + " " + y + " " + z1 + " " + s.text + " replace", n));
        }
    }

    private static BlockState parse(String st) {
        try { BlockState b = V.parseState(st); return b.isAir() ? null : b; }
        catch (Exception e) { return null; }
    }

    private static void tick(Minecraft mc) {
        if (recheckIn > 0 && --recheckIn == 0 && !running()) { secondPass = true; start(lastLayer, true); return; }
        if (!running()) return;
        if (mc.player == null || mc.level == null) { queue = new ArrayList<>(); next = 0; return; }
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            if (batchPending) return;   // the game is still placing the last batch
            if (lastBatchNs > 0) {      // aim for about 25 ms of the game's time per batch
                double f = Math.max(0.5, Math.min(2.0, 25_000_000.0 / lastBatchNs));
                perTickLocal = (int) Math.max(100, Math.min(50_000, perTickLocal * f));
            }
            var server = mc.getSingleplayerServer();
            var dim = mc.level.dimension();
            int end = Math.min(queue.size(), next + perTickLocal);
            List<Job> batch = new ArrayList<>(queue.subList(next, end));
            next = end;
            batchPending = true;
            server.execute(() -> {
                try {
                    long t0 = System.nanoTime();
                    ServerLevel lvl = server.getLevel(dim);
                    if (lvl == null) return;
                    for (Job j : batch) lvl.setBlock(j.pos, j.state, FLAGS);
                    lastBatchNs = System.nanoTime() - t0;
                } finally { batchPending = false; }
            });
        } else {
            if (cmds == null) { cmds = commands(queue.subList(next, queue.size())); nextCmd = 0; }
            int sent = 0, blocks = 0;
            while (nextCmd < cmds.size() && sent < CMDS_PER_TICK && blocks < BLOCKS_PER_TICK_SERVER) {
                Cmd c = cmds.get(nextCmd++);
                V.command(mc.player, c.text);
                sent++; blocks += c.blocks; next += c.blocks;
            }
            if (nextCmd >= cmds.size()) next = queue.size();
        }
        if (!secondPass && (++barTick % 10 == 0 || !running()))
            Chat.bar(undoing ? "Undoing... " + next + " / " + total : "Auto-building... " + next + " / " + total);
        if (!running()) {
            if (undoing || !secondPass) Chat.ok(undoing ? "Undo finished." : "Auto-build finished! " + total + " blocks placed. Use Undo in the Page Up menu (or /buildblueprint autobuild undo) to take it back.");
            if (!undoing) BuildBlueprintClient.SESSION.locked = true;   // it's built now: don't let it slide away
            if (!undoing && !secondPass) recheckIn = mc.hasSingleplayerServer() ? 5 : 100;
            cmds = null;
            undoing = false;
            Materials.refresh();
        }
    }
}
