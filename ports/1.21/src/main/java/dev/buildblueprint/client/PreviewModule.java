package dev.buildblueprint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.MaterialMap;
import dev.buildblueprint.core.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * In-world preview. Blocks still to place are drawn as see-through copies of the real block (a "ghost").
 * Where a different block is in the way, a thick red box is drawn. Blocks already built correctly are not drawn.
 */
public final class PreviewModule {
    private PreviewModule() {}

    private static final int MAX_GHOSTS = 60000, MAX_RED = 3000;
    private static final double RANGE = 64;
    private static int chestTick = 0;
    private static List<BlockPos> chestCache = new ArrayList<>();

    /** palette string -> parsed block state (null if it can't be parsed). */
    private static final Map<String, BlockState> STATES = new HashMap<>();
    private static final Map<BlockState, List<BakedQuad>[]> QUADS = new HashMap<>();
    private static final Direction[] DIRS = Direction.values();

    private static final class Ghost {
        int x, y, z;   // where it is in the world (moves with the build)
        final BlockState state;
        final int lx, ly, lz;   // where it is in the build
        int hide, tintI = -1, tintC = 0xFFFFFF;   // faces not drawn (one bit each), the colour worked out last
        List<BakedQuad>[] q;   // what it looks like (worked out the first time it's drawn)
        float[] box;
        boolean ready;
        Ghost(int x, int y, int z, BlockState state, int lx, int ly, int lz) { this.x = x; this.y = y; this.z = z; this.state = state; this.lx = lx; this.ly = ly; this.lz = lz; }
    }
    private record Red(int x, int y, int z) {}
    private static List<Ghost> ghosts = new ArrayList<>();
    private static List<Red> reds = new ArrayList<>();

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> Safe.run("the ghost preview", () -> render(ctx)));
    }

    private static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        long perfT = System.nanoTime();
        update(mc);
        Vec3 cam = ctx.camera().getPosition();
        final double cx = cam.x, cy = cam.y, cz = cam.z;
        PoseStack pose = ctx.matrixStack() != null ? ctx.matrixStack() : new PoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cx, -cy, -cz);
        if (!ghosts.isEmpty()) {
            // far first so see-through blocks blend correctly (re-sorted when the camera has moved a bit)
            if (!sorted || Math.abs(cx - sortX) + Math.abs(cy - sortY) + Math.abs(cz - sortZ) > 2.0) {
                sortGhosts(cx, cy, cz);
                sorted = true; sortX = cx; sortY = cy; sortZ = cz;
            }
            aim(Minecraft.getInstance(), cx, cy, cz);
            Perf.scan(System.nanoTime() - perfT); Perf.ghosts = ghosts.size();
            RenderType ghostType = Draw.ghostType();
            Safe.run("drawing ghost blocks", () -> drawGhosts(pose, buf.getBuffer(ghostType)));
            buf.endBatch(ghostType);
        }
        VertexConsumer lines = buf.getBuffer(Draw.lineType());
        if (Settings.redOutline) for (Red r : reds) box(pose, lines, new AABB(new BlockPos(r.x, r.y, r.z)).inflate(0.004), 0xFFFF2020);
        if (Settings.scaffold) for (BlockPos sb : Materials.scaffold()) box(pose, lines, new AABB(sb).inflate(-0.08), 0xFF60C8FF);   // where temporary blocks help (Settings > Extras)
        // next block to place: pulsing yellow box, and a guide line when it's far away
        if ((Settings.nextHint || Mistakes.showing()) && Hint.pos != null) {
            float pulse = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 180.0);
            int col = Hint.inHand ? 0xFF60FF60 : 0xFFFFE040;
            box(pose, lines, new AABB(Hint.pos).inflate(0.01 + 0.03 * pulse), col);
            var pl = mc.player;
            if (pl != null && Hint.dist > 6) {
                Vec3 from = pl.position().add(0, 1.0, 0).add(pl.getLookAngle().multiply(1.5, 0, 1.5));
                line(pose, lines, from, Vec3.atCenterOf(Hint.pos), 0xC0FFE040);
            }
        }
        // chest finder: chests you've opened that hold blocks you still need
        if (Settings.chestFinder && BuildBlueprintClient.SESSION.loaded()) {
            if (++chestTick % 20 == 0) chestCache = Chests.useful(48);
            for (BlockPos c : chestCache) box(pose, lines, new AABB(c).inflate(0.01), 0xFF40FF80);
        }
        // copy mode: the box being selected (cyan), live to where you're looking until the 2nd corner is set
        if (Copier.active && Copier.c1 != null && mc.player != null) {
            BlockPos a = Copier.c1, b = Copier.c2 != null ? Copier.c2 : Copier.c1;
            box(pose, lines, new AABB(Math.min(a.getX(), b.getX()) - 0.01, Math.min(a.getY(), b.getY()) - 0.01, Math.min(a.getZ(), b.getZ()) - 0.01,
                    Math.max(a.getX(), b.getX()) + 1.01, Math.max(a.getY(), b.getY()) + 1.01, Math.max(a.getZ(), b.getZ()) + 1.01), 0xFF20E0FF);
            box(pose, lines, new AABB(a).inflate(0.02), 0xFFFFFF40); box(pose, lines, new AABB(b).inflate(0.02), 0xFFFFFF40);   // yellow corners
        }
        buf.endBatch(Draw.lineType());
        pose.popPose();
    }

    private static void box(PoseStack pose, VertexConsumer vc, AABB bb, int argb) {
        double[] xs = {bb.minX, bb.maxX}, ys = {bb.minY, bb.maxY}, zs = {bb.minZ, bb.maxZ};
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {   // the 12 edges
            line(pose, vc, new Vec3(xs[0], ys[a], zs[b]), new Vec3(xs[1], ys[a], zs[b]), argb);
            line(pose, vc, new Vec3(xs[a], ys[0], zs[b]), new Vec3(xs[a], ys[1], zs[b]), argb);
            line(pose, vc, new Vec3(xs[a], ys[b], zs[0]), new Vec3(xs[a], ys[b], zs[1]), argb);
        }
    }

    private static void line(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, int argb) {
        Vec3 n = b.subtract(a).normalize();
        Draw.lineVertex(vc, pose.last(), a, n, argb);
        Draw.lineVertex(vc, pose.last(), b, n, argb);
    }

    // Which blocks to draw. Checking every cell of a big build against the world takes a while, so it's done a few
    // milliseconds at a time over several frames instead of freezing the game. Moving the build moves the ghosts
    // already worked out straight away. Between full checks only the ghosts, the red boxes and the cells around you
    // are looked at again (that's where blocks get placed and broken).
    private static final int NEAR_BELOW = 3, NEAR_ABOVE = 4;   // "nearby layers only": from 3 below your feet to 4 above
    private static final long FIRST_NS = 8_000_000L, BUDGET_NS = 3_000_000L, FULL_EVERY_MS = 2500;
    private static String lastBuild = "", lastNear = "";
    private static int lastOx, lastOy, lastOz, frame = 0;
    private static long lastFull = 0;
    private static boolean sorted = false, wasShown = false;
    private static double sortX, sortY, sortZ;
    private static Scan job, view;   // the check in progress, and the one the ghosts on screen came from

    private static void update(Minecraft mc) {
        Session s = BuildBlueprintClient.SESSION;
        frame++;
        if (!s.loaded() || !s.visible || mc.level == null || mc.player == null) {
            job = null; view = null; lastBuild = "";
            if (!ghosts.isEmpty() || !reds.isEmpty()) { ghosts = new ArrayList<>(); reds = new ArrayList<>(); }
            if (wasShown || frame % 8 == 0) { Hint.clear(); pickHint(mc); }
            wasShown = false;
            return;
        }
        wasShown = true;
        Blueprint bp = s.blueprint();
        String build = System.identityHashCode(bp) + ":" + s.layer + ":" + Settings.range + ":" + Hide.version() + ":" + Coop.parts + "/" + Coop.me + ":" + Settings.nearLayers;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        String near = Settings.nearLayers && s.layer < 0 ? "" + (int) Math.floor(py) : "";   // nearby layers only: which height you're at
        long now = System.currentTimeMillis(), t0 = System.nanoTime();
        if (!build.equals(lastBuild)) {   // another build, layer, distance or hidden blocks: start again
            lastBuild = build; lastOx = s.ox; lastOy = s.oy; lastOz = s.oz;
            ghosts = new ArrayList<>(); reds = new ArrayList<>(); view = null;
            job = new Scan(s, bp, px, py, pz); lastFull = now; lastNear = near;
            run(mc, s, t0 + FIRST_NS);
        } else if (s.ox != lastOx || s.oy != lastOy || s.oz != lastOz) {   // moved: the ghosts go with it now, then it's checked again
            int dx = s.ox - lastOx, dy = s.oy - lastOy, dz = s.oz - lastOz;
            for (Ghost g : ghosts) { g.x += dx; g.y += dy; g.z += dz; }
            if (view != null) { view.ox += dx; view.oy += dy; view.oz += dz; }
            reds = new ArrayList<>();
            if (view != null) view.redAt.clear();
            lastOx = s.ox; lastOy = s.oy; lastOz = s.oz;
            job = new Scan(s, bp, px, py, pz); lastFull = now; lastNear = near;
            run(mc, s, t0 + FIRST_NS);
        } else if (job != null) {
            run(mc, s, t0 + BUDGET_NS);
        } else if (now - lastFull > FULL_EVERY_MS || view == null || view.far(px, py, pz) || !near.equals(lastNear)) {   // now and then, when you've walked a fair way, or gone up or down (nearby layers only)
            job = new Scan(s, bp, px, py, pz); lastFull = now; lastNear = near;
            run(mc, s, t0 + BUDGET_NS);
        } else if (frame % 8 == 0) {
            refresh(mc, s);
            pickHint(mc);
        }
    }

    private static void run(Minecraft mc, Session s, long until) {
        if (!job.step(mc, s, until)) return;
        view = job; job = null;
        ghosts = view.g; reds = view.r;
        faces();
        sorted = false;
        pickHint(mc);
    }

    /** What each block of a build needs, worked out once per build. Index 0 = air. */
    private static final class Pal {
        final Blueprint bp;
        final BlockState[] want;
        final String[] id, item;
        final boolean[] props, full;
        Pal(Blueprint bp) {
            this.bp = bp;
            int n = bp.palette.length + 1;
            want = new BlockState[n]; id = new String[n]; item = new String[n]; props = new boolean[n]; full = new boolean[n];
            for (int i = 1; i < n; i++) {
                String st = bp.palette[i - 1];
                if (st == null) continue;
                want[i] = parse(st);
                id[i] = MaterialMap.blockId(st);
                props[i] = st.indexOf('[') >= 0;
                full[i] = want[i] != null && full(want[i]);
                MaterialMap.Req r = MaterialMap.of(st);
                item[i] = r == null ? null : r.itemId();
            }
        }
    }
    private static Pal pal;
    private static Pal pal(Blueprint bp) { if (pal == null || pal.bp != bp) pal = new Pal(bp); return pal; }

    private static final Map<net.minecraft.world.level.block.Block, String> IDS = new java.util.IdentityHashMap<>();
    private static String idOf(BlockState b) { return IDS.computeIfAbsent(b.getBlock(), k -> V.blockId(k)); }

    /** 0 = nothing to draw (already built the right way round), 1 = a ghost block, 2 = a red box (another block is in the way). */
    private static int check(Minecraft mc, Pal p, int c, int wx, int wy, int wz, BlockPos.MutableBlockPos pos) {
        BlockState here = mc.level.getBlockState(pos.set(wx, wy, wz));
        if (idOf(here).equals(p.id[c]) && (!p.props[c] || dev.buildblueprint.core.Orient.ok(p.bp.palette[c - 1], here.toString()))) return 0;   // the wrong way round gets a red box
        if (here.isAir() || !here.getFluidState().isEmpty() || V.replaceable(here)) return p.want[c] != null ? 1 : 0;
        return 2;
    }

    /** One check of the build against the world, done a bit at a time. */
    private static final class Scan {
        final Blueprint bp;
        final Pal p;
        final boolean[] hid;
        int ox, oy, oz;
        final double px, py, pz, r2;
        final int x0, x1, y0, y1, z0, z1, feet;
        final boolean near;
        int y, z;
        final List<Ghost> g = new ArrayList<>();
        final List<Red> r = new ArrayList<>();
        final java.util.BitSet at = new java.util.BitSet(), redAt = new java.util.BitSet();

        Scan(Session s, Blueprint bp, double px, double py, double pz) {
            this.bp = bp; p = pal(bp); ox = s.ox; oy = s.oy; oz = s.oz; this.px = px; this.py = py; this.pz = pz;
            double range = Settings.range;
            r2 = range * range;
            hid = new boolean[bp.palette.length + 1];
            if (Hide.any()) for (int i = 0; i < bp.palette.length; i++) hid[i + 1] = bp.palette[i] != null && Hide.state(bp.palette[i]);   // hidden with the eye in the build menu
            x0 = Math.max(0, (int) Math.floor(px - range) - ox); x1 = Math.min(bp.sx - 1, (int) Math.ceil(px + range) - ox);   // only the part that's close enough
            near = Settings.nearLayers && s.layer < 0; feet = (int) Math.floor(py);
            y0 = Math.max(0, Math.max((int) Math.floor(py - range) - oy, near ? feet - NEAR_BELOW - oy : 0)); y1 = Math.min(bp.sy - 1, Math.min((int) Math.ceil(py + range) - oy, near ? feet + NEAR_ABOVE - oy : bp.sy - 1));
            z0 = Math.max(0, (int) Math.floor(pz - range) - oz); z1 = Math.min(bp.sz - 1, (int) Math.ceil(pz + range) - oz);
            y = y0; z = z0;
        }

        /** Is this layer drawn? The layer you're on when building layer by layer; with "nearby layers only" just the ones around your height. */
        boolean shown(Session s, int y) { return s.layerShown(y) && (!near || (oy + y >= feet - NEAR_BELOW && oy + y <= feet + NEAR_ABOVE)); }

        /** Drawn at all? A block on a layer that's shown, not hidden, in my part (co-op) and close enough. */
        boolean in(Session s, int c, int x, int y, int z) {
            if (c == 0 || hid[c] || !shown(s, y) || !Coop.mine(bp, x, z)) return false;
            double dx = ox + x + 0.5 - px, dy = oy + y + 0.5 - py, dz = oz + z + 0.5 - pz;
            return dx * dx + dy * dy + dz * dz <= r2;
        }

        boolean far(double x, double y, double z) { return Math.abs(x - px) + Math.abs(y - py) + Math.abs(z - pz) > 8; }

        /** Works through the build a row at a time until the time is up. True once it's all done. */
        boolean step(Minecraft mc, Session s, long until) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (y <= y1 && x0 <= x1 && z0 <= z1) {
                if (shown(s, y)) {
                    int base = (y * bp.sz + z) * bp.sx;
                    for (int x = x0; x <= x1; x++) {
                        int c = bp.cells[base + x];
                        if (c != 0 && in(s, c, x, y, z)) add(mc, c, x, y, z, pos);
                    }
                } else z = z1;   // a layer that isn't shown: skip all of it
                if (++z > z1) { z = z0; y++; }
                if (System.nanoTime() > until) break;
            }
            return y > y1 || x0 > x1 || z0 > z1;
        }

        void add(Minecraft mc, int c, int x, int y, int z, BlockPos.MutableBlockPos pos) {
            int k = check(mc, p, c, ox + x, oy + y, oz + z, pos);
            if (k == 1 && g.size() < MAX_GHOSTS) { g.add(new Ghost(ox + x, oy + y, oz + z, p.want[c], x, y, z)); at.set(bp.index(x, y, z)); }
            else if (k == 2 && r.size() < MAX_RED) { r.add(new Red(ox + x, oy + y, oz + z)); redAt.set(bp.index(x, y, z)); }
        }
    }

    /** Between full checks: are the ghosts and red boxes still right, and has anything changed right around you? */
    private static void refresh(Minecraft mc, Session s) {
        Scan v = view;
        if (v == null || v.bp != s.blueprint()) return;
        Blueprint bp = v.bp;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<Ghost> ng = new ArrayList<>(ghosts.size());
        List<Red> nr = new ArrayList<>(reds.size());
        boolean changed = false, added = false;
        for (Ghost g : ghosts) {
            int i = bp.index(g.lx, g.ly, g.lz), k = check(mc, v.p, bp.cells[i], g.x, g.y, g.z, pos);
            if (k == 1) { ng.add(g); continue; }
            changed = true; v.at.clear(i);
            if (k == 2 && nr.size() < MAX_RED) { nr.add(new Red(g.x, g.y, g.z)); v.redAt.set(i); }
        }
        for (Red r : reds) {
            int lx = r.x - v.ox, ly = r.y - v.oy, lz = r.z - v.oz;
            if (lx < 0 || ly < 0 || lz < 0 || lx >= bp.sx || ly >= bp.sy || lz >= bp.sz) { changed = true; continue; }
            int i = bp.index(lx, ly, lz), c = bp.cells[i], k = check(mc, v.p, c, r.x, r.y, r.z, pos);
            if (k == 2) { nr.add(r); continue; }
            changed = true; v.redAt.clear(i);
            if (k == 1 && ng.size() < MAX_GHOSTS) { ng.add(new Ghost(r.x, r.y, r.z, v.p.want[c], lx, ly, lz)); v.at.set(i); added = true; }
        }
        int cx = (int) Math.floor(mc.player.getX()) - v.ox, cy = (int) Math.floor(mc.player.getY()) - v.oy, cz = (int) Math.floor(mc.player.getZ()) - v.oz;
        for (int y = Math.max(0, cy - 6); y <= Math.min(bp.sy - 1, cy + 7); y++)
            for (int z = Math.max(0, cz - 6); z <= Math.min(bp.sz - 1, cz + 6); z++)
                for (int x = Math.max(0, cx - 6); x <= Math.min(bp.sx - 1, cx + 6); x++) {
                    int i = bp.index(x, y, z), c = bp.cells[i];
                    if (c == 0 || v.at.get(i) || v.redAt.get(i) || !v.in(s, c, x, y, z)) continue;
                    int k = check(mc, v.p, c, v.ox + x, v.oy + y, v.oz + z, pos);
                    if (k == 1 && ng.size() < MAX_GHOSTS) { ng.add(new Ghost(v.ox + x, v.oy + y, v.oz + z, v.p.want[c], x, y, z)); v.at.set(i); changed = added = true; }
                    else if (k == 2 && nr.size() < MAX_RED) { nr.add(new Red(v.ox + x, v.oy + y, v.oz + z)); v.redAt.set(i); changed = true; }
                }
        if (!changed) return;
        ghosts = ng; reds = nr;
        faces();
        if (added) sorted = false;
    }

    /** Faces pressed against another solid ghost block can't be seen, so they aren't drawn. */
    private static void faces() {
        Scan v = view;
        if (v == null) return;
        Blueprint bp = v.bp;
        Session s = BuildBlueprintClient.SESSION;
        for (Ghost g : ghosts) {
            int m = 0;
            if (v.p.full[bp.cells[bp.index(g.lx, g.ly, g.lz)]])
                for (int i = 0; i < 6; i++) {
                    Direction d = DIRS[i];
                    int nx = g.lx + d.getStepX(), ny = g.ly + d.getStepY(), nz = g.lz + d.getStepZ();
                    if (nx < 0 || ny < 0 || nz < 0 || nx >= bp.sx || ny >= bp.sy || nz >= bp.sz || !v.shown(s, ny)) continue;
                    int n = bp.index(nx, ny, nz);
                    if (v.at.get(n) && v.p.full[bp.cells[n]]) m |= 1 << i;
                }
            g.hide = m;
        }
    }

    /** Far ones first, so see-through blocks blend the right way. Sorts plain numbers, which is quick even for 60,000 blocks. */
    private static void sortGhosts(double cx, double cy, double cz) {
        int n = ghosts.size();
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) {
            long q = (long) Math.min(d2(ghosts.get(i), cx, cy, cz) * 4, (double) ((1L << 45) - 1));
            keys[i] = (((1L << 45) - 1 - q) << 17) | i;
        }
        java.util.Arrays.sort(keys);
        List<Ghost> out = new ArrayList<>(n);
        for (long k : keys) out.add(ghosts.get((int) (k & 0x1FFFF)));
        ghosts = out;
    }

    // Ghosts behind the camera aren't drawn. Worked out from where the player looks (the same in every Minecraft
    // version); in the front view (F5 twice) the camera looks back at you, and when the camera is far from the
    // player (spectating, free camera mods) nothing is skipped.
    private static double camX, camY, camZ, lookX, lookY, lookZ;
    private static boolean cull;
    private static void aim(Minecraft mc, double cx, double cy, double cz) {
        camX = cx; camY = cy; camZ = cz; cull = false;
        if (mc.player == null) return;
        Vec3 v = mc.player.getViewVector(1f);
        double ex = cx - mc.player.getX(), ey = cy - mc.player.getEyeY(), ez = cz - mc.player.getZ();
        if (ex * ex + ey * ey + ez * ez > 36) return;
        double s = ex * v.x + ey * v.y + ez * v.z > 0.5 ? -1 : 1;
        lookX = v.x * s; lookY = v.y * s; lookZ = v.z * s; cull = true;
    }

    /** The item for a ghost block (for the next block helper). */
    private static String itemOf(Ghost g) {
        Scan v = view;
        if (v == null) return null;
        return v.p.item[v.bp.cells[v.bp.index(g.lx, g.ly, g.lz)]];
    }

    /** Next block helper: lowest unfinished layer first, then the block you're holding, then the closest. */
    private static void pickHint(Minecraft mc) {
        if (Mistakes.aim(mc)) return;   // showing mistakes: the guide points at the one to fix
        if (!Settings.nextHint || ghosts.isEmpty() || mc.player == null) { Hint.clear(); return; }
        int low = Integer.MAX_VALUE;
        for (Ghost g : ghosts) low = Math.min(low, g.ly);
        String held = V.itemId(mc.player.getMainHandItem().getItem());
        double px = mc.player.getX(), py = mc.player.getEyeY() - 1, pz = mc.player.getZ();
        Ghost best = null; double bd = 0; boolean bestHeld = false;
        for (Ghost g : ghosts) {
            if (g.ly != low) continue;
            String it = itemOf(g);
            boolean h = it != null && it.equals(held);
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
            out = V.parseState(st);
        } catch (Exception e) {
            try {   // unknown property: fall back to the plain block
                out = V.block(MaterialMap.blockId(st)).defaultBlockState();
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
            Draw.quads(st, out);
            return out;
        });
    }

    private static void drawGhosts(PoseStack ps, VertexConsumer vc) {
        Minecraft mc = Minecraft.getInstance();
        long perfD = System.nanoTime();
        try {
        Blueprint bp = BuildBlueprintClient.SESSION.blueprint();
        if (bp == null) return;
        float alpha = Settings.ghostAlpha / 100f;
        for (Ghost g : ghosts) {
            if (!g.ready) {
                g.ready = true;
                g.q = quads(g.state);
                if (empty(g.q)) {   // chests, signs, beds...: no normal model, so draw a look-alike block at the real block's size
                    Object[] sb = standin(g.state);
                    g.q = sb == null ? null : quads((BlockState) sb[0]);
                    g.box = sb == null ? null : (float[]) sb[1];
                }
            }
            List<BakedQuad>[] q = g.q;
            float[] box = g.box;
            if (q == null) continue;
            if ((g.hide & 63) == 63 && q[6].isEmpty()) continue;   // covered by other ghosts on every side
            if (cull && (g.x + 0.5 - camX) * lookX + (g.y + 0.5 - camY) * lookY + (g.z + 0.5 - camZ) * lookZ < -2) continue;   // behind the camera
            ps.pushPose();
            ps.translate(g.x, g.y, g.z);
            if (box != null) { ps.translate(box[0], box[1], box[2]); ps.scale(box[3], box[4], box[5]); }
            PoseStack.Pose p = ps.last();
            for (int i = 0; i < 7; i++) {
                if (i < 6 && (g.hide >> i & 1) != 0) continue;   // skip faces pressed against another ghost
                for (BakedQuad quad : q[i]) {
                    int rgb = 0xFFFFFF;
                    int tint = Draw.tintIndex(quad);
                    if (tint >= 0) {
                        try { rgb = g.tintI == tint ? g.tintC : (g.tintC = mc.getBlockColors().getColor(g.state, mc.level, new BlockPos(g.x, g.y, g.z), g.tintI = tint) & 0xFFFFFF); }
                        catch (Exception ignored) {}
                    }
                    Draw.quad(vc, p, quad, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha, 0xF000F0, OverlayTexture.NO_OVERLAY);
                }
            }
            ps.popPose();
        }
        } finally { Perf.draw(System.nanoTime() - perfD); }
    }

    private static final Map<BlockState, Object[]> STANDIN = new HashMap<>();
    private static boolean empty(List<BakedQuad>[] q) { for (List<BakedQuad> l : q) if (!l.isEmpty()) return false; return true; }
    /** For blocks the game draws in a special way (chests, signs, beds, banners, heads...): a plain look-alike block and the real block's size. */
    private static Object[] standin(BlockState st) {
        if (STANDIN.containsKey(st)) return STANDIN.get(st);
        Object[] out = null;
        for (String id : dev.buildblueprint.core.Standin.of(V.blockId(st.getBlock()))) {
            BlockState b = parse(id);
            if (b == null || empty(quads(b))) continue;
            float[] box = {0.0625f, 0f, 0.0625f, 0.875f, 0.875f, 0.875f};
            try {
                net.minecraft.world.phys.shapes.VoxelShape sh = st.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (!sh.isEmpty()) {
                    net.minecraft.world.phys.AABB a = sh.bounds();
                    box = new float[]{(float) a.minX, (float) a.minY, (float) a.minZ, (float) (a.maxX - a.minX), (float) (a.maxY - a.minY), (float) (a.maxZ - a.minZ)};
                }
            } catch (Throwable ignored) {}
            out = new Object[]{b, box};
            break;
        }
        STANDIN.put(st, out);
        return out;
    }

    private static final Map<BlockState, Boolean> FULL = new HashMap<>();
    /** a full, solid, non-see-through cube (stairs, slabs, glass, leaves... are not) */
    private static boolean full(BlockState st) { return FULL.computeIfAbsent(st, b -> V.solid(b) && b.canOcclude()); }

}
