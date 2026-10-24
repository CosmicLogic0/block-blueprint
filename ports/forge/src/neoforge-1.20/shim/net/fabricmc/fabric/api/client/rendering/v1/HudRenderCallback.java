package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Stand-in for Fabric's HUD callback, backed by NeoForge's RenderGuiEvent.Post. */
public interface HudRenderCallback {
    void onHudRender(GuiGraphics g, float delta);
    Holder EVENT = new Holder();
    final class Holder {
        private final List<HudRenderCallback> list = new ArrayList<>();
        private boolean hooked;
        public void register(HudRenderCallback cb) {
            if (!hooked) { hooked = true; NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> { for (HudRenderCallback c : list) c.onHudRender(e.getGuiGraphics(), e.getPartialTick()); }); }
            list.add(cb);
        }
    }
}
