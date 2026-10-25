package net.fabricmc.fabric.api.client.rendering.v1.level;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Stand-in for Fabric's level render events (NeoForge 26.1.x). Ghost blocks: SubmitCustomGeometryEvent, where Fabric
 * fires COLLECT_SUBMITS. Outlines (gizmos): ExtractLevelRenderStateEvent, which fires right after Minecraft emits its
 * own debug gizmos for the frame.
 */
public final class LevelRenderEvents {
    private LevelRenderEvents() {}
    public interface Callback { void run(LevelRenderContext ctx); }
    public static final class Event {
        final List<Callback> list = new ArrayList<>();
        public void register(Callback cb) { hook(); list.add(cb); }
    }
    public static final Event COLLECT_SUBMITS = new Event();
    public static final Event BEFORE_GIZMOS = new Event();
    private static boolean hooked;
    private static void hook() {
        if (hooked) return;
        hooked = true;
        NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent e) -> {
            final LevelRenderState state = e.getLevelRenderState(); final PoseStack pose = e.getPoseStack(); final SubmitNodeCollector out = e.getSubmitNodeCollector();
            LevelRenderContext ctx = new LevelRenderContext() {
                public LevelRenderState levelState() { return state; }
                public PoseStack poseStack() { return pose; }
                public SubmitNodeCollector submitNodeCollector() { return out; }
            };
            for (Callback cb : COLLECT_SUBMITS.list) cb.run(ctx);
        });
        NeoForge.EVENT_BUS.addListener((ExtractLevelRenderStateEvent e) -> {
            final LevelRenderState state = e.getRenderState();
            LevelRenderContext ctx = new LevelRenderContext() {
                public LevelRenderState levelState() { return state; }
                public PoseStack poseStack() { return new PoseStack(); }
                public SubmitNodeCollector submitNodeCollector() { throw new IllegalStateException("not while extracting"); }
            };
            for (Callback cb : BEFORE_GIZMOS.list) cb.run(ctx);
        });
    }
}
