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
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { if (++tick % 20 == 0) Safe.run("materials list", Materials::refresh); });
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
        Map<String, int[]> per = new LinkedHashMap<>();          // item -> {left, total}
        Map<String, MaterialMap.Req> reqCache = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int done = 0, all = 0;
        Map<Long, String[]> wrong = new HashMap<>();
        List<Mistakes.Mistake> mistakes = new ArrayList<>();
        int[] perLayer = new int[bp.sy], totLayer = new int[bp.sy];
        byte[] status = new byte[bp.sx * bp.sy * bp.sz];   // 1 to place, 2 done, 3 wrong
        List<Map<String, int[]>> perItemLayer = new ArrayList<>();
        for (int y = 0; y < bp.sy; y++) perItemLayer.add(new LinkedHashMap<>());
        for (int y = 0; y < bp.sy; y++)
            for (int z = 0; z < bp.sz; z++)
                for (int x = 0; x < bp.sx; x++) {
                    String st = bp.stateAt(x, y, z);
                    if (st == null || !Coop.mine(bp, x, z)) continue;   // co-op: only my part of the build
                    MaterialMap.Req r = reqCache.computeIfAbsent(st, MaterialMap::of);
                    if (r == null) continue;
                    all++;
                    int[] c = per.computeIfAbsent(r.itemId(), k -> new int[2]);
                    c[1] += r.count();
                    int[] cl = perItemLayer.get(y).computeIfAbsent(r.itemId(), k -> new int[2]);
                    cl[1] += r.count(); totLayer[y]++;
                    pos.set(s.ox + x, s.oy + y, s.oz + z);
                    net.minecraft.world.level.block.state.BlockState hs = mc.level.getBlockState(pos);
                    net.minecraft.world.level.block.Block hb = hs.getBlock();
                    String have = V.blockId(hb);
                    int idx = bp.index(x, y, z);
                    if (have.equals(MaterialMap.blockId(st))) {
                        if (st.indexOf('[') < 0 || Orient.ok(st, hs.toString())) { done++; status[idx] = 2; }
                        else {   // there, but the wrong way round
                            perLayer[y]++; status[idx] = 3;
                            wrong.put(pos.asLong(), new String[]{st, hs.toString(), r.itemId()});
                            mistakes.add(Mistakes.make(pos.asLong(), st, hs.toString(), r.itemId(), true));
                        }
                    } else {
                        c[0] += r.count(); cl[0] += r.count(); perLayer[y]++;
                        if (solid(hs)) { status[idx] = 3; mistakes.add(Mistakes.make(pos.asLong(), st, "Block{" + have + "}", r.itemId(), false)); }
                        else status[idx] = 1;
                    }
                }
        Map<String, Integer> inv = PlayerInfo.inventoryCounts();
        rows = toRows(per, inv);
        Layers.auto(perLayer);
        int L = s.layer;
        if (L < 0) { L = 0; for (int y = 0; y < bp.sy; y++) if (perLayer[y] > 0) { L = y; break; } }
        L = Math.max(0, Math.min(bp.sy - 1, L));
        layer = L; layerTotal = totLayer[L]; layerPlaced = totLayer[L] - perLayer[L];
        layerRows = toRows(perItemLayer.get(L), inv);
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
                cells[z * bp.sx + x] = v == 0 || status[i] == 0 ? -1 : (status[i] << 24) | (v - 1);
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
                boolean ok = status[i] == 2 || status[i] == 3;   // already a block there
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
