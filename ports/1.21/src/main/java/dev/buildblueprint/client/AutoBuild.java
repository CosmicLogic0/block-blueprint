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

    private static final int CMDS_PER_TICK = 60, BLOCKS_PER_TICK_SERVER = 12_000, FILL_MAX = 32_768;   // at Normal speed
    private static final double[] SPEED = {0.25, 1, 2, 4, 8};   // Settings > Auto-build speed: slow, normal, fast, very fast, max
    private static double speed() { return SPEED[Math.max(0, Math.min(SPEED.length - 1, Settings.autoSpeed))]; }
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
    public static void start(int onlyLayer) { secondPass = false; farChunks = new java.util.HashSet<>(); start(onlyLayer, false, null); }

    // Parts of the build in chunks the game hasn't loaded can't be checked or placed yet: they wait here, and get built
    // as you come closer (and the chunks load). Blocks already placed are always skipped, so starting again is quick.
    private static java.util.Set<Long> farChunks = new java.util.HashSet<>();
    private static Blueprint farBp;
    private static int farOx, farOy, farOz, farTick = 0;
    private static boolean hadFar = false;
    private static void farDone() { if (hadFar && farChunks.isEmpty() && !running()) { hadFar = false; Chat.ok("Auto-build finished the far parts too!"); } }
    private static long chunkKey(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    private static void start(int onlyLayer, boolean quiet) { start(onlyLayer, quiet, null); }

    private static void start(int onlyLayer, boolean quiet, java.util.Set<Long> only) {
        Session s = BuildBlueprintClient.SESSION;
        Minecraft mc = Minecraft.getInstance();
        if (!PlayerInfo.inWorld()) return;
        if (!s.loaded()) { Chat.warn("Load a blueprint first."); return; }
        if (running()) { Chat.warn("Already building - /buildblueprint autobuild stop to stop."); return; }
        if (!creative()) return;
        Blueprint bp = s.blueprint();
        int n = bp.palette.length + 1;   // what each kind of block in the build is, worked out once (not for every block)
        BlockState[] wantP = new BlockState[n];
        String[] idP = new String[n], textP = new String[n];
        boolean[] propsP = new boolean[n], fluidP = new boolean[n];
        for (int i = 1; i < n; i++) {
            String st = bp.palette[i - 1];
            if (st == null || (wantP[i] = parse(st)) == null) continue;
            idP[i] = MaterialMap.blockId(st); propsP[i] = st.indexOf('[') >= 0; fluidP[i] = dev.buildblueprint.core.Orient.fluid(st); textP[i] = V.stateString(wantP[i]);
        }
        int cx0 = s.ox >> 4, cz0 = s.oz >> 4, cx1 = (s.ox + bp.sx - 1) >> 4, cz1 = (s.oz + bp.sz - 1) >> 4;
        boolean[][] loaded = new boolean[cx1 - cx0 + 1][cz1 - cz0 + 1];
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++)
            loaded[cx - cx0][cz - cz0] = (only == null || only.contains(chunkKey(cx, cz))) && mc.level.getChunkSource().hasChunk(cx, cz);
        java.util.Set<Long> far = new java.util.HashSet<>();
        Map<Block, String> ids = new java.util.IdentityHashMap<>();
        List<Job> jobs = new ArrayList<>(), before = new ArrayList<>();
        int kept = 0, farBlocks = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = 0; y < bp.sy; y++) {
            if (onlyLayer >= 0 && y != onlyLayer) continue;
            for (int z = 0; z < bp.sz; z++) for (int x = 0; x < bp.sx; x++) {
                int c = bp.cells[bp.index(x, y, z)];
                if (c == 0 || wantP[c] == null) continue;
                int wx = s.ox + x, wz = s.oz + z;
                if (!loaded[(wx >> 4) - cx0][(wz >> 4) - cz0]) {   // not loaded: later
                    if (only == null) { far.add(chunkKey(wx >> 4, wz >> 4)); farBlocks++; }
                    continue;
                }
                p.set(wx, s.oy + y, wz);
                BlockState here = mc.level.getBlockState(p);
                String have = ids.computeIfAbsent(here.getBlock(), b -> V.blockId(b));
                if (MaterialMap.sameBlock(have, idP[c]) && (!fluidP[c] || dev.buildblueprint.core.Orient.fluidOk(bp.palette[c - 1], here.toString())) && (!propsP[c] || !have.equals(idP[c]) || dev.buildblueprint.core.Orient.ok(bp.palette[c - 1], here.toString()))) continue; // already right (and the right way round)
                if (V.hasBE(here)) { kept++; continue; }   // don't wipe out chests, furnaces...
                BlockPos at = p.immutable();
                jobs.add(new Job(at, wantP[c], textP[c]));
                before.add(new Job(at, here, V.stateString(here)));
            }
        }
        if (only == null) { farChunks = far; farBp = bp; farOx = s.ox; farOy = s.oy; farOz = s.oz; hadFar = farBlocks > 0; }
        else farChunks.removeAll(only);
        if (only == null && farBlocks > 0 && !quiet)
            Chat.warn(farBlocks + " blocks are too far away to place yet (that part of the world isn't loaded). Auto-build carries on by itself as you get closer.");
        if (jobs.isEmpty()) {
            if (quiet) return;
            if (farBlocks > 0) return;   // the "too far away" message says it all
            Chat.ok(kept > 0 ? "Nothing to place (" + kept + " spots have chests or similar in the way)." : "Nothing to place - it's all built already!");
            return;
        }
        if (!quiet && !mc.hasSingleplayerServer()) Chat.warn("On a server this uses /setblock, so you need to be an operator.");
        if (quiet) before.addAll(undo);   // the second pass: undo still takes everything back (what was there first goes back last, so it wins)
        queue = jobs; next = 0; total = jobs.size(); undo = before; undoing = false; if (only == null) lastLayer = onlyLayer; cmds = null; batchPending = false;
        if (quiet) return;
        Chat.ok("Auto-building " + total + " blocks" + (onlyLayer >= 0 ? " on layer " + (onlyLayer + 1) : "") + "..."
                + (kept > 0 ? " (skipping " + kept + " spots with chests or similar in the way)" : ""));
    }

    public static void undoLast() {
        if (running()) { Chat.warn("Wait for the auto-build to finish (or stop it) first."); return; }
        if (undo.isEmpty()) { Chat.warn("Nothing to undo."); return; }
        if (!creative()) return;
        queue = new ArrayList<>(undo); next = 0; total = queue.size(); undo = new ArrayList<>(); undoing = true; cmds = null; batchPending = false;
        farChunks = new java.util.HashSet<>();
        Chat.ok("Undoing the last auto-build (" + total + " blocks)...");
    }

    public static void stop() {
        if (!running()) { Chat.say("Not auto-building right now."); return; }
        Chat.ok("Stopped after " + next + " of " + total + " blocks.");
        queue = new ArrayList<>(queue.subList(0, next));
        cmds = null;
        farChunks = new java.util.HashSet<>();
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
        if (mc.player == null || mc.level == null) { queue = new ArrayList<>(); next = 0; farChunks = new java.util.HashSet<>(); return; }
        if (!running() && recheckIn == 0 && !farChunks.isEmpty() && ++farTick % 40 == 0) {   // parts that were too far: built once their chunks load
            Session s = BuildBlueprintClient.SESSION;
            if (!s.loaded() || s.blueprint() != farBp || s.ox != farOx || s.oy != farOy || s.oz != farOz || !mc.player.isCreative()) { farChunks = new java.util.HashSet<>(); return; }
            java.util.Set<Long> now = new java.util.HashSet<>();
            for (long k : farChunks) if (mc.level.getChunkSource().hasChunk((int) (k >> 32), (int) k)) now.add(k);
            if (!now.isEmpty()) {
                secondPass = true;
                start(lastLayer, true, now);
                farDone();
            }
            return;
        }
        if (!running()) return;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            if (batchPending) return;   // the game is still placing the last batch
            if (lastBatchNs > 0) {      // aim for about 25 ms of the game's time per batch
                double f = Math.max(0.5, Math.min(2.0, Math.min(200_000_000.0, 25_000_000.0 * speed()) / lastBatchNs));
                perTickLocal = (int) Math.max(25, Math.min(50_000 * speed(), perTickLocal * f));
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
            int maxCmds = (int) Math.max(5, CMDS_PER_TICK * speed()), maxBlocks = (int) (BLOCKS_PER_TICK_SERVER * speed());
            while (nextCmd < cmds.size() && sent < maxCmds && blocks < maxBlocks) {
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
            if (!undoing && secondPass) farDone();
            cmds = null;
            undoing = false;
            Materials.refresh();
        }
    }
}
