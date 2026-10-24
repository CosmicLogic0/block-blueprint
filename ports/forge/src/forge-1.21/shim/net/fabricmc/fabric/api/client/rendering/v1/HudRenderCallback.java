package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/** Stand-in for Fabric's HUD callback (Forge 1.20.6+: drawn with the chat overlay, once per frame). */
public interface HudRenderCallback {
    void onHudRender(GuiGraphics g, float delta);
    Holder EVENT = new Holder();
    final class Holder {
        private final List<HudRenderCallback> list = new ArrayList<>();
        private boolean hooked;
        public void register(HudRenderCallback cb) {
            if (!hooked) { hooked = true; MinecraftForge.EVENT_BUS.addListener((CustomizeGuiOverlayEvent.Chat e) -> { for (HudRenderCallback c : list) c.onHudRender(e.getGuiGraphics(), 0f); }); }
            list.add(cb);
        }
    }
}
