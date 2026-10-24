package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.BlueprintCodec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Copy a build that already exists in your world.
 *   Insert (looking at your build) -> the mod finds the whole build and puts a box around it.
 *   Arrow keys push a side of the box out, Alt + arrows pull it in, Ctrl + Up/Down for the top/bottom, Shift = 5.
 *   Insert again -> name it and save.  Delete -> cancel.
 * Ground (dirt, grass, natural stone under the build) and trees can be left out automatically.
 */
public final class Copier {
    private Copier() {}

    public static final int MAX_SIDE = 256, MAX_VOLUME = 4_000_000;
    /** The selection box, inclusive: c1 = lowest corner, c2 = highest corner. */
    public static BlockPos c1, c2;
    public static boolean active;
    public static boolean skipGround = true, skipTrees = true;
    private static int countTick, lastCount = -1;

    public static int count() { return lastCount; }

    /** The block you're looking at, or the block at your feet when you aren't looking at one. */
    public static BlockPos target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof BlockHitResult bh && mc.hitResult.getType() == HitResult.Type.BLOCK) return bh.getBlockPos();
        return new BlockPos((int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()));
    }

    public static void start() {
        if (!PlayerInfo.inWorld()) return;
        Minecraft mc = Minecraft.getInstance();
        BlockPos t = target();
        int[] box = smartBox(mc, t);
        if (box == null) { box = new int[]{t.getX(), t.getY(), t.getZ(), t.getX(), t.getY(), t.getZ()}; }
        c1 = new BlockPos(box[0], box[1], box[2]); c2 = new BlockPos(box[3], box[4], box[5]);
        active = true; lastCount = -1; countTick = 0;
        int[] s = size();
        Chat.ok("Copying: found a " + s[0] + " x " + s[1] + " x " + s[2] + " build. Arrows grow the box, Alt + arrows shrink it, Ctrl + Up/Down for top/bottom.");
        Chat.say("Press Insert again to save it, Delete to cancel.");
    }

    public static void cancel() { active = false; c1 = null; c2 = null; Chat.bar("Copy cancelled"); }

    /** Insert key. */
    public static void corner() {
        if (!PlayerInfo.inWorld()) return;
        if (!active || c1 == null) { start(); return; }
        if (!openScreen()) Chat.say("Type /buildblueprint copy save <name> to save it.");
    }

    /** Grow (or shrink) the box on the side the player points to. dir = forward/back/left/right/up/down. */
    public static void nudge(String dir, int n, boolean shrink, float yaw) {
        if (!active || c1 == null) return;
        int[] f = dev.buildblueprint.core.Session.forward(yaw);
        int dx = 0, dy = 0, dz = 0;
        switch (dir) {
            case "forward" -> { dx = f[0]; dz = f[1]; }
            case "back" -> { dx = -f[0]; dz = -f[1]; }
            case "left" -> { dx = f[1]; dz = -f[0]; }
            case "right" -> { dx = -f[1]; dz = f[0]; }
            case "up" -> dy = 1;
            case "down" -> dy = -1;
            default -> { return; }
        }
        int x0 = c1.getX(), y0 = c1.getY(), z0 = c1.getZ(), x1 = c2.getX(), y1 = c2.getY(), z1 = c2.getZ();
        int m = shrink ? -n : n;
        if (dx > 0) x1 += m; else if (dx < 0) x0 -= m;
        if (dy > 0) y1 += m; else if (dy < 0) y0 -= m;
        if (dz > 0) z1 += m; else if (dz < 0) z0 -= m;
        if (x1 < x0 || y1 < y0 || z1 < z0) { Chat.bar("The box can't get any smaller that way"); return; }
        if (x1 - x0 >= MAX_SIDE || y1 - y0 >= MAX_SIDE || z1 - z0 >= MAX_SIDE) { Chat.bar("That's as big as a copy can be (" + MAX_SIDE + " per side)"); return; }
        c1 = new BlockPos(x0, y0, z0); c2 = new BlockPos(x1, y1, z1); lastCount = -1; countTick = 0;
        int[] s = size();
        Chat.bar((shrink ? "Shrunk " : "Grew ") + dir + "  ·  box " + s[0] + " x " + s[1] + " x " + s[2] + "  ·  Insert = save, Delete = cancel");
    }

    /** Called every tick from the keys module: keeps the block count in the corner up to date. */
    public static void tick() {
        if (!active || c1 == null || ++countTick % 20 != 1) return;
        int[] s = size();
        if ((long) s[0] * s[1] * s[2] > 1_500_000) { lastCount = -2; return; }   // too big to count every second
        Blueprint bp = capture("x");
        lastCount = bp == null ? 0 : bp.blockCount();
    }

    private static boolean openScreen() {
        try { Class.forName("dev.buildblueprint.client.MenuModule").getMethod("openCopy").invoke(null); return true; }
        catch (ReflectiveOperationException | LinkageError e) { return false; }
    }

    public static int[] size() {
        if (c1 == null || c2 == null) return new int[]{0, 0, 0};
        return new int[]{c2.getX() - c1.getX() + 1, c2.getY() - c1.getY() + 1, c2.getZ() - c1.getZ() + 1};
    }

    // ---- what counts as ground / trees -------------------------------------------------------------

    private static final Set<String> GROUND = new HashSet<>(Arrays.asList(
            "dirt", "grass_block", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "dirt_path", "farmland", "mud", "clay", "gravel",
            "sand", "red_sand", "stone", "deepslate", "tuff", "granite", "diorite", "andesite", "calcite", "dripstone_block", "netherrack",
            "end_stone", "bedrock", "snow", "snow_block", "powder_snow", "ice", "packed_ice", "soul_sand", "soul_soil", "basalt", "moss_block",
            "suspicious_sand", "suspicious_gravel", "terracotta", "red_terracotta", "orange_terracotta", "yellow_terracotta", "white_terracotta",
            "brown_terracotta", "light_gray_terracotta", "smooth_basalt", "magma_block", "obsidian_ground_placeholder"));
    private static final Set<String> PLANTS = new HashSet<>(Arrays.asList(
            "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "vine", "seagrass", "tall_seagrass", "kelp", "kelp_plant", "sweet_berry_bush",
            "sugar_cane", "bamboo", "cactus", "glow_lichen", "hanging_roots", "moss_carpet", "pink_petals", "leaf_litter", "bush", "firefly_bush",
            "short_dry_grass", "tall_dry_grass", "pointed_dripstone", "big_dripleaf", "big_dripleaf_stem", "small_dripleaf", "spore_blossom", "cave_vines", "cave_vines_plant"));

    private static String path(BlockState st) { return V.blockId(st.getBlock()).replaceFirst("^[^:]*:", ""); }
    static boolean isGround(BlockState st) { String p = path(st); return GROUND.contains(p) || p.endsWith("_ore") || st.getBlock() instanceof LiquidBlock; }
    private static final Set<String> SOIL = new HashSet<>(Arrays.asList("dirt", "grass_block", "coarse_dirt", "rooted_dirt", "podzol", "mycelium",
            "dirt_path", "farmland", "mud", "clay", "gravel", "sand", "red_sand", "snow", "snow_block", "powder_snow", "moss_block", "suspicious_sand", "suspicious_gravel", "soul_soil"));
    static boolean isSoil(BlockState st) { return SOIL.contains(path(st)) || path(st).endsWith("_ore"); }
    static boolean isPlant(BlockState st) {
        String p = path(st);
        return PLANTS.contains(p) || p.endsWith("_sapling") || st.is(BlockTags.LEAVES) || st.is(BlockTags.FLOWERS) || p.endsWith("_mushroom");
    }
    static boolean isLog(BlockState st) { return st.is(BlockTags.LOGS) && !path(st).startsWith("stripped_"); }

    /**
     * Smart select: starting from the block you look at, follow every connected block that isn't ground, plants or
     * air (diagonals count, so roofs and hulls stay together) and return the box around it.
     */
    static int[] smartBox(Minecraft mc, BlockPos start) {
        if (mc.level == null) return null;
        BlockState s0 = mc.level.getBlockState(start);
        if (s0.isAir() || isPlant(s0) || s0.getBlock() instanceof LiquidBlock) return null;
        // ground level under the build: go down through the build (and any water) until natural ground
        int groundY = start.getY() - 200;
        BlockPos.MutableBlockPos g = new BlockPos.MutableBlockPos(start.getX(), start.getY(), start.getZ());
        // first soil (dirt, grass, sand...) below; or, failing that, natural stone below an air/water gap
        boolean gap = false; int stoneY = Integer.MIN_VALUE;
        for (int y = start.getY() - 1; y > start.getY() - 200 && y >= V.minY(mc.level); y--) {
            BlockState st = mc.level.getBlockState(g.setY(y));
            if (st.isAir() || st.getBlock() instanceof LiquidBlock) { gap = true; continue; }
            if (isSoil(st)) { groundY = y; break; }
            if (gap && isGround(st) && stoneY == Integer.MIN_VALUE) stoneY = y;
        }
        if (groundY == start.getY() - 200 && stoneY != Integer.MIN_VALUE) groundY = stoneY;
        if (start.getY() <= groundY) return null;
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(start); seen.add(start.asLong());
        int x0 = start.getX(), y0 = start.getY(), z0 = start.getZ(), x1 = x0, y1 = y0, z1 = z0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (!q.isEmpty() && seen.size() < 400_000) {
            BlockPos c = q.poll();
            x0 = Math.min(x0, c.getX()); y0 = Math.min(y0, c.getY()); z0 = Math.min(z0, c.getZ());
            x1 = Math.max(x1, c.getX()); y1 = Math.max(y1, c.getY()); z1 = Math.max(z1, c.getZ());
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                if (Math.abs(p.getX() - start.getX()) > MAX_SIDE / 2 || Math.abs(p.getZ() - start.getZ()) > MAX_SIDE / 2 || Math.abs(p.getY() - start.getY()) > MAX_SIDE / 2) continue;
                long k = p.asLong();
                if (seen.contains(k) || !mc.level.isLoaded(p)) continue;
                BlockState st = mc.level.getBlockState(p);
                if (st.isAir() || isPlant(st) || st.getBlock() instanceof LiquidBlock || p.getY() <= groundY) continue;
                if (isSoil(st)) continue;   // dirt, grass, sand... never part of the build's outline
                seen.add(k);
                q.add(p.immutable());
            }
        }
        return new int[]{x0, y0, z0, x1, y1, z1};
    }

    // ---- reading the box ---------------------------------------------------------------------------

    /** Reads the box out of the world, leaving out ground / trees when those options are on. Null when nothing is left. */
    public static Blueprint capture(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (c1 == null || c2 == null || mc.level == null) return null;
        int x0 = c1.getX(), y0 = c1.getY(), z0 = c1.getZ();
        int sx = c2.getX() - x0 + 1, sy = c2.getY() - y0 + 1, sz = c2.getZ() - z0 + 1;
        BlockState[] grid = new BlockState[sx * sy * sz];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) {
            BlockState st = mc.level.getBlockState(pos.set(x0 + x, y0 + y, z0 + z));
            if (st.isAir() || st.getBlock() instanceof LiquidBlock) continue;
            grid[(y * sz + z) * sx + x] = st;
        }
        int groundTop = skipGround ? removeGround(grid, sx, sy, sz) : -1;
        if (skipTrees) removeTrees(grid, sx, sy, sz);
        if (skipGround || skipTrees) removeCrumbs(grid, sx, sy, sz, Math.max(0, groundTop) + 1);

        Map<String, Integer> pidx = new HashMap<>();
        List<String> pal = new ArrayList<>();
        int[] cells = new int[sx * sy * sz];
        int[] mn = {sx, sy, sz}, mx = {-1, -1, -1};
        for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) {
            BlockState st = grid[(y * sz + z) * sx + x];
            if (st == null) continue;
            String s = V.stateString(st);
            Integer i = pidx.get(s);
            if (i == null) { pal.add(s); i = pal.size(); pidx.put(s, i); }
            cells[(y * sz + z) * sx + x] = i;
            mn[0] = Math.min(mn[0], x); mn[1] = Math.min(mn[1], y); mn[2] = Math.min(mn[2], z);
            mx[0] = Math.max(mx[0], x); mx[1] = Math.max(mx[1], y); mx[2] = Math.max(mx[2], z);
        }
        if (mx[0] < 0) return null;
        int tx = mx[0] - mn[0] + 1, ty = mx[1] - mn[1] + 1, tz = mx[2] - mn[2] + 1;
        int[] out = new int[tx * ty * tz];
        for (int y = 0; y < ty; y++) for (int z = 0; z < tz; z++) for (int x = 0; x < tx; x++)
            out[(y * tz + z) * tx + x] = cells[((y + mn[1]) * sz + z + mn[2]) * sx + x + mn[0]];
        return new Blueprint(name, tx, ty, tz, pal.toArray(new String[0]), out);
    }

    /**
     * Ground = ground-type blocks joined to the bottom of the box that are at or below the usual ground height there.
     * Stone used higher up in the build itself (roofs, walls) is kept.
     */
    private static int removeGround(BlockState[] g, int sx, int sy, int sz) {
        // ground height: in each column, how high the natural blocks go from the bottom; take the most common height
        int[] hist = new int[sy + 1];
        for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) {
            int y = 0;
            while (y < sy) { BlockState b = g[(y * sz + z) * sx + x]; if (b == null || !isGround(b)) break; y++; }
            hist[y]++;
        }
        int level = 0; for (int y = 1; y <= sy; y++) if (hist[y] > hist[level]) level = y;
        final int top = level;   // blocks with y < top are ground height
        ArrayDeque<int[]> q = new ArrayDeque<>();
        boolean[] gone = new boolean[g.length];
        for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) { int i = z * sx + x; if (g[i] != null && isGround(g[i])) { gone[i] = true; q.add(new int[]{x, 0, z}); } }
        // plants standing on the ground go too (grass, flowers)
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
                if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) continue;
                int i = (y * sz + z) * sx + x;
                if (gone[i] || g[i] == null) continue;
                boolean plantOnTop = d[1] == 1 && isPlant(g[i]) && !g[i].is(BlockTags.LEAVES);
                boolean ground = isGround(g[i]) && (y < top + 1 || isSoil(g[i]));
                if (!ground && !plantOnTop) continue;
                gone[i] = true;
                if (!plantOnTop) q.add(new int[]{x, y, z});
            }
        }
        for (int i = 0; i < g.length; i++) if (gone[i]) g[i] = null;
        return top;
    }

    /** Little loose bits left on the ground (a stray stone, half a path): groups of fewer than 8 blocks near the bottom. */
    private static void removeCrumbs(BlockState[] g, int sx, int sy, int sz, int nearY) {
        boolean[] seen = new boolean[g.length];
        for (int i = 0; i < g.length; i++) {
            if (seen[i] || g[i] == null) continue;
            List<Integer> comp = new ArrayList<>();
            ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(i); seen[i] = true;
            int minY = Integer.MAX_VALUE;
            while (!q.isEmpty()) {
                int c = q.poll(); comp.add(c);
                int x = c % sx, z = (c / sx) % sz, y = c / (sx * sz);
                minY = Math.min(minY, y);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    int nx = x + dx, ny = y + dy, nz = z + dz;
                    if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue;
                    int j = (ny * sz + nz) * sx + nx;
                    if (!seen[j] && g[j] != null) { seen[j] = true; q.add(j); }
                }
            }
            if (comp.size() < 8 && minY <= nearY) for (int c : comp) g[c] = null;
        }
    }

    /** Trees = leaves and plants, plus log columns that only touch leaves, ground and air (house frame logs touch planks). */
    private static void removeTrees(BlockState[] g, int sx, int sy, int sz) {
        boolean[] gone = new boolean[g.length], seen = new boolean[g.length];
        for (int i = 0; i < g.length; i++) if (g[i] != null && isPlant(g[i])) gone[i] = true;
        int[][] D = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int i = 0; i < g.length; i++) {
            if (seen[i] || g[i] == null || !isLog(g[i])) continue;
            List<Integer> comp = new ArrayList<>();
            ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(i); seen[i] = true;
            boolean leaves = false, building = false;
            while (!q.isEmpty()) {
                int c = q.poll(); comp.add(c);
                int x = c % sx, z = (c / sx) % sz, y = c / (sx * sz);
                for (int[] d : D) {
                    int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                    if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue;
                    int j = (ny * sz + nz) * sx + nx;
                    BlockState n = g[j];
                    if (n == null) continue;
                    if (isLog(n)) { if (!seen[j]) { seen[j] = true; q.add(j); } }
                    else if (n.is(BlockTags.LEAVES)) leaves = true;
                    else if (!isGround(n) && !isPlant(n) && V.solid(n)) building = true;   // planks, glass, stone... (fences don't count)
                }
            }
            if (leaves && !building) for (int c : comp) gone[c] = true;
        }
        for (int i = 0; i < g.length; i++) if (gone[i]) g[i] = null;
    }

    /** Captures, saves to Saved builds, copies the app code. Returns the saved entry or null. */
    public static SavedBuilds.Entry save(String name) {
        if (name == null || name.isBlank()) name = "Copied build";
        name = name.trim();
        if (name.length() > 40) name = name.substring(0, 40);
        Blueprint bp = capture(name);
        if (bp == null) { Chat.err("There are no blocks inside that box."); return null; }
        try {
            SavedBuilds.Entry e = SavedBuilds.save(name, BlueprintCodec.encode(bp));
            Minecraft.getInstance().keyboardHandler.setClipboard(appCode(bp));
            Chat.ok("Saved \"" + name + "\" (" + bp.blockCount() + " blocks, " + bp.sx + " x " + bp.sy + " x " + bp.sz + ") to your Saved builds.");
            Chat.say("Its build code is on your clipboard - paste it in the Block Blueprint app (Templates > Import code) or send it to someone.");
            active = false; c1 = null; c2 = null;
            return e;
        } catch (Exception ex) {
            Chat.err("Couldn't save: " + ex.getMessage());
            return null;
        }
    }

    public static void saveNow(String name) {
        if (c1 == null || c2 == null) { Chat.warn("Start a copy first: look at your build and press Insert (or /buildblueprint copy)."); return; }
        save(name);
    }

    // ---- BB1: code (the app's share code) ----

    /** "BB1:" + base64url(raw-deflate(JSON)) in the app's block-blueprint format. */
    public static String appCode(Blueprint bp) {
        StringBuilder j = new StringBuilder(bp.blockCount() * 40 + 100);
        j.append("{\"format\":\"block-blueprint\",\"version\":1,\"mc\":\"26.3\",\"name\":").append(q(bp.name == null ? "Build" : bp.name))
         .append(",\"desc\":\"Copied from a Minecraft world.\",\"blocks\":[");
        String[][] split = new String[bp.palette.length][];
        for (int i = 0; i < bp.palette.length; i++) split[i] = nameAndLabel(bp.palette[i]);
        int ox = bp.sx / 2, oz = bp.sz / 2;
        boolean first = true;
        for (int y = 0; y < bp.sy; y++) for (int z = 0; z < bp.sz; z++) for (int x = 0; x < bp.sx; x++) {
            int c = bp.cells[bp.index(x, y, z)];
            if (c == 0) continue;
            if (!first) j.append(',');
            first = false;
            j.append('[').append(x - ox).append(',').append(y).append(',').append(z - oz).append(',')
             .append(q(split[c - 1][0])).append(',').append(q(split[c - 1][1])).append(",0]");
        }
        j.append("]}");
        byte[] raw = j.toString().getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(9, true);
        d.setInput(raw); d.finish();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) o.write(buf, 0, d.deflate(buf));
        d.end();
        return "BB1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(o.toByteArray());
    }

    /** "minecraft:oak_stairs[half=bottom,facing=north]" -> {"oak_stairs", "facing=north,half=bottom"} (props sorted, or "default"). */
    static String[] nameAndLabel(String state) {
        int b = state.indexOf('[');
        String id = b < 0 ? state : state.substring(0, b);
        if (id.startsWith("minecraft:")) id = id.substring(10);
        String label = "default";
        if (b >= 0 && state.endsWith("]") && state.length() > b + 2) {
            String[] kv = state.substring(b + 1, state.length() - 1).split(",");
            Arrays.sort(kv);
            label = String.join(",", kv);
        }
        return new String[]{id, label};
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') sb.append('\\').append(ch);
            else if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
            else sb.append(ch);
        }
        return sb.append('"').toString();
    }
}
