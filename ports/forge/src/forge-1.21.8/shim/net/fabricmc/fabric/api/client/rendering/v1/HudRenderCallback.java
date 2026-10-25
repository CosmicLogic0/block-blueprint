package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;

/** Stand-in for Fabric's HUD callback (Forge 1.21.6+: drawn with the chat overlay). */
public interface HudRenderCallback {
    void onHudRender(GuiGraphics g, float delta);
    Holder EVENT = new Holder();
    final class Holder {
        private final List<HudRenderCallback> list = new ArrayList<>();
        private boolean hooked;
        public void register(HudRenderCallback cb) {
            if (!hooked) { hooked = true; CustomizeGuiOverlayEvent.Chat.BUS.addListener(e -> { for (HudRenderCallback c : list) c.onHudRender(e.getGuiGraphics(), 0f); }); }
            list.add(cb);
        }
    }
}
