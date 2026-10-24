package dev.buildblueprint.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Before 1.19.3 buttons had no builder or Tooltip class; this gives the shared screens the newer style. */
public final class Btn {
    private Btn() {}
    public static final class Tip { final Component text; Tip(Component t) { text = t; } }
    public static Tip tip(Component c) { return new Tip(c); }
    public static Builder builder(Component msg, Button.OnPress press) { return new Builder(msg, press); }
    public static final class Builder {
        private final Component msg; private final Button.OnPress press; private int x, y, w = 150, h = 20; private Tip tip;
        Builder(Component msg, Button.OnPress press) { this.msg = msg; this.press = press; }
        public Builder bounds(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; return this; }
        public Builder tooltip(Tip t) { tip = t; return this; }
        public Button build() {
            if (tip == null) return new Button(x, y, w, h, msg, press);
            final Tip t = tip;
            return new Button(x, y, w, h, msg, press, (b, pose, mx, my) -> {
                Screen s = Minecraft.getInstance().screen;
                if (s != null) s.renderTooltip(pose, Minecraft.getInstance().font.split(t.text, 200), mx, my);
            });
        }
    }
}
