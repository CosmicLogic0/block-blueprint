package dev.buildblueprint.mixin;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 26.1.x has no world render event: the same two hooks Fabric uses, inside LevelRenderer's main pass. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private SubmitNodeStorage submitNodeStorage;

    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=renderSolidFeatures"))
    private void buildblueprint$collectSubmits(CallbackInfo ci) {
        try { LevelRenderEvents.COLLECT_SUBMITS.fire(levelRenderState, submitNodeStorage); } catch (Throwable ignored) {}
    }

    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizeGizmoCollection()V"))
    private void buildblueprint$beforeGizmos(CallbackInfo ci) {
        try { LevelRenderEvents.BEFORE_GIZMOS.fire(levelRenderState, submitNodeStorage); } catch (Throwable ignored) {}
    }
}
