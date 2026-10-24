package dev.buildblueprint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Drawing calls that differ between Minecraft versions (this copy: 1.17.1). */
public final class Draw {
    private Draw() {}
    public static RenderType ghostType() { return Sheets.translucentCullBlockSheet(); }
    public static RenderType lineType() { return RenderType.lines(); }
    /** out[0..5] = quads on each side, out[6] = the rest */
    public static void quads(BlockState st, List<BakedQuad>[] out) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(st);
        for (Direction d : Direction.values()) out[d.ordinal()].addAll(model.getQuads(st, d, new java.util.Random(42L)));
        out[6].addAll(model.getQuads(st, null, new java.util.Random(42L)));
    }
    public static int tintIndex(BakedQuad q) { return q.isTinted() ? q.getTintIndex() : -1; }
    public static void lineVertex(VertexConsumer vc, PoseStack.Pose p, Vec3 at, Vec3 n, int argb) {
        vc.vertex(p.pose(), (float) at.x, (float) at.y, (float) at.z).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255).normal(p.normal(), (float) n.x, (float) n.y, (float) n.z).endVertex();
    }
    private static AlphaConsumer wrap;
    public static void quad(VertexConsumer vc0, PoseStack.Pose p, BakedQuad q, float r, float g, float b, float a, int light, int overlay) {
        if (wrap == null || wrap.inner != vc0) wrap = new AlphaConsumer(vc0);
        wrap.alpha = Math.round(a * 255); VertexConsumer vc = wrap;
        vc.putBulkData(p, q, r, g, b, light, overlay);   // this version has no alpha here, so the wrapper adds it
    }
}
