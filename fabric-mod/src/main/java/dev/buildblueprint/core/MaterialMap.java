package dev.buildblueprint.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Works out which inventory item (and how many) a placed block state costs. */
public final class MaterialMap {
    private MaterialMap() {}

    public record Req(String itemId, int count) {}

    /** Blocks that have no item and are never hand-placed. */
    private static final Set<String> SKIP = new java.util.HashSet<>(java.util.Arrays.asList(
            "air", "cave_air", "void_air", "water", "lava", "bubble_column", "fire", "soul_fire", "piston_head",
            "moving_piston", "nether_portal", "end_portal", "end_gateway", "frosted_ice", "light_block_placeholder"));

    private static final Map<String, String> RENAME = new java.util.HashMap<>();
    static {
        RENAME.put("redstone_wire", "redstone");
        RENAME.put("redstone_wall_torch", "redstone_torch");
        RENAME.put("wall_torch", "torch");
        RENAME.put("soul_wall_torch", "soul_torch");
        RENAME.put("tripwire", "string");
        RENAME.put("carrots", "carrot");
        RENAME.put("potatoes", "potato");
        RENAME.put("beetroots", "beetroot_seeds");
        RENAME.put("wheat", "wheat_seeds");
        RENAME.put("melon_stem", "melon_seeds");
        RENAME.put("attached_melon_stem", "melon_seeds");
        RENAME.put("pumpkin_stem", "pumpkin_seeds");
        RENAME.put("attached_pumpkin_stem", "pumpkin_seeds");
        RENAME.put("sweet_berry_bush", "sweet_berries");
        RENAME.put("cocoa", "cocoa_beans");
        RENAME.put("kelp_plant", "kelp");
        RENAME.put("bamboo_sapling", "bamboo");
        RENAME.put("cave_vines", "glow_berries");
        RENAME.put("cave_vines_plant", "glow_berries");
        RENAME.put("light", "light");
        RENAME.put("big_dripleaf_stem", "big_dripleaf");
        RENAME.put("tall_seagrass", "seagrass");
        RENAME.put("weeping_vines_plant", "weeping_vines");
        RENAME.put("twisting_vines_plant", "twisting_vines");
        RENAME.put("powder_snow", "powder_snow_bucket");
    }

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
