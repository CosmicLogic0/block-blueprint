package net.fabricmc.fabric.api.client.rendering.v1.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/** Stand-in for Fabric's level render context (NeoForge build). */
public interface LevelRenderContext {
    LevelRenderState levelState();
    PoseStack poseStack();
    SubmitNodeCollector submitNodeCollector();
}
