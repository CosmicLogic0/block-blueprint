package dev.buildblueprint.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Reads the player's position, facing and inventory. */
public final class PlayerInfo {
    private PlayerInfo() {}

    public static boolean inWorld() { return Minecraft.getInstance().player != null; }
    public static int x() { return (int) Math.floor(Minecraft.getInstance().player.getX()); }
    public static int y() { return (int) Math.floor(Minecraft.getInstance().player.getY()); }
    public static int z() { return (int) Math.floor(Minecraft.getInstance().player.getZ()); }
    public static float yaw() { return Minecraft.getInstance().player.getYRot(); }

    /** item id (e.g. "minecraft:oak_planks") -> how many you carry (hotbar, main inventory, off-hand). */
    public static Map<String, Integer> inventoryCounts() {
        Map<String, Integer> out = new HashMap<>();
        var player = Minecraft.getInstance().player;
        if (player == null) return out;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            out.merge(V.itemId(st.getItem()), st.getCount(), Integer::sum);
        }
        return out;
    }

    /** "minecraft:oak_planks" -> "Oak Planks" (plain fallback; the menu uses the real item name when it can). */
    public static String pretty(String id) {
        String n = id.substring(id.indexOf(':') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : n.split("_")) { if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' '); }
        return sb.toString().trim();
    }
}
