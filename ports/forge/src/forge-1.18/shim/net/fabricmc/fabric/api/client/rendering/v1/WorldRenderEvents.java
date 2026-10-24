package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLevelLastEvent;
import net.minecraftforge.common.MinecraftForge;

/** Stand-in for Fabric's world render events (1.18 Forge: RenderLevelLastEvent). */
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
        MinecraftForge.EVENT_BUS.addListener((RenderLevelLastEvent e) -> {
            final Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera(); final PoseStack pose = e.getPoseStack();
            WorldRenderContext ctx = new WorldRenderContext() {
                public Camera camera() { return cam; }
                public PoseStack matrixStack() { return pose; }
            };
            for (AfterTranslucent cb : AFTER_TRANSLUCENT.list) cb.afterTranslucent(ctx);
        });
    }
}
