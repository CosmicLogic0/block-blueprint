package dev.buildblueprint.client;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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
        HudRenderCallback.EVENT.register((g, delta) -> Safe.run("the corner list", () -> {
            Minecraft mc = Minecraft.getInstance();
            var s = BuildBlueprintClient.SESSION;
            if (mc.player != null && Copier.active && Mc.screen() == null) { copyPanel(g, mc); return; }
            if (!Settings.hudShow || !s.loaded() || !s.visible || mc.player == null) return;
            if (Mc.screen() instanceof MaterialsScreen || Mc.screen() instanceof SavedBuildsScreen || Mc.screen() instanceof SettingsScreen) return;
            float sc = Settings.hudScale / 100f;
            int sw = (int) (g.guiWidth() / sc);
            V.hudPush(g, sc);
            draw(g, mc, Settings.hudRight ? sw - W - 4 : 4, 4);
            V.hudPop(g);
        }));
    }

    /** While copying a build: box size, block count and the keys. */
    private static void copyPanel(GuiGraphics g, Minecraft mc) {
        var font = mc.font;
        int x = 6, y = 6, w = 190;
        String[] keys = {"Arrows: grow the box that way", "Alt + arrows: shrink it", "Ctrl + Up / Down: top / bottom", "Shift: 5 blocks at a time",
                "Insert: save it  ·  Delete: cancel"};
        int h = 36 + keys.length * 10;
        g.fill(x - 3, y - 3, x + w + 3, y + h, 0xD0081820);
        g.fill(x - 3, y - 3, x + w + 3, y - 2, 0xFF20E0FF);
        g.drawString(font, "Copying a build", x, y, 0xFF20E0FF, true);
        int[] s = Copier.size();
        g.drawString(font, s[0] + " x " + s[1] + " x " + s[2] + " box", x, y + 12, 0xFFFFFFFF, true);
        int c = Copier.count();
        String cnt = c == -1 ? "counting..." : c == -2 ? "big box" : c + " blocks";
        g.drawString(font, cnt, x + w - font.width(cnt), y + 12, 0xFFAAFFAA, true);
        String opts = (Copier.skipGround ? "no ground" : "with ground") + " · " + (Copier.skipTrees ? "no trees" : "with trees");
        g.drawString(font, opts, x, y + 22, 0xFF888888, false);
        for (int i = 0; i < keys.length; i++) g.drawString(font, keys[i], x, y + 34 + i * 10, 0xFFCCCCCC, false);
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
        g.drawString(font, t, x + w / 2 - font.width(t) / 2, y + 1, 0xFFFFFFFF, true);
    }

    private static void draw(GuiGraphics g, Minecraft mc, int x, int y) {
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
        int h = 14 + (notPlaced ? 32 : 0) + (Settings.hudBar ? 26 : 0) + (stats ? 11 : 0) + (hint ? 22 : 0) + 12 + lines * 18 + (shown > lines ? 10 : 0) + (shown == 0 ? 10 : 0) + (Settings.hudHints ? 11 : 0);
        // panel: dark glass with a 1px light edge, like vanilla tooltips
        g.fill(x - 3, y - 3, x + W + 3, y + h, 0xD0100A18);
        g.fill(x - 3, y - 3, x + W + 3, y - 2, 0xFF5A3FA0);
        g.fill(x - 3, y + h - 1, x + W + 3, y + h, 0xFF2A1A60);

        String name = s.blueprint().name == null ? "Blueprint" : s.blueprint().name;
        String tag = s.locked ? "placed" : "";
        int nroom = W - 4 - (tag.isEmpty() ? 0 : font.width(tag) + 6);
        if (font.width(name) > nroom) name = font.plainSubstrByWidth(name, nroom - 6) + "..";
        g.drawString(font, name, x, y, 0xFFFFFF55, true);
        if (!tag.isEmpty()) g.drawString(font, tag, x + W - font.width(tag), y, 0xFF55FF55, true);
        int ly = y + 12;
        if (notPlaced) {   // still positioning: remind how to move and place it
            g.fill(x - 1, ly - 1, x + W + 1, ly + 30, 0x30FF9020);
            g.fill(x - 1, ly - 1, x + 1, ly + 30, 0xFFFFB050);
            g.drawString(font, "Not placed yet", x + 4, ly, 0xFFFFB050, true);
            g.drawString(font, "Arrows move it, PgDn turns it", x + 4, ly + 10, 0xFFCCCCCC, false);
            g.drawString(font, "Shift = 5 blocks · Delete = place", x + 4, ly + 20, 0xFFCCCCCC, false);
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
            g.drawString(font, a, x, ly, 0xFFBBBBBB, true);
            g.drawString(font, b, x + W - font.width(b), ly, 0xFFBBBBBB, true);
            ly += 11;
        }
        if (hint) {
            // "Next" card: icon, block name and where it is from you
            g.fill(x - 1, ly - 1, x + W + 1, ly + 19, Hint.inHand ? 0x3040FF40 : 0x30FFE040);
            g.fill(x - 1, ly - 1, x + 1, ly + 19, Hint.inHand ? 0xFF60FF60 : 0xFFFFE040);
            if (!Hint.icon.isEmpty()) g.renderItem(Hint.icon, x + 3, ly + 1);
            String n = "Next: " + Hint.name;
            if (font.width(n) > W - 26) n = font.plainSubstrByWidth(n, W - 32) + "..";
            g.drawString(font, n, x + 22, ly, 0xFFFFFFFF, true);
            String w = Hint.where(mc.player) + (Hint.inHand ? "  (in hand)" : "");
            if (font.width(w) > W - 26) w = font.plainSubstrByWidth(w, W - 32) + "..";
            g.drawString(font, w, x + 22, ly + 10, 0xFFCCCCCC, false);
            ly += 22;
        }
        if (complete) { g.drawString(font, "Build complete!", x, ly + 1, 0xFF55FF55, true); ly += 12; }
        else {
            String head = layerMode ? "Layer " + (Materials.layer() + 1) + " needs:" : "Whole build needs:";
            g.drawString(font, head, x, ly + 1, 0xFFAAAAFF, true);
            String hv = "have/need";
            g.drawString(font, hv, x + W - font.width(hv), ly + 1, 0xFF888888, false);
            ly += 12;
            int drawn = 0;
            for (Materials.Row r : rows) {
                if (r.done()) continue;
                if (drawn++ >= lines) break;
                if (drawn % 2 == 0) g.fill(x - 1, ly - 1, x + W + 1, ly + 17, 0x18FFFFFF);
                if (!r.icon().isEmpty()) g.renderItem(r.icon(), x, ly);
                String n = r.name();
                String count = r.have() + "/" + r.left();
                int room = W - 22 - font.width(count) - 6;
                if (font.width(n) > room) n = font.plainSubstrByWidth(n, room - 6) + "..";
                g.drawString(font, n, x + 20, ly + 5, 0xFFE0E0E0, true);
                g.drawString(font, count, x + W - font.width(count), ly + 5, r.enough() ? 0xFF55FF55 : 0xFFFF6060, true);
                ly += 18;
            }
            if (shown > lines) { g.drawString(font, "+" + (shown - lines) + " more  (Page Up)", x, ly, 0xFF999999, true); ly += 10; }
            if (shown == 0) { g.drawString(font, "Layer done!", x, ly, 0xFF55FF55, true); ly += 10; }
        }
        if (Settings.hudHints) g.drawString(font, "Home/End layer · PgUp menu", x, ly + 1, 0xFF777777, false);
    }
}
