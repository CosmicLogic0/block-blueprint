package dev.buildblueprint.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The few Minecraft calls that changed between versions. One copy of this file per version (src/compat/<version>). */
public final class Mc {
    private Mc() {}
    public static Screen screen() { return Minecraft.getInstance().screen; }
    public static void setScreen(Screen s) { Minecraft.getInstance().setScreen(s); }
    /** the keyboard input type (called KEYSYM before 26.3) */
    public static InputConstants.Type keyboard() { return InputConstants.Type.values()[0]; }
    /** Is this keyboard key held down right now? */
    public static boolean keyDown(int key) { return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key); }

    /** Blocks inside a shulker box (or another box item) you carry: they count as yours. */
    public static void containerItems(net.minecraft.world.item.ItemStack st, java.util.function.BiConsumer<String, Integer> out) {
        net.minecraft.world.item.component.ItemContainerContents c = st.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (c == null) return;
        for (net.minecraft.world.item.ItemStack in : c.nonEmptyItems()) out.accept(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(in.getItem()).toString(), in.getCount());
    }
}
