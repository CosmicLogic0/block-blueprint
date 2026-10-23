package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.MaterialMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Block swapping: change one block for another everywhere in the blueprint, or change a whole "set" at once
 * (every oak piece to spruce, every stone-brick piece to deepslate tiles, every black wool/carpet/glass to red...).
 * Stairs stay stairs, slabs stay slabs, and their facing/shape is kept.
 */
public final class Swapper {
    private Swapper() {}

    /** A material set: what the full block is called and the prefix its shaped pieces use. */
    public record Mat(String key, String label, String full, String prefix, String kind) {}

    public static final List<Mat> WOODS = new ArrayList<>(), STONES = new ArrayList<>(), COLORS = new ArrayList<>();
    static {
        for (String w : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped"})
            WOODS.add(new Mat(w, PlayerInfo.pretty(w), w + "_planks", w, "wood"));
        String[][] st = {
            {"stone", "stone"}, {"cobblestone", "cobblestone"}, {"mossy_cobblestone", "mossy_cobblestone"}, {"smooth_stone", "smooth_stone"},
            {"stone_bricks", "stone_brick"}, {"mossy_stone_bricks", "mossy_stone_brick"}, {"granite", "granite"}, {"polished_granite", "polished_granite"},
            {"diorite", "diorite"}, {"polished_diorite", "polished_diorite"}, {"andesite", "andesite"}, {"polished_andesite", "polished_andesite"},
            {"cobbled_deepslate", "cobbled_deepslate"}, {"polished_deepslate", "polished_deepslate"}, {"deepslate_bricks", "deepslate_brick"},
            {"deepslate_tiles", "deepslate_tile"}, {"tuff", "tuff"}, {"polished_tuff", "polished_tuff"}, {"tuff_bricks", "tuff_brick"},
            {"bricks", "brick"}, {"mud_bricks", "mud_brick"}, {"sandstone", "sandstone"}, {"smooth_sandstone", "smooth_sandstone"},
            {"red_sandstone", "red_sandstone"}, {"smooth_red_sandstone", "smooth_red_sandstone"}, {"prismarine", "prismarine"},
            {"prismarine_bricks", "prismarine_brick"}, {"dark_prismarine", "dark_prismarine"}, {"nether_bricks", "nether_brick"},
            {"red_nether_bricks", "red_nether_brick"}, {"blackstone", "blackstone"}, {"polished_blackstone", "polished_blackstone"},
            {"polished_blackstone_bricks", "polished_blackstone_brick"}, {"end_stone_bricks", "end_stone_brick"}, {"purpur_block", "purpur"},
            {"quartz_block", "quartz"}, {"smooth_quartz", "smooth_quartz"}, {"resin_bricks", "resin_brick"}};
        for (String[] s : st) STONES.add(new Mat(s[0], PlayerInfo.pretty(s[0]), s[0], s[1], "stone"));
        for (String c : new String[]{"white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow", "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"})
            COLORS.add(new Mat(c, PlayerInfo.pretty(c), c + "_wool", c, "color"));
    }

    private static final String[] WOOD_FORMS = {"_planks", "_log", "_wood", "_stem", "_hyphae", "_stairs", "_slab", "_fence_gate", "_fence", "_door", "_trapdoor",
            "_button", "_pressure_plate", "_wall_hanging_sign", "_hanging_sign", "_wall_sign", "_sign", "_leaves", "_shelf", "_block", "_mosaic", "_mosaic_stairs", "_mosaic_slab"};
    private static final String[] STONE_FORMS = {"_stairs", "_slab", "_wall"};
    private static final String[] COLOR_FORMS = {"_wool", "_carpet", "_concrete", "_concrete_powder", "_terracotta", "_glazed_terracotta", "_stained_glass_pane",
            "_stained_glass", "_wall_banner", "_banner", "_bed", "_candle", "_shulker_box"};

    public static boolean exists(String id) {
        try { return V.blockExists(id); } catch (Exception e) { return false; }
    }
    private static String path(String id) { return id.startsWith("minecraft:") ? id.substring(10) : id; }

    /** Which set a block belongs to (null if none). */
    public static Mat matOf(String blockId) {
        String n = path(blockId);
        if (n.startsWith("stripped_")) n = n.substring(9);
        for (Mat m : STONES) { if (n.equals(m.full())) return m; for (String f : STONE_FORMS) if (n.equals(m.prefix() + f)) return m; }
        for (Mat m : COLORS) for (String f : COLOR_FORMS) if (n.equals(m.prefix() + f)) return m;
        Mat best = null;
        for (Mat m : WOODS) for (String f : WOOD_FORMS) if (n.equals(m.prefix() + f) && (best == null || m.prefix().length() > best.prefix().length())) best = m;
        return best;
    }

    /** blockId in set a -> the same piece in set b (or null when b has no such piece). */
    public static String mapId(String blockId, Mat a, Mat b) {
        String n = path(blockId);
        boolean stripped = n.startsWith("stripped_");
        if (stripped) n = n.substring(9);
        String out = null;
        if (a.kind().equals("stone")) {
            if (n.equals(a.full())) out = b.full();
            else for (String f : STONE_FORMS) if (n.equals(a.prefix() + f)) out = b.prefix() + f;
        } else if (n.startsWith(a.prefix() + "_")) {
            String form = n.substring(a.prefix().length());
            out = b.prefix() + form;
            // logs are called stems / blocks for some woods
            if (!exists("minecraft:" + (stripped ? "stripped_" : "") + out)) {
                if (form.equals("_log")) out = b.prefix() + (b.key().equals("bamboo") ? "_block" : "_stem");
                else if (form.equals("_stem")) out = b.prefix() + "_log";
                else if (form.equals("_wood")) out = b.prefix() + "_hyphae";
                else if (form.equals("_hyphae")) out = b.prefix() + "_wood";
            }
        }
        if (out == null) return null;
        out = "minecraft:" + (stripped ? "stripped_" : "") + out;
        return exists(out) ? out : null;
    }

    /** Swap the block in a state string, keeping every property the new block also has. */
    public static String reState(String state, String newId) {
        int b = state.indexOf('[');
        Block nb = V.block(newId);
        if (b < 0) return newId;
        Map<String, String> kv = new LinkedHashMap<>();
        for (String p : state.substring(b + 1, state.length() - 1).split(",")) { int i = p.indexOf('='); if (i > 0) kv.put(p.substring(0, i), p.substring(i + 1)); }
        BlockState def = nb.defaultBlockState();
        StringBuilder sb = new StringBuilder();
        for (Property<?> pr : def.getProperties()) {
            String v = kv.get(pr.getName());
            if (v == null || pr.getValue(v).isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(pr.getName()).append('=').append(v);
        }
        return sb.length() == 0 ? newId : newId + "[" + sb + "]";
    }

    /** Block ids in the current blueprint whose item is this item (the materials list works with items). */
    public static Set<String> blocksForItem(String itemId) {
        Set<String> out = new LinkedHashSet<>();
        Blueprint bp = BuildBlueprintClient.SESSION.blueprint();
        if (bp == null) return out;
        for (String st : bp.palette) { MaterialMap.Req r = MaterialMap.of(st); if (r != null && r.itemId().equals(itemId)) out.add(MaterialMap.blockId(st)); }
        return out;
    }

    /** How many blocks in the build each block id has. */
    public static Map<String, Integer> counts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        Blueprint bp = BuildBlueprintClient.SESSION.blueprint();
        if (bp == null) return out;
        int[] per = new int[bp.palette.length];
        for (int c : bp.cells) if (c != 0) per[c - 1]++;
        for (int i = 0; i < per.length; i++) if (per[i] > 0) out.merge(MaterialMap.blockId(bp.palette[i]), per[i], Integer::sum);
        return out;
    }

    /** Apply: map of old block id -> new block id. Returns how many blocks changed. */
    public static int apply(Map<String, String> map) {
        if (map.isEmpty()) return 0;
        Map<String, Integer> c = counts();
        int n = 0;
        for (String k : map.keySet()) n += c.getOrDefault(k, 0);
        BuildBlueprintClient.SESSION.mapPalette(st -> { String id = MaterialMap.blockId(st); String to = map.get(id); return to == null ? st : reState(st, to); });
        Materials.refresh();
        SessionStore.dirty();
        return n;
    }

    /** The whole-set swap a -> b for this blueprint (old id -> new id). */
    public static Map<String, String> setSwap(Mat a, Mat b) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String id : counts().keySet()) {
            Mat of = matOf(id);
            if (of == null || !of.key().equals(a.key())) continue;
            String to = mapId(id, a, b);
            if (to != null) m.put(id, to);
        }
        return m;
    }

    public static List<Mat> setsLike(Mat a) { return a.kind().equals("wood") ? WOODS : a.kind().equals("stone") ? STONES : COLORS; }

    /** Blocks that can replace this one on its own: same kind of piece (stairs for stairs...), or any full block. */
    public static List<String> singleCandidates(String blockId) {
        String n = path(blockId);
        String suffix = null;
        for (String f : new String[]{"_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_door", "_trapdoor", "_button", "_pressure_plate", "_carpet", "_stained_glass_pane", "_pane", "_wall_sign", "_sign", "_banner", "_candle", "_lantern"})
            if (n.endsWith(f)) { suffix = f; break; }
        List<String> out = new ArrayList<>();
        BlockState self = V.block(blockId).defaultBlockState();
        boolean cube = V.solid(self) || V.glass(self.getBlock());
        for (Block blk : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(blk).toString();
            if (id.equals(blockId) || blk.asItem() == net.minecraft.world.item.Items.AIR) continue;
            String p = path(id);
            if (suffix != null) { if (p.endsWith(suffix) && !(suffix.equals("_slab") && false)) out.add(id); }
            else if (cube) { BlockState d = blk.defaultBlockState(); if ((V.solid(d) || V.glass(blk)) && !d.hasBlockEntity()) out.add(id); }
            else if (blk.getClass() == self.getBlock().getClass()) out.add(id);
        }
        return out;
    }
}
