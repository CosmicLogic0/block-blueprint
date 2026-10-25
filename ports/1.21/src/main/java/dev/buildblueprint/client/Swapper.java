package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.MaterialMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Block swapping: change one block for another everywhere in the blueprint. Any block the game knows can be picked,
 * including blocks added by other mods. Stairs stay stairs where possible and keep their facing / shape, and wall
 * versions (wall signs, wall torches, wall banners...) follow along.
 */
public final class Swapper {
    private Swapper() {}

    /** One block you can swap to: its id, the name players see, the mod it comes from ("" = Minecraft) and its shape. */
    public record Option(String id, String name, String mod, ItemStack icon, String shape, int color, boolean machine) {}

    public static boolean exists(String id) {
        try { return V.blockExists(id); } catch (Exception e) { return false; }
    }
    private static String path(String id) { int i = id.indexOf(':'); return i < 0 ? id : id.substring(i + 1); }
    private static String ns(String id) { int i = id.indexOf(':'); return i < 0 ? "minecraft" : id.substring(0, i); }

    // blocks nobody builds with (or that can't be placed by hand)
    private static final Set<String> HIDDEN = new HashSet<>(java.util.Arrays.asList("air", "cave_air", "void_air", "barrier", "light", "structure_void", "structure_block",
            "jigsaw", "command_block", "chain_command_block", "repeating_command_block", "debug_stick", "test_block", "test_instance_block", "moving_piston",
            "piston_head", "end_portal", "end_gateway", "nether_portal", "bubble_column", "frosted_ice", "spawner", "trial_spawner", "vault", "budding_amethyst",
            "reinforced_deepslate", "petrified_oak_slab", "infested_stone", "infested_cobblestone", "infested_stone_bricks", "infested_mossy_stone_bricks",
            "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks", "infested_deepslate"));

    /** What kind of piece a block is: stairs swap to stairs, slabs to slabs, full blocks to full blocks... */
    public static String shape(Block b, String id) {
        String p = path(id);
        if (b instanceof net.minecraft.world.level.block.StairBlock) return "stairs";
        if (b instanceof net.minecraft.world.level.block.SlabBlock) return "slab";
        if (b instanceof net.minecraft.world.level.block.WallBlock) return "wall";
        if (b instanceof net.minecraft.world.level.block.FenceGateBlock) return "fence gate";
        if (b instanceof net.minecraft.world.level.block.FenceBlock) return "fence";
        if (b instanceof net.minecraft.world.level.block.DoorBlock) return "door";
        if (b instanceof net.minecraft.world.level.block.TrapDoorBlock) return "trapdoor";
        if (b instanceof net.minecraft.world.level.block.ButtonBlock) return "button";
        if (b instanceof net.minecraft.world.level.block.BasePressurePlateBlock) return "pressure plate";
        if (b instanceof net.minecraft.world.level.block.IronBarsBlock) return "pane";
        if (b instanceof net.minecraft.world.level.block.SignBlock) return p.contains("hanging") ? "hanging sign" : "sign";
        if (b instanceof net.minecraft.world.level.block.AbstractBannerBlock) return "banner";
        if (b instanceof net.minecraft.world.level.block.BedBlock) return "bed";
        if (b instanceof net.minecraft.world.level.block.TorchBlock) return "torch";
        if (b instanceof net.minecraft.world.level.block.LeavesBlock) return "leaves";
        if (p.endsWith("carpet")) return "carpet";
        return shapeByName(p, b);
    }
    /** Shape from the name only (for blocks this Minecraft version doesn't have). */
    private static String shapeByName(String p, Block b) {
        String[][] ends = {{"_stairs", "stairs"}, {"_slab", "slab"}, {"_wall", "wall"}, {"_fence_gate", "fence gate"}, {"_fence", "fence"},
                {"_door", "door"}, {"_trapdoor", "trapdoor"}, {"_button", "button"}, {"_pressure_plate", "pressure plate"}, {"_pane", "pane"},
                {"_bars", "pane"}, {"_hanging_sign", "hanging sign"}, {"_sign", "sign"}, {"_banner", "banner"}, {"_bed", "bed"}, {"torch", "torch"},
                {"lantern", "lantern"}, {"_leaves", "leaves"}, {"carpet", "carpet"}};
        for (String[] e : ends) if (p.endsWith(e[0])) return e[1];
        if (b == null) return "block";
        BlockState d = b.defaultBlockState();
        if (V.solid(d) || V.glass(b)) return "block";
        return "other";
    }

    private static List<Option> all;
    /** Every block in the game you could build with (vanilla and modded), sorted by name. Built once. */
    public static List<Option> allOptions() {
        if (all != null) return all;
        List<Option> out = new ArrayList<>();
        for (Block blk : V.allBlocks()) {
            try {
                String id = V.blockId(blk);
                if (ns(id).equals("minecraft") && HIDDEN.contains(path(id))) continue;
                Item it = blk.asItem();
                if (it == null || it == net.minecraft.world.item.Items.AIR) continue;
                if (!V.itemId(it).equals(id)) continue;   // wall torch, wall sign... share the item of the normal one
                ItemStack st = new ItemStack(it);
                String mod = ns(id).equals("minecraft") ? "" : PlayerInfo.pretty(ns(id));
                out.add(new Option(id, st.getHoverName().getString(), mod, st, shape(blk, id), mapCol(blk.defaultBlockState()), V.hasBE(blk.defaultBlockState())));
            } catch (Throwable ignored) {}
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        all = out;
        return out;
    }

    /** The block's colour on maps (a rough "what colour is it"), 0 if none. */
    private static int mapCol(BlockState s) {
        try { return V.mapColor(s); } catch (Throwable t) { return 0; }
    }
    private static int colorDist(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
        return dr * dr * 3 + dg * dg * 4 + db * db * 2;
    }

    /** The shape of a block id, even if this Minecraft version doesn't have it. */
    public static String shapeOf(String blockId) {
        if (exists(blockId)) return shape(V.block(blockId), blockId);
        return shapeByName(path(blockId), null);
    }

    private static Set<String> words(String id) {
        Set<String> w = new HashSet<>();
        for (String s : path(id).split("_")) if (s.length() > 2) w.add(s);
        return w;
    }

    /** Blocks of the same kind of piece, the most alike first: sharing a word in the name ("terracotta", "oak"...), then the closest colour. */
    public static List<Option> similar(String blockId) {
        String shape = shapeOf(blockId);
        Set<String> mine = words(blockId);
        int myCol = exists(blockId) ? mapCol(V.block(blockId).defaultBlockState()) : 0;
        List<Option> out = new ArrayList<>();
        for (Option o : allOptions()) if (!o.id().equals(blockId) && o.shape().equals(shape)) out.add(o);
        out.sort((a, b) -> {
            int sa = shared(mine, a.id()), sb = shared(mine, b.id());
            if (sa != sb) return Integer.compare(sb, sa);
            if (a.machine() != b.machine()) return a.machine() ? 1 : -1;   // furnaces, chests... after plain blocks
            if (myCol != 0 && a.color() != b.color()) {
                int da = a.color() == 0 ? Integer.MAX_VALUE : colorDist(myCol, a.color()), dbb = b.color() == 0 ? Integer.MAX_VALUE : colorDist(myCol, b.color());
                if (da != dbb) return Integer.compare(da, dbb);
            }
            if (a.mod().isEmpty() != b.mod().isEmpty()) return a.mod().isEmpty() ? -1 : 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        return out;
    }
    private static int shared(Set<String> mine, String id) { int n = 0; for (String w : words(id)) if (mine.contains(w)) n++; return n; }

    /** Search every block by name, id or mod name. Same-shape blocks come first. */
    public static List<Option> search(String blockId, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        String shape = shapeOf(blockId);
        List<Option> hit = new ArrayList<>();
        for (Option o : allOptions()) {
            if (o.id().equals(blockId)) continue;
            if (o.name().toLowerCase(Locale.ROOT).contains(q) || o.id().contains(q.replace(' ', '_')) || o.mod().toLowerCase(Locale.ROOT).contains(q)) hit.add(o);
        }
        hit.sort((a, b) -> {
            boolean sa = a.shape().equals(shape), sb = b.shape().equals(shape);
            if (sa != sb) return sa ? -1 : 1;
            boolean pa = a.name().toLowerCase(Locale.ROOT).startsWith(q), pb = b.name().toLowerCase(Locale.ROOT).startsWith(q);
            if (pa != pb) return pa ? -1 : 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        return hit;
    }

    /** Blocks from other mods (empty without block mods). */
    public static List<Option> modded() {
        List<Option> out = new ArrayList<>();
        for (Option o : allOptions()) if (!o.mod().isEmpty()) out.add(o);
        out.sort((a, b) -> a.mod().equals(b.mod()) ? a.name().compareToIgnoreCase(b.name()) : a.mod().compareToIgnoreCase(b.mod()));
        return out;
    }

    /** The wall-mounted version of a block, if it has one (spruce_sign -> spruce_wall_sign, torch -> wall_torch...). */
    private static String wallVersionOf(String id) {
        String n = ns(id), p = path(id);
        List<String> tries = new ArrayList<>();
        int i = p.lastIndexOf('_');
        tries.add(i < 0 ? "wall_" + p : p.substring(0, i + 1) + "wall_" + p.substring(i + 1));
        if (p.endsWith("_hanging_sign")) tries.add(p.replace("_hanging_sign", "_wall_hanging_sign"));
        for (String t : tries) if (exists(n + ":" + t)) return n + ":" + t;
        return null;
    }

    /** What each block of this materials row becomes when swapped to target (old block id -> new block id). */
    public static Map<String, String> plan(String itemId, String target) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String b : blocksForItem(itemId)) {
            String to = target;
            if (path(b).contains("wall_") && !path(target).contains("wall_")) { String w = wallVersionOf(target); if (w != null) to = w; }
            if (!to.equals(b)) m.put(b, to);
        }
        return m;
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
            if (v == null || !pr.getValue(v).isPresent()) continue;
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

    private static Object nigFor; private static Set<String> nig = Collections.emptySet();
    /** Materials rows (item ids) whose block doesn't exist in this Minecraft version (a build made in a newer version, or with a mod you don't have). */
    public static Set<String> notInGame() {
        Blueprint bp = BuildBlueprintClient.SESSION.blueprint();
        if (bp == null) return Collections.emptySet();
        String key = System.identityHashCode(bp.palette) + ":" + String.join("|", bp.palette).hashCode();
        if (key.equals(nigFor)) return nig;
        Set<String> out = new LinkedHashSet<>();
        for (String st : bp.palette) {
            String id = MaterialMap.blockId(st);
            if (exists(id)) continue;
            MaterialMap.Req r = MaterialMap.of(st);
            out.add(r != null ? r.itemId() : id);
        }
        nigFor = key; nig = out;
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
}
