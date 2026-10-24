package dev.buildblueprint.mixin;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the ghost preview after the world has been rendered (this Forge version has no event for it). */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void buildblueprint$afterLevel(CallbackInfo ci) {
        try { WorldRenderEvents.fire(); } catch (Throwable ignored) {}
    }
}
