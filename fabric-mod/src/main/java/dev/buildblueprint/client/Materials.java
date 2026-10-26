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
import net.minecraft.core.registries.BuiltInRegistries;
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

    private static List<Row> rows = List.of(), layerRows = List.of();
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
            for (Item it : BuiltInRegistries.ITEM) itemIndex.put(BuiltInRegistries.ITEM.getKey(it).toString(), it);
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

    /** Recount now (also called when the menu opens). */
    public static void refresh() {
        Session s = BuildBlueprintClient.SESSION;
        Minecraft mc = Minecraft.getInstance();
        if (!s.loaded() || mc.level == null || mc.player == null) { rows = layerRows = List.of(); placed = totalBlocks = layerPlaced = layerTotal = 0; return; }
        Blueprint bp = s.blueprint();
        Map<String, int[]> per = new LinkedHashMap<>();          // item -> {left, total}
        Map<String, MaterialMap.Req> reqCache = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int done = 0, all = 0;
        Map<Long, String[]> wrong = new HashMap<>();
        int[] perLayer = new int[bp.sy], totLayer = new int[bp.sy];
        List<Map<String, int[]>> perItemLayer = new ArrayList<>();
        for (int y = 0; y < bp.sy; y++) perItemLayer.add(new LinkedHashMap<>());
        for (int y = 0; y < bp.sy; y++)
            for (int z = 0; z < bp.sz; z++)
                for (int x = 0; x < bp.sx; x++) {
                    String st = bp.stateAt(x, y, z);
                    if (st == null) continue;
                    MaterialMap.Req r = reqCache.computeIfAbsent(st, MaterialMap::of);
                    if (r == null) continue;
                    all++;
                    int[] c = per.computeIfAbsent(r.itemId(), k -> new int[2]);
                    c[1] += r.count();
                    int[] cl = perItemLayer.get(y).computeIfAbsent(r.itemId(), k -> new int[2]);
                    cl[1] += r.count(); totLayer[y]++;
                    pos.set(s.ox + x, s.oy + y, s.oz + z);
                    net.minecraft.world.level.block.state.BlockState hs = mc.level.getBlockState(pos);
                    String have = BuiltInRegistries.BLOCK.getKey(hs.getBlock()).toString();
                    if (have.equals(MaterialMap.blockId(st))) {
                        if (st.indexOf('[') < 0 || Orient.ok(st, hs.toString())) done++;
                        else { perLayer[y]++; wrong.put(pos.asLong(), new String[]{st, hs.toString(), r.itemId()}); }   // there, but the wrong way round
                    }
                    else { c[0] += r.count(); cl[0] += r.count(); perLayer[y]++; }
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
        totalBlocks = all;
        Stats.update(bp, done, all);
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
