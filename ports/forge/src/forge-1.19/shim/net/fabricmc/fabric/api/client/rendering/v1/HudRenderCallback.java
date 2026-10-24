package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/** Stand-in for Fabric's HUD callback (1.19 Forge: drawn after the vanilla HUD). */
public interface HudRenderCallback {
    void onHudRender(PoseStack pose, float delta);
    Holder EVENT = new Holder();
    final class Holder {
        private final List<HudRenderCallback> list = new ArrayList<>();
        private boolean hooked;
        public void register(HudRenderCallback cb) {
            if (!hooked) { hooked = true; MinecraftForge.EVENT_BUS.addListener((RenderGuiOverlayEvent.Post e) -> {
                if (!"chat_panel".equals(e.getOverlay().id().getPath())) return;   // once per frame, just before chat
                for (HudRenderCallback c : list) c.onHudRender(e.getPoseStack(), e.getPartialTick()); }); }
            list.add(cb);
        }
    }
}
