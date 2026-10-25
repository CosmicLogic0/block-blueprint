package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.BlueprintCodec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Saved builds (Page Up when nothing is loaded): paste builds from the app, keep them on this computer,
 * look at them in a turnable 3D preview (or top / front view), rename, delete and load them into the world.
 */
public class SavedBuildsScreen extends BBScreen {
    private List<SavedBuilds.Entry> builds;
    private int sel = 0, scroll = 0;
    private int view = 0;          // 0 = 3D, 1 = top, 2 = front
    private int turn = 0;          // quarter turns of the 3D view
    private float zoom = 1f;       // multiplier on the auto-fit size
    private int panX = 0, panY = 0;
    private String msg = "", err = "";
    private EditBox nameBox;
    private Button saveName, viewBtn;
    private boolean renaming = false;

    public SavedBuildsScreen() { super(Component.literal("Saved builds")); }

    @Override public boolean isPauseScreen() { return false; }

    private SavedBuilds.Entry cur() { return builds.isEmpty() ? null : builds.get(Math.max(0, Math.min(sel, builds.size() - 1))); }

    // ---- layout
    private int M() { return 10; }
    private int listW() { return Math.min(170, width / 4); }
    private int T() { return 30; }
    private int B() { return height - 30; }                    // bottom of panels
    private int rowH() { return 18; }
    private int rows() { return Math.max(1, (B() - T() - 52) / rowH()); }
    private int pvX() { return M() + listW() + 8; }
    private int pvY() { return T() + 22; }
    private int pvW() { return width - pvX() - M(); }
    private int pvH() { return B() - pvY() - 24; }

    @Override
    protected void init() {
        builds = SavedBuilds.list();
        if (pick != null) { for (int i = 0; i < builds.size(); i++) if (builds.get(i).file().equals(pick)) sel = i; pick = null; }
        int lx = M(), lw = listW();
        addRenderableWidget(Button.builder(Component.literal("+ Paste build"), b -> paste()).bounds(lx, B() - 44, lw, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Paste a build code (BB1:...) or the app's Export lines from your clipboard. Ctrl+V works too."))).build());
        addRenderableWidget(Button.builder(Component.literal("Copy from world"), b -> { onClose(); Copier.start(); }).bounds(lx, B() - 22, lw, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Save a build that's already in your world: look at one corner, press Insert, look at the opposite corner, press Insert."))).build());
        addRenderableWidget(Button.builder(Component.literal("Materials"), b -> MenuModule.openMaterials()).bounds(lx, B() + 4, lw / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> MenuModule.openSettings(this)).bounds(lx + lw / 2 + 2, B() + 4, lw / 2 - 2, 20).build());
        // preview toolbar
        int x = pvX(), y = T();
        viewBtn = addRenderableWidget(Button.builder(Component.literal(viewName()), b -> { view = (view + 1) % 3; panX = panY = 0; b.setMessage(Component.literal(viewName())); }).bounds(x, y, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("⟲"), b -> turn = (turn + 3) % 4).bounds(x + 74, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("⟳"), b -> turn = (turn + 1) % 4).bounds(x + 96, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> zoom = Math.max(0.25f, zoom / 1.25f)).bounds(x + 122, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoom = Math.min(8f, zoom * 1.25f)).bounds(x + 144, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fit"), b -> { zoom = 1f; panX = panY = 0; }).bounds(x + 166, y, 30, 20).build());
        // actions under the preview
        int ay = B() + 4;
        addRenderableWidget(Button.builder(Component.literal("Load into world"), b -> loadSel()).bounds(x, ay, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> startRename()).bounds(x + 114, ay, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteSel()).bounds(x + 188, ay, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Copy code"), b -> copyCode()).bounds(x + 252, ay, 70, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Puts this build's BB1: code on your clipboard, to import in the Block Blueprint app or send to a friend."))).build());
        nameBox = new EditBox(font, x + 2, B() - 20, Math.min(220, pvW() - 80), 16, Component.literal("Name"));
        nameBox.setMaxLength(40); nameBox.visible = false;
        addRenderableWidget(nameBox);
        saveName = addRenderableWidget(Button.builder(Component.literal("Save"), b -> finishRename()).bounds(x + Math.min(224, pvW() - 76), B() - 22, 60, 20).build());
        saveName.visible = false;
    }
    private int ticks = 0;
    /** Builds sent from the desktop app show up without reopening the menu. */
    @Override public void tick() {
        if (++ticks % 40 != 0 || renaming) return;
        List<SavedBuilds.Entry> now = SavedBuilds.list();
        if (now.size() != builds.size() || (!now.isEmpty() && !now.get(0).file().equals(builds.get(0).file()))) {
            boolean added = now.size() > builds.size();
            builds = now;
            if (added) { sel = 0; scroll = 0; resetView(); msg = "New build arrived: \"" + builds.get(0).name() + "\""; err = ""; }
            else sel = Math.min(sel, Math.max(0, builds.size() - 1));
        }
    }
    private String viewName() { return view == 0 ? "View: 3D" : view == 1 ? "View: Top" : "View: Front"; }

    // ---- actions
    private void paste() {
        err = ""; msg = "";
        try {
            String data = SavedBuilds.parsePasted(Minecraft.getInstance().keyboardHandler.getClipboard());
            for (int i = 0; i < builds.size(); i++) if (builds.get(i).data().equals(data)) { sel = i; msg = "You already saved this build."; return; }
            Blueprint bp = BlueprintCodec.decode(data);
            SavedBuilds.save(bp.name == null || bp.name.trim().isEmpty() ? "My build" : bp.name, data);
            builds = SavedBuilds.list(); sel = 0; scroll = 0; resetView();
            msg = "Saved \"" + builds.get(0).name() + "\" on this computer.";
        } catch (Exception e) { err = e.getMessage() == null ? "Could not read the clipboard." : e.getMessage(); }
    }
    private void copyCode() {
        SavedBuilds.Entry e = cur(); if (e == null) return;
        try {
            Blueprint bp = SavedBuilds.blueprint(e);
            Blueprint named = new Blueprint(e.name(), bp.sx, bp.sy, bp.sz, bp.palette, bp.cells);
            Minecraft.getInstance().keyboardHandler.setClipboard(Copier.appCode(named));
            msg = "Code for \"" + e.name() + "\" copied - paste it in the app (Templates > Import code)."; err = "";
        } catch (Exception ex) { err = ex.getMessage(); }
    }
    /** Select a build by file name (used after copying one from the world). */
    public SavedBuildsScreen select(String file) { this.pick = file; return this; }
    private String pick;
    private void resetView() { zoom = 1f; panX = panY = 0; }
    private void loadSel() {
        SavedBuilds.Entry e = cur(); if (e == null || !PlayerInfo.inWorld()) return;
        try {
            Blueprint bp = SavedBuilds.blueprint(e);
            BuildBlueprintClient.SESSION.load(bp, PlayerInfo.x(), PlayerInfo.y(), PlayerInfo.z(), PlayerInfo.yaw());
            Materials.refresh();
            Chat.ok("Loaded \"" + e.name() + "\" in front of you, fully built. Arrows move it (Shift = 5 blocks), Page Down turns it, Delete places it.");
            onClose();
        } catch (Exception ex) { err = ex.getMessage(); }
    }
    private void startRename() {
        SavedBuilds.Entry e = cur(); if (e == null) return;
        renaming = true; nameBox.visible = true; saveName.visible = true; nameBox.setValue(e.name()); setFocused(nameBox);
    }
    private void finishRename() {
        SavedBuilds.Entry e = cur(); if (e == null || !renaming) return;
        String n = nameBox.getValue().trim(); if (n.isEmpty()) return;
        try { SavedBuilds.rename(e, n); builds = SavedBuilds.list(); msg = "Renamed to \"" + n + "\"."; err = ""; } catch (Exception ex) { err = ex.getMessage(); }
        renaming = false; nameBox.visible = false; saveName.visible = false;
    }
    private void deleteSel() {
        SavedBuilds.Entry e = cur(); if (e == null) return;
        try { SavedBuilds.delete(e); builds = SavedBuilds.list(); sel = Math.max(0, sel - 1); resetView(); msg = "Deleted \"" + e.name() + "\"."; err = ""; } catch (Exception ex) { err = ex.getMessage(); }
    }

    // ---- input
    @Override public boolean keyPressed(KeyEvent e) {
        if (renaming) { if (e.key() == 257 || e.key() == 335) { finishRename(); return true; } if (e.key() == 256) { renaming = false; nameBox.visible = false; saveName.visible = false; return true; } return super.keyPressed(e); }
        if (Modules.menuKey != null && V.keyMatches(Modules.menuKey, e.key(), e.scancode())) { onClose(); return true; }
        if (e.hasControlDown() && e.key() == 86) { paste(); return true; }   // Ctrl+V
        if (e.key() == 265) { sel = Math.max(0, sel - 1); resetView(); return true; }   // up
        if (e.key() == 264) { sel = Math.min(builds.size() - 1, sel + 1); resetView(); return true; } // down
        return super.keyPressed(e);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) return true;
        double mx = e.x(), my = e.y();
        if (mx >= M() && mx < M() + listW() && my >= T() + 14 && my < T() + 14 + rows() * rowH()) {
            int i = scroll + (int) ((my - T() - 14) / rowH());
            if (i < builds.size()) { if (i != sel) resetView(); sel = i; if (dbl) loadSel(); }
            return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (e.x() >= pvX() && e.y() >= pvY() && e.x() < pvX() + pvW() && e.y() < pvY() + pvH()) { panX += (int) dx; panY += (int) dy; return true; }
        return super.mouseDragged(e, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x < pvX()) scroll = Math.max(0, Math.min(Math.max(0, builds.size() - rows()), scroll - (int) Math.signum(dy)));
        else zoom = Math.max(0.25f, Math.min(8f, dy > 0 ? zoom * 1.15f : zoom / 1.15f));
        return true;
    }

    // ---- preview
    private static final Map<String, Integer> COLOR = new HashMap<>();
    private static int colorOf(String state) {
        return COLOR.computeIfAbsent(state, st -> {
            try {
                BlockState bs = V.parseState(st);
                if (bs.isAir()) return 0;
                int col = V.mapColor(bs);
                if (col == 0) return st.contains("glass") ? 0xFFA8D8F0 : 0xFF9A9A9A; // glass, panes, small decor
                return col | 0xFF000000;
            } catch (Exception e) { return 0xFF808080; }
        });
    }
    private static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f)), g = Math.min(255, (int) (((c >> 8) & 255) * f)), b = Math.min(255, (int) ((c & 255) * f));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private Blueprint cachedBp; private int cachedTurn = -1; private int[] grid; private int W, H, D;
    /** colours in view space: x across, y up, d = depth (0 = nearest) */
    private void prepare(Blueprint bp) {
        if (bp == cachedBp && turn == cachedTurn) return;
        cachedBp = bp; cachedTurn = turn;
        boolean swap = turn % 2 == 1;
        W = swap ? bp.sz : bp.sx; D = swap ? bp.sx : bp.sz; H = bp.sy;
        grid = new int[W * H * D];
        for (int y = 0; y < H; y++) for (int z = 0; z < bp.sz; z++) for (int x = 0; x < bp.sx; x++) {
            String st = bp.stateAt(x, y, z); if (st == null) continue;
            int c = colorOf(st); if (c == 0) continue;
            int vx, vd;
            switch (turn) { case 1 -> { vx = z; vd = bp.sx - 1 - x; } case 2 -> { vx = bp.sx - 1 - x; vd = bp.sz - 1 - z; } case 3 -> { vx = bp.sz - 1 - z; vd = x; } default -> { vx = x; vd = bp.sz - 1 - z; } }
            grid[(y * D + vd) * W + vx] = c;
        }
    }
    private int at(int x, int y, int d) { return x < 0 || y < 0 || d < 0 || x >= W || y >= H || d >= D ? 0 : grid[(y * D + d) * W + x]; }

    private void drawPreview(GuiGraphics g, Blueprint bp) {
        prepare(bp);
        int px = pvX(), py = pvY(), pw = pvW(), ph = pvH();
        g.enableScissor(px, py, px + pw, py + ph);
        if (view == 0) {
            float fit = Math.min(pw / (W + D * 0.5f + 1), ph / (H + D * 0.5f + 1));
            int s = Math.max(2, Math.round(fit * zoom)); int hs = Math.max(1, s / 2);
            int totW = W * s + D * hs, totH = H * s + D * hs;
            int ox = px + (pw - totW) / 2 + panX, oy = py + (ph - totH) / 2 + panY + totH; // oy = screen y of ground at nearest row
            for (int d = D - 1; d >= 0; d--) for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int c = at(x, y, d); if (c == 0) continue;
                boolean front = at(x, y, d - 1) == 0, top = at(x, y + 1, d) == 0, right = at(x + 1, y, d) == 0;
                if (!front && !top && !right) continue;
                int X = ox + x * s + d * hs, Y = oy - y * s - d * hs;
                if (X > px + pw + s || X + s + hs < px || Y < py - s || Y - s - hs > py + ph) continue;
                if (front) g.fill(X, Y - s, X + s, Y, shade(c, 0.86f));
                if (top) for (int k = 0; k < hs; k++) g.fill(X + k + 1, Y - s - k - 1, X + s + k + 1, Y - s - k, c);
                if (right) for (int k = 0; k < hs; k++) g.fill(X + s + k, Y - s - k, X + s + k + 1, Y - k, shade(c, 0.66f));
            }
        } else {
            boolean topView = view == 1;
            int cw = W, chh = topView ? D : H;
            float fit = Math.min(pw / (float) (cw + 1), ph / (float) (chh + 1));
            int s = Math.max(1, Math.round(fit * zoom));
            int ox = px + (pw - cw * s) / 2 + panX, oy = py + (ph - chh * s) / 2 + panY;
            for (int a = 0; a < cw; a++) for (int b = 0; b < chh; b++) {
                int c = 0, depth = 0;
                if (topView) { int d = D - 1 - b; for (int y = H - 1; y >= 0; y--) if ((c = at(a, y, d)) != 0) { depth = y; break; } }
                else { int y = H - 1 - b; for (int d = 0; d < D; d++) if ((c = at(a, y, d)) != 0) { depth = D - 1 - d; break; } }
                if (c == 0) continue;
                float f = 0.55f + 0.45f * (topView ? depth / (float) Math.max(1, H - 1) : depth / (float) Math.max(1, D - 1));
                g.fill(ox + a * s, oy + b * s, ox + a * s + s, oy + b * s + s, shade(c, f));
            }
        }
        g.disableScissor();
    }

    @Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { Safe.run("the saved builds menu", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int lx = M(), lw = listW(), t = T();
        g.drawString(font, "Saved builds", lx, 10, 0xFFFFFF55, true);
        String hint = "Paste a build code, or copy one from your world (Insert key)";
        g.drawString(font, hint, pvX() + 204 > width - font.width(hint) - M() ? width - font.width(hint) - M() : pvX() + 204, 10, 0xFF999999, false);
        // list panel
        g.fill(lx - 2, t - 2, lx + lw + 2, B() - 48, 0xE0101018);
        g.drawString(font, builds.size() + " saved", lx + 2, t + 2, 0xFF888888, false);
        for (int i = 0; i < rows(); i++) {
            int idx = scroll + i, y = t + 14 + i * rowH();
            if (idx >= builds.size()) break;
            boolean hov = mouseX >= lx && mouseX < lx + lw && mouseY >= y && mouseY < y + rowH();
            if (idx == sel) g.fill(lx, y, lx + lw, y + rowH() - 1, 0xFF3A4A8A);
            else if (hov) g.fill(lx, y, lx + lw, y + rowH() - 1, 0x40FFFFFF);
            String n = builds.get(idx).name();
            if (font.width(n) > lw - 10) n = font.plainSubstrByWidth(n, lw - 16) + "..";
            g.drawString(font, n, lx + 5, y + 5, idx == sel ? 0xFFFFFFFF : 0xFFCCCCCC, true);
        }
        if (builds.isEmpty()) {
            String[] help = { "No saved builds yet.", "", "From the app: copy a", "build code, then press", "+ Paste build.", "", "From your world: press", "Copy from world." };
            for (int i = 0; i < help.length; i++) g.drawString(font, help[i], lx + 5, t + 18 + i * 11, 0xFFAAAAAA, false);
        }
        // preview panel
        g.fill(pvX() - 2, pvY() - 2, pvX() + pvW() + 2, pvY() + pvH() + 2, 0xFF000000);
        g.fill(pvX(), pvY(), pvX() + pvW(), pvY() + pvH(), 0xFF1E4F8F);
        for (int gx = pvX(); gx < pvX() + pvW(); gx += 16) g.fill(gx, pvY(), gx + 1, pvY() + pvH(), 0x18FFFFFF);
        for (int gy = pvY(); gy < pvY() + pvH(); gy += 16) g.fill(pvX(), gy, pvX() + pvW(), gy + 1, 0x18FFFFFF);
        SavedBuilds.Entry e = cur();
        if (e != null) {
            try {
                Blueprint bp = SavedBuilds.blueprint(e);
                drawPreview(g, bp);
                if (!renaming) {
                    g.drawString(font, e.name(), pvX() + 2, B() - 18, 0xFFFFFFFF, true);
                    String info = bp.sx + " x " + bp.sy + " x " + bp.sz + " · " + bp.blockCount() + " blocks · " + bp.sy + " layers";
                    g.drawString(font, info, pvX() + pvW() - font.width(info), B() - 18, 0xFFAAAAAA, true);
                }
                g.drawString(font, "drag to move · scroll to zoom", pvX() + 4, pvY() + pvH() - 11, 0x99FFFFFF, false);
            } catch (Exception ex) { g.drawString(font, "Can't read this build: " + ex.getMessage(), pvX() + 6, pvY() + 6, 0xFFFF5555, true); }
        } else g.drawString(font, "Paste a build to see it here", pvX() + pvW() / 2 - 70, pvY() + pvH() / 2, 0xFFDDDDDD, true);

        super.extractRenderState(g, mouseX, mouseY, delta);
        String line = !err.isEmpty() ? err : msg;
        if (!line.isEmpty()) g.drawString(font, line, width / 2 - font.width(line) / 2, height - 10 - 0, !err.isEmpty() ? 0xFFFF5555 : 0xFF55FF55, true);
    }
}
