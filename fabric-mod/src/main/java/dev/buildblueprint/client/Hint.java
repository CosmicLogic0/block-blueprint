package dev.buildblueprint.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** The "next block" helper: which ghost block to place next (lowest layer first, then nearest). Filled in by the preview. */
public final class Hint {
    private Hint() {}

    public static BlockPos pos;          // null = nothing to suggest
    public static String state;          // block state string
    public static ItemStack icon = ItemStack.EMPTY;
    public static String name = "";
    public static double dist;
    public static boolean inHand;        // you are holding the right block already

    public static void clear() { pos = null; state = null; icon = ItemStack.EMPTY; name = ""; }

    /** "3 ahead, 2 left, 1 up" relative to where the player looks. */
    public static String where(net.minecraft.world.entity.player.Player p) {
        if (pos == null || p == null) return "";
        double dx = pos.getX() + 0.5 - p.getX(), dy = pos.getY() - Math.floor(p.getY()), dz = pos.getZ() + 0.5 - p.getZ();
        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);              // forward
        double ahead = dx * fx + dz * fz, right = dx * -fz + dz * fx;
        StringBuilder sb = new StringBuilder();
        int a = (int) Math.round(ahead), r = (int) Math.round(right), u = (int) Math.round(dy);
        if (a != 0) sb.append(Tr.t((a > 0 ? "{0} ahead" : "{0} behind").replace("{0}", String.valueOf(Math.abs(a)))));
        if (r != 0) sb.append(sb.length() > 0 ? ", " : "").append(Tr.t((r > 0 ? "{0} right" : "{0} left").replace("{0}", String.valueOf(Math.abs(r)))));
        if (u != 0) sb.append(sb.length() > 0 ? ", " : "").append(Tr.t((u > 0 ? "{0} up" : "{0} down").replace("{0}", String.valueOf(Math.abs(u)))));
        return sb.length() == 0 ? Tr.t("right here") : sb.toString();
    }
}
