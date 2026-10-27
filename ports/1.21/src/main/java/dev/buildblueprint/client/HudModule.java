package dev.buildblueprint.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The corner panel while a blueprint is loaded: build name, overall progress, current layer progress,
 * and the blocks still needed (for the current layer or the whole build, see Settings). Position, size and
 * contents are set in the Build Blueprint settings menu.
 */
public final class HudModule {
    private HudModule() {}

    private static final int W = 168;

    public static void init() {
        V.onHud(g -> Safe.run("the corner list", () -> {
            Minecraft mc = Minecraft.getInstance();
            var s = BuildBlueprintClient.SESSION;
            if (mc.player != null && Copier.active && Mc.screen() == null) { copyPanel(g, mc); return; }
            if (mc.player != null && Mc.screen() == null && Stats.completeAt > 0 && System.currentTimeMillis() - Stats.completeAt < 9000) doneCard(g, mc);
            if (!Settings.hudShow || !s.loaded() || !s.visible || mc.player == null) return;
            if (Mc.screen() instanceof MaterialsScreen || Mc.screen() instanceof SavedBuildsScreen || Mc.screen() instanceof SettingsScreen) return;
            float sc = Settings.hudScale / 100f;
            int sw = (int) (g.guiWidth() / sc);
            V.hudPush(g, sc);
            int hx = Settings.hudRight ? sw - W - 4 : 4;
            int bottom = draw(g, mc, hx, 4);
            if (Settings.layerMap) layerMap(g, mc, hx, bottom + 8);
            V.hudPop(g);
        }));
    }

    /** While copying a build: box size, block count and the keys. */
    private static void copyPanel(GuiGraphics g, Minecraft mc) {
        var font = mc.font;
        int x = 6, y = 6, w = 200;
        String[][] keys = {{"Arrows", "grow the box that way"}, {"Alt + arrows", "shrink it"}, {"Ctrl + Up/Down", "top / bottom"},
                {"Page Down", "stretch to where you look"}, {"Shift", "5 blocks at a time"}, {"Insert", "save it"}, {"Delete", "cancel"}};
        int h = 34 + keys.length * 12;
        UiKit.panel(g, x - 3, y - 3, w + 6, h + 3, UiKit.CYAN);
        g.drawString(font, Tr.t("Copying a build"), x, y, UiKit.CYAN, true);
        int[] s = Copier.size();
        g.drawString(font, Tr.t(s[0] + " x " + s[1] + " x " + s[2] + " box"), x, y + 12, UiKit.WHITE, true);
        int c = Copier.count();
        String cnt = c == -1 ? "counting..." : c == -2 ? "big box" : c + " blocks";
        g.drawString(font, Tr.t(cnt), x + w - font.width(Tr.t(cnt)), y + 12, 0xFFAAFFAA, true);
        g.drawString(font, Tr.t("Make the box as big as your build"), x, y + 22, UiKit.MUTED, false);
        for (int i = 0; i < keys.length; i++) UiKit.key(g, font, x, y + 36 + i * 12, keys[i][0], keys[i][1]);
    }

    private static void bar(GuiGraphics g, Minecraft mc, int x, int y, int w, int done, int total, int col, String label) {
        var font = mc.font;
        total = Math.max(1, total);
        g.fill(x - 1, y - 1, x + w + 1, y + 10, 0xFF000000);
        g.fill(x, y, x + w, y + 9, 0xFF2B2B2B);
        int f = (int) Math.round(w * (done / (double) total));
        g.fill(x, y, x + f, y + 9, col);
        if (f > 0) g.fill(x, y, x + f, y + 2, 0x50FFFFFF);
        String t = label + "  " + (int) Math.floor(100.0 * done / total) + "%";
        g.drawString(font, Tr.t(t), x + w / 2 - font.width(Tr.t(t)) / 2, y + 1, 0xFFFFFFFF, true);
    }

    /** Draws the corner list; returns where it ends. */
    private static int draw(GuiGraphics g, Minecraft mc, int x, int y) {
        var s = BuildBlueprintClient.SESSION;
        var font = mc.font;
        boolean layerMode = Materials.showLayer();
        List<Materials.Row> rows = layerMode ? Materials.layerRows() : Materials.rows();
        int shown = 0; for (Materials.Row r : rows) if (!r.done()) shown++;
        int lines = Math.min(Settings.hudLines, shown);
        boolean complete = Materials.placed() >= Materials.totalBlocks() && Materials.totalBlocks() > 0;

        boolean stats = Settings.hudStats && Stats.started();
        boolean hint = Settings.nextHint && Hint.pos != null && !complete;
        boolean notPlaced = !s.locked;
        boolean coop = Coop.on();
        int h = 14 + (coop ? 11 : 0) + (notPlaced ? 32 : 0) + (Settings.hudBar ? 26 : 0) + (stats ? 11 : 0) + (hint ? 22 : 0) + 12 + lines * 18 + (shown > lines ? 10 : 0) + (shown == 0 ? 10 : 0) + (Settings.hudHints ? 11 : 0);
        // panel: dark glass with a 1px light edge, like vanilla tooltips
        g.fill(x - 3, y - 3, x + W + 3, y + h, 0xD0100A18);
        g.fill(x - 3, y - 3, x + W + 3, y - 2, 0xFF5A3FA0);
        g.fill(x - 3, y + h - 1, x + W + 3, y + h, 0xFF2A1A60);

        String name = s.blueprint().name == null ? "Blueprint" : s.blueprint().name;
        String tag = s.locked ? "placed" : "";
        int nroom = W - 4 - (tag.isEmpty() ? 0 : font.width(Tr.t(tag)) + 6);
        if (font.width(Tr.t(name)) > nroom) name = font.plainSubstrByWidth(name, nroom - 6) + "..";
        g.drawString(font, Tr.t(name), x, y, 0xFFFFFF55, true);
        if (!tag.isEmpty()) g.drawString(font, Tr.t(tag), x + W - font.width(Tr.t(tag)), y, 0xFF55FF55, true);
        int ly = y + 12;
        if (coop) { g.drawString(font, Tr.t("Co-op: " + Coop.where(s.blueprint())), x, ly, UiKit.CYAN, false); ly += 11; }
        if (notPlaced) {   // still positioning: remind how to move and place it
            g.fill(x - 1, ly - 1, x + W + 1, ly + 30, 0x30FF9020);
            g.fill(x - 1, ly - 1, x + 1, ly + 30, 0xFFFFB050);
            g.drawString(font, Tr.t("Not placed yet"), x + 4, ly, 0xFFFFB050, true);
            g.drawString(font, Tr.t("Arrows move it, PgDn turns it"), x + 4, ly + 10, 0xFFCCCCCC, false);
            g.drawString(font, Tr.t("Shift = 5 blocks · Delete = place"), x + 4, ly + 20, 0xFFCCCCCC, false);
            ly += 32;
        }
        if (Settings.hudBar) {
            bar(g, mc, x, ly, W, Materials.placed(), Materials.totalBlocks(), 0xFF3FAF3F, Materials.placed() + "/" + Materials.totalBlocks());
            ly += 13;
            int L = Materials.layer(), top = s.blueprint().sy;
            bar(g, mc, x, ly, W, Materials.layerPlaced(), Materials.layerTotal(), 0xFF3F7FCF, "Layer " + (L + 1) + "/" + top);
            ly += 13;
        }
        if (stats) {
            String a = "Time " + Stats.time(Stats.activeMs()), b = Math.round(Stats.perMinute()) + " blocks/min";
            g.drawString(font, Tr.t(a), x, ly, 0xFFBBBBBB, true);
            g.drawString(font, Tr.t(b), x + W - font.width(Tr.t(b)), ly, 0xFFBBBBBB, true);
            ly += 11;
        }
        if (hint) {
            // "Next" card: icon, block name and where it is from you
            g.fill(x - 1, ly - 1, x + W + 1, ly + 19, Hint.inHand ? 0x3040FF40 : 0x30FFE040);
            g.fill(x - 1, ly - 1, x + 1, ly + 19, Hint.inHand ? 0xFF60FF60 : 0xFFFFE040);
            if (!Hint.icon.isEmpty()) g.renderItem(Hint.icon, x + 3, ly + 1);
            String n = "Next: " + Hint.name;
            if (font.width(Tr.t(n)) > W - 26) n = font.plainSubstrByWidth(n, W - 32) + "..";
            g.drawString(font, Tr.t(n), x + 22, ly, 0xFFFFFFFF, true);
            String w = Hint.where(mc.player) + (Hint.inHand ? "  (in hand)" : "");
            if (font.width(Tr.t(w)) > W - 26) w = font.plainSubstrByWidth(w, W - 32) + "..";
            g.drawString(font, Tr.t(w), x + 22, ly + 10, 0xFFCCCCCC, false);
            ly += 22;
        }
        if (complete) {
            g.drawString(font, Tr.t("Build complete!"), x, ly + 1, 0xFF55FF55, true); ly += 12;
            g.drawString(font, Tr.t("PgUp > Remove to clear it"), x, ly, 0xFF999999, false); ly += 10;
        }
        else {
            String head = layerMode ? "Layer " + (Materials.layer() + 1) + " needs:" : "Whole build needs:";
            g.drawString(font, Tr.t(head), x, ly + 1, 0xFFAAAAFF, true);
            String hv = "have/need";
            g.drawString(font, Tr.t(hv), x + W - font.width(Tr.t(hv)), ly + 1, 0xFF888888, false);
            ly += 12;
            int drawn = 0;
            for (Materials.Row r : rows) {
                if (r.done()) continue;
                if (drawn++ >= lines) break;
                if (drawn % 2 == 0) g.fill(x - 1, ly - 1, x + W + 1, ly + 17, 0x18FFFFFF);
                if (!r.icon().isEmpty()) g.renderItem(r.icon(), x, ly);
                String n = r.name();
                String count = r.have() + "/" + r.left();
                int room = W - 22 - font.width(Tr.t(count)) - 6;
                if (font.width(Tr.t(n)) > room) n = font.plainSubstrByWidth(n, room - 6) + "..";
                g.drawString(font, Tr.t(n), x + 20, ly + 5, 0xFFE0E0E0, true);
                g.drawString(font, Tr.t(count), x + W - font.width(Tr.t(count)), ly + 5, r.enough() ? 0xFF55FF55 : 0xFFFF6060, true);
                ly += 18;
            }
            if (shown > lines) { g.drawString(font, Tr.t("+" + (shown - lines) + " more  (Page Up)"), x, ly, 0xFF999999, true); ly += 10; }
            if (shown == 0) { g.drawString(font, Tr.t("Layer done!"), x, ly, 0xFF55FF55, true); ly += 10; }
        }
        if (Settings.hudHints) g.drawString(font, Tr.t("Home/End layer · PgUp menu"), x, ly + 1, 0xFF777777, false);
        return y + h;
    }

    /**
     * Layer map (Settings > Extras): the layer you're on, seen from above. Block colours = still to place,
     * dark green = done, red = wrong block, white dot = you. Big builds show the part around you.
     */
    private static void layerMap(GuiGraphics g, Minecraft mc, int x, int y) {
        int[] cells = Materials.layerCells();
        int W0 = Materials.mapW(), D0 = Materials.mapD();
        var s = BuildBlueprintClient.SESSION;
        if (cells.length == 0 || W0 == 0 || D0 == 0 || mc.player == null || !s.loaded()) return;
        var bp = s.blueprint();
        int max = 56;
        int pxl = (int) Math.floor(mc.player.getX()) - s.ox, pzl = (int) Math.floor(mc.player.getZ()) - s.oz;
        int w = Math.min(W0, max), d = Math.min(D0, max);
        int x0 = Math.max(0, Math.min(W0 - w, pxl - w / 2)), z0 = Math.max(0, Math.min(D0 - d, pzl - d / 2));
        int cell = Math.max(1, Math.min(6, (W - 4) / Math.max(w, d)));
        int mw = w * cell, mh = d * cell, mx = x + (W - mw) / 2, my = y + 12;
        g.fill(x - 3, y - 3, x + W + 3, my + mh + 4, 0xD0100A18);
        g.fill(x - 3, y - 3, x + W + 3, y - 2, 0xFF5A3FA0);
        g.drawString(mc.font, "Layer " + (Materials.mapLayer() + 1) + " from above", x, y, 0xFFAAAAFF, true);
        g.fill(mx - 1, my - 1, mx + mw + 1, my + mh + 1, 0xFF000000);
        for (int zz = 0; zz < d; zz++)
            for (int xx = 0; xx < w; xx++) {
                int v = cells[(z0 + zz) * W0 + x0 + xx];
                if (v < 0) continue;
                int st = v >>> 24, pal = v & 0xFFFFFF, col;
                if (st == 2) col = 0xFF2F6B35;
                else if (st == 3) col = 0xFFFF3030;
                else col = 0xFF000000 | (pal < bp.palette.length ? Swapper.colorOf(dev.buildblueprint.core.MaterialMap.blockId(bp.palette[pal])) : 0x808080);
                g.fill(mx + xx * cell, my + zz * cell, mx + xx * cell + cell, my + zz * cell + cell, col);
            }
        int ux = pxl - x0, uz = pzl - z0;
        if (ux >= 0 && uz >= 0 && ux < w && uz < d) {   // you: a white dot with a tick showing where you look
            int cx = mx + ux * cell + cell / 2, cz = my + uz * cell + cell / 2;
            g.fill(cx - 2, cz - 2, cx + 2, cz + 2, 0xFFFFFFFF);
            double yaw = Math.toRadians(PlayerInfo.yaw());
            int fx = (int) Math.round(-Math.sin(yaw) * 5), fz = (int) Math.round(Math.cos(yaw) * 5);
            g.fill(cx + fx - 1, cz + fz - 1, cx + fx + 1, cz + fz + 1, 0xFFFFFF55);
        }
    }

    /** "Build complete!" card in the top middle for a few seconds when the last block is placed. */
    private static void doneCard(GuiGraphics g, Minecraft mc) {
        var font = mc.font;
        String a = "Build complete!";
        String b = Stats.completePerMin > 0 ? Stats.completeBlocks + " blocks  ·  " + Stats.time(Stats.completeMs) + "  ·  " + Stats.completePerMin + " a minute"
                : Stats.completeBlocks + " blocks  ·  " + Stats.time(Stats.completeMs);
        String c = "Page Up > Remove to clear it";
        int w = Math.max(font.width(Tr.t(b)), font.width(Tr.t(c))) + 24, x = g.guiWidth() / 2 - w / 2, y = 26;
        UiKit.panel(g, x, y, w, 44, UiKit.GREEN);
        UiKit.center(g, font, a, x + w / 2, y + 6, UiKit.GREEN);
        UiKit.center(g, font, b, x + w / 2, y + 18, UiKit.WHITE);
        UiKit.center(g, font, c, x + w / 2, y + 30, UiKit.MUTED);
    }
}
