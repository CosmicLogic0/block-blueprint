package dev.buildblueprint.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Swap a block in the blueprint for another one. "Whole set" changes every piece of a wood / stone / colour set at once
 * (oak planks, stairs, slabs, fences, doors... all to spruce). "Just this block" swaps one kind of block, with a search.
 */
public class SwapScreen extends BBScreen {
    private final Screen parent;
    private final String itemId;          // the row clicked in the materials list
    private final String blockId;         // the block that item places
    private final Swapper.Mat set;        // its wood / stone / colour set, or null
    private boolean setMode;
    private List<String> options = new ArrayList<>();   // set keys, or block ids
    private List<String> shown = new ArrayList<>();
    private String pick;
    private int scroll = 0;
    private EditBox search;
    private Button tabSet, tabOne, apply, undo;
    private String msg = "";

    public SwapScreen(Screen parent, String itemId) {
        super(Component.literal("Swap blocks"));
        this.parent = parent; this.itemId = itemId;
        var ids = Swapper.blocksForItem(itemId);
        this.blockId = ids.isEmpty() ? itemId : ids.iterator().next();
        this.set = Swapper.matOf(blockId);
        this.setMode = set != null;
    }

    @Override public boolean isPauseScreen() { return false; }

    private int pw() { return Math.min(440, width - 20); }
    private int px() { return (width - pw()) / 2; }
    private int gridTop() { return 92; }
    private int gridBottom() { return height - 92; }
    private int cell() { return setMode ? 22 : 20; }
    private int cols() { return Math.max(4, (pw() - 16) / cell()); }

    @Override protected void init() {
        int x = px(), w = pw();
        String setName = set == null ? "" : set.label();
        tabSet = addRenderableWidget(Button.builder(Component.literal(set == null ? "Whole set" : "Whole " + setName + " set"), b -> mode(true)).bounds(x, 50, w / 2 - 2, 20)
                .tooltip(Tooltip.create(Component.literal(set == null ? "This block isn't part of a wood, stone or colour set." : "Change every " + setName + " piece at once: blocks, stairs, slabs, fences, doors..."))).build());
        tabOne = addRenderableWidget(Button.builder(Component.literal("Just this block"), b -> mode(false)).bounds(x + w / 2 + 2, 50, w / 2 - 2, 20)
                .tooltip(Tooltip.create(Component.literal("Swap only this one kind of block. Stairs swap to stairs, slabs to slabs."))).build());
        tabSet.active = set != null;
        search = new EditBox(font, x, 72, w, 16, Component.literal("Search"));
        search.setHint(Component.literal("Search blocks..."));
        search.setResponder(t -> { filter(); scroll = 0; });
        addRenderableWidget(search);
        int by = height - 26, bw = (w - 8) / 3;
        apply = addRenderableWidget(Button.builder(Component.literal("Swap"), b -> doApply()).bounds(x, by, bw, 20).build());
        undo = addRenderableWidget(Button.builder(Component.literal("Undo last swap"), b -> { if (BuildBlueprintClient.SESSION.undoSwap()) { Materials.refresh(); SessionStore.dirty(); msg = "Undone."; } }).bounds(x + bw + 4, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(x + 2 * (bw + 4), by, bw, 20).build());
        mode(setMode);
    }

    private void mode(boolean s) {
        setMode = s && set != null; pick = null; scroll = 0; msg = "";
        options = new ArrayList<>();
        if (setMode) for (Swapper.Mat m : Swapper.setsLike(set)) { if (!m.key().equals(set.key()) && Swapper.exists("minecraft:" + m.full())) options.add(m.key()); }
        else options = Swapper.singleCandidates(blockId);
        tabSet.active = !setMode && set != null; tabOne.active = setMode;
        filter();
    }

    private Swapper.Mat mat(String key) { for (Swapper.Mat m : Swapper.setsLike(set)) if (m.key().equals(key)) return m; return null; }
    private ItemStack icon(String opt) {
        String id = setMode ? "minecraft:" + mat(opt).full() : opt;
        try { return new ItemStack(V.block(id).asItem()); } catch (Exception e) { return ItemStack.EMPTY; }
    }
    private String name(String opt) { if (setMode) return mat(opt).label(); ItemStack s = icon(opt); return s.isEmpty() ? PlayerInfo.pretty(opt) : s.getHoverName().getString(); }

    private void filter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        for (String o : options) if (q.isEmpty() || name(o).toLowerCase(Locale.ROOT).contains(q) || o.contains(q)) shown.add(o);
    }

    private Map<String, String> plan() {
        Map<String, String> m = new LinkedHashMap<>();
        if (pick == null) return m;
        if (setMode) return Swapper.setSwap(set, mat(pick));
        for (String b : Swapper.blocksForItem(itemId)) m.put(b, pick);
        return m;
    }

    private void doApply() { Safe.run("swapping blocks", this::doApply0); }
    private void doApply0() {
        Map<String, String> m = plan();
        if (m.isEmpty()) { msg = pick == null ? "Pick what to change it to first." : "Nothing to change."; return; }
        int n = Swapper.apply(m);
        msg = "Swapped " + n + " block" + (n == 1 ? "" : "s") + ". Undo last swap puts it back.";
        Chat.bar("Swapped " + n + " blocks");
        pick = null;
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) return true;
        int c = cell(), x0 = px() + 8, i = hit(e.x(), e.y());
        if (i >= 0) { pick = shown.get(i); msg = ""; if (dbl) doApply(); return true; }
        return false;
    }
    private int hit(double mx, double my) {
        int c = cell(), x0 = px() + 8, y0 = gridTop() + 4;
        if (mx < x0 || my < y0 || my >= gridBottom()) return -1;
        int col = (int) ((mx - x0) / c), row = (int) ((my - y0) / c);
        if (col >= cols()) return -1;
        int i = (row + scroll) * cols() + col;
        return i < shown.size() ? i : -1;
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int rows = (shown.size() + cols() - 1) / cols(), vis = (gridBottom() - gridTop() - 4) / cell();
        scroll = Math.max(0, Math.min(Math.max(0, rows - vis), scroll - (int) Math.signum(dy)));
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) { onClose(); return true; }
        return super.keyPressed(e);
    }
    @Override public void onClose() { Materials.refresh(); Mc.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { Safe.run("the swap screen", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = px(), w = pw();
        g.fill(x - 6, 8, x + w + 6, height - 4, 0xE0101018);
        g.fill(x - 6, 8, x + w + 6, 9, 0xFFFFB040);
        // what is being swapped
        ItemStack self = Materials.stackFor(itemId);
        if (!self.isEmpty()) g.renderItem(self, x, 14);
        String title = "Swap " + (self.isEmpty() ? PlayerInfo.pretty(itemId) : self.getHoverName().getString());
        g.drawString(font, title, x + 22, 14, 0xFFFFFF55, true);
        int count = 0; var cs = Swapper.counts(); for (String b : Swapper.blocksForItem(itemId)) count += cs.getOrDefault(b, 0);
        String sub = count + " in this build" + (set != null ? "  ·  part of the " + set.label() + " set" : "");
        g.drawString(font, sub, x + 22, 26, 0xFFAAAAAA, false);
        String how = setMode ? "Pick a new " + (set.kind().equals("color") ? "colour" : set.kind()) + " for every " + set.label() + " piece:" : "Pick the block to use instead:";
        g.drawString(font, how, x, 40, 0xFFDDDDDD, false);
        search.visible = !setMode;

        // grid
        int c = cell(), cols = cols(), x0 = x + 8, y0 = gridTop() + 4;
        g.fill(x, gridTop(), x + w, gridBottom(), 0xFF1A1A22);
        g.enableScissor(x, gridTop(), x + w, gridBottom());
        String hover = null;
        for (int i = scroll * cols; i < shown.size(); i++) {
            int r = i / cols - scroll, col = i % cols, cx = x0 + col * c, cy = y0 + r * c;
            if (cy > gridBottom()) break;
            String o = shown.get(i);
            boolean sel = o.equals(pick), hov = mouseX >= cx && mouseX < cx + c && mouseY >= cy && mouseY < cy + c;
            g.fill(cx, cy, cx + c - 2, cy + c - 2, sel ? 0xFFFFB040 : hov ? 0xFF4A4A60 : 0xFF2A2A36);
            if (sel) g.fill(cx + 1, cy + 1, cx + c - 3, cy + c - 3, 0xFF3A3020);
            ItemStack st = icon(o);
            if (!st.isEmpty()) g.renderItem(st, cx + (c - 18) / 2, cy + (c - 18) / 2);
            if (hov) hover = name(o);
        }
        g.disableScissor();
        if (shown.isEmpty()) g.drawString(font, "No matches.", x + 10, y0 + 6, 0xFF888888, false);

        // preview of the change
        int py = gridBottom() + 6;
        Map<String, String> m = plan();
        if (pick == null) g.drawString(font, "Click a block above (double-click swaps straight away).", x, py, 0xFF999999, false);
        else {
            int n = 0; for (String k : m.keySet()) n += cs.getOrDefault(k, 0);
            String head = name(pick) + ": " + m.size() + " kind" + (m.size() == 1 ? "" : "s") + ", " + n + " blocks will change";
            g.drawString(font, head, x, py, 0xFFFFFFFF, true);
            int k = 0;
            for (Map.Entry<String, String> en : m.entrySet()) {
                if (k == 3) { g.drawString(font, "+" + (m.size() - 3) + " more", x + 4, py + 12 + k * 10, 0xFF888888, false); break; }
                String line = PlayerInfo.pretty(en.getKey()) + "  ->  " + PlayerInfo.pretty(en.getValue());
                if (font.width(line) > w - 8) line = font.plainSubstrByWidth(line, w - 14) + "..";
                g.drawString(font, line, x + 4, py + 12 + k * 10, 0xFFBBBBBB, false);
                k++;
            }
        }
        apply.active = pick != null;
        undo.active = BuildBlueprintClient.SESSION.canUndoSwap();
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (!msg.isEmpty()) g.drawString(font, msg, width / 2 - font.width(msg) / 2, height - 38, 0xFF55FF55, true);
        if (hover != null) V.tooltip(g, font, java.util.Collections.singletonList(Component.literal(hover)), mouseX, mouseY);
    }
}
