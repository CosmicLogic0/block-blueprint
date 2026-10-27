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

/** Minecraft 1.16.5 draws menus with a PoseStack; this gives the shared screen code the newer GuiGraphics calls it uses. */
public final class GuiGraphics {
    private final PoseStack pose; private final Screen screen;
    public GuiGraphics(PoseStack pose, Screen screen) { this.pose = pose; this.screen = screen; }
    public PoseStack pose() { return pose; }
    public void fill(int x0, int y0, int x1, int y1, int color) { GuiComponent.fill(pose, x0, y0, x1, y1, color); }
    public int drawString(Font f, String s, int x, int y, int color, boolean shadow) { return shadow ? f.drawShadow(pose, s, x, y, color) : f.draw(pose, s, x, y, color); }
    public int drawString(Font f, String s, int x, int y, int color) { return drawString(f, s, x, y, color, true); }
    public void renderItem(ItemStack st, int x, int y) {
        RenderSystem.pushMatrix(); RenderSystem.multMatrix(pose.last().pose());
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(st, x, y);
        RenderSystem.popMatrix();
    }
    public void enableScissor(int x0, int y0, int x1, int y1) {
        Window w = Minecraft.getInstance().getWindow(); double s = w.getGuiScale();
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        org.lwjgl.opengl.GL11.glScissor((int) (x0 * s), (int) (w.getHeight() - y1 * s), Math.max(0, (int) ((x1 - x0) * s)), Math.max(0, (int) ((y1 - y0) * s)));
    }
    public void disableScissor() { org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST); }
    public int guiWidth() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
    public int guiHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
    public void renderComponentTooltip(Font f, List<Component> lines, int x, int y) {
        if (screen == null) return;
        try { screen.renderComponentTooltip(pose, lines, x, y); }
        catch (NoSuchMethodError old) {   // Minecraft 1.16.1 (Forge file shared with 1.16.3): the same method had another name
            try {
                for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass())
                    for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                        Class<?>[] t = m.getParameterTypes();
                        if (t.length == 4 && t[0] == PoseStack.class && t[1] == List.class && t[2] == int.class && t[3] == int.class && m.getReturnType() == void.class) {
                            m.setAccessible(true); m.invoke(screen, pose, lines, x, y); return;
                        }
                    }
            } catch (Throwable ignored) {}
        }
    }
}
