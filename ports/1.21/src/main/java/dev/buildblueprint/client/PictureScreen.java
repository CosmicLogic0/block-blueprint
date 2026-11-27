package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.BlueprintCodec;
import dev.buildblueprint.core.PictureBuild;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Build from a picture: drop a PNG / JPG on the window (or paste a link to one), pick standing up or lying flat, the
 * size and the blocks, see it, and save it as a build. Each pixel becomes the block with the closest color.
 */
public class PictureScreen extends BBScreen {
    private final Screen parent;
    private PictureBuild.Picture pic;
    private String picName = "My picture";
    private static boolean floor = false, everyBlock = false;
    private static int size = 64;
    private static final int[] SIZES = {16, 24, 32, 48, 64, 96, 128, 160, 192, 256};
    private int bw, bh, kinds, count;
    private int[] spots;
    private List<PictureBuild.Block> pal;
    private EditBox name;
    private Button shapeBtn, blocksBtn, sizeBtn, saveBtn;
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
        shapeBtn = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> { floor = !floor; labels(); })
                .bounds(x, y + 34, w, 20).tooltip(Tooltip.create(Component.literal(Tr.t("Standing up: a wall facing you. Lying flat: on the ground, like map art.")))).build());
        addRenderableWidget(Button.builder(Component.literal(Tr.t("-")), b -> step(-1)).bounds(x, y + 58, 20, 20).build());
        sizeBtn = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> step(1)).bounds(x + 22, y + 58, w - 44, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("How many blocks across the long side of the picture is (up to 256).")))).build());
        addRenderableWidget(Button.builder(Component.literal(Tr.t("+")), b -> step(1)).bounds(x + w - 20, y + 58, 20, 20).build());
        blocksBtn = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> { everyBlock = !everyBlock; remake(); })
                .bounds(x, y + 82, w, 20).tooltip(Tooltip.create(Component.literal(Tr.t("Wool, concrete and terracotta are easy to get and come in many colors. Every full block gives the closest colors, but uses rarer blocks.")))).build());
        saveBtn = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Save build", w - 8)), b -> save()).bounds(x, y + 112, w, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Saves it in Saved builds. Load it into the world from there.")))).build());
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Back", w - 8)), b -> onClose()).bounds(x, height - 30, w, 20).build());
        remake();
    }

    private void step(int d) {
        int i = 0;
        while (i < SIZES.length - 1 && SIZES[i] < size) i++;
        size = SIZES[Math.max(0, Math.min(SIZES.length - 1, i + d))];
        remake();
    }

    private void labels() {
        if (shapeBtn == null) return;
        shapeBtn.setMessage(Component.literal(UiKit.fit(font, floor ? "Lying flat (floor)" : "Standing up (wall)", cw() - 8)));
        sizeBtn.setMessage(Component.literal(UiKit.fit(font, bw > 0 ? bw + " x " + bh + " blocks" : size + " blocks", cw() - 52)));
        blocksBtn.setMessage(Component.literal(UiKit.fit(font, everyBlock ? "Blocks: every full block" : "Blocks: wool, concrete...", cw() - 8)));
        saveBtn.active = spots != null && count > 0 && !busy;
    }

    /** The blocks for the picture again (after a change of size or blocks). */
    private void remake() {
        spots = null; bw = bh = kinds = count = 0;
        if (pic != null) {
            pal = PictureBuild.palette(everyBlock, id -> !Materials.stackFor(id).isEmpty());   // only blocks this game version has
            if (pal.isEmpty()) { err = "No blocks to use."; labels(); return; }
            int[] s = PictureBuild.fit(pic, size);
            bw = s[0]; bh = s[1];
            spots = PictureBuild.match(pic, bw, bh, pal);
            boolean[] used = new boolean[pal.size()];
            for (int v : spots) if (v >= 0) { count++; if (!used[v]) { used[v] = true; kinds++; } }
        }
        labels();
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

    private void save() {
        if (spots == null || count == 0) return;
        try {
            String n = name.getValue().trim().isEmpty() ? picName : name.getValue().trim();
            Blueprint bp = PictureBuild.build(n, spots, bw, bh, pal, floor);
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
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { Safe.run("the picture screen", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = px(), w = pw();
        UiKit.panel(g, x - 6, 4, w + 12, height - 8, UiKit.CYAN);
        g.drawString(font, Tr.t("Build from a picture"), x, 12, UiKit.GOLD, true);
        g.drawString(font, Tr.t("Name:"), cx(), 30, 0xFFAAAAAA, false);
        // the preview
        int vx = x, vy = 30, vw = w - cw() - 10, vh = height - 70;
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF15151C);
        if (spots == null) {
            String[] lines = pic == null
                    ? new String[]{"Drop a picture (PNG or JPG)", "on this window,", "", "or copy a link to a picture", "and press Ctrl+V."}
                    : new String[]{"Couldn't make blocks from it."};
            int ly = vy + vh / 2 - lines.length * 6;
            for (String l : lines) { UiKit.center(g, font, l, vx + vw / 2, ly, 0xFFDDDDDD); ly += 12; }
        } else {
            float cell = Math.min((vw - 8) / (float) bw, (vh - 8) / (float) bh);
            int ox = vx + (int) ((vw - cell * bw) / 2), oy = vy + (int) ((vh - cell * bh) / 2);
            int step = Math.max(1, (int) Math.ceil(1f / Math.max(cell, 0.0001f)));   // more blocks than pixels: skip some
            for (int by = 0; by < bh; by += step)
                for (int bx = 0; bx < bw; bx += step) {
                    int v = spots[by * bw + bx];
                    if (v < 0) continue;
                    int x0 = ox + (int) (bx * cell), y0 = oy + (int) (by * cell);
                    int x1 = Math.max(x0 + 1, ox + (int) ((bx + step) * cell)), y1 = Math.max(y0 + 1, oy + (int) ((by + step) * cell));
                    g.fill(x0, y0, x1, y1, 0xFF000000 | pal.get(v).rgb);
                }
        }
        String info = spots == null ? "" : count + " blocks, " + kinds + " kinds";
        g.drawString(font, Tr.t(info), cx(), 30 + 138, 0xFFDDDDDD, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
        String line = !err.isEmpty() ? err : msg;
        if (!line.isEmpty()) g.drawString(font, Tr.t(UiKit.fit(font, line, w - cw() - 14)), x, height - 34, err.isEmpty() ? UiKit.GREEN : 0xFFFF6060, true);
    }
}
