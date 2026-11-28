package dev.buildblueprint.core;

/**
 * Chests, signs, beds, banners, heads, shulker boxes... have no normal block model (the game draws them in a
 * special way), so their ghost block would be invisible. Instead the ghost is drawn as a plain block that looks
 * about the same (oak planks for a chest, obsidian for an ender chest, the right colour of wool for a bed), squeezed
 * to the real block's size. This picks that stand-in block. The first one that exists in this version is used.
 */
public final class Standin {
    private Standin() {}

    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final String[] WOODS = {"pale_oak", "dark_oak", "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped"};

    /** Stand-in block ids (best first), or an empty list when nothing should be drawn (water, lava, air-like blocks). */
    public static String[] of(String blockId) {
        String ns = blockId.contains(":") ? blockId.substring(0, blockId.indexOf(':')) : "minecraft";
        String p = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
        if (p.equals("water") || p.equals("lava") || p.equals("bubble_column") || p.endsWith("air") || p.equals("light") || p.equals("structure_void")
                || p.equals("barrier") || p.equals("moving_piston") || p.contains("portal") || p.equals("end_gateway") || p.equals("fire") || p.equals("soul_fire")) return new String[0];
        String color = prefix(p, COLORS);
        String wood = prefix(p, WOODS);
        if (p.equals("ender_chest")) return m("obsidian");
        if (p.contains("copper_chest") || p.contains("copper_golem_statue")) {
            String waxed = p.startsWith("waxed_") ? "waxed_" : "";
            String age = p.contains("exposed") ? "exposed_" : p.contains("weathered") ? "weathered_" : p.contains("oxidized") ? "oxidized_" : "";
            return age.isEmpty() ? m(waxed + "copper_block", "copper_block") : m(waxed + age + "copper", age + "copper", "copper_block");
        }
        if (p.contains("chest")) return wood != null && !wood.equals("oak") ? m(wood + "_planks", "oak_planks") : m("oak_planks");
        if (p.contains("shulker_box")) return color != null ? m(color + "_concrete", color + "_wool", "purpur_block") : m("purpur_block");
        if (p.endsWith("_bed") || p.endsWith("_banner")) return m((color != null ? color : "white") + "_wool", "white_wool");
        if (p.endsWith("_sign")) return wood != null ? m(wood + "_planks", "oak_planks") : m("oak_planks");
        if (p.contains("wither_skeleton")) return m("blackstone", "coal_block");
        if (p.contains("skeleton_skull") || p.contains("skeleton_wall_skull")) return m("bone_block");
        if (p.contains("zombie_")) return m("green_terracotta");
        if (p.contains("creeper_")) return m("lime_concrete");
        if (p.contains("dragon_")) return m("black_concrete");
        if (p.contains("piglin_")) return m("pink_terracotta");
        if (p.contains("player_") || p.endsWith("_head") || p.endsWith("_skull")) return m("brown_terracotta");
        if (p.equals("decorated_pot")) return m("terracotta");
        if (p.equals("conduit")) return m("prismarine");
        if (!ns.equals("minecraft")) return m("oak_planks");   // a mod's special block: at least show where it goes
        return m("white_concrete");
    }

    /**
     * Extra pieces drawn with the stand-in so the ghost looks like the real thing: for a chest, the dark seam under the
     * lid and the latch on the front (half of it on each side of a double chest). b = the real block's shape bounds
     * (minX, minY, minZ, maxX, maxY, maxZ, 0..1); state = the block state as text ("...[facing=north,type=single,...]").
     * Each piece: {String[] stand-in block ids, best first; float[] {x, y, z, w, h, d} in the block}.
     */
    public static java.util.List<Object[]> extras(String blockId, String state, double[] b) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        String p = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
        if (!p.contains("chest") || b == null || b.length < 6) return out;
        String facing = state.contains("facing=south") ? "south" : state.contains("facing=west") ? "west" : state.contains("facing=east") ? "east" : "north";
        float px = 1f / 16, e = 0.004f;
        float y0 = (float) b[1], y1 = (float) b[4], h = y1 - y0;
        // the seam: a thin dark band a little outside the sides, where the lid meets the body
        float sy = y0 + h * (9.5f / 14f);
        out.add(new Object[]{m(p.equals("ender_chest") ? "black_concrete" : "dark_oak_planks", "oak_planks"),
                new float[]{(float) b[0] - e, sy, (float) b[2] - e, (float) (b[3] - b[0]) + 2 * e, px, (float) (b[5] - b[2]) + 2 * e}});
        // the latch: 2 wide, 4 high, 1 deep, on the front; a double chest has half of it on each side of the middle
        boolean alongX = facing.equals("north") || facing.equals("south");
        double lo = alongX ? b[0] : b[2], hi = alongX ? b[3] : b[5];
        float a0, a1;
        if (lo <= 0.001) { a0 = 0; a1 = px; }                 // joined on the low side
        else if (hi >= 0.999) { a0 = 1 - px; a1 = 1; }         // joined on the high side
        else { a0 = 7 * px; a1 = 9 * px; }                      // a single chest: in the middle
        float ly = y0 + h * (7f / 14f), lh = 4 * px;
        String[] latch = m(p.equals("ender_chest") ? "gold_block" : "iron_block", "stone");
        float[] box;
        switch (facing) {
            case "south": box = new float[]{a0, ly, (float) b[5], a1 - a0, lh, px}; break;
            case "west": box = new float[]{(float) b[0] - px, ly, a0, px, lh, a1 - a0}; break;
            case "east": box = new float[]{(float) b[3], ly, a0, px, lh, a1 - a0}; break;
            default: box = new float[]{a0, ly, (float) b[2] - px, a1 - a0, lh, px};
        }
        out.add(new Object[]{latch, box});
        return out;
    }

    private static String[] m(String... ids) {
        String[] out = new String[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = "minecraft:" + ids[i];
        return out;
    }

    private static String prefix(String p, String[] names) {
        for (String n : names) if (p.startsWith(n + "_")) return n;   // the longer names come first ("dark_oak" before "oak")
        return null;
    }
}
