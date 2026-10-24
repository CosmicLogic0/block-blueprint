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
}
