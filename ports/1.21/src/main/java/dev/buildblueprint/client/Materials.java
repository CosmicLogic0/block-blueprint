package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.MaterialMap;
import dev.buildblueprint.core.Orient;
import dev.buildblueprint.core.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps an up-to-date list of what the loaded blueprint still needs: for each item, how many blocks are not yet placed
 * in the world, how many the whole build uses, and how many you are carrying. Refreshed once a second.
 */
public final class Materials {
    private Materials() {}

    /** left = still to place, total = whole build, have = in your inventory. */
    public record Row(String id, ItemStack icon, String name, int left, int total, int have) {
        public int missing() { return Math.max(0, left - have); }
        public boolean done() { return left == 0; }
        public boolean enough() { return have >= left; }
    }

    private static List<Row> rows = java.util.Collections.emptyList(), layerRows = java.util.Collections.emptyList();
    private static int placed, totalBlocks, layer = 0, layerPlaced, layerTotal;
    private static int tick;
    private static Map<String, Item> itemIndex;

    public static List<Row> rows() { return rows; }
    public static int placed() { return placed; }
    public static int totalBlocks() { return totalBlocks; }
    /** Materials for one layer: the layer being shown, or (when showing everything) the lowest unfinished layer. */
    public static List<Row> layerRows() { return layerRows; }
    public static int layer() { return layer; }
    public static int layerPlaced() { return layerPlaced; }
    public static int layerTotal() { return layerTotal; }
    /** What the on-screen list should show right now, following the "Materials list" setting. */
    public static boolean showLayer() {
        String m = Settings.matMode;
        return m.equals("layer") || (m.equals("auto") && BuildBlueprintClient.SESSION.layer >= 0);
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {   // about once a second; less often for huge builds, so counting them never takes over the game
            if (++tick < nextTick) return;
            long t0 = System.nanoTime();
            Safe.run("materials list", Materials::refresh);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            nextTick = tick + (int) Math.max(20, Math.min(1200, ms / 5));
        });
    }

    public static ItemStack stackFor(String id) {
        if (itemIndex == null) {
            itemIndex = new HashMap<>();
            for (Item it : V.allItems()) itemIndex.put(V.itemId(it), it);
        }
        Item it = itemIndex.get(id);
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    private static List<Row> toRows(Map<String, int[]> per, Map<String, Integer> inv) {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, int[]> e : per.entrySet()) {
            ItemStack icon = stackFor(e.getKey());
            String name = icon.isEmpty() ? PlayerInfo.pretty(e.getKey()) : icon.getHoverName().getString();
            out.add(new Row(e.getKey(), icon, name, e.getValue()[0], e.getValue()[1], inv.getOrDefault(e.getKey(), 0)));
        }
        // Most still-missing first, finished items last.
        out.sort((a, b) -> a.done() != b.done() ? (a.done() ? 1 : -1)
                : a.missing() != b.missing() ? Integer.compare(b.missing(), a.missing())
                : Integer.compare(b.left, a.left));
        return out;
    }

    /** Layer map: the shown layer as a grid. Each cell: -1 = nothing there, else (status << 24) | palette index; status 1 = to place, 2 = done, 3 = wrong. */
    private static int[] layerCells = new int[0];
    private static int mapW, mapD, mapLayer = -1;
    private static List<BlockPos> scaffold = java.util.Collections.emptyList();
    public static int[] layerCells() { return layerCells; }
    public static int mapW() { return mapW; }
    public static int mapD() { return mapD; }
    public static int mapLayer() { return mapLayer; }
    /** Scaffolding hints: where temporary blocks help to reach parts of this layer that float in the air. */
    public static List<BlockPos> scaffold() { return scaffold; }

    /** Is there something solid in the world here to place a block against? */
    private static boolean solid(net.minecraft.world.level.block.state.BlockState b) { return !b.isAir() && b.getFluidState().isEmpty() && !V.replaceable(b); }

    private static Blueprint palFor;
    private static MaterialMap.Req[] palReq;
    private static String[] palId;
    private static boolean[] palProps, palFluid;
    private static byte[] statusBuf = new byte[0];
    // What every block of the build was last seen as. Parts of the world that aren't loaded (too far away) keep what
    // they were last seen as, so the progress and the block list stay right when you walk away. Saved with the world.
    private static String statusKey = "";
    private static boolean statusChanged = false;
    private static String keyOf(Blueprint bp, Session s) { return System.identityHashCode(bp) + "@" + s.ox + "," + s.oy + "," + s.oz; }

    /** The progress to save (null if none): what each block of the build was last seen as. */
    public static byte[] progress() {
        Session s = BuildBlueprintClient.SESSION;
        if (!s.loaded() || !statusKey.equals(keyOf(s.blueprint(), s))) return null;
        statusChanged = false;
        return statusBuf.clone();
    }
    public static boolean progressChanged() { return statusChanged; }

    /** Brings back saved progress for the build that was just restored. */
    public static void restoreProgress(byte[] saved) {
        Session s = BuildBlueprintClient.SESSION;
        if (!s.loaded() || saved == null) return;
        Blueprint bp = s.blueprint();
        if (saved.length != bp.sx * bp.sy * bp.sz) return;
        statusBuf = saved; statusKey = keyOf(bp, s);
    }
    private static final Map<net.minecraft.world.level.block.Block, String> IDS = new java.util.IdentityHashMap<>();
    private static String idOf(net.minecraft.world.level.block.Block b) { return IDS.computeIfAbsent(b, k -> V.blockId(k)); }

    private static int nextTick = 20;
    private static final int MAX_MISTAKES = 50_000;   // a huge build sunk in the ground could have millions

    /** Recount now (also called when the menu opens). */
    public static void refresh() {
        Session s = BuildBlueprintClient.SESSION;
        Minecraft mc = Minecraft.getInstance();
        if (!s.loaded() || mc.level == null || mc.player == null) {
            rows = layerRows = java.util.Collections.emptyList(); placed = totalBlocks = layerPlaced = layerTotal = 0;
            layerCells = new int[0]; mapLayer = -1; scaffold = java.util.Collections.emptyList(); Mistakes.update(new ArrayList<>());
            return;
        }
        Blueprint bp = s.blueprint();
        if (palFor != bp) {   // what each kind of block in the build needs: worked out once per build, not for every block every second
            int n = bp.palette.length + 1;
            palReq = new MaterialMap.Req[n]; palId = new String[n]; palProps = new boolean[n]; palFluid = new boolean[n];
            for (int i = 1; i < n; i++) {
                String st = bp.palette[i - 1];
                if (st == null) continue;
                palReq[i] = MaterialMap.of(st); palId[i] = MaterialMap.blockId(st); palProps[i] = st.indexOf('[') >= 0; palFluid[i] = Orient.fluid(st);
            }
            palFor = bp;
        }
        int n = palReq.length;
        int[] tot = new int[n], left = new int[n], order = new int[n];
        int kinds = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int done = 0, all = 0;
        Map<Long, String[]> wrong = new HashMap<>();
        List<Mistakes.Mistake> mistakes = new ArrayList<>();
        int[] perLayer = new int[bp.sy], totLayer = new int[bp.sy];
        int vol = bp.sx * bp.sy * bp.sz;
        String key = keyOf(bp, s);
        if (!key.equals(statusKey) || statusBuf.length != vol) { statusBuf = new byte[vol]; statusKey = key; }   // another build, or moved: start fresh
        byte[] status = statusBuf;   // 0 not seen yet, 1 to place, 2 done, 3 something else in the way, 4 there but the wrong way round
        int cx0 = s.ox >> 4, cz0 = s.oz >> 4;
        boolean[][] loaded = new boolean[((s.ox + bp.sx - 1) >> 4) - cx0 + 1][((s.oz + bp.sz - 1) >> 4) - cz0 + 1];
        for (int a = 0; a < loaded.length; a++) for (int b = 0; b < loaded[a].length; b++) loaded[a][b] = mc.level.getChunkSource().hasChunk(cx0 + a, cz0 + b);
        for (int y = 0; y < bp.sy; y++)
            for (int z = 0; z < bp.sz; z++) {
                int row = bp.index(0, y, z);
                for (int x = 0; x < bp.sx; x++) {
                    int idx = row + x, c = bp.cells[idx];
                    boolean here = loaded[((s.ox + x) >> 4) - cx0][((s.oz + z) >> 4) - cz0];
                    if (c == 0 || palReq[c] == null || !Coop.mine(bp, x, z)) { if (here) status[idx] = 0; continue; }   // co-op: only my part of the build
                    MaterialMap.Req r = palReq[c];
                    String st = bp.palette[c - 1];
                    all++;
                    if (tot[c]++ == 0) order[kinds++] = c;
                    totLayer[y]++;
                    if (!here) {   // too far away to see: what it was last time
                        byte was = status[idx];
                        if (was == 2) done++;
                        else if (was == 4) perLayer[y]++;
                        else { left[c]++; perLayer[y]++; if (was == 0) status[idx] = 1; }
                        continue;
                    }
                    byte before = status[idx];
                    pos.set(s.ox + x, s.oy + y, s.oz + z);
                    net.minecraft.world.level.block.state.BlockState hs = mc.level.getBlockState(pos);
                    String have = idOf(hs.getBlock());
                    if (palFluid[c] && MaterialMap.sameBlock(have, palId[c]) && !Orient.fluidOk(st, hs.toString())) { left[c]++; perLayer[y]++; status[idx] = 1; }   // flowing water, or a block still missing its water
                    else if (MaterialMap.sameBlock(have, palId[c])) {   // (dirt counts for grass)
                        if (!palProps[c] || !have.equals(palId[c]) || Orient.ok(st, hs.toString())) { done++; status[idx] = 2; }
                        else {   // there, but the wrong way round
                            perLayer[y]++; status[idx] = 4;
                            if (wrong.size() < MAX_MISTAKES) wrong.put(pos.asLong(), new String[]{st, hs.toString(), r.itemId()});
                            if (mistakes.size() < MAX_MISTAKES) mistakes.add(Mistakes.make(pos.asLong(), st, hs.toString(), r.itemId(), true));
                        }
                    } else {
                        left[c]++; perLayer[y]++;
                        if (solid(hs)) { status[idx] = 3; if (mistakes.size() < MAX_MISTAKES) mistakes.add(Mistakes.make(pos.asLong(), st, "Block{" + have + "}", r.itemId(), false)); }
                        else status[idx] = 1;
                    }
                    if (status[idx] != before) statusChanged = true;
                }
            }
        Map<String, int[]> per = new LinkedHashMap<>();          // item -> {left, total}, in the order they first turn up
        for (int k = 0; k < kinds; k++) {
            int c = order[k];
            MaterialMap.Req r = palReq[c];
            int[] e = per.computeIfAbsent(r.itemId(), q -> new int[2]);
            e[0] += left[c] * r.count(); e[1] += tot[c] * r.count();
        }
        Map<String, Integer> inv = PlayerInfo.inventoryCounts();
        rows = toRows(per, inv);
        Layers.auto(perLayer);
        int L = s.layer;
        if (L < 0) { L = 0; for (int y = 0; y < bp.sy; y++) if (perLayer[y] > 0) { L = y; break; } }
        L = Math.max(0, Math.min(bp.sy - 1, L));
        layer = L; layerTotal = totLayer[L]; layerPlaced = totLayer[L] - perLayer[L];
        Map<String, int[]> perL = new LinkedHashMap<>();
        for (int z = 0; z < bp.sz; z++)
            for (int x = 0; x < bp.sx; x++) {
                int idx = bp.index(x, L, z), c = bp.cells[idx];
                if (c == 0 || status[idx] == 0) continue;
                MaterialMap.Req r = palReq[c];
                int[] e = perL.computeIfAbsent(r.itemId(), q -> new int[2]);
                e[1] += r.count();
                if (status[idx] == 1 || status[idx] == 3) e[0] += r.count();
            }
        layerRows = toRows(perL, inv);
        placed = done;
        sayWrong(wrong, mc);
        Mistakes.update(mistakes);
        totalBlocks = all;
        Stats.update(bp, done, all);
        // layer map
        int[] cells = new int[bp.sx * bp.sz];
        for (int z = 0; z < bp.sz; z++)
            for (int x = 0; x < bp.sx; x++) {
                int i = bp.index(x, L, z), v = bp.cells[i];
                cells[z * bp.sx + x] = v == 0 || status[i] == 0 ? -1 : (Math.min(3, (int) status[i]) << 24) | (v - 1);
            }
        layerCells = cells; mapW = bp.sx; mapD = bp.sz; mapLayer = L;
        scaffold = Settings.scaffold ? findScaffold(bp, s, mc, status, L) : java.util.Collections.<BlockPos>emptyList();
    }

    /**
     * Parts of layer L still to place that don't touch anything solid - not the world, and not a block of the layer that
     * can be reached from something solid - float in the air. For each such floating group, one column of temporary
     * blocks from the ground up to just under it shows where to put scaffolding.
     */
    private static List<BlockPos> findScaffold(Blueprint bp, Session s, Minecraft mc, byte[] status, int L) {
        int W = bp.sx, D = bp.sz;
        boolean[] reach = new boolean[W * D];
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int[][] six = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int z = 0; z < D; z++)
            for (int x = 0; x < W; x++) {
                int i = bp.index(x, L, z);
                if (bp.cells[i] == 0 || status[i] == 0) continue;
                boolean ok = status[i] >= 2;   // already a block there
                for (int[] d : six) {
                    if (ok) break;
                    p.set(s.ox + x + d[0], s.oy + L + d[1], s.oz + z + d[2]);
                    if (solid(mc.level.getBlockState(p))) ok = true;
                }
                if (ok) { reach[z * W + x] = true; q.add(z * W + x); }
            }
        while (!q.isEmpty()) {   // anything joined to a reachable block can be placed against it
            int c = q.poll(), x = c % W, z = c / W;
            int[][] four = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : four) {
                int nx = x + d[0], nz = z + d[1];
                if (nx < 0 || nz < 0 || nx >= W || nz >= D) continue;
                int n = nz * W + nx;
                if (reach[n] || bp.cells[bp.index(nx, L, nz)] == 0 || status[bp.index(nx, L, nz)] == 0) continue;
                reach[n] = true; q.add(n);
            }
        }
        List<BlockPos> out = new ArrayList<>();
        boolean[] seen = new boolean[W * D];
        for (int z = 0; z < D; z++)
            for (int x = 0; x < W; x++) {
                int c = z * W + x;
                if (seen[c] || reach[c] || bp.cells[bp.index(x, L, z)] == 0 || status[bp.index(x, L, z)] != 1) continue;
                // one floating group: find the cell with the shortest drop to the ground
                java.util.ArrayDeque<Integer> g = new java.util.ArrayDeque<>();
                g.add(c); seen[c] = true;
                int bestX = x, bestZ = z, bestDrop = Integer.MAX_VALUE;
                while (!g.isEmpty()) {
                    int k = g.poll(), gx = k % W, gz = k / W;
                    int drop = 0;
                    for (int y = s.oy + L - 1; y > s.oy + L - 24; y--) { p.set(s.ox + gx, y, s.oz + gz); if (solid(mc.level.getBlockState(p))) break; drop++; }
                    if (drop < bestDrop) { bestDrop = drop; bestX = gx; bestZ = gz; }
                    int[][] four = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] d : four) {
                        int nx = gx + d[0], nz = gz + d[1];
                        if (nx < 0 || nz < 0 || nx >= W || nz >= D) continue;
                        int n = nz * W + nx;
                        if (seen[n] || reach[n] || bp.cells[bp.index(nx, L, nz)] == 0 || status[bp.index(nx, L, nz)] != 1) continue;
                        seen[n] = true; g.add(n);
                    }
                }
                for (int i = 1; i <= Math.min(bestDrop, 23); i++) out.add(new BlockPos(s.ox + bestX, s.oy + L - i, s.oz + bestZ));
                if (out.size() > 400) return out;
            }
        return out;
    }

    private static java.util.Set<Long> wrongBefore = new java.util.HashSet<>();
    private static int wrongCount;
    public static int wrongWay() { return wrongCount; }
    /** A block that was just placed the wrong way round (stairs upside down, a log on its side...): say how it should go. */
    private static void sayWrong(Map<Long, String[]> wrong, Minecraft mc) {
        wrongCount = wrong.size();
        String[] first = null; double best = 0;
        for (Map.Entry<Long, String[]> e : wrong.entrySet()) {
            if (wrongBefore.contains(e.getKey())) continue;
            BlockPos b = BlockPos.of(e.getKey());
            double dx = b.getX() + 0.5 - mc.player.getX(), dy = b.getY() + 0.5 - mc.player.getY(), dz = b.getZ() + 0.5 - mc.player.getZ(), d = dx * dx + dy * dy + dz * dz;
            if (d > 100 || (first != null && d >= best)) continue;
            first = e.getValue(); best = d;
        }
        boolean fresh = !wrongBefore.isEmpty() || wrong.size() < 50;   // not right after loading a build into an old one
        wrongBefore = new java.util.HashSet<>(wrong.keySet());
        if (first == null || !fresh) return;
        ItemStack it = stackFor(first[2]);
        String name = it.isEmpty() ? PlayerInfo.pretty(MaterialMap.blockId(first[0])) : it.getHoverName().getString();
        Chat.bar("Wrong way round: this " + name + " " + Orient.fix(first[0], first[1]) + "  ·  break it and place it again");
    }
}
