package dev.buildblueprint.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Passes vertices through but replaces the alpha, so ghost blocks can be see-through on versions without an alpha option. */
final class AlphaConsumer implements VertexConsumer {
    final VertexConsumer inner; int alpha = 255;
    AlphaConsumer(VertexConsumer inner) { this.inner = inner; }
    @Override public VertexConsumer vertex(double x, double y, double z) { inner.vertex(x, y, z); return this; }
    @Override public VertexConsumer color(int r, int g, int b, int a) { inner.color(r, g, b, alpha); return this; }
    @Override public VertexConsumer uv(float u, float v) { inner.uv(u, v); return this; }
    @Override public VertexConsumer overlayCoords(int u, int v) { inner.overlayCoords(u, v); return this; }
    @Override public VertexConsumer uv2(int u, int v) { inner.uv2(u, v); return this; }
    @Override public VertexConsumer normal(float x, float y, float z) { inner.normal(x, y, z); return this; }
    @Override public void endVertex() { inner.endVertex(); }
    @Override public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        inner.vertex(x, y, z, r, g, b, alpha / 255f, u, v, overlay, light, nx, ny, nz);
    }
}
