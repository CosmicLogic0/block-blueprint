package dev.buildblueprint.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.Window;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Minecraft 1.19.4 draws menus with a PoseStack; this gives the shared screen code the newer GuiGraphics calls it uses. */
public final class GuiGraphics {
    private final PoseStack pose; private final Screen screen;
    public GuiGraphics(PoseStack pose, Screen screen) { this.pose = pose; this.screen = screen; }
    public PoseStack pose() { return pose; }
    public void fill(int x0, int y0, int x1, int y1, int color) { GuiComponent.fill(pose, x0, y0, x1, y1, color); }
    public int drawString(Font f, String s, int x, int y, int color, boolean shadow) { return shadow ? f.drawShadow(pose, s, x, y, color) : f.draw(pose, s, x, y, color); }
    public int drawString(Font f, String s, int x, int y, int color) { return drawString(f, s, x, y, color, true); }
    public void renderItem(ItemStack st, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(pose, st, x, y);
    }
    public void enableScissor(int x0, int y0, int x1, int y1) {
        Window w = Minecraft.getInstance().getWindow(); double s = w.getGuiScale();
        RenderSystem.enableScissor((int) (x0 * s), (int) (w.getHeight() - y1 * s), Math.max(0, (int) ((x1 - x0) * s)), Math.max(0, (int) ((y1 - y0) * s)));
    }
    public void disableScissor() { RenderSystem.disableScissor(); }
    public int guiWidth() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
    public int guiHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
    public void renderComponentTooltip(Font f, List<Component> lines, int x, int y) { if (screen != null) screen.renderComponentTooltip(pose, lines, x, y); }
}
