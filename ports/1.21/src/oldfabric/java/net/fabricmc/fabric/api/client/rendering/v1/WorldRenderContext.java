package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;

/** Stand-in for Fabric's world render context (old Minecraft versions, see WorldRenderEvents). */
public interface WorldRenderContext {
    Camera camera();
    PoseStack matrixStack();
}
