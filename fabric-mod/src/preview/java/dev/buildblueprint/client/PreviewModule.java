package dev.buildblueprint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.MaterialMap;
import dev.buildblueprint.core.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * In-world preview. Blocks still to place are drawn as see-through copies of the real block (a "ghost").
 * Where a different block is in the way, a thick red box is drawn. Blocks already built correctly are not drawn.
 */
public final class PreviewModule {
    private PreviewModule() {}

    private static final int MAX_GHOSTS = 60000, MAX_RED = 3000;
    private static final double RANGE = 64;
    private static final GizmoStyle RED = GizmoStyle.stroke(0xFFFF2020, 6f);
    private static final GizmoStyle CHEST = GizmoStyle.stroke(0xFF40FF80, 3f);
    private static int chestTick = 0;
    private static List<BlockPos> chestCache = new ArrayList<>();
    private static final GizmoStyle COPY = GizmoStyle.stroke(0xFF20E0FF, 4f), COPY1 = GizmoStyle.stroke(0xFFFFFF40, 4f);

    /** palette string -> parsed block state (null if it can't be parsed). */
    private static final Map<String, BlockState> STATES = new HashMap<>();
    private static final Map<BlockState, List<BakedQuad>[]> QUADS = new HashMap<>();
    private static final Direction[] DIRS = Direction.values();

    private record Ghost(int x, int y, int z, BlockState state, int lx, int ly, int lz) {}
    private record Red(int x, int y, int z) {}
    private static final List<Ghost> ghosts = new ArrayList<>();
    private static final List<Red> reds = new ArrayList<>();
    private static final java.util.Set<Long> ghostAt = new java.util.HashSet<>();

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            if (needScan()) { scan(); pickHint(Minecraft.getInstance()); sorted = false; }
            if (ghosts.isEmpty()) return;
            Vec3 cam = ctx.levelState().cameraRenderState.pos;
            final double cx = cam.x, cy = cam.y, cz = cam.z;
            // far first so see-through blocks blend correctly (re-sorted when the camera has moved a bit)
            if (!sorted || Math.abs(cx - sortX) + Math.abs(cy - sortY) + Math.abs(cz - sortZ) > 1.5) {
                ghosts.sort((a, b2) -> Double.compare(d2(b2, cx, cy, cz), d2(a, cx, cy, cz)));
                sorted = true; sortX = cx; sortY = cy; sortZ = cz;
            }
            PoseStack pose = ctx.poseStack();
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.translucentMovingBlock(), PreviewModule::drawGhosts);
            pose.popPose();
        });
        LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> {
            if (Settings.redOutline) for (Red r : reds) Gizmos.cuboid(new BlockPos(r.x, r.y, r.z), RED);
            // next block to place: pulsing yellow box, and a guide line when it's far away
            if (Settings.nextHint && Hint.pos != null) {
                float pulse = 3f + 2f * (float) Math.sin(System.currentTimeMillis() / 180.0);
                Gizmos.cuboid(Hint.pos, GizmoStyle.stroke(Hint.inHand ? 0xFF60FF60 : 0xFFFFE040, pulse));
                var pl = Minecraft.getInstance().player;
                if (pl != null && Hint.dist > 6) {
                    Vec3 from = pl.position().add(0, 1.0, 0).add(pl.getLookAngle().multiply(1.5, 0, 1.5));
                    Gizmos.line(from, Vec3.atCenterOf(Hint.pos), 0xC0FFE040, 2f);
                }
            }
            // chest finder: chests you've opened that hold blocks you still need
            if (Settings.chestFinder && BuildBlueprintClient.SESSION.loaded()) {
                if (++chestTick % 20 == 0) chestCache = Chests.useful(48);
                for (BlockPos c : chestCache) Gizmos.cuboid(c, CHEST);
            }
            // copy mode: the box being selected (cyan), live to where you're looking until the 2nd corner is set
            if (Copier.active && Copier.c1 != null && Minecraft.getInstance().player != null) {
                BlockPos a = Copier.c1, b = Copier.c2 != null ? Copier.c2 : Copier.c1;
                Gizmos.cuboid(new net.minecraft.world.phys.AABB(Math.min(a.getX(), b.getX()) - 0.01, Math.min(a.getY(), b.getY()) - 0.01, Math.min(a.getZ(), b.getZ()) - 0.01,
                        Math.max(a.getX(), b.getX()) + 1.01, Math.max(a.getY(), b.getY()) + 1.01, Math.max(a.getZ(), b.getZ()) + 1.01), COPY);
                Gizmos.cuboid(a, COPY1); Gizmos.cuboid(b, COPY1);   // yellow corners
            }
        });
    }

    // Big builds: checking every cell against the world each frame is slow, so it's done every few frames,
    // or straight away when the blueprint moves, turns, swaps or changes layer.
    private static int frame = 0;
    private static String lastKey = "";
    private static boolean sorted = false;
    private static double sortX, sortY, sortZ;
    private static boolean needScan() {
        Session s = BuildBlueprintClient.SESSION;
        Minecraft mc = Minecraft.getInstance();
        String key = s.loaded() && mc.player != null ? System.identityHashCode(s.blueprint()) + ":" + s.ox + "," + s.oy + "," + s.oz + ":" + s.layer + ":" + s.visible + ":" + Settings.range
                + ":" + ((int) mc.player.getX() >> 2) + "," + ((int) mc.player.getY() >> 2) + "," + ((int) mc.player.getZ() >> 2) : "none";
        boolean changed = !key.equals(lastKey);
        lastKey = key;
        return changed || ++frame % 8 == 0;
    }

    /** Works out which blocks to draw. */
    private static void scan() {
        ghosts.clear();
        reds.clear();
        ghostAt.clear();
        Session s = BuildBlueprintClient.SESSION;
        Minecraft mc = Minecraft.getInstance();
        if (!s.loaded() || !s.visible || mc.level == null || mc.player == null) { Hint.clear(); return; }
        Blueprint bp = s.blueprint();
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < bp.sy; y++) {
            if (!s.layerShown(y)) continue;
            for (int z = 0; z < bp.sz; z++)
                for (int x = 0; x < bp.sx; x++) {
                    String st = bp.stateAt(x, y, z);
                    if (st == null) continue;
                    int wx = s.ox + x, wy = s.oy + y, wz = s.oz + z;
                    double dx = wx + 0.5 - px, dy = wy + 0.5 - py, dz = wz + 0.5 - pz;
                    if (dx * dx + dy * dy + dz * dz > (double) Settings.range * Settings.range) continue;
                    pos.set(wx, wy, wz);
                    BlockState here = mc.level.getBlockState(pos);
                    String have = BuiltInRegistries.BLOCK.getKey(here.getBlock()).toString();
                    if (have.equals(MaterialMap.blockId(st))) continue;     // already built
                    if (here.isAir() || !here.getFluidState().isEmpty() || here.canBeReplaced()) {
                        BlockState want = parse(st);
                        if (want != null && ghosts.size() < MAX_GHOSTS) { ghosts.add(new Ghost(wx, wy, wz, want, x, y, z)); ghostAt.add(((long) x << 40) | ((long) y << 20) | z); }
                    } else if (reds.size() < MAX_RED) {
                        reds.add(new Red(wx, wy, wz));
                    }
                }
        }
    }

    /** Next block helper: lowest unfinished layer first, then the block you're holding, then the closest. */
    private static void pickHint(Minecraft mc) {
        if (!Settings.nextHint || ghosts.isEmpty() || mc.player == null) { Hint.clear(); return; }
        int low = Integer.MAX_VALUE;
        for (Ghost g : ghosts) low = Math.min(low, g.ly);
        String held = BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString();
        double px = mc.player.getX(), py = mc.player.getEyeY() - 1, pz = mc.player.getZ();
        Ghost best = null; double bd = 0; boolean bestHeld = false;
        for (Ghost g : ghosts) {
            if (g.ly != low) continue;
            MaterialMap.Req r = MaterialMap.of(BuildBlueprintClient.SESSION.blueprint().stateAt(g.lx, g.ly, g.lz));
            boolean h = r != null && r.itemId().equals(held);
            double d = d2(g, px, py, pz);
            if (best == null || (h && !bestHeld) || (h == bestHeld && d < bd)) { best = g; bd = d; bestHeld = h; }
        }
        if (best == null) { Hint.clear(); return; }
        String st = BuildBlueprintClient.SESSION.blueprint().stateAt(best.lx, best.ly, best.lz);
        Hint.pos = new BlockPos(best.x, best.y, best.z);
        Hint.state = st;
        MaterialMap.Req r = MaterialMap.of(st);
        Hint.icon = r == null ? net.minecraft.world.item.ItemStack.EMPTY : Materials.stackFor(r.itemId());
        Hint.name = Hint.icon.isEmpty() ? PlayerInfo.pretty(MaterialMap.blockId(st)) : Hint.icon.getHoverName().getString();
        Hint.dist = Math.sqrt(bd);
        Hint.inHand = bestHeld;
    }

    private static double d2(Ghost g, double x, double y, double z) { double a = g.x + 0.5 - x, b = g.y + 0.5 - y, c = g.z + 0.5 - z; return a * a + b * b + c * c; }

    private static BlockState parse(String st) {
        if (STATES.containsKey(st)) return STATES.get(st);
        BlockState out = null;
        try {
            out = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, st, false).blockState();
        } catch (Exception e) {
            try {   // unknown property: fall back to the plain block
                out = BuiltInRegistries.BLOCK.getValue(Identifier.parse(MaterialMap.blockId(st))).defaultBlockState();
            } catch (Exception ignored) {}
        }
        if (out != null && out.isAir()) out = null;
        STATES.put(st, out);
        return out;
    }

    /** Quads for a state: index 0-5 = faces that touch each side, 6 = the rest. */
    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] quads(BlockState state) {
        return QUADS.computeIfAbsent(state, st -> {
            List<BakedQuad>[] out = new List[7];
            for (int i = 0; i < 7; i++) out[i] = new ArrayList<>();
            List<BlockStateModelPart> parts = new ArrayList<>();
            Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(st).collectParts(RandomSource.create(42L), parts);
            for (BlockStateModelPart part : parts) {
                for (Direction d : DIRS) out[d.ordinal()].addAll(part.getQuads(d));
                out[6].addAll(part.getQuads(null));
            }
            return out;
        });
    }

    private static void drawGhosts(PoseStack.Pose base, VertexConsumer vc) {
        Minecraft mc = Minecraft.getInstance();
        Blueprint bp = BuildBlueprintClient.SESSION.blueprint();
        if (bp == null) return;
        PoseStack ps = new PoseStack();
        ps.last().pose().set(base.pose());
        ps.last().normal().set(base.normal());
        QuadInstance qi = new QuadInstance();
        qi.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
        qi.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        for (Ghost g : ghosts) {
            List<BakedQuad>[] q = quads(g.state);
            ps.pushPose();
            ps.translate(g.x, g.y, g.z);
            PoseStack.Pose p = ps.last();
            for (int i = 0; i < 7; i++) {
                if (i < 6 && hidden(bp, g, DIRS[i])) continue;   // skip faces pressed against another ghost
                for (BakedQuad quad : q[i]) {
                    int rgb = 0xFFFFFF;
                    var info = quad.materialInfo();
                    if (info.isTinted()) {
                        try {
                            BlockTintSource src = mc.getBlockColors().getTintSource(g.state, info.tintIndex());
                            if (src != null) rgb = src.color(g.state) & 0xFFFFFF;
                        } catch (Exception ignored) {}
                    }
                    qi.setColor(((int) (Settings.ghostAlpha * 2.55) << 24) | rgb);
                    vc.putBakedQuad(p, quad, qi);
                }
            }
            ps.popPose();
        }
    }

    private static final Map<BlockState, Boolean> FULL = new HashMap<>();
    /** a full, solid, non-see-through cube (stairs, slabs, glass, leaves... are not) */
    private static boolean full(BlockState st) { return FULL.computeIfAbsent(st, b -> b.isSolidRender() && b.canOcclude()); }

    /** True when the neighbouring cell is another solid ghost block, so this face can't be seen anyway. */
    private static boolean hidden(Blueprint bp, Ghost g, Direction d) {
        int nx = g.lx + d.getStepX(), ny = g.ly + d.getStepY(), nz = g.lz + d.getStepZ();
        if (nx < 0 || ny < 0 || nz < 0 || nx >= bp.sx || ny >= bp.sy || nz >= bp.sz) return false;
        if (!BuildBlueprintClient.SESSION.layerShown(ny)) return false; // that neighbour isn't drawn: show this face
        if (!ghostAt.contains(((long) nx << 40) | ((long) ny << 20) | nz)) return false; // neighbour already built (or blocked): show this face
        String n = bp.stateAt(nx, ny, nz);
        if (n == null) return false;
        BlockState ns = parse(n);
        return ns != null && full(ns) && full(g.state);
    }
}
