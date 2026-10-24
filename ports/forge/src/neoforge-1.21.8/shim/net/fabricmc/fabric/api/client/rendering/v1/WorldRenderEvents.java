package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Stand-in for Fabric's world render events (NeoForge 1.21.6+: one event class per stage). */
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
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks e) -> {
            final Camera cam = e.getCamera(); final PoseStack pose = e.getPoseStack();
            WorldRenderContext ctx = new WorldRenderContext() {
                public Camera camera() { return cam; }
                public PoseStack matrixStack() { return pose; }
            };
            for (AfterTranslucent cb : AFTER_TRANSLUCENT.list) cb.afterTranslucent(ctx);
        });
    }
}
