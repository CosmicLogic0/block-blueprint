package dev.buildblueprint.client;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Chest finder: remembers what was inside every chest, barrel or shulker box you open (while you play), so the
 * mod can show which nearby chests hold the blocks you still need. Open a chest once and it's remembered.
 */
public final class Chests {
    private Chests() {}

    /** chest position -> item id -> count, as last seen. */
    private static final Map<BlockPos, Map<String, Integer>> SEEN = new LinkedHashMap<>();
    private static BlockPos opening;           // what you were looking at when the chest screen opened
    private static Object lastScreen;
    private static Object level;

    static void init() { ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("chest finder", () -> tick(mc))); }

    private static void tick(Minecraft mc) {
        if (mc.level != level) { SEEN.clear(); level = mc.level; }   // new world / server
        var sc = Mc.screen();
        if (sc != lastScreen) {
            lastScreen = sc;
            opening = null;
            if (sc instanceof AbstractContainerScreen<?> && !(sc instanceof InventoryScreen) && !(sc instanceof CreativeModeInventoryScreen)
                    && mc.hitResult instanceof BlockHitResult bh && mc.hitResult.getType() == HitResult.Type.BLOCK) opening = bh.getBlockPos();
        }
        if (opening == null || !(sc instanceof AbstractContainerScreen<?> cs)) return;
        if (mc.level.getBlockEntity(opening) == null) return;
        Map<String, Integer> c = new HashMap<>();
        for (Slot s : cs.getMenu().slots) {
            if (s.container instanceof Inventory) continue;     // your own inventory rows
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            c.merge(V.itemId(st.getItem()), st.getCount(), Integer::sum);
        }
        SEEN.put(opening.immutable(), c);
    }

    /** How many of an item are in remembered chests within range blocks of you, and in how many chests. */
    public static int[] nearby(String itemId, double range) {
        Minecraft mc = Minecraft.getInstance();
        int n = 0, chests = 0;
        if (mc.player == null) return new int[]{0, 0};
        for (Map.Entry<BlockPos, Map<String, Integer>> e : SEEN.entrySet()) {
            Integer k = e.getValue().get(itemId);
            if (k == null || k <= 0) continue;
            if (d2(e.getKey(), mc.player.position()) > range * range) continue;
            n += k; chests++;
        }
        return new int[]{n, chests};
    }

    /** Remembered chests near you that hold something the blueprint still needs (you don't carry enough of). */
    public static java.util.List<BlockPos> useful(double range) {
        Minecraft mc = Minecraft.getInstance();
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        if (mc.player == null || mc.level == null) return out;
        java.util.Set<String> missing = new java.util.HashSet<>();
        for (Materials.Row r : Materials.rows()) if (r.missing() > 0) missing.add(r.id());
        if (missing.isEmpty()) return out;
        for (Map.Entry<BlockPos, Map<String, Integer>> e : SEEN.entrySet()) {
            if (d2(e.getKey(), mc.player.position()) > range * range) continue;
            if (mc.level.getBlockEntity(e.getKey()) == null) continue;   // chest was broken
            for (String id : e.getValue().keySet()) if (missing.contains(id)) { out.add(e.getKey()); break; }
        }
        return out;
    }

    public static int remembered() { return SEEN.size(); }

    private static double d2(BlockPos p, net.minecraft.world.phys.Vec3 v) { double x = p.getX() + 0.5 - v.x, y = p.getY() + 0.5 - v.y, z = p.getZ() + 0.5 - v.z; return x * x + y * y + z * z; }
}
