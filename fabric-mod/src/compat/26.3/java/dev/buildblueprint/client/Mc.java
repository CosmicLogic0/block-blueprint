package dev.buildblueprint.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The few Minecraft calls that changed between versions. One copy of this file per version (src/compat/<version>). */
public final class Mc {
    private Mc() {}
    public static Screen screen() { return Minecraft.getInstance().gui.screen(); }
    public static void setScreen(Screen s) { Minecraft.getInstance().gui.setScreen(s); }
    public static InputConstants.Type keyboard() { return InputConstants.Type.KEYBOARD; }
    /** Is this keyboard key held down right now? (26.3 asks with just the key.) */
    public static boolean keyDown(int key) { return InputConstants.isKeyDown(key); }

    /** Blocks inside a shulker box (or another box item) you carry: they count as yours. */
    public static void containerItems(net.minecraft.world.item.ItemStack st, java.util.function.BiConsumer<String, Integer> out) {
        net.minecraft.world.item.component.ItemContainerContents c = st.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (c == null) return;
        for (Object o : c.nonEmptyItems()) {   // 26.x: ItemStackTemplate (the game isn't obfuscated, so its methods can be looked up by name)
            net.minecraft.world.item.ItemStack in = stackOf(o);
            if (!in.isEmpty()) out.accept(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(in.getItem()).toString(), in.getCount());
        }
    }

    private static net.minecraft.world.item.ItemStack stackOf(Object o) {
        if (o instanceof net.minecraft.world.item.ItemStack s) return s;
        for (String m : new String[]{"create", "toStack", "asStack", "copy"}) {
            try { Object r = o.getClass().getMethod(m).invoke(o); if (r instanceof net.minecraft.world.item.ItemStack s) return s; } catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
        try {   // a record of (item, count, ...)
            Object item = o.getClass().getMethod("item").invoke(o);
            if (item instanceof net.minecraft.core.Holder<?> h) item = h.value();
            int n = ((Number) o.getClass().getMethod("count").invoke(o)).intValue();
            if (item instanceof net.minecraft.world.item.Item it) return new net.minecraft.world.item.ItemStack(it, n);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
