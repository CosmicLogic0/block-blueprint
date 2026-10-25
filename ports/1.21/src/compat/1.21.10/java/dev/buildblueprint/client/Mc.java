package dev.buildblueprint.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Small helpers for calls that differ between Minecraft versions. */
public final class Mc {
    private Mc() {}
    public static Screen screen() { return Minecraft.getInstance().screen; }
    public static void setScreen(Screen s) { Minecraft.getInstance().setScreen(s); }
    public static InputConstants.Type keyboard() { return InputConstants.Type.values()[0]; /* KEYSYM */ }
}
