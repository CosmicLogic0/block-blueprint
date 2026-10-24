package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/** Stand-in for Fabric's HUD callback (1.18 Forge). */
public interface HudRenderCallback {
    void onHudRender(PoseStack pose, float delta);
    Holder EVENT = new Holder();
    final class Holder {
        private final List<HudRenderCallback> list = new ArrayList<>();
        private boolean hooked;
        public void register(HudRenderCallback cb) {
            if (!hooked) { hooked = true; MinecraftForge.EVENT_BUS.addListener((RenderGameOverlayEvent.Post e) -> {
                if (e.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
                for (HudRenderCallback c : list) c.onHudRender(e.getMatrixStack(), e.getPartialTicks()); }); }
            list.add(cb);
        }
    }
}
