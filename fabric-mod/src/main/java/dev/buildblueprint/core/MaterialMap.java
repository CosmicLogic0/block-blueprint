package dev.buildblueprint.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Works out which inventory item (and how many) a placed block state costs. */
public final class MaterialMap {
    private MaterialMap() {}

    public record Req(String itemId, int count) {}

    /** Blocks that have no item and are never hand-placed. */
    private static final Set<String> SKIP = Set.of(
            "air", "cave_air", "void_air", "water", "lava", "bubble_column", "fire", "soul_fire", "piston_head",
            "moving_piston", "nether_portal", "end_portal", "end_gateway", "frosted_ice", "light_block_placeholder");

    private static final Map<String, String> RENAME = Map.ofEntries(
            Map.entry("redstone_wire", "redstone"), Map.entry("redstone_wall_torch", "redstone_torch"),
            Map.entry("wall_torch", "torch"), Map.entry("soul_wall_torch", "soul_torch"),
            Map.entry("tripwire", "string"), Map.entry("carrots", "carrot"), Map.entry("potatoes", "potato"),
            Map.entry("beetroots", "beetroot_seeds"), Map.entry("wheat", "wheat_seeds"),
            Map.entry("melon_stem", "melon_seeds"), Map.entry("attached_melon_stem", "melon_seeds"),
            Map.entry("pumpkin_stem", "pumpkin_seeds"), Map.entry("attached_pumpkin_stem", "pumpkin_seeds"),
            Map.entry("sweet_berry_bush", "sweet_berries"), Map.entry("cocoa", "cocoa_beans"),
            Map.entry("kelp_plant", "kelp"), Map.entry("bamboo_sapling", "bamboo"),
            Map.entry("cave_vines", "glow_berries"), Map.entry("cave_vines_plant", "glow_berries"),
            Map.entry("light", "light"), Map.entry("big_dripleaf_stem", "big_dripleaf"),
            Map.entry("tall_seagrass", "seagrass"), Map.entry("weeping_vines_plant", "weeping_vines"),
            Map.entry("twisting_vines_plant", "twisting_vines"), Map.entry("powder_snow", "powder_snow_bucket"));

    /** Properties whose value is the number of items the block represents. */
    private static final String[] COUNT_PROPS = {"layers", "candles", "pickles", "eggs", "flower_amount", "segment_amount"};

    public static String blockId(String state) {
        int b = state.indexOf('[');
        return b < 0 ? state : state.substring(0, b);
    }

    public static Map<String, String> props(String state) {
        Map<String, String> m = new LinkedHashMap<>();
        int b = state.indexOf('[');
        if (b < 0 || !state.endsWith("]")) return m;
        for (String part : state.substring(b + 1, state.length() - 1).split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) m.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return m;
    }

    /** Returns null when the state costs nothing (air, fluids, the second half of a door/bed, ...). */
    public static Req of(String state) {
        String id = blockId(state);
        String name = id.startsWith("minecraft:") ? id.substring(10) : id;
        String ns = id.startsWith("minecraft:") ? "minecraft:" : "";
        if (SKIP.contains(name)) return null;
        Map<String, String> p = props(state);
        if ("upper".equals(p.get("half"))) return null;      // top of a door / tall plant
        if ("head".equals(p.get("part"))) return null;       // head of a bed
        if (name.startsWith("potted_")) name = "flower_pot";

        String item = RENAME.getOrDefault(name, name);
        if (item.equals(name)) {
            if (name.endsWith("_wall_sign")) item = name.replace("_wall_sign", "_sign");
            else if (name.endsWith("_wall_hanging_sign")) item = name.replace("_wall_hanging_sign", "_hanging_sign");
            else if (name.endsWith("_wall_banner")) item = name.replace("_wall_banner", "_banner");
            else if (name.endsWith("_wall_head")) item = name.replace("_wall_head", "_head");
            else if (name.endsWith("_wall_skull")) item = name.replace("_wall_skull", "_skull");
            else if (name.endsWith("_wall_fan")) item = name.replace("_wall_fan", "_fan");
        }
        int count = 1;
        if ("double".equals(p.get("type")) && name.endsWith("_slab")) count = 2;
        for (String cp : COUNT_PROPS) {
            if (p.containsKey(cp)) { try { count = Integer.parseInt(p.get(cp)); } catch (NumberFormatException ignored) { /* keep 1 */ } }
        }
        return new Req(ns + item, count);
    }
}
