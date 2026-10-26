package dev.buildblueprint.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the ghost preview after the world (old versions whose Fabric API has no event for it). */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void buildblueprint$afterLevel(PoseStack pose, float partialTick, long finishNanos, boolean outline, Camera camera,
                                          net.minecraft.client.renderer.GameRenderer gameRenderer, net.minecraft.client.renderer.LightTexture light,
                                          com.mojang.math.Matrix4f projection, CallbackInfo ci) {
        try { WorldRenderEvents.fire(pose, camera); } catch (Throwable ignored) {}
    }
}
