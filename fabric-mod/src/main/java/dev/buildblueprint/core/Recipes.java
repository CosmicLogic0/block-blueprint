package dev.buildblueprint.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Crafting breakdown: turns the blocks a build needs into the raw things you gather. Spruce stairs -> spruce planks ->
 * spruce logs, glass panes -> glass -> sand, stone bricks -> stone -> cobblestone (smelted), and so on. Uses the plain
 * crafting table / furnace amounts every player can make. Blocks it doesn't know (and blocks from mods) stay as they are.
 */
public final class Recipes {
    private Recipes() {}

    /** One step: making 1 of something takes {@code amount} of {@code from}. */
    public static final class Step {
        public final String from; public final double amount; public final String how;
        Step(String from, double amount, String how) { this.from = from; this.amount = amount; this.how = how; }
    }

    /** One raw material: how many to gather, and which blocks of the build it is for. */
    public static final class Raw {
        public final String id; public double amount; public final List<String> forItems = new ArrayList<>(); public boolean smelt;
        Raw(String id) { this.id = id; }
        public int count() { return (int) Math.ceil(amount - 1e-9); }
    }

    private static final String[] WOODS = {"pale_oak", "dark_oak", "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped"};
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    private static String wood(String p) { for (String w : WOODS) if (p.startsWith(w + "_")) return w; return null; }
    private static String color(String p) { for (String c : COLORS) if (p.startsWith(c + "_")) return c; return null; }
    private static String mc(String p) { return "minecraft:" + p; }

    /** What 1 of this item is made from (null = it's raw, or we don't know it). */
    public static Step step(String itemId, Predicate<String> exists) {
        if (!itemId.startsWith("minecraft:")) return null;
        String p = itemId.substring(10);
        String w = wood(p);
        if (w != null) {
            String rest = p.substring(w.length() + 1);
            String log = w.equals("crimson") || w.equals("warped") ? w + "_stem" : w.equals("bamboo") ? "bamboo_block" : w + "_log";
            String planks = mc(w + "_planks");
            switch (rest) {
                case "planks": return new Step(mc(log), w.equals("bamboo") ? 0.5 : 0.25, "craft");
                case "stairs": return new Step(planks, 1.5, "craft");
                case "slab": return new Step(planks, 0.5, "craft");
                case "fence": return new Step(planks, 5.0 / 3, "craft");
                case "fence_gate": return new Step(planks, 4, "craft");
                case "door": return new Step(planks, 2, "craft");
                case "trapdoor": return new Step(planks, 3, "craft");
                case "pressure_plate": return new Step(planks, 2, "craft");
                case "button": return new Step(planks, 1, "craft");
                case "sign": return new Step(planks, 6.5 / 3, "craft");
                case "hanging_sign": return null;
                case "wood": case "hyphae": return new Step(mc(log), 4.0 / 3, "craft");
                default: break;
            }
        }
        if (p.startsWith("stripped_")) {   // stripped log / wood: the normal one, with an axe
            String base = mc(p.substring(9));
            return exists.test(base) ? new Step(base, 1, "axe") : null;
        }
        String c = color(p);
        if (c != null) {
            String rest = p.substring(c.length() + 1);
            switch (rest) {
                case "stained_glass": return new Step(mc("glass"), 1, "craft");
                case "stained_glass_pane": return new Step(mc(c + "_stained_glass"), 6.0 / 16, "craft");
                case "terracotta": return new Step(mc("terracotta"), 1, "craft");
                case "glazed_terracotta": return new Step(mc(c + "_terracotta"), 1, "smelt");
                case "concrete": return new Step(mc(c + "_concrete_powder"), 1, "water");
                case "concrete_powder": return new Step(mc("sand"), 0.5, "craft");
                case "carpet": return new Step(mc(c + "_wool"), 2.0 / 3, "craft");
                default: break;
            }
        }
        switch (p) {
            case "stone": return new Step(mc("cobblestone"), 1, "smelt");
            case "smooth_stone": return new Step(mc("stone"), 1, "smelt");
            case "stone_bricks": return new Step(mc("stone"), 1, "craft");
            case "cracked_stone_bricks": return new Step(mc("stone_bricks"), 1, "smelt");
            case "chiseled_stone_bricks": return new Step(mc("stone_brick_slab"), 2, "craft");
            case "mossy_cobblestone": return new Step(mc("cobblestone"), 1, "craft");
            case "mossy_stone_bricks": return new Step(mc("stone_bricks"), 1, "craft");
            case "glass": return new Step(mc("sand"), 1, "smelt");
            case "glass_pane": return new Step(mc("glass"), 6.0 / 16, "craft");
            case "tinted_glass": return null;
            case "bricks": return new Step(mc("clay_ball"), 4, "smelt");
            case "terracotta": return new Step(mc("clay"), 1, "smelt");
            case "sandstone": return new Step(mc("sand"), 4, "craft");
            case "red_sandstone": return new Step(mc("red_sand"), 4, "craft");
            case "cut_sandstone": case "chiseled_sandstone": return new Step(mc("sandstone"), 1, "craft");
            case "cut_red_sandstone": case "chiseled_red_sandstone": return new Step(mc("red_sandstone"), 1, "craft");
            case "smooth_sandstone": return new Step(mc("sandstone"), 1, "smelt");
            case "smooth_red_sandstone": return new Step(mc("red_sandstone"), 1, "smelt");
            case "polished_andesite": return new Step(mc("andesite"), 1, "craft");
            case "polished_diorite": return new Step(mc("diorite"), 1, "craft");
            case "polished_granite": return new Step(mc("granite"), 1, "craft");
            case "deepslate": return new Step(mc("cobbled_deepslate"), 1, "smelt");
            case "polished_deepslate": return new Step(mc("cobbled_deepslate"), 1, "craft");
            case "deepslate_bricks": return new Step(mc("polished_deepslate"), 1, "craft");
            case "deepslate_tiles": return new Step(mc("deepslate_bricks"), 1, "craft");
            case "cracked_deepslate_bricks": return new Step(mc("deepslate_bricks"), 1, "smelt");
            case "cracked_deepslate_tiles": return new Step(mc("deepslate_tiles"), 1, "smelt");
            case "polished_blackstone": return new Step(mc("blackstone"), 1, "craft");
            case "polished_blackstone_bricks": return new Step(mc("polished_blackstone"), 1, "craft");
            case "quartz_block": return new Step(mc("quartz"), 4, "craft");
            case "quartz_pillar": case "chiseled_quartz_block": case "quartz_bricks": return new Step(mc("quartz_block"), 1, "craft");
            case "smooth_quartz": return new Step(mc("quartz_block"), 1, "smelt");
            case "nether_bricks": return new Step(mc("nether_brick"), 4, "craft");
            case "nether_brick": return new Step(mc("netherrack"), 1, "smelt");
            case "end_stone_bricks": return new Step(mc("end_stone"), 1, "craft");
            case "purpur_block": return new Step(mc("popped_chorus_fruit"), 1, "craft");
            case "purpur_pillar": return new Step(mc("purpur_block"), 1, "craft");
            case "prismarine_bricks": return new Step(mc("prismarine_shard"), 9, "craft");
            case "prismarine": return new Step(mc("prismarine_shard"), 4, "craft");
            case "mud_bricks": return new Step(mc("packed_mud"), 1, "craft");
            case "tuff_bricks": case "polished_tuff": return new Step(mc("tuff"), 1, "craft");
            case "iron_bars": return new Step(mc("iron_ingot"), 6.0 / 16, "craft");
            case "torch": return new Step(mc("coal"), 0.25, "craft");
            case "lantern": return new Step(mc("iron_nugget"), 8, "craft");
            case "ladder": return new Step(mc("stick"), 7.0 / 3, "craft");
            case "stick": return new Step(mc("oak_planks"), 0.5, "craft");
            case "bookshelf": return new Step(mc("oak_planks"), 6, "craft");
            case "crafting_table": return new Step(mc("oak_planks"), 4, "craft");
            case "chest": return new Step(mc("oak_planks"), 8, "craft");
            case "barrel": return new Step(mc("oak_planks"), 7.0, "craft");
            case "furnace": return new Step(mc("cobblestone"), 8, "craft");
            case "white_wool": return null;
            case "white_carpet": return new Step(mc("white_wool"), 2.0 / 3, "craft");
            case "hay_block": return new Step(mc("wheat"), 9, "craft");
            case "packed_ice": return new Step(mc("ice"), 9, "craft");
            case "snow_block": return new Step(mc("snowball"), 4, "craft");
            case "clay": return new Step(mc("clay_ball"), 4, "craft");
            default: break;
        }
        // stone-type stairs / slabs / walls: made from the block they're named after (crafting table amounts)
        String[][] shapes = {{"_stairs", "1.5"}, {"_slab", "0.5"}, {"_wall", "1"}};
        for (String[] sh : shapes) {
            if (!p.endsWith(sh[0])) continue;
            String b = p.substring(0, p.length() - sh[0].length());
            for (String cand : new String[]{b + "s", b + "_block", b}) {   // stone_brick -> stone_bricks, quartz -> quartz_block, stone -> stone
                if (!cand.equals(p) && exists.test(mc(cand))) return new Step(mc(cand), Double.parseDouble(sh[1]), "craft");
            }
        }
        return null;
    }

    /**
     * Breaks needed items down to raw materials. {@code need}: item id -> how many still to get.
     * Returns raw item id -> Raw (amount, which build blocks it is for), most first.
     */
    public static List<Raw> breakDown(Map<String, Integer> need, Predicate<String> exists) {
        Map<String, Raw> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            if (e.getValue() <= 0) continue;
            String id = e.getKey(); double amt = e.getValue(); boolean smelt = false;
            for (int guard = 0; guard < 8; guard++) {
                Step s = step(id, exists);
                if (s == null || !exists.test(s.from)) break;
                if (s.how.equals("smelt")) smelt = true;
                id = s.from; amt *= s.amount;
            }
            Raw r = out.computeIfAbsent(id, Raw::new);
            r.amount += amt;
            if (smelt) r.smelt = true;
            if (!r.forItems.contains(e.getKey())) r.forItems.add(e.getKey());
        }
        List<Raw> list = new ArrayList<>(out.values());
        list.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return list;
    }
}
