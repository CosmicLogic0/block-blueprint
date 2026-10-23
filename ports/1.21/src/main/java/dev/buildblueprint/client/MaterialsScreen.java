package dev.buildblueprint.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Materials menu (Page Up while a build is loaded). Two tabs: what the whole build still needs, and what the
 * current layer needs (so you know exactly what to bring for the layer you're on). Each row shows the block,
 * how many you carry, how many are still to place and a small bar. Scroll for more.
 */
public class MaterialsScreen extends BBScreen {
    private static boolean layerTab = false;   // remembered while the game runs
    private int scroll = 0, tick = 0;
    private Button tabAll, tabLayer;

    public MaterialsScreen() { super(Component.literal("Blueprint materials")); layerTab = Materials.showLayer(); }

    @Override public boolean isPauseScreen() { return false; }

    private int pw() { return Math.min(380, width - 20); }
    private int px() { return (width - pw()) / 2; }
    private int top() { return 46; }
    private int rowH() { return 20; }
    private int rowsFit() { return Math.max(3, (height - top() - 88) / rowH()); }

    @Override protected void init() {
        Materials.refresh();
        int x = px(), w = pw();
        tabAll = addRenderableWidget(Button.builder(Component.literal("Whole build"), b -> { layerTab = false; scroll = 0; }).bounds(x, top() - 22, w / 2 - 2, 20).build());
        tabLayer = addRenderableWidget(Button.builder(Component.literal("This layer"), b -> { layerTab = true; scroll = 0; }).bounds(x + w / 2 + 2, top() - 22, w / 2 - 2, 20).build());
        int by = height - 24, n = 5, bw = Math.min(84, (Math.max(w, 400) - 4 * (n - 1)) / n);
        int bx = width / 2 - (bw * n + 4 * (n - 1)) / 2;
        var tip = (java.util.function.Function<String, net.minecraft.client.gui.components.Tooltip>) t -> net.minecraft.client.gui.components.Tooltip.create(Component.literal(t));
        lockBtn = addRenderableWidget(Button.builder(Component.literal(lockLabel()), b -> { BuildBlueprintClient.setLocked(!BuildBlueprintClient.SESSION.locked); b.setMessage(Component.literal(lockLabel())); })
                .bounds(bx, by, bw, 20).tooltip(tip.apply("Place: locks the blueprint where it is so the arrow keys and Page Down can't move or turn it by accident. Press again to unlock.")).build());
        addRenderableWidget(Button.builder(Component.literal("< Layer"), b -> { Layers.step(-1); Materials.refresh(); }).bounds(bx + (bw + 4), by, bw, 20).tooltip(tip.apply("Layer down (End key)")).build());
        addRenderableWidget(Button.builder(Component.literal("Layer >"), b -> { Layers.step(1); Materials.refresh(); }).bounds(bx + 2 * (bw + 4), by, bw, 20).tooltip(tip.apply("Layer up (Home key). Past the top shows all layers.")).build());
        addRenderableWidget(Button.builder(Component.literal("Saved builds"), b -> MenuModule.openSaved()).bounds(bx + 3 * (bw + 4), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> MenuModule.openSettings(this)).bounds(bx + 4 * (bw + 4), by, bw, 20).build());
        // second row: helpers (auto-build is Creative only)
        int aw = (bw * n + 4 * (n - 1) - 16) / 5, ax = bx, ay = by - 24;
        abAll = addRenderableWidget(Button.builder(Component.literal("Auto-build"), b -> { AutoBuild.start(-1); onClose(); }).bounds(ax, ay, aw, 20)
                .tooltip(tip.apply("Creative only: places the whole blueprint for you, bottom layer first. Blocks in the way are replaced (chests are left alone).")).build());
        abLayer = addRenderableWidget(Button.builder(Component.literal("Build layer"), b -> { AutoBuild.start(Materials.layer()); onClose(); }).bounds(ax + aw + 4, ay, aw, 20)
                .tooltip(tip.apply("Creative only: places just the layer shown in the 'Layer' tab.")).build());
        abUndo = addRenderableWidget(Button.builder(Component.literal("Undo build"), b -> { AutoBuild.undoLast(); onClose(); }).bounds(ax + 2 * (aw + 4), ay, aw, 20)
                .tooltip(tip.apply("Puts back what was there before the last auto-build.")).build());
        mirrorBtn = addRenderableWidget(Button.builder(Component.literal("Mirror"), b -> { BuildBlueprintClient.mirror(); Materials.refresh(); }).bounds(ax + 3 * (aw + 4), ay, aw, 20)
                .tooltip(tip.apply("Flip the blueprint left <-> right, like looking in a mirror. Great for matching houses on both sides of a street.")).build());
        groundBtn = addRenderableWidget(Button.builder(Component.literal("To ground"), b -> { BuildBlueprintClient.snapToGround(); Materials.refresh(); }).bounds(ax + 4 * (aw + 4), ay, aw, 20)
                .tooltip(tip.apply("Move the blueprint up or down so it sits right on the ground under it.")).build());
    }

    private Button lockBtn, abAll, abLayer, abUndo, mirrorBtn, groundBtn;
    private static String lockLabel() { return BuildBlueprintClient.SESSION.locked ? "Unlock" : "Place it"; }

    @Override public void tick() { if (++tick % 10 == 0) Materials.refresh(); }

    @Override public boolean keyPressed(KeyEvent e) {
        if (Modules.menuKey != null && Modules.menuKey.matches(e.key(), e.scancode())) { onClose(); return true; }
        if (e.key() == 268) { Layers.step(1); Materials.refresh(); return true; }   // Home
        if (e.key() == 269) { Layers.step(-1); Materials.refresh(); return true; }  // End
        return super.keyPressed(e);
    }

    private Materials.Row hoveredRow;
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) return true;
        if (hoveredRow != null && BuildBlueprintClient.SESSION.loaded()) { Mc.setScreen(new SwapScreen(this, hoveredRow.id())); return true; }
        return false;
    }

    private List<Materials.Row> list() { return layerTab ? Materials.layerRows() : Materials.rows(); }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll = Math.max(0, Math.min(Math.max(0, list().size() - rowsFit()), scroll - (int) Math.signum(dy) * 2));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { Safe.run("the materials menu", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        var s = BuildBlueprintClient.SESSION;
        int x = px(), w = pw(), t = top();
        tabAll.active = layerTab; tabLayer.active = !layerTab;
        boolean cr = minecraft != null && minecraft.player != null && minecraft.player.isCreative();
        abAll.active = cr && s.loaded() && !AutoBuild.running(); abLayer.active = abAll.active; abUndo.active = cr && AutoBuild.canUndo();
        lockBtn.setMessage(Component.literal(lockLabel()));
        mirrorBtn.active = s.loaded() && !s.locked; groundBtn.active = mirrorBtn.active;
        tabLayer.setMessage(Component.literal("Layer " + (Materials.layer() + 1) + (s.loaded() ? " of " + s.blueprint().sy : "")));
        String title = s.loaded() ? s.blueprint().name + (s.locked ? "  [placed]" : "") : "No blueprint loaded";
        g.drawString(font, title, width / 2 - font.width(title) / 2, 6, 0xFFFFFF55, true);
        // progress line
        int done = layerTab ? Materials.layerPlaced() : Materials.placed(), total = Math.max(1, layerTab ? Materials.layerTotal() : Materials.totalBlocks());
        g.fill(x, 16, x + w, 24, 0xFF000000); g.fill(x + 1, 17, x + w - 1, 23, 0xFF2B2B2B);
        g.fill(x + 1, 17, x + 1 + (int) ((w - 2) * done / (double) total), 23, layerTab ? 0xFF3F7FCF : 0xFF3FAF3F);
        String pl = done + " / " + total + " placed (" + (int) (100.0 * done / total) + "%)";
        g.drawString(font, pl, width / 2 - font.width(pl) / 2, 16, 0xFFFFFFFF, true);

        // list panel
        int rows = rowsFit(), h = rows * rowH() + 16;
        g.fill(x - 2, t - 2, x + w + 2, t + h, 0xE0101018);
        g.drawString(font, "Block", x + 24, t + 2, 0xFF888888, false);
        g.drawString(font, "Have", x + w - 120, t + 2, 0xFF888888, false);
        g.drawString(font, "Still to place", x + w - 74, t + 2, 0xFF888888, false);
        List<Materials.Row> list = list();
        Materials.Row hovered = null;
        int y = t + 14;
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++, y += rowH()) {
            Materials.Row r = list.get(i);
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + rowH();
            if (hov) hovered = r;
            g.fill(x, y, x + w, y + rowH() - 1, hov ? 0x40FFFFFF : (i % 2 == 0 ? 0x14FFFFFF : 0x00000000));
            int col = r.done() ? 0xFF55FF55 : r.enough() ? 0xFF7FD7FF : 0xFFFF6060;
            g.fill(x, y, x + 2, y + rowH() - 1, col);
            if (!r.icon().isEmpty()) g.renderItem(r.icon(), x + 4, y + 1);
            String n = r.name();
            int room = w - 24 - 130;
            if (font.width(n) > room) n = font.plainSubstrByWidth(n, room - 6) + "..";
            g.drawString(font, n, x + 24, y + 6, r.done() ? 0xFF888888 : 0xFFFFFFFF, true);
            if (!r.enough() && font.width(n) + 40 < room && Chests.nearby(r.id(), 48)[0] > 0) {   // some of it is in a chest near you
                int cx = x + 24 + font.width(n) + 5;
                g.fill(cx, y + 5, cx + 30, y + 15, 0xFF1C5A6A);
                g.drawString(font, "chest", cx + 3, y + 6, 0xFF9FF0FF, false);
            }
            String hv = String.valueOf(r.have());
            g.drawString(font, hv, x + w - 108 - font.width(hv) / 2, y + 6, r.enough() ? 0xFF55FF55 : 0xFFFF6060, true);
            String left = r.done() ? "done" : String.valueOf(r.left());
            // little bar: share of this block already placed
            int bx = x + w - 74, bw = 66;
            g.fill(bx, y + 14, bx + bw, y + 17, 0xFF2B2B2B);
            int placed = r.total() - r.left();
            g.fill(bx, y + 14, bx + (int) (bw * placed / (double) Math.max(1, r.total())), y + 17, col);
            g.drawString(font, left, bx + bw / 2 - font.width(left) / 2, y + 4, 0xFFFFFFFF, true);
        }
        if (list.isEmpty()) g.drawString(font, s.loaded() ? "Nothing needed here." : "Load a build first (Saved builds).", x + 8, t + 20, 0xFFAAAAAA, true);
        if (list.size() > rows) {
            int sh = Math.max(12, (h - 16) * rows / list.size()), sy = t + 14 + (h - 16 - sh) * scroll / Math.max(1, list.size() - rows);
            g.fill(x + w - 1, sy, x + w + 1, sy + sh, 0xFFAAAAAA);
        }
        int missing = 0; for (Materials.Row r : list) if (!r.enough()) missing++;
        String foot = list.isEmpty() ? "" : missing == 0 ? "You have everything for " + (layerTab ? "this layer!" : "the whole build!") : missing + " block type" + (missing == 1 ? "" : "s") + " missing" + (layerTab ? " for this layer" : "");
        if (!foot.isEmpty()) foot += "  ·  click a block to swap it";
        g.drawString(font, foot, width / 2 - font.width(foot) / 2, t + h + 3, missing == 0 ? 0xFF55FF55 : 0xFFFFAA00, true);

        super.extractRenderState(g, mouseX, mouseY, delta);

        hoveredRow = hovered;
        if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hovered.name()).withStyle(ChatFormatting.WHITE));
            tip.add(Component.literal("Still to place: " + hovered.left()).withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal((layerTab ? "This layer uses: " : "Whole build uses: ") + hovered.total()).withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("You have: " + hovered.have()).withStyle(hovered.enough() ? ChatFormatting.GREEN : ChatFormatting.RED));
            int[] ch = Chests.nearby(hovered.id(), 48);
            if (ch[0] > 0) tip.add(Component.literal("In chests nearby: " + ch[0] + " (" + ch[1] + " chest" + (ch[1] == 1 ? "" : "s") + ", green boxes)").withStyle(ChatFormatting.AQUA));
            if (hovered.missing() > 0) tip.add(Component.literal("Get " + hovered.missing() + " more" + (hovered.missing() >= 64 ? " (" + (hovered.missing() + 63) / 64 + " stacks)" : "")).withStyle(ChatFormatting.RED));
            tip.add(Component.literal("Click to swap it for another block").withStyle(ChatFormatting.YELLOW));
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }
}
