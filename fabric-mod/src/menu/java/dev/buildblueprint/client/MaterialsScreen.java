package dev.buildblueprint.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * The build menu (Page Up while a build is loaded). Top: the build's name and overall progress. Then two tabs - the whole
 * build, or one layer (with arrows to change layer) - and the list of blocks: how many you have and how many are still
 * to place. Click a block to swap it. Bottom: the few things you do most (place, turn, saved builds, settings) and a
 * row of tools that only shows what makes sense right now (moving tools while it isn't placed, auto-build in Creative).
 */
public class MaterialsScreen extends Screen {
    private static boolean layerTab = false;   // remembered while the game runs
    private static int scroll = 0;
    private int tick = 0;
    private String state = "";                 // what the tool row was built for; the screen rebuilds when it changes
    private Button tabAll, tabLayer, layerDown, layerUp;

    public MaterialsScreen() { super(Component.literal("Build")); }
    public MaterialsScreen(boolean firstOpen) { this(); if (firstOpen) { layerTab = Materials.showLayer(); scroll = 0; } }

    @Override public boolean isPauseScreen() { return false; }

    private int pw() { return Math.min(400, width - 20); }
    private int px() { return (width - pw()) / 2; }
    private int top() { return 60; }
    private int rowH() { return 20; }
    private boolean toolsShown() { return !tools().isEmpty(); }
    private int rowsFit() { return Math.max(3, (height - top() - 18 - (toolsShown() ? 54 : 30)) / rowH()); }

    private static boolean creative() { var mc = net.minecraft.client.Minecraft.getInstance(); return mc.player != null && mc.player.isCreative(); }
    private static net.minecraft.client.gui.components.Tooltip tip(String t) { return Tooltip.create(Component.literal(t)); }

    /** The tool buttons that make sense right now, as {label, tooltip, action id}. */
    private static List<String[]> tools() {
        var s = BuildBlueprintClient.SESSION;
        List<String[]> t = new ArrayList<>();
        if (!s.loaded()) return t;
        if (!s.locked) {
            t.add(new String[]{"Mirror", "Flip the build left <-> right, like in a mirror. Handy for matching houses on both sides of a street.", "mirror"});
            t.add(new String[]{"To ground", "Move the build up or down so it sits right on the ground under it.", "ground"});
        }
        if (creative()) {
            t.add(new String[]{"Auto-build", "Creative only: places the whole build for you, bottom layer first. Blocks in the way are replaced (chests are left alone).", "auto"});
            t.add(new String[]{"Build layer", "Creative only: places just the layer you're on.", "autoLayer"});
            if (AutoBuild.canUndo()) t.add(new String[]{"Undo build", "Puts back what was there before the last auto-build.", "undo"});
        }
        t.add(new String[]{"Remove", "Take this build out of your world view. It stays in Saved builds, so you can load it again.", "remove"});
        return t;
    }
    private static String stateKey() {
        var s = BuildBlueprintClient.SESSION;
        return s.loaded() + "|" + s.locked + "|" + creative() + "|" + AutoBuild.canUndo() + "|" + AutoBuild.running();
    }

    @Override protected void init() {
        Materials.refresh();
        state = stateKey();
        var s = BuildBlueprintClient.SESSION;
        int x = px(), w = pw(), ty = 36;
        addRenderableWidget(Button.builder(Component.literal("i"), b -> Mc.setScreen(new HelpScreen(this, 0))).bounds(x + w - 16, 3, 16, 14)
                .tooltip(tip("How Build Blueprint works")).build());
        // tabs: whole build | < layer N of M >
        int half = w / 2 - 2;
        tabAll = addRenderableWidget(Button.builder(Component.literal("Whole build"), b -> { layerTab = false; scroll = 0; }).bounds(x, ty, half, 20)
                .tooltip(tip("Every block the build still needs.")).build());
        layerDown = addRenderableWidget(Button.builder(Component.literal("<"), b -> { Layers.step(-1); layerTab = true; Materials.refresh(); }).bounds(x + half + 4, ty, 20, 20)
                .tooltip(tip("Layer down (End key)")).build());
        tabLayer = addRenderableWidget(Button.builder(Component.literal("Layer"), b -> { layerTab = true; scroll = 0; }).bounds(x + half + 26, ty, half - 44, 20)
                .tooltip(tip("Just the layer you're building now.")).build());
        layerUp = addRenderableWidget(Button.builder(Component.literal(">"), b -> { Layers.step(1); layerTab = true; Materials.refresh(); }).bounds(x + w - 20, ty, 20, 20)
                .tooltip(tip("Layer up (Home key). Past the top shows all layers.")).build());
        // main row
        int by = height - 24, bw = Math.min(96, (w - 12) / 4);
        int[] bx = UiKit.row(width / 2, 4, bw, 4);
        addRenderableWidget(Button.builder(Component.literal(s.locked ? "Unlock" : "Place it"), b -> { BuildBlueprintClient.setLocked(!BuildBlueprintClient.SESSION.locked); rebuild(); })
                .bounds(bx[0], by, bw, 20).tooltip(tip(s.locked ? "Unlock it so you can move or turn it again." : "Lock the build where it is and start building, layer by layer (Delete key).")).build()).active = s.loaded();
        addRenderableWidget(Button.builder(Component.literal("Turn"), b -> { BuildBlueprintClient.turn(); Materials.refresh(); })
                .bounds(bx[1], by, bw, 20).tooltip(tip("Turn the build 90 degrees (Page Down).")).build()).active = s.loaded() && !s.locked;
        addRenderableWidget(Button.builder(Component.literal("Saved builds"), b -> MenuModule.openSaved()).bounds(bx[2], by, bw, 20)
                .tooltip(tip("Your saved builds and schematics: load another one, paste a code, copy one from your world.")).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> MenuModule.openSettings(this)).bounds(bx[3], by, bw, 20).build());
        // tools row: only what makes sense now
        List<String[]> t = tools();
        if (!t.isEmpty()) {
            int tw = Math.min(84, (w - 4 * (t.size() - 1)) / t.size());
            int[] tx = UiKit.row(width / 2, t.size(), tw, 4);
            for (int i = 0; i < t.size(); i++) {
                String[] d = t.get(i);
                Button btn = addRenderableWidget(Button.builder(Component.literal(d[0]), b -> tool(d[2])).bounds(tx[i], by - 24, tw, 20).tooltip(tip(d[1])).build());
                if ((d[2].equals("auto") || d[2].equals("autoLayer")) && AutoBuild.running()) btn.active = false;
            }
        }
    }

    private void tool(String id) {
        switch (id) {
            case "mirror" -> { BuildBlueprintClient.mirror(); Materials.refresh(); }
            case "ground" -> { BuildBlueprintClient.snapToGround(); Materials.refresh(); }
            case "auto" -> { AutoBuild.start(-1); onClose(); }
            case "autoLayer" -> { AutoBuild.start(Materials.layer()); onClose(); }
            case "undo" -> { AutoBuild.undoLast(); onClose(); }
            case "remove" -> { BuildBlueprintClient.removeBuild(); MenuModule.openSaved(); }
            default -> { }
        }
    }

    private void rebuild() { Mc.setScreen(new MaterialsScreen()); }

    @Override public void tick() {
        if (++tick % 10 == 0) Materials.refresh();
        if (!stateKey().equals(state)) rebuild();
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (Modules.menuKey != null && Modules.menuKey.matches(e)) { onClose(); return true; }
        if (e.key() == 268) { Layers.step(1); layerTab = true; Materials.refresh(); return true; }   // Home
        if (e.key() == 269) { Layers.step(-1); layerTab = true; Materials.refresh(); return true; }  // End
        return super.keyPressed(e);
    }

    private Materials.Row hoveredRow, hoveredEye;
    private int[] undoLink, eyeAll;
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) return true;
        if (undoLink != null && e.x() >= undoLink[0] && e.x() < undoLink[2] && e.y() >= undoLink[1] && e.y() < undoLink[3]) {
            if (BuildBlueprintClient.SESSION.undoSwap()) { Materials.refresh(); SessionStore.dirty(); Chat.bar("Last swap undone"); }
            return true;
        }
        if (hoveredEye != null && BuildBlueprintClient.SESSION.loaded()) {   // the eye: hide / show this block (Shift: only this block)
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.hasShiftDown()) {
                List<String> all = new ArrayList<>();
                for (Materials.Row r : Materials.rows()) all.add(r.id());
                Hide.only(hoveredEye.id(), all);
                Chat.bar(Hide.any() ? "Showing only " + hoveredEye.name() + "  ·  Shift-click the eye again to show everything" : "Showing every block again");
            } else {
                Hide.toggle(hoveredEye.id());
                Chat.bar((Hide.item(hoveredEye.id()) ? "Hid " : "Showing ") + hoveredEye.name() + (Hide.any() ? "  ·  " + Hide.count() + " hidden" : ""));
            }
            return true;
        }
        if (eyeAll != null && e.x() >= eyeAll[0] && e.x() < eyeAll[2] && e.y() >= eyeAll[1] && e.y() < eyeAll[3] && Hide.any()) { Hide.showAll(); Chat.bar("Showing every block again"); return true; }
        if (hoveredRow != null && BuildBlueprintClient.SESSION.loaded()) { Mc.setScreen(new SwapScreen(this, hoveredRow.id())); return true; }
        return false;
    }

    private List<Materials.Row> list() { return layerTab ? Materials.layerRows() : Materials.rows(); }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll = Math.max(0, Math.min(Math.max(0, list().size() - rowsFit()), scroll - (int) Math.signum(dy) * 2));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { Safe.run("the build menu", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        var s = BuildBlueprintClient.SESSION;
        int x = px(), w = pw(), t = top();
        tabAll.active = layerTab; tabLayer.active = !layerTab;
        int top = s.loaded() ? s.blueprint().sy : 0;
        tabLayer.setMessage(Component.literal(s.loaded() ? "Layer " + (Materials.layer() + 1) + " of " + top : "Layer"));
        layerDown.active = s.loaded(); layerUp.active = s.loaded();
        // header: name + status, then overall progress
        String name = s.loaded() ? UiKit.fit(font, s.blueprint().name, w - 90) : "No build loaded";
        g.text(font, name, x, 8, UiKit.GOLD, true);
        if (s.loaded()) {
            String st = s.locked ? "placed" : "not placed yet";
            g.text(font, st, x + w - 22 - font.width(st), 8, s.locked ? UiKit.GREEN : UiKit.ORANGE, true);
            UiKit.bar(g, font, x, 20, w, Materials.placed(), Materials.totalBlocks(), UiKit.BAR_BUILD, Materials.placed() + " / " + Materials.totalBlocks() + " placed");
        }
        // the list
        int rows = rowsFit(), h = rows * rowH() + 16;
        UiKit.panel(g, x - 2, t - 2, w + 4, h + 2, layerTab ? UiKit.BAR_LAYER : UiKit.BAR_BUILD);
        g.text(font, layerTab ? "Blocks for this layer" : "Blocks for the whole build", x + 4, t + 2, UiKit.MUTED, false);
        int ex = x + w - 150;   // the eye column
        boolean eyeAllHov = Hide.any() && mouseX >= ex - 2 && mouseX < ex + 15 && mouseY >= t && mouseY < t + 12;
        UiKit.eye(g, ex, t + 1, !Hide.any(), eyeAllHov ? UiKit.WHITE : Hide.any() ? UiKit.ORANGE : UiKit.MUTED);
        eyeAll = new int[]{ex - 2, t, ex + 15, t + 12};
        g.text(font, "have", x + w - 120, t + 2, UiKit.MUTED, false);
        g.text(font, "to place", x + w - 62, t + 2, UiKit.MUTED, false);
        List<Materials.Row> list = list();
        java.util.Set<String> notHere = Swapper.notInGame();
        Materials.Row hovered = null, eyeHov = null;
        int y = t + 14;
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++, y += rowH()) {
            Materials.Row r = list.get(i);
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + rowH();
            boolean onEye = hov && mouseX >= ex - 3 && mouseX < ex + 16;
            if (onEye) eyeHov = r; else if (hov) hovered = r;
            boolean shown = !Hide.item(r.id());
            g.fill(x, y, x + w, y + rowH() - 1, hov ? 0x40FFFFFF : (i % 2 == 0 ? 0x14FFFFFF : 0x00000000));
            int col = r.done() ? UiKit.GREEN : r.enough() ? UiKit.BLUE : UiKit.RED;
            g.fill(x, y, x + 2, y + rowH() - 1, col);
            if (!r.icon().isEmpty()) g.item(r.icon(), x + 4, y + 1);
            UiKit.eye(g, ex, y + 5, shown, onEye ? UiKit.WHITE : shown ? 0xFFB8B8B8 : 0xFF6A6A6A);
            int room = w - 24 - 156;   // name, up to the eye
            boolean missingHere = notHere.contains(r.id());
            String n = UiKit.fit(font, r.name(), missingHere ? room - 70 : room);
            g.text(font, n, x + 24, y + 6, !shown ? UiKit.DIM : missingHere ? UiKit.ORANGE : r.done() ? UiKit.DIM : UiKit.WHITE, true);
            if (!shown) g.fill(x + 24, y + 10, x + 24 + font.width(n), y + 11, 0xFF6A6A6A);   // crossed out while hidden
            if (missingHere) {   // this block isn't in this Minecraft version: click it to pick another
                int cx = x + 24 + font.width(n) + 5;
                g.fill(cx, y + 5, cx + 62, y + 15, 0xFF5A3A10);
                g.text(font, "not in game", cx + 3, y + 6, 0xFFFFC060, false);
            }
            if (!missingHere && !r.enough() && font.width(n) + 40 < room && Chests.nearby(r.id(), 48)[0] > 0) {   // some of it is in a chest near you
                int cx = x + 24 + font.width(n) + 5;
                g.fill(cx, y + 5, cx + 30, y + 15, 0xFF1C5A6A);
                g.text(font, "chest", cx + 3, y + 6, 0xFF9FF0FF, false);
            }
            String hv = String.valueOf(r.have());
            g.text(font, hv, x + w - 108 - font.width(hv) / 2, y + 6, r.enough() ? UiKit.GREEN : UiKit.RED, true);
            String left = r.done() ? "done" : String.valueOf(r.left());
            int bx = x + w - 66, bw = 60;   // little bar: share of this block already placed
            g.fill(bx, y + 14, bx + bw, y + 17, 0xFF2B2B2B);
            g.fill(bx, y + 14, bx + (int) (bw * (r.total() - r.left()) / (double) Math.max(1, r.total())), y + 17, col);
            g.text(font, left, bx + bw / 2 - font.width(left) / 2, y + 4, UiKit.WHITE, true);
        }
        if (list.isEmpty()) g.text(font, !s.loaded() ? "Load a build first (Saved builds)." : layerTab ? "This layer is done!" : "Nothing left to place - the build is done!", x + 8, t + 22, UiKit.MUTED, true);
        if (list.size() > rows) {
            int sh = Math.max(12, (h - 16) * rows / list.size()), sy = t + 14 + (h - 16 - sh) * scroll / Math.max(1, list.size() - rows);
            g.fill(x + w - 1, sy, x + w + 1, sy + sh, 0xFFAAAAAA);
        }
        // under the list: a warning only when the build has blocks this Minecraft version doesn't have, else a plain hint
        undoLink = null;
        if (s.loaded()) {
            int bad = notHere.size();
            String foot = bad > 0 ? bad + " block" + (bad == 1 ? "" : "s") + " in this build " + (bad == 1 ? "isn't" : "aren't") + " in Minecraft " + dev.buildblueprint.core.SchemCodec.minecraftVersion() + "  ·  click one to swap it"
                    : Hide.any() ? Hide.count() + " hidden  ·  click the eye at the top to show them all" : "Click a block to swap or remove it  ·  the eye hides it";
            String link = s.canUndoSwap() ? "Undo swap" : "";
            String sep = link.isEmpty() ? "" : "  ·  ";
            int fw = font.width(foot) + font.width(sep) + font.width(link), fx = width / 2 - fw / 2, fy = t + h + 3;
            g.text(font, foot, fx, fy, bad > 0 ? UiKit.ORANGE : UiKit.MUTED, true);
            if (!link.isEmpty()) {
                int lx = fx + font.width(foot) + font.width(sep);
                g.text(font, sep, fx + font.width(foot), fy, UiKit.MUTED, true);
                boolean lh = mouseX >= lx && mouseX < lx + font.width(link) && mouseY >= fy - 1 && mouseY < fy + 10;
                g.text(font, link, lx, fy, lh ? UiKit.WHITE : UiKit.BLUE, true);
                g.fill(lx, fy + 9, lx + font.width(link), fy + 10, lh ? UiKit.WHITE : UiKit.BLUE);
                undoLink = new int[]{lx, fy - 1, lx + font.width(link), fy + 10};
            }
        }

        super.extractRenderState(g, mouseX, mouseY, delta);

        hoveredRow = hovered; hoveredEye = eyeHov;
        if (eyeHov != null) {
            List<Component> tip = new ArrayList<>();
            boolean hid = Hide.item(eyeHov.id());
            tip.add(Component.literal((hid ? "Show " : "Hide ") + eyeHov.name()).withStyle(ChatFormatting.WHITE));
            tip.add(Component.literal(hid ? "Its ghost blocks are hidden right now" : "Hides its ghost blocks so you can see the rest better").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Shift-click: show only this block").withStyle(ChatFormatting.YELLOW));
            g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        } else if (eyeAllHov) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal("Show every block again").withStyle(ChatFormatting.WHITE));
            tip.add(Component.literal(Hide.count() + " hidden").withStyle(ChatFormatting.GRAY));
            g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
        if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hovered.name()).withStyle(ChatFormatting.WHITE));
            if (notHere.contains(hovered.id())) tip.add(Component.literal("This block doesn't exist in Minecraft " + dev.buildblueprint.core.SchemCodec.minecraftVersion() + " - click to pick one to use instead").withStyle(ChatFormatting.GOLD));
            tip.add(Component.literal("Still to place: " + hovered.left() + "  (of " + hovered.total() + ")").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("You have: " + hovered.have()).withStyle(hovered.enough() ? ChatFormatting.GREEN : ChatFormatting.RED));
            int[] ch = Chests.nearby(hovered.id(), 48);
            if (ch[0] > 0) tip.add(Component.literal("In chests nearby: " + ch[0] + " (" + ch[1] + " chest" + (ch[1] == 1 ? "" : "s") + ", green boxes)").withStyle(ChatFormatting.AQUA));
            if (hovered.missing() > 0) tip.add(Component.literal("Get " + hovered.missing() + " more" + (hovered.missing() >= 64 ? " (" + (hovered.missing() + 63) / 64 + " stacks)" : "")).withStyle(ChatFormatting.RED));
            tip.add(Component.literal("Click to swap it or remove it from the build").withStyle(ChatFormatting.YELLOW));
            g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }
}
