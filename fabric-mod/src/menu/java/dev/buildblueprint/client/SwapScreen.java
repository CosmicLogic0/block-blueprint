package dev.buildblueprint.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Swap one block of the build for any other block in the game, including blocks from other mods.
 * Top: what you're swapping and a search box. Then three lists: Similar (same kind of piece, most alike first),
 * All blocks, and blocks From mods. Pick one, check the preview at the bottom ("Blue Terracotta -> Cyan Terracotta,
 * 876 blocks"), press Swap. Double-click swaps straight away; Enter swaps the picked block.
 * "Remove from build" takes that block out of the build altogether (snow on a roof, say).
 */
public class SwapScreen extends Screen {
    private final Screen parent;
    private final String itemId;           // the row clicked in the materials list
    private final String blockId;          // the block that item places
    private final boolean notInGame;       // that block doesn't exist in this Minecraft version
    private static String tab = "similar"; // similar | all | mods (remembered while the game runs)
    private List<Swapper.Option> similar, mods, shown = new ArrayList<>();
    private Swapper.Option pick;
    private int scroll = 0;
    private boolean dragging;
    private EditBox search;
    private Button tSimilar, tAll, tMods, swapBtn, undoBtn;
    private String msg = "";

    public SwapScreen(Screen parent, String itemId) {
        super(Component.literal(Tr.t("Swap blocks")));
        this.parent = parent; this.itemId = itemId;
        var ids = Swapper.blocksForItem(itemId);
        this.blockId = ids.isEmpty() ? itemId : ids.iterator().next();
        this.notInGame = !Swapper.exists(blockId);
    }

    @Override public boolean isPauseScreen() { return false; }

    // layout
    private int pw() { return Math.min(460, width - 16); }
    private int px() { return (width - pw()) / 2; }
    private int listTop() { return 86; }
    private int listBottom() { return height - 58; }
    private int rowH() { return 20; }
    private int cols() { return pw() >= 340 ? 2 : 1; }
    private int colW() { return (pw() - 8 - (cols() - 1) * 4) / cols(); }
    private int visRows() { return Math.max(1, (listBottom() - listTop() - 4) / rowH()); }
    private int totalRows() { return (shown.size() + cols() - 1) / cols(); }
    private int maxScroll() { return Math.max(0, totalRows() - visRows()); }
    private static net.minecraft.client.gui.components.Tooltip tip(String t) { return Tooltip.create(Component.literal(Tr.t(t))); }

    @Override protected void init() {
        if (similar == null) { similar = Swapper.similar(blockId); mods = Swapper.modded(); }
        int x = px(), w = pw();
        addRenderableWidget(Button.builder(Component.literal(Tr.t("i")), b -> Mc.setScreen(new HelpScreen(this, 4))).bounds(x + w - 20, 8, 20, 20)
                .tooltip(tip("How swapping works")).build());
        int rw = Math.min(110, font.width(Tr.t("Remove from build")) + 12);
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Remove from build", rw - 8)), b -> doRemove()).bounds(x + w - 24 - rw, 8, rw, 20)
                .tooltip(tip("Take every " + fromName() + " out of the build, for example snow on a roof. Undo swap puts it back.")).build());
        String q = search == null ? "" : search.getValue();
        search = new EditBox(font, x, 38, w, 18, Component.literal(Tr.t("Search")));
        search.setHint(Component.literal(Tr.t("Search every block by name or mod...")));
        search.setValue(q);
        search.setResponder(t -> { scroll = 0; refill(); });
        addRenderableWidget(search);
        boolean hasMods = !mods.isEmpty();
        int n = hasMods ? 3 : 2, tw = (w - (n - 1) * 4) / n;
        tSimilar = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Similar (" + similar.size() + ")", tw - 8)), b -> setTab("similar")).bounds(x, 60, tw, 20)
                .tooltip(tip("Blocks of the same kind: stairs for stairs, slabs for slabs, full blocks for full blocks. The most alike come first.")).build());
        tAll = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "All blocks (" + Swapper.allOptions().size() + ")", tw - 8)), b -> setTab("all")).bounds(x + tw + 4, 60, tw, 20)
                .tooltip(tip("Every block in the game, A to Z.")).build());
        if (hasMods) tMods = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "From mods (" + mods.size() + ")", (w - 2 * (tw + 4)) - 8)), b -> setTab("mods")).bounds(x + 2 * (tw + 4), 60, w - 2 * (tw + 4), 20)
                .tooltip(tip("Blocks added by the other mods you have installed.")).build());
        int by = height - 24, bw = (w - 8) / 3;
        swapBtn = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Swap", bw - 8)), b -> doSwap()).bounds(x, by, bw, 20)
                .tooltip(tip("Swap every block of this kind in the build (Enter).")).build());
        undoBtn = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Undo last swap", bw - 8)), b -> undo()).bounds(x + bw + 4, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Back", (w - 2 * (bw + 4)) - 8)), b -> onClose()).bounds(x + 2 * (bw + 4), by, w - 2 * (bw + 4), 20).build());
        if (tab.equals("similar") && similar.isEmpty()) tab = "all";
        if (tab.equals("mods") && !hasMods) tab = "all";
        refill();
        setFocused(search);
    }

    /** For the automatic screenshot test: type into the search box. */
    void testSearch(String q) { if (search != null) search.setValue(q); }

    private void setTab(String t) { tab = t; scroll = 0; if (!search.getValue().isEmpty()) search.setValue(""); refill(); }

    private void refill() {
        String q = search == null ? "" : search.getValue().trim();
        if (!q.isEmpty()) shown = Swapper.search(blockId, q);
        else shown = tab.equals("all") ? Swapper.allOptions() : tab.equals("mods") ? mods : similar;
        scroll = Math.min(scroll, maxScroll());
        if (pick != null && !shown.contains(pick) && q.isEmpty()) pick = null;
    }

    private void undo() {
        if (BuildBlueprintClient.SESSION.undoSwap()) { Materials.refresh(); SessionStore.dirty(); msg = "Last swap undone."; }
    }

    private void doSwap() { Safe.run("swapping blocks", this::doSwap0); }
    private void doSwap0() {
        if (pick == null) { msg = "Pick a block from the list first."; return; }
        Map<String, String> m = Swapper.plan(itemId, pick.id());
        if (m.isEmpty()) { msg = "Nothing to change."; return; }
        int n = Swapper.apply(m);
        Chat.bar("Swapped " + n + " " + fromName() + " to " + pick.name() + "  ·  Undo: click Undo swap in the build menu");
        onClose();
    }

    private void doRemove() { Safe.run("removing blocks", () -> {
        int n = Swapper.remove(itemId);
        if (n == 0) { msg = "Nothing to remove."; return; }
        Chat.bar("Removed " + n + " " + fromName() + " from the build  ·  Undo: click Undo swap in the build menu");
        onClose();
    }); }

    private String fromName() { ItemStack s = Materials.stackFor(itemId); return s.isEmpty() ? PlayerInfo.pretty(blockId) : s.getHoverName().getString(); }
    private int count() { int c = 0; var cs = Swapper.counts(); for (String b : Swapper.blocksForItem(itemId)) c += cs.getOrDefault(b, 0); return c; }

    // --- input
    private int hit(double mx, double my) {
        int x0 = px() + 4, y0 = listTop() + 2;
        if (mx < x0 || mx >= px() + pw() - 6 || my < y0 || my >= listTop() + 2 + visRows() * rowH()) return -1;
        int col = (int) ((mx - x0) / (colW() + 4)), row = (int) ((my - y0) / rowH());
        if (col >= cols()) return -1;
        int i = (row + scroll) * cols() + col;
        return i < shown.size() ? i : -1;
    }
    private boolean onBar(double mx, double my) { return maxScroll() > 0 && mx >= px() + pw() - 6 && mx < px() + pw() + 2 && my >= listTop() && my < listBottom(); }
    private void barTo(double my) {
        double f = (my - listTop()) / Math.max(1, listBottom() - listTop());
        scroll = Math.max(0, Math.min(maxScroll(), (int) Math.round(f * maxScroll())));
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        dragging = false;
        if (super.mouseClicked(e, dbl)) return true;
        if (onBar(e.x(), e.y())) { dragging = true; barTo(e.y()); return true; }
        int i = hit(e.x(), e.y());
        if (i >= 0) { pick = shown.get(i); msg = ""; if (dbl) doSwap(); return true; }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging) { barTo(e.y()); return true; }
        return super.mouseDragged(e, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(dy) * 2));
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) { onClose(); return true; }
        if ((e.key() == 257 || e.key() == 335) && pick != null) { doSwap(); return true; }   // Enter
        return super.keyPressed(e);
    }
    @Override public void onClose() { Materials.refresh(); Mc.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { Safe.run("the swap screen", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = px(), w = pw();
        UiKit.panel(g, x - 6, 4, w + 12, height - 8, UiKit.ORANGE);
        // what's being swapped
        ItemStack self = Materials.stackFor(itemId);
        if (!self.isEmpty()) g.item(self, x, 9);
        g.text(font, Tr.t(UiKit.fit(font, "Swap " + fromName(), w - 160)), x + 20, 9, UiKit.GOLD, true);
        String sub = count() + " in this build";
        g.text(font, Tr.t(sub), x + 20, 21, UiKit.MUTED, false);
        if (notInGame) g.text(font, Tr.t("  ·  not in Minecraft " + dev.buildblueprint.core.SchemCodec.minecraftVersion() + ", pick one to use instead"), x + 20 + font.width(Tr.t(sub)), 21, UiKit.ORANGE, false);
        // tabs: the active one is greyed out (pressed), like the build menu tabs
        String q = search.getValue().trim();
        tSimilar.active = !similar.isEmpty() && (!q.isEmpty() || !tab.equals("similar"));
        tAll.active = !q.isEmpty() || !tab.equals("all");
        if (tMods != null) tMods.active = !q.isEmpty() || !tab.equals("mods");

        // the list
        int top = listTop(), bot = listBottom(), cw = colW(), rows = visRows();
        g.fill(x, top, x + w, bot, 0xFF15151C);
        g.fill(x, top, x + w, top + 1, 0xFF2C2C38);
        Swapper.Option hover = null;
        int y0 = top + 2;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols(); c++) {
                int i = (r + scroll) * cols() + c;
                if (i >= shown.size()) break;
                Swapper.Option o = shown.get(i);
                int cx = x + 4 + c * (cw + 4), cy = y0 + r * rowH();
                boolean sel = o.equals(pick), hov = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + rowH() - 1;
                if (hov) hover = o;
                g.fill(cx, cy, cx + cw, cy + rowH() - 1, sel ? 0xFF4A3A18 : hov ? 0xFF33334A : 0xFF20202A);
                if (sel) { g.fill(cx, cy, cx + cw, cy + 1, UiKit.GOLD); g.fill(cx, cy + rowH() - 2, cx + cw, cy + rowH() - 1, UiKit.GOLD); g.fill(cx, cy, cx + 1, cy + rowH() - 1, UiKit.GOLD); g.fill(cx + cw - 1, cy, cx + cw, cy + rowH() - 1, UiKit.GOLD); }
                g.item(o.icon(), cx + 2, cy + 1);
                int room = cw - 24;
                String tag = o.mod();
                int tagW = tag.isEmpty() ? 0 : Math.min(font.width(Tr.t(tag)) + 6, room / 2);
                g.text(font, Tr.t(UiKit.fit(font, o.name(), room - tagW)), cx + 21, cy + 6, sel ? UiKit.WHITE : UiKit.TEXT, false);
                if (tagW > 0) {
                    String t = UiKit.fit(font, tag, tagW - 6);
                    g.text(font, Tr.t(t), cx + cw - 3 - font.width(Tr.t(t)), cy + 6, 0xFF8C7CC8, false);
                }
            }
        }
        if (shown.isEmpty()) UiKit.center(g, font, q.isEmpty() ? "Nothing here." : "No block matches \"" + q + "\".", x + w / 2, top + 20, UiKit.MUTED);
        // scroll bar
        if (maxScroll() > 0) {
            int track = bot - top - 4, sh = Math.max(14, track * rows / Math.max(1, totalRows())), sy = top + 2 + (track - sh) * scroll / maxScroll();
            g.fill(x + w - 4, top + 2, x + w - 1, bot - 2, 0xFF22222C);
            g.fill(x + w - 4, sy, x + w - 1, sy + sh, dragging ? 0xFFFFFFFF : 0xFFAAAAAA);
        }

        // preview of the change
        int py = bot + 6;
        if (pick == null) {
            UiKit.center(g, font, msg.isEmpty() ? "Click a block to pick it  ·  double-click swaps right away" : msg, x + w / 2, py + 4, msg.isEmpty() ? UiKit.MUTED : UiKit.ORANGE);
        } else {
            String a = UiKit.fit(font, fromName(), (w - 140) / 2), b = UiKit.fit(font, pick.name(), (w - 140) / 2), n = count() + " blocks";
            int total = 18 + font.width(Tr.t(a)) + 22 + 18 + font.width(Tr.t(b)) + 12 + font.width(Tr.t(n)), sx = x + (w - total) / 2;
            if (!self.isEmpty()) g.item(self, sx, py);
            g.text(font, Tr.t(a), sx + 18, py + 4, UiKit.WHITE, true);
            int ax = sx + 18 + font.width(Tr.t(a)) + 6;
            g.text(font, Tr.t("->"), ax, py + 4, UiKit.GOLD, true);
            g.item(pick.icon(), ax + 16, py);
            g.text(font, Tr.t(b), ax + 34, py + 4, UiKit.GOLD, true);
            g.text(font, Tr.t(n), ax + 34 + font.width(Tr.t(b)) + 12, py + 4, UiKit.MUTED, false);
        }
        swapBtn.active = pick != null;
        undoBtn.active = BuildBlueprintClient.SESSION.canUndoSwap();
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (hover != null && !dragging) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(Tr.t(hover.name())).withStyle(ChatFormatting.WHITE));
            tip.add(Component.literal(Tr.t((hover.mod().isEmpty() ? "Minecraft" : hover.mod()) + "  ·  " + hover.shape())).withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal(Tr.t("Double-click to swap to this")).withStyle(ChatFormatting.YELLOW));
            g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }
}
