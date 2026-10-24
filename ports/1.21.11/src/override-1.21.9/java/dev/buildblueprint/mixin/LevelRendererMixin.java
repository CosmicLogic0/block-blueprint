package dev.buildblueprint.mixin;

import dev.buildblueprint.client.PreviewModule;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric 1.21.9 has no world render events, so the preview is drawn from here. */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void buildblueprint$afterLevel(CallbackInfo ci) { PreviewModule.afterLevel(); }
}
