package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;

/**
 * Stand-in for Fabric's world render event on old Minecraft versions whose Fabric API doesn't have it yet
 * (1.16.1 and older). Fired by our own LevelRendererMixin after the world has been drawn.
 * Only built into the jars for those versions.
 */
public final class WorldRenderEvents {
    private WorldRenderEvents() {}
    public interface AfterTranslucent { void afterTranslucent(WorldRenderContext ctx); }
    public static final class Event {
        final List<AfterTranslucent> list = new ArrayList<>();
        public void register(AfterTranslucent cb) { list.add(cb); }
    }
    public static final Event AFTER_TRANSLUCENT = new Event();

    public static void fire(final PoseStack pose, final Camera cam) {
        WorldRenderContext ctx = new WorldRenderContext() {
            public Camera camera() { return cam; }
            public PoseStack matrixStack() { return pose; }
        };
        for (AfterTranslucent cb : AFTER_TRANSLUCENT.list) cb.afterTranslucent(ctx);
    }
}
