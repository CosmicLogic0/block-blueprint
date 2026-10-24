package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

/** Stand-in for Fabric's world render events. Forge 1.21.3+ has no world render event, so a small mixin calls fire(). */
public final class WorldRenderEvents {
    private WorldRenderEvents() {}
    public interface AfterTranslucent { void afterTranslucent(WorldRenderContext ctx); }
    public static final class Event {
        final List<AfterTranslucent> list = new ArrayList<>();
        public void register(AfterTranslucent cb) { list.add(cb); }
    }
    public static final Event AFTER_TRANSLUCENT = new Event();
    /** Called at the end of LevelRenderer.renderLevel: the camera rotation has to be applied by hand there. */
    public static void fire() {
        if (AFTER_TRANSLUCENT.list.isEmpty()) return;
        final Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        final PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(cam.getXRot()));
        pose.mulPose(Axis.YP.rotationDegrees(cam.getYRot() + 180f));
        WorldRenderContext ctx = new WorldRenderContext() {
            public Camera camera() { return cam; }
            public PoseStack matrixStack() { return pose; }
        };
        for (AfterTranslucent cb : AFTER_TRANSLUCENT.list) cb.afterTranslucent(ctx);
    }
}
