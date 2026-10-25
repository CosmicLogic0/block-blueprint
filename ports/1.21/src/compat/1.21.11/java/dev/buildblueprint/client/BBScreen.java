package dev.buildblueprint.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Minecraft 1.21.11 passes key and mouse events as objects; the shared screen code uses the small
 * dev.buildblueprint.client KeyEvent / MouseButtonEvent records, so this converts between them.
 */
public abstract class BBScreen extends Screen {
    private net.minecraft.client.input.KeyEvent lastKey;
    private net.minecraft.client.input.MouseButtonEvent lastMouse;

    protected BBScreen(Component title) { super(title); }

    public boolean keyPressed(KeyEvent e) { return super.keyPressed(lastKey != null ? lastKey : new net.minecraft.client.input.KeyEvent(e.key(), e.scancode(), e.modifiers())); }
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) { return lastMouse != null && super.mouseClicked(lastMouse, dbl); }
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return lastMouse != null && super.mouseDragged(lastMouse, dx, dy); }
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { super.render(g, mouseX, mouseY, delta); }

    @Override public final boolean keyPressed(net.minecraft.client.input.KeyEvent e) { lastKey = e; return keyPressed(new KeyEvent(e.key(), e.scancode(), e.modifiers())); }
    @Override public final boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) { lastMouse = e; return mouseClicked(new MouseButtonEvent(e.x(), e.y(), e.button()), dbl); }
    @Override public final boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dx, double dy) { lastMouse = e; return mouseDragged(new MouseButtonEvent(e.x(), e.y(), e.button()), dx, dy); }
    @Override public final void render(GuiGraphics g, int mouseX, int mouseY, float delta) { extractRenderState(g, mouseX, mouseY, delta); }
}
