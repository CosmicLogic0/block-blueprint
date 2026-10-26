package dev.buildblueprint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
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
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { super.render(mouseX, mouseY, delta); }

    @Override public final boolean keyPressed(int key, int scancode, int modifiers) { return keyPressed(new KeyEvent(key, scancode, modifiers)); }
    @Override public final boolean mouseClicked(double x, double y, int button) {
        long now = System.currentTimeMillis();
        boolean dbl = button == lastButton && now - lastClick < 250 && Math.abs(x - lastX) < 4 && Math.abs(y - lastY) < 4;
        lastClick = dbl ? 0 : now; lastX = x; lastY = y; lastButton = button;
        return mouseClicked(new MouseButtonEvent(x, y, button), dbl);
    }
    @Override public final boolean mouseDragged(double x, double y, int button, double dx, double dy) { return mouseDragged(new MouseButtonEvent(x, y, button), dx, dy); }

    @Override public final void render(int mouseX, int mouseY, float delta) {
        renderBackground();   // this version's Screen.render doesn't draw the background itself
        GuiGraphics g = new GuiGraphics(null, this);
        extractRenderState(g, mouseX, mouseY, delta);
        for (AbstractWidget w : buttons)   // button tooltips (this version's buttons have none)
            if (w instanceof Btn.TipButton && ((Btn.TipButton) w).tip != null && ((Btn.TipButton) w).hovered() && w.visible) {
                java.util.List<net.minecraft.network.chat.Component> l = new java.util.ArrayList<>();
                for (String s : font.split(((Btn.TipButton) w).tip.text.getString(), 200)) l.add(new net.minecraft.network.chat.TextComponent(s));
                g.renderComponentTooltip(font, l, mouseX, mouseY);
            }
    }
    // this version scrolls with one amount; the shared screens use the newer (x, y, dx, dy) form
    public boolean mouseScrolled(double x, double y, double dx, double dy) { return super.mouseScrolled(x, y, dy); }
    @Override public final boolean mouseScrolled(double x, double y, double d) { return mouseScrolled(x, y, 0, d); }
}
