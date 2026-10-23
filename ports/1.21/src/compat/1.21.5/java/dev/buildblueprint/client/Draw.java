package dev.buildblueprint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Drawing calls that differ between Minecraft versions (this copy: 1.21.5). */
public final class Draw {
    private Draw() {}
    public static RenderType ghostType() { return RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS); }
    public static RenderType lineType() { return RenderType.lines(); }
    /** out[0..5] = quads on each side, out[6] = the rest */
    public static void quads(BlockState st, List<BakedQuad>[] out) {
        List<BlockModelPart> parts = Minecraft.getInstance().getBlockRenderer().getBlockModel(st).collectParts(RandomSource.create(42L));
        for (BlockModelPart part : parts) {
            for (Direction d : Direction.values()) out[d.ordinal()].addAll(part.getQuads(d));
            out[6].addAll(part.getQuads(null));
        }
    }
    public static int tintIndex(BakedQuad q) { return q.isTinted() ? q.tintIndex() : -1; }
    public static void lineVertex(VertexConsumer vc, PoseStack.Pose p, Vec3 at, Vec3 n, int argb) {
        vc.addVertex(p, (float) at.x, (float) at.y, (float) at.z).setColor(argb).setNormal(p, (float) n.x, (float) n.y, (float) n.z);
    }
}
