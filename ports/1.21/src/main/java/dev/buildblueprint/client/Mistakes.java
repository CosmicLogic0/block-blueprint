package dev.buildblueprint.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Mistake finder: blocks in the build that are wrong - a different block where the build wants another one, or the right
 * block the wrong way round. "Show" in the build menu points the guide box and line at the nearest one; when it's fixed
 * it moves on to the next one by itself, until everything is right.
 */
public final class Mistakes {
    private Mistakes() {}

    /** One wrong block: where, what the build wants, what's there, and whether it's only turned the wrong way. */
    public static final class Mistake {
        public final long pos; public final String want; public final String have; public final String itemId; public final boolean wrongWay;
        Mistake(long pos, String want, String have, String itemId, boolean wrongWay) { this.pos = pos; this.want = want; this.have = have; this.itemId = itemId; this.wrongWay = wrongWay; }
    }

    private static List<Mistake> list = new ArrayList<>();
    private static BlockPos focus;     // the mistake being shown, null = not showing
    private static int fixed;

    public static int count() { return list.size(); }
    public static List<Mistake> all() { return list; }
    public static boolean showing() { return focus != null; }

    static Mistake make(long pos, String want, String have, String itemId, boolean wrongWay) { return new Mistake(pos, want, have, itemId, wrongWay); }

    /** Called by Materials.refresh with the new list. Moves on when the one being shown got fixed. */
    static void update(List<Mistake> now) {
        list = now;
        if (focus == null) return;
        long f = focus.asLong();
        for (Mistake m : now) if (m.pos == f) return;   // still wrong: keep pointing at it
        fixed++;
        if (now.isEmpty()) { focus = null; Chat.bar("All fixed! Nothing wrong in the build any more."); Chat.ding(false); return; }
        pick();
        Chat.bar("Fixed! " + now.size() + " left  ·  " + describe(current()));
    }

    /** Start (or move to the next): point at the nearest mistake. */
    public static void show() {
        if (list.isEmpty()) { focus = null; Chat.bar("Nothing to fix - every placed block is right."); return; }
        fixed = 0;
        pick();
        Chat.bar("Mistake 1 of " + list.size() + ": " + describe(current()));
    }
    public static void stop() { focus = null; }

    private static void pick() {
        Minecraft mc = Minecraft.getInstance();
        Mistake best = null; double bd = 0;
        for (Mistake m : list) {
            BlockPos b = BlockPos.of(m.pos);
            double d = mc.player == null ? 0 : sq(b.getX() + 0.5 - mc.player.getX()) + sq(b.getY() + 0.5 - mc.player.getY()) + sq(b.getZ() + 0.5 - mc.player.getZ());
            if (best == null || d < bd) { best = m; bd = d; }
        }
        focus = best == null ? null : BlockPos.of(best.pos);
    }
    private static double sq(double v) { return v * v; }

    private static Mistake current() {
        if (focus == null) return null;
        long f = focus.asLong();
        for (Mistake m : list) if (m.pos == f) return m;
        return null;
    }

    static String describe(Mistake m) {
        if (m == null) return "";
        String want = name(m.itemId, m.want);
        if (m.wrongWay) return "this " + want + " " + dev.buildblueprint.core.Orient.fix(m.want, m.have);
        return "a " + PlayerInfo.pretty(dev.buildblueprint.core.MaterialMap.blockId(m.have.replaceFirst("^Block\\{([^}]*)\\}.*$", "$1"))) + " is where " + want + " goes - break it";
    }
    private static String name(String itemId, String state) {
        net.minecraft.world.item.ItemStack it = Materials.stackFor(itemId);
        return it.isEmpty() ? PlayerInfo.pretty(dev.buildblueprint.core.MaterialMap.blockId(state)) : it.getHoverName().getString();
    }

    /**
     * For the preview's "next block" helper: while showing mistakes, the guide box and line point at the mistake.
     * Returns true when it filled in Hint.
     */
    public static boolean aim(Minecraft mc) {
        if (focus == null || mc.player == null) return false;
        Mistake m = current();
        if (m == null) return false;
        Hint.pos = focus;
        Hint.state = m.want;
        Hint.icon = Materials.stackFor(m.itemId);
        Hint.name = "Fix: " + name(m.itemId, m.want);
        Hint.inHand = false;
        double dx = focus.getX() + 0.5 - mc.player.getX(), dy = focus.getY() + 0.5 - mc.player.getEyeY(), dz = focus.getZ() + 0.5 - mc.player.getZ();
        Hint.dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return true;
    }
}
