package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;

/** Stand-in for Fabric's world render events (Forge 1.20.6+: the camera rotation is already on the model-view stack). */
public final class WorldRenderEvents {
    private WorldRenderEvents() {}
    public interface AfterTranslucent { void afterTranslucent(WorldRenderContext ctx); }
    public static final class Event {
        final List<AfterTranslucent> list = new ArrayList<>();
        public void register(AfterTranslucent cb) { hook(); list.add(cb); }
    }
    public static final Event AFTER_TRANSLUCENT = new Event();
    private static boolean hooked;
    private static void hook() {
        if (hooked) return;
        hooked = true;
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
            final Camera cam = e.getCamera(); final PoseStack pose = new PoseStack();
            WorldRenderContext ctx = new WorldRenderContext() {
                public Camera camera() { return cam; }
                public PoseStack matrixStack() { return pose; }
            };
            for (AfterTranslucent cb : AFTER_TRANSLUCENT.list) cb.afterTranslucent(ctx);
        });
    }
}
