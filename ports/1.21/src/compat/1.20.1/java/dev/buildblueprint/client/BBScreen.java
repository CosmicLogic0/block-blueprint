package dev.buildblueprint.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Bridges this version's screen methods to the newer style the shared screen code is written in:
 * keyPressed(KeyEvent), mouseClicked(MouseButtonEvent, doubleClick), extractRenderState(...).
 */
public abstract class BBScreen extends Screen {
    private long lastClick; private double lastX, lastY; private int lastButton = -1;

    protected BBScreen(Component title) { super(title); }

    public boolean keyPressed(KeyEvent e) { return super.keyPressed(e.key(), e.scancode(), e.modifiers()); }
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) { return super.mouseClicked(e.x(), e.y(), e.button()); }
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return super.mouseDragged(e.x(), e.y(), e.button(), dx, dy); }
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { super.render(g, mouseX, mouseY, delta); }

    @Override public final boolean keyPressed(int key, int scancode, int modifiers) { return keyPressed(new KeyEvent(key, scancode, modifiers)); }
    @Override public final boolean mouseClicked(double x, double y, int button) {
        long now = System.currentTimeMillis();
        boolean dbl = button == lastButton && now - lastClick < 250 && Math.abs(x - lastX) < 4 && Math.abs(y - lastY) < 4;
        lastClick = dbl ? 0 : now; lastX = x; lastY = y; lastButton = button;
        return mouseClicked(new MouseButtonEvent(x, y, button), dbl);
    }
    @Override public final boolean mouseDragged(double x, double y, int button, double dx, double dy) { return mouseDragged(new MouseButtonEvent(x, y, button), dx, dy); }

    @Override public final void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g);   // this version's Screen.render doesn't draw the background itself
        extractRenderState(g, mouseX, mouseY, delta);
    }
    // this version scrolls with one amount; the shared screens use the newer (x, y, dx, dy) form
    public boolean mouseScrolled(double x, double y, double dx, double dy) { return super.mouseScrolled(x, y, dy); }
    @Override public final boolean mouseScrolled(double x, double y, double d) { return mouseScrolled(x, y, 0, d); }
}
