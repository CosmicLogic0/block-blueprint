package net.fabricmc.fabric.api.client.rendering.v1.level;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/** Stand-in for Fabric's level render events (Forge 26.x): fired by dev.buildblueprint.mixin.LevelRendererMixin. */
public final class LevelRenderEvents {
    private LevelRenderEvents() {}
    public interface Callback { void run(LevelRenderContext ctx); }
    public static final class Event {
        final List<Callback> list = new ArrayList<>();
        public void register(Callback cb) { list.add(cb); }
        public void fire(LevelRenderState state, SubmitNodeCollector out) {
            if (list.isEmpty()) return;
            final PoseStack pose = new PoseStack();   // Minecraft starts this step with a fresh one too
            LevelRenderContext ctx = new LevelRenderContext() {
                public LevelRenderState levelState() { return state; }
                public PoseStack poseStack() { return pose; }
                public SubmitNodeCollector submitNodeCollector() { return out; }
            };
            for (Callback cb : list) cb.run(ctx);
        }
    }
    public static final Event COLLECT_SUBMITS = new Event();
    public static final Event BEFORE_GIZMOS = new Event();
}
