package dev.buildblueprint.client;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Inside a chest (barrel, shulker box, any container): the slots that hold blocks your build still needs get a green
 * frame, so you can see straight away what to take out. Only the container's slots, not your own inventory.
 * Follows the "Chest finder" setting.
 */
public final class ChestHighlight {
    private ChestHighlight() {}

    private static boolean said;

    public static void init() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> Safe.run("chest highlight", () -> draw(s, g))));
    }

    /** Item ids the build still needs more of than you carry. */
    private static Set<String> needed() {
        Set<String> out = new HashSet<>();
        for (Materials.Row r : Materials.rows()) if (r.missing() > 0) out.add(r.id());
        return out;
    }

    private static void draw(Screen s, GuiGraphicsExtractor g) {
        if (!(s instanceof AbstractContainerScreen<?> cs)) return;
        if (!said && "world".equals(System.getProperty("buildblueprint.uitest"))) { said = true; System.out.println("BBUITEST chest highlight hooked on " + s.getClass().getSimpleName()); }
        if (!Settings.chestFinder || !BuildBlueprintClient.SESSION.loaded()) return;
        Set<String> need = needed();
        if (need.isEmpty()) return;
        int[] o = origin(cs);
        if (o == null) return;
        int n = 0;
        for (Slot sl : cs.getMenu().slots) {
            if (sl.container instanceof Inventory || !sl.hasItem()) continue;
            ItemStack st = sl.getItem();
            if (!need.contains(itemId(st))) continue;
            int x = o[0] + sl.x - 1, y = o[1] + sl.y - 1;
            g.fill(x, y, x + 18, y + 18, 0x3055FF55);
            g.fill(x, y, x + 18, y + 1, 0xFF55FF55); g.fill(x, y + 17, x + 18, y + 18, 0xFF55FF55);
            g.fill(x, y, x + 1, y + 18, 0xFF55FF55); g.fill(x + 17, y, x + 18, y + 18, 0xFF55FF55);
            n++;
        }
        if (n > 0) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String t = Tr.t(n == 1 ? "Build Blueprint: your build needs the block in green" : "Build Blueprint: your build needs the blocks in green");
            g.text(font, t, s.width / 2 - font.width(t) / 2, Math.max(2, o[1] - 12), 0xFF55FF55, true);
        }
    }

    private static String itemId(ItemStack st) { Item it = st.getItem(); return BuiltInRegistries.ITEM.getKey(it).toString(); }

    /** Where the container's picture starts on screen (its leftPos / topPos). */
    private static int[] origin(AbstractContainerScreen<?> cs) {
        Class<?> c = AbstractContainerScreen.class;
        try {   // Forge / NeoForge have public getters
            return new int[]{(Integer) c.getMethod("getGuiLeft").invoke(cs), (Integer) c.getMethod("getGuiTop").invoke(cs)};
        } catch (Throwable ignored) {}
        try {   // names are readable on Minecraft 26.x
            var l = c.getDeclaredField("leftPos"); var t = c.getDeclaredField("topPos");
            l.setAccessible(true); t.setAccessible(true);
            return new int[]{l.getInt(cs), t.getInt(cs)};
        } catch (Throwable ignored) {}
        // older Fabric (names scrambled): the picture is centred, so leftPos = (width - imageWidth) / 2 for some pair of int fields
        try {
            java.util.List<Integer> v = new java.util.ArrayList<>();
            for (var f : c.getDeclaredFields()) {
                if (f.getType() != int.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true); v.add(f.getInt(cs));
            }
            Integer left = null, top = null;
            for (int w : v) if (w >= 100 && w <= cs.width) for (int a : v) if (a == (cs.width - w) / 2) left = a;
            for (int h : v) if (h >= 100 && h <= cs.height) for (int a : v) if (a == (cs.height - h) / 2) top = a;
            if (left != null && top != null) return new int[]{left, top};
        } catch (Throwable ignored) {}
        return null;
    }
}
