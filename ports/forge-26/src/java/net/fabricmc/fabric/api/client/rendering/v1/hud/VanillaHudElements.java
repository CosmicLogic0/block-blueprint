package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.resources.Identifier;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

/** Stand-in for Fabric's names of the vanilla HUD parts (only the ones the mod uses). */
public final class VanillaHudElements {
    private VanillaHudElements() {}
    public static final Identifier CHAT = ForgeLayeredDraw.CHAT_OVERLAY;
}
