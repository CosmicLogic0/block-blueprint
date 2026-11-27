package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.BlueprintCodec;
import dev.buildblueprint.core.MapArt;
import dev.buildblueprint.core.PictureBuild;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Build from a picture: drop a PNG / JPG on the window (or paste a link to one), pick standing up or lying flat, the
 * size and the blocks, see it, and save it as a build. Each pixel becomes the block with the closest color.
 * Map art: the picture made so a map of the spot shows it - 1x1 up to 4x4 maps, every zoom, flat or staircase, carpets
 * or every map color - and "Build here" puts it in the world lined up with the map grid.
 */
public class PictureScreen extends Screen {
    private final Screen parent;
    private PictureBuild.Picture pic;
    private String picName = "My picture";
    private static boolean everyBlock = false;
    private static int mode = 0;   // 0 standing up, 1 lying flat, 2 map art
    private static int size = 64;
    // map art
    private static final int[][] GRIDS = {{1, 1}, {2, 1}, {1, 2}, {2, 2}, {3, 2}, {2, 3}, {3, 3}, {4, 3}, {3, 4}, {4, 4}};
    private static int grid = 0, zoom = 0;
    private static boolean carpets = true, staircase = false;
    private MapArt.Plan plan;
    private List<MapArt.Block> mpal;
    private Blueprint mapBuild;   // made when the map art changes (for its size and the buttons)
    // the preview: prevW x prevH colors (-1 = empty)
    private int prevW, prevH;
    private int[] prev;
    private static final int[] SIZES = {16, 24, 32, 48, 64, 96, 128, 160, 192, 256};
    private int bw, bh, kinds, count;
    private int[] spots;
    private List<PictureBuild.Block> pal;
    private EditBox name;
    private Button shapeBtn, blocksBtn, sizeBtn, saveBtn, zoomBtn, shadeBtn, hereBtn;
    private String msg = "", err = "";
    private boolean busy;

    public PictureScreen(Screen parent) { super(Component.literal(Tr.t("Build from a picture"))); this.parent = parent; }

    /** Opens with a picture already (dropped or downloaded on the Saved builds screen). */
    public PictureScreen(Screen parent, String fileName, byte[] data) {
        this(parent);
        load(fileName, data);
    }

    @Override public boolean isPauseScreen() { return false; }

    /** How many blocks the picture makes (for the test). */
    int blocks() { return count; }
    /** The test: which mode the screen opens in (0 standing up, 2 map art). */
    static void testMode(int m) { mode = m; }
    /** The map art build's size (for the test). */
    String mapSize() { return mapBuild == null ? "none" : mapBuild.sx + "x" + mapBuild.sy + "x" + mapBuild.sz; }

    private int pw() { return Math.min(460, width - 16); }
    private int px() { return (width - pw()) / 2; }
    private int cw() { return 150; }                       // controls column
    private int cx() { return px() + pw() - cw(); }

    @Override protected void init() {
        int x = cx(), w = cw(), y = 30;
        String keep = name == null ? picName : name.getValue();
        name = new EditBox(font, x, y + 10, w, 18, Component.literal(Tr.t("Name")));
        name.setMaxLength(40);
        name.setValue(keep);
        addRenderableWidget(name);
        shapeBtn = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> { mode = (mode + 1) % 3; rebuild(); })
                .bounds(x, y + 34, w, 20).tooltip(Tooltip.create(Component.literal(Tr.t("Standing up: a wall facing you. Lying flat: on the ground. Map art: so a map of the spot shows the picture.")))).build());
        addRenderableWidget(Button.builder(Component.literal(Tr.t("-")), b -> step(-1)).bounds(x, y + 58, 20, 20).build());
        sizeBtn = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> step(1)).bounds(x + 22, y + 58, w - 44, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("How many blocks across the long side of the picture is (up to 256).")))).build());
        addRenderableWidget(Button.builder(Component.literal(Tr.t("+")), b -> step(1)).bounds(x + w - 20, y + 58, 20, 20).build());
        boolean map = mode == 2;
        blocksBtn = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> { if (mode == 2) carpets = !carpets; else everyBlock = !everyBlock; remake(); })
                .bounds(x, y + 82, w, 20).tooltip(Tooltip.create(Component.literal(Tr.t(map
                        ? "Carpets are cheap and quick (16 colors). Every map color uses all the blocks a map can show (about 60 colors), some of them rare."
                        : "Wool, concrete and terracotta are easy to get and come in many colors. Every full block gives the closest colors, but uses rarer blocks.")))).build());
        int sy = y + 112;
        if (map) {
            zoomBtn = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> { zoom = (zoom + 1) % 5; remake(); }).bounds(x, y + 106, w, 20)
                    .tooltip(Tooltip.create(Component.literal(Tr.t("The map's zoom level. Zoomed-out maps cover more ground: each map pixel is a bigger square of blocks.")))).build());
            shadeBtn = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> { staircase = !staircase; remake(); }).bounds(x, y + 130, w, 20)
                    .tooltip(Tooltip.create(Component.literal(Tr.t("Flat is easy to build. Staircase steps the blocks up and down so the map shows 3 shades of each color - nicer, but taller and harder to build.")))).build());
            sy = y + 160;
        }
        saveBtn = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Save build", w - 8)), b -> save()).bounds(x, sy, w, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Saves it in Saved builds. Load it into the world from there.")))).build());
        if (map) hereBtn = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Build here (map grid)", w - 8)), b -> buildHere()).bounds(x, sy + 24, w, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Saves it and puts it in the world where you stand, lined up with the map grid, so a map made here shows the whole picture.")))).build());
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Back", w - 8)), b -> onClose()).bounds(x, height - 30, w, 20).build());
        remake();
    }

    private void rebuild() { Mc.setScreen(this); }   // the buttons differ between the modes: lay the screen out again

    private void step(int d) {
        if (mode == 2) { grid = Math.max(0, Math.min(GRIDS.length - 1, grid + d)); remake(); return; }
        int i = 0;
        while (i < SIZES.length - 1 && SIZES[i] < size) i++;
        size = SIZES[Math.max(0, Math.min(SIZES.length - 1, i + d))];
        remake();
    }

    private void labels() {
        if (shapeBtn == null) return;
        shapeBtn.setMessage(Component.literal(UiKit.fit(font, mode == 1 ? "Lying flat (floor)" : mode == 2 ? "Map art" : "Standing up (wall)", cw() - 8)));
        if (mode == 2) {
            int[] gr = GRIDS[grid];
            sizeBtn.setMessage(Component.literal(UiKit.fit(font, "Maps: " + gr[0] + " x " + gr[1], cw() - 52)));
            blocksBtn.setMessage(Component.literal(UiKit.fit(font, carpets ? "Blocks: carpets" : "Blocks: every map color", cw() - 8)));
            if (zoomBtn != null) zoomBtn.setMessage(Component.literal(UiKit.fit(font, "Zoom " + zoom + " (" + MapArt.mapBlocks(zoom) + " blocks a map)", cw() - 8)));
            if (shadeBtn != null) shadeBtn.setMessage(Component.literal(UiKit.fit(font, staircase ? "Shading: staircase" : "Shading: flat", cw() - 8)));
            boolean ok = mapBuild != null && count > 0 && !busy;
            saveBtn.active = ok;
            if (hereBtn != null) hereBtn.active = ok && BuildBlueprintClient.SESSION != null && Minecraft.getInstance().player != null;
            return;
        }
        sizeBtn.setMessage(Component.literal(UiKit.fit(font, bw > 0 ? bw + " x " + bh + " blocks" : size + " blocks", cw() - 52)));
        blocksBtn.setMessage(Component.literal(UiKit.fit(font, everyBlock ? "Blocks: every full block" : "Blocks: wool, concrete...", cw() - 8)));
        saveBtn.active = spots != null && count > 0 && !busy;
    }

    /** The blocks for the picture again (after a change of size or blocks). */
    private void remake() {
        spots = null; bw = bh = kinds = count = 0; plan = null; mapBuild = null; prev = null; err = err.startsWith("Too") ? "" : err;
        if (pic != null && mode == 2) { remakeMap(); labels(); return; }
        if (pic != null) {
            pal = PictureBuild.palette(everyBlock, id -> !Materials.stackFor(id).isEmpty());   // only blocks this game version has
            if (pal.isEmpty()) { err = "No blocks to use."; labels(); return; }
            int[] s = PictureBuild.fit(pic, size);
            bw = s[0]; bh = s[1];
            spots = PictureBuild.match(pic, bw, bh, pal);
            boolean[] used = new boolean[pal.size()];
            for (int v : spots) if (v >= 0) { count++; if (!used[v]) { used[v] = true; kinds++; } }
            prevW = bw; prevH = bh; prev = new int[bw * bh];
            for (int i = 0; i < prev.length; i++) prev[i] = spots[i] < 0 ? -1 : pal.get(spots[i]).rgb;
        }
        labels();
    }

    /** The blocks a map shows best: carpets (16 colors), or one block for every map color the game has. */
    private static List<MapArt.Block> mapPalette(boolean carpets) {
        List<MapArt.Block> out = new java.util.ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (String c : colors) {
            String id = "minecraft:" + c + "_carpet";
            int rgb = BuildPicture.mapColor(id);
            if (rgb >= 0 && !Materials.stackFor(id).isEmpty() && seen.add(rgb)) out.add(new MapArt.Block(id, rgb, true));
        }
        if (!carpets)   // then every plain full block, one per map color (the easy ones first)
            for (PictureBuild.Block b : PictureBuild.blocks()) {
                int rgb = BuildPicture.mapColor(b.id);
                if (rgb >= 0 && !Materials.stackFor(b.id).isEmpty() && seen.add(rgb)) out.add(new MapArt.Block(b.id, rgb, false));
            }
        return out;
    }

    private void remakeMap() {
        int[] gr = GRIDS[grid];
        if (!MapArt.fits(gr[0], gr[1], zoom)) { err = "Too big: at most " + MapArt.MAX_SIDE + " blocks across. Pick fewer maps or a lower zoom."; return; }
        mpal = mapPalette(carpets);
        if (mpal.isEmpty()) { err = "No blocks to use."; return; }
        plan = MapArt.plan(pic, gr[0], gr[1], mpal, staircase);
        mapBuild = MapArt.build("map", plan, mpal, zoom, staircase, "minecraft:stone");
        boolean[] used = new boolean[mpal.size()];
        for (int v : plan.block) if (v >= 0) { count++; if (!used[v]) { used[v] = true; kinds++; } }
        count = mapBuild.blockCount();
        if (mapBuild.sy > 300) err = "Too tall for most worlds (" + mapBuild.sy + " blocks): try flat shading or a lower zoom.";
        prevW = plan.w; prevH = plan.h; prev = new int[plan.w * plan.h];
        for (int i = 0; i < prev.length; i++) prev[i] = plan.block[i] < 0 ? -1 : plan.shown[i];
    }

    private void load(String fileName, byte[] data) {
        try {
            pic = PictureBuild.read(data);
            String n = fileName == null ? "" : fileName.replaceAll("[?#].*$", "");
            n = n.substring(n.lastIndexOf('/') + 1);
            if (n.contains(".")) n = n.substring(0, n.lastIndexOf('.'));
            picName = n.trim().isEmpty() ? "My picture" : SavedBuilds.pretty(n);
            if (name != null) name.setValue(picName);
            msg = ""; err = "";
        } catch (Exception e) { err = e.getMessage() == null ? "Couldn't read that picture." : e.getMessage(); }
        remake();
    }

    private String nameNow() { return name.getValue().trim().isEmpty() ? picName : name.getValue().trim(); }

    /** The finished build, named. */
    private Blueprint made() {
        String n = nameNow();
        if (mode == 2) return mapBuild == null ? null : new Blueprint(n, mapBuild.sx, mapBuild.sy, mapBuild.sz, mapBuild.palette, mapBuild.cells);
        return spots == null ? null : PictureBuild.build(n, spots, bw, bh, pal, mode == 1);
    }

    /** Map art: save it and put it where the player stands, lined up with the map grid (placed, so it can't slide). */
    private void buildHere() {
        Blueprint bp = made();
        var p = Minecraft.getInstance().player;
        if (bp == null || p == null) return;
        try {
            SavedBuilds.save(bp.name, BlueprintCodec.encode(bp));
            int px = 1 << zoom, north = staircase ? px : 0;   // staircase art has one extra row north of the maps
            int ox = MapArt.gridStart((int) Math.floor(p.getX()), zoom), oz = MapArt.gridStart((int) Math.floor(p.getZ()), zoom) - north;
            int oy = (int) Math.floor(p.getY());   // on the ground you stand on
            BuildBlueprintClient.SESSION.restore(bp, ox, oy, oz, 0, true);
            Coop.off();
            Materials.refresh(); SessionStore.dirty();
            Chat.ok("Map art placed on the map grid: " + ox + ", " + oz + ". Make a map standing anywhere on it.");
            Mc.setScreen(null);
        } catch (Exception ex) { err = "Couldn't place it: " + ex.getMessage(); }
    }

    private void save() {
        if (count == 0) return;
        try {
            Blueprint bp = made();
            if (bp == null) return;
            String n = bp.name;
            SavedBuilds.Entry e = SavedBuilds.save(n, BlueprintCodec.encode(bp));
            Mc.setScreen(new SavedBuildsScreen().select(e.file()));
        } catch (Exception ex) { err = "Couldn't save it: " + ex.getMessage(); }
    }

    /** A picture dropped on the window. */
    // no @Override: Minecraft 1.15 has no file dropping (there it is simply never called)
    public void onFilesDrop(List<java.nio.file.Path> paths) {
        for (java.nio.file.Path p : paths) {
            if (!PictureBuild.isPictureFile(p.getFileName().toString())) continue;
            try {
                if (java.nio.file.Files.size(p) > 60_000_000L) { err = "That picture is too big."; return; }
                load(p.getFileName().toString(), java.nio.file.Files.readAllBytes(p));
            } catch (Exception e) { err = "Couldn't read that picture."; }
            return;
        }
        err = "Drop a picture: PNG or JPG.";
    }

    /** Ctrl+V with a link to a picture: download it (only when the player pastes it). */
    private void pasteLink() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null || !clip.trim().matches("(?i)https?://\\S+")) { err = "Copy a link to a picture first (or drop a picture on this window)."; return; }
        if (busy) return;
        String url = clip.trim();
        busy = true; msg = "Downloading the picture..."; err = "";
        Thread th = new Thread(() -> {
            byte[] data = null; String e2 = null;
            try { data = TemplatesScreen.fetchOne(url); } catch (Exception ex) { e2 = "Couldn't download that link" + (ex.getMessage() == null ? "." : " (" + ex.getMessage() + ")."); }
            final byte[] fd = data; final String fe = e2;
            Minecraft.getInstance().execute(() -> { busy = false; msg = ""; if (fd != null) load(url, fd); else err = fe; });
        }, "Build Blueprint picture download");
        th.setDaemon(true);
        th.start();
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) { onClose(); return true; }
        if (e.hasControlDown() && e.key() == 86 && (name == null || !name.isFocused())) { pasteLink(); return true; }   // Ctrl+V
        return super.keyPressed(e);
    }

    @Override public void onClose() { Mc.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { Safe.run("the picture screen", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = px(), w = pw();
        UiKit.panel(g, x - 6, 4, w + 12, height - 8, UiKit.CYAN);
        g.text(font, Tr.t("Build from a picture"), x, 12, UiKit.GOLD, true);
        g.text(font, Tr.t("Name:"), cx(), 30, 0xFFAAAAAA, false);
        // the preview
        int vx = x, vy = 30, vw = w - cw() - 10, vh = height - 70;
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF15151C);
        if (prev == null) {
            String[] lines = pic == null
                    ? new String[]{"Drop a picture (PNG or JPG)", "on this window,", "", "or copy a link to a picture", "and press Ctrl+V."}
                    : new String[]{"Couldn't make blocks from it."};
            int ly = vy + vh / 2 - lines.length * 6;
            for (String l : lines) { UiKit.center(g, font, l, vx + vw / 2, ly, 0xFFDDDDDD); ly += 12; }
        } else {
            float cell = Math.min((vw - 8) / (float) prevW, (vh - 8) / (float) prevH);
            int ox = vx + (int) ((vw - cell * prevW) / 2), oy = vy + (int) ((vh - cell * prevH) / 2);
            int step = Math.max(1, (int) Math.ceil(1f / Math.max(cell, 0.0001f)));   // more pixels than screen pixels: skip some
            for (int by = 0; by < prevH; by += step)
                for (int bx = 0; bx < prevW; bx += step) {
                    int v = prev[by * prevW + bx];
                    if (v < 0) continue;
                    int x0 = ox + (int) (bx * cell), y0 = oy + (int) (by * cell);
                    int x1 = Math.max(x0 + 1, ox + (int) ((bx + step) * cell)), y1 = Math.max(y0 + 1, oy + (int) ((by + step) * cell));
                    g.fill(x0, y0, x1, y1, 0xFF000000 | v);
                }
            if (mode == 2) {   // the map edges
                int[] gr = GRIDS[grid];
                for (int i = 1; i < gr[0]; i++) { int lx = ox + (int) (i * 128 * cell); g.fill(lx, oy, lx + 1, oy + (int) (prevH * cell), 0x80FFFFFF); }
                for (int i = 1; i < gr[1]; i++) { int ly = oy + (int) (i * 128 * cell); g.fill(ox, ly, ox + (int) (prevW * cell), ly + 1, 0x80FFFFFF); }
            }
        }
        String info = prev == null ? "" : mode == 2 && mapBuild != null
                ? mapBuild.sx + " x " + mapBuild.sz + " blocks, " + mapBuild.sy + " high"
                : count + " blocks, " + kinds + " kinds";
        g.text(font, Tr.t(UiKit.fit(font, info, cw())), cx(), mode == 2 ? 30 + 186 + 22 : 30 + 138, 0xFFDDDDDD, false);
        if (mode == 2 && mapBuild != null) g.text(font, Tr.t(UiKit.fit(font, count + " blocks, " + kinds + " colors", cw())), cx(), 30 + 186 + 33, 0xFFAAAAAA, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
        String line = !err.isEmpty() ? err : msg;
        if (!line.isEmpty()) g.text(font, Tr.t(UiKit.fit(font, line, w - cw() - 14)), x, height - 34, err.isEmpty() ? UiKit.GREEN : 0xFFFF6060, true);
    }
}
