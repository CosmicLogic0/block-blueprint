package dev.buildblueprint.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Shopping list (build menu > Shopping list): what you still have to get for the build, after counting what you carry
 * and what's in the chests you've opened nearby, in stacks. Copy it to paste in a chat or a note, or to print.
 */
public class ShoppingScreen extends Screen {
    private final Screen parent;
    private final boolean layer;
    private int scroll;
    private String msg = "";

    /** One line: the block, how many the build still needs, carried, in chests, still to get. */
    private record Line(Materials.Row row, int need, int have, int chest, int chests, int get) {}

    public ShoppingScreen(Screen parent, boolean layer) { super(Component.literal(Tr.t("Shopping list"))); this.parent = parent; this.layer = layer; }

    @Override public boolean isPauseScreen() { return false; }

    private int pw() { return Math.min(470, width - 16); }
    private int px() { return (width - pw()) / 2; }
    private int top() { return 46; }
    private int rowH() { return 18; }
    private int rowsFit() { return Math.max(1, (height - 40 - top() - 14) / rowH()); }

    private List<Line> lines() {
        List<Line> get = new ArrayList<>(), ready = new ArrayList<>();
        for (Materials.Row r : layer ? Materials.layerRows() : Materials.rows()) {
            if (r.left() <= 0) continue;
            int[] ch = Chests.nearby(r.id(), 48);
            int g = Math.max(0, r.left() - r.have() - ch[0]);
            (g > 0 ? get : ready).add(new Line(r, r.left(), r.have(), ch[0], ch[1], g));
        }
        get.sort((a, b) -> Integer.compare(b.get, a.get));
        get.addAll(ready);
        return get;
    }

    @Override protected void init() {
        int x = px(), w = pw(), by = height - 26, bw = (w - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Copy list", bw - 8)), b -> copy()).bounds(x, by, bw, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Copy the list as text, to paste in a chat or a note, or to print.")))).build());
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Back", (w - bw - 4) - 8)), b -> onClose()).bounds(x + bw + 4, by, w - bw - 4, 20).build());
    }

    static String stacks(int n) {
        int st = n / 64, rest = n % 64;
        if (st == 0) return String.valueOf(n);
        if (st == 1) return rest > 0 ? "1 stack + " + rest : "1 stack";
        return rest > 0 ? st + " stacks + " + rest : st + " stacks";
    }

    private void copy() {
        var s = BuildBlueprintClient.SESSION;
        StringBuilder sb = new StringBuilder();
        sb.append(Tr.t("Shopping list")).append(" - ").append(s.loaded() && s.blueprint().name != null ? s.blueprint().name : "Build")
                .append(" (").append(Tr.t(layer ? "layer " + (Materials.layer() + 1) : "whole build")).append(")\n");
        List<Line> ls = lines();
        boolean any = false;
        for (Line l : ls) if (l.get > 0) {
            if (!any) { sb.append(Tr.t("To get:")).append('\n'); any = true; }
            sb.append("- ").append(l.row.name()).append(": ").append(l.get).append(l.get >= 64 ? " (" + Tr.t(stacks(l.get)) + ")" : "").append('\n');
        }
        boolean head = false;
        for (Line l : ls) if (l.get == 0) {
            if (!head) { sb.append(Tr.t("Already got (carried or in chests):")).append('\n'); head = true; }
            sb.append("- ").append(l.row.name()).append(": ").append(l.need).append('\n');
        }
        if (ls.isEmpty()) sb.append(Tr.t("Nothing left to get!")).append('\n');
        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        msg = "Copied - paste it anywhere (Ctrl+V)";
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll = Math.max(0, Math.min(Math.max(0, lines().size() - rowsFit()), scroll - (int) Math.signum(dy) * 2));
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) { onClose(); return true; }
        return super.keyPressed(e);
    }
    @Override public void onClose() { Mc.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { Safe.run("the shopping list", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = px(), w = pw(), t = top(), rows = rowsFit();
        UiKit.panel(g, x - 6, 4, w + 12, height - 8, UiKit.BLUE);
        g.text(font, Tr.t("Shopping list"), x, 10, UiKit.GOLD, true);
        g.text(font, Tr.t(UiKit.fit(font, "What you still have to get, after what you carry and the chests you've opened nearby.", w)), x, 22, UiKit.MUTED, false);
        String what = layer ? "Layer " + (Materials.layer() + 1) : "Whole build";
        g.text(font, Tr.t(what), x, 34, UiKit.TEXT, false);
        int cNeed = x + w - 196, cHave = x + w - 146, cChest = x + w - 96, cGet = x + w - 40;
        g.text(font, Tr.t("need"), cNeed - font.width(Tr.t("need")) / 2, t, UiKit.MUTED, false);
        g.text(font, Tr.t("carried"), cHave - font.width(Tr.t("carried")) / 2, t, UiKit.MUTED, false);
        g.text(font, Tr.t("in chests"), cChest - font.width(Tr.t("in chests")) / 2, t, UiKit.MUTED, false);
        g.text(font, Tr.t("to get"), cGet - font.width(Tr.t("to get")) / 2, t, UiKit.GOLD, false);
        List<Line> ls = lines();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, ls.size() - rows)));
        int y = t + 12;
        String tip = null;
        for (int i = scroll; i < Math.min(ls.size(), scroll + rows); i++, y += rowH()) {
            Line l = ls.get(i);
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + rowH();
            g.fill(x, y, x + w, y + rowH() - 1, hov ? 0x40FFFFFF : (i % 2 == 0 ? 0x14FFFFFF : 0x00000000));
            g.fill(x, y, x + 2, y + rowH() - 1, l.get > 0 ? UiKit.RED : UiKit.GREEN);
            if (!l.row.icon().isEmpty()) g.item(l.row.icon(), x + 4, y + 1);
            g.text(font, Tr.t(UiKit.fit(font, l.row.name(), cNeed - x - 50)), x + 24, y + 5, l.get > 0 ? UiKit.WHITE : UiKit.DIM, true);
            num(g, String.valueOf(l.need), cNeed, y + 5, UiKit.TEXT);
            num(g, String.valueOf(l.have), cHave, y + 5, l.have > 0 ? UiKit.GREEN : UiKit.DIM);
            num(g, String.valueOf(l.chest), cChest, y + 5, l.chest > 0 ? 0xFF9FF0FF : UiKit.DIM);
            num(g, l.get > 0 ? stacks(l.get) : "got it", cGet, y + 5, l.get > 0 ? UiKit.GOLD : UiKit.GREEN);
            if (hov && l.chests > 0) tip = l.chests == 1 ? l.chest + " in 1 chest near you" : l.chest + " in " + l.chests + " chests near you";
        }
        if (ls.isEmpty()) UiKit.center(g, font, BuildBlueprintClient.SESSION.loaded() ? "Nothing left to get!" : "Load a build first (Saved builds).", x + w / 2, t + 24, UiKit.MUTED);
        if (ls.size() > rows) {
            int track = rows * rowH(), sh = Math.max(12, track * rows / ls.size()), sy = t + 12 + (track - sh) * scroll / Math.max(1, ls.size() - rows);
            g.fill(x + w + 1, sy, x + w + 3, sy + sh, 0xFFAAAAAA);
        }
        UiKit.center(g, font, msg.isEmpty() ? (Chests.remembered() == 0 ? "Open your chests once so their blocks are counted." : "Chests within 48 blocks are counted.") : msg,
                x + w / 2, height - 38, msg.isEmpty() ? UiKit.DIM : UiKit.GREEN);
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (tip != null) g.setComponentTooltipForNextFrame(font, java.util.Collections.singletonList(Component.literal(Tr.t(tip))), mouseX, mouseY);
    }

    private void num(GuiGraphicsExtractor g, String s, int cx, int y, int color) { g.text(font, Tr.t(s), cx - font.width(Tr.t(s)) / 2, y, color, true); }
}
