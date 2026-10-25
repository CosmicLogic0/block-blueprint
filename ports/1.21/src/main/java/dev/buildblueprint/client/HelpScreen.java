package dev.buildblueprint.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * "How it works": seven short pages with a small picture each, one idea per page. Opened with the i button in the build
 * menu, the swap screen and Settings. Left: the page list. Right: the page. Bottom: Back / Next / Done.
 */
public class HelpScreen extends BBScreen {
    private final Screen parent;
    private int page;
    private Button prev, next;
    private final List<Button> tabs = new ArrayList<>();

    private static final String[] TITLES = {"Get a build", "Move and turn it", "Place it", "Build layer by layer", "Blocks and swapping", "Copy a build", "Good to know"};
    private static final String[][] ICONS = {
        {"minecraft:writable_book", "minecraft:chest", "minecraft:paper"}, {"minecraft:compass"}, {"minecraft:lodestone"},
        {"minecraft:oak_planks", "minecraft:cobblestone", "minecraft:stone_bricks"}, {"minecraft:blue_terracotta", "minecraft:cyan_terracotta"},
        {"minecraft:map"}, {"minecraft:command_block", "minecraft:clock"}};
    /** Each page: lines of text; a line starting with "key:" is a key hint "key:KEY:what it does". */
    private static final String[][] TEXT = {
        {"A build is what you want to make, shown as see-through ghost blocks in your world.",
         "Open the build menu, click Saved builds, pick one and press Load into world.",
         "Builds come from three places:",
         "- the free Block Blueprint app: make or pick a build and press Send to Minecraft",
         "- schematic files (.schem, .litematic, .nbt) in the BlockBlueprint folder in your user folder",
         "- builds you copy from your own world (see Copy a build)",
         "key:Page Up:open the build menu"},
        {"The build appears in front of you. Move it to where you want to build it.",
         "key:Arrow keys:move it (hold Shift for 5 blocks)",
         "key:Ctrl + Up / Down:move it up or down",
         "key:Page Down:turn it",
         "In the build menu, To ground sets it right on the ground and Mirror flips it."},
        {"When it's in the right spot, place it. A placed build is locked, so it can't slide away while you build.",
         "key:Delete:place it (press again to unlock and move it)",
         "Placing starts layer by layer building at the bottom layer."},
        {"Build from the bottom up. Only the layer you're on is shown, so it's easy to see what goes where.",
         "The next block to place glows. When a layer is finished you move up to the next one by itself.",
         "key:Home / End:go up or down a layer",
         "Blocks you placed correctly disappear from the ghost. A red outline means a different block is in the way."},
        {"The build menu lists every block the build needs: how many you have and how many are still to place.",
         "The small list in the corner shows the same while you build. Chests you opened nearby that hold blocks you need glow green.",
         "Don't have a block? Click it in the list and pick any other block in the game (blocks from other mods too).",
         "Every block of that kind in the build changes. Undo swap puts it back."},
        {"See a build you like in your world? Copy it and build it again anywhere.",
         "key:Insert:start a copy box on the block you look at",
         "key:Arrow keys:make the box bigger (Alt + arrows: smaller)",
         "key:Page Down:stretch the box to the block you look at",
         "key:Insert:save it (again)",
         "Copies go to Saved builds, and into the app too."},
        {"In Creative mode, Auto-build places the whole build for you and Build layer places one layer. Undo build takes it back.",
         "key:Middle-click:hold the block of the ghost you look at",
         "Settings lets you change how see-through the ghosts are, the corner list and more.",
         "Every key can be changed in Settings -> Key binds."}};

    public HelpScreen(Screen parent, int page) {
        super(Component.literal("How it works"));
        this.parent = parent; this.page = Math.max(0, Math.min(TITLES.length - 1, page));
    }

    @Override public boolean isPauseScreen() { return false; }

    private boolean wide() { return width >= 400; }
    private int pw() { return Math.min(480, width - 16); }
    private int px() { return (width - pw()) / 2; }
    private int navW() { return wide() ? 118 : 0; }

    @Override protected void init() {
        tabs.clear();
        int x = px(), w = pw();
        if (wide()) {
            for (int i = 0; i < TITLES.length; i++) {
                int p = i;
                tabs.add(addRenderableWidget(Button.builder(Component.literal((i + 1) + ". " + TITLES[i]), b -> go(p)).bounds(x, 34 + i * 22, navW() - 6, 20).build()));
            }
        }
        int by = height - 26, bw = 80;
        prev = addRenderableWidget(Button.builder(Component.literal("< Back"), b -> go(page - 1)).bounds(x + navW(), by, bw, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("Next >"), b -> go(page + 1)).bounds(x + navW() + bw + 4, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x + w - bw, by, bw, 20).build());
        go(page);
    }

    private void go(int p) {
        page = Math.max(0, Math.min(TITLES.length - 1, p));
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).active = i != page;
        if (prev != null) { prev.active = page > 0; next.active = page < TITLES.length - 1; }
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) { onClose(); return true; }
        if (e.key() == 262) { go(page + 1); return true; }   // right arrow
        if (e.key() == 263) { go(page - 1); return true; }   // left arrow
        return super.keyPressed(e);
    }
    @Override public void onClose() { Mc.setScreen(parent); }

    /** Word wrap with this font. */
    private List<String> wrap(String s, int w) {
        List<String> out = new ArrayList<>();
        String indent = s.startsWith("- ") ? "  " : "";
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = line.length() == 0 ? word : line + " " + word;
            if (font.width(t) > w && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(indent + word); }
            else line = new StringBuilder(t);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { Safe.run("the help screen", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = px(), w = pw();
        UiKit.panel(g, x - 6, 4, w + 12, height - 8, UiKit.CYAN);
        g.drawString(font, "How Build Blueprint works", x, 12, UiKit.GOLD, true);
        String pg = (page + 1) + " / " + TITLES.length;
        g.drawString(font, pg, x + w - font.width(pg), 12, UiKit.MUTED, false);
        if (!wide()) { g.drawString(font, TITLES[page], x, 24, UiKit.WHITE, true); }

        // the page
        int cx = x + navW(), cw = w - navW(), y = 34;
        g.fill(cx, y, cx + cw, height - 32, 0xFF15151C);
        g.fill(cx, y, cx + cw, y + 1, UiKit.CYAN);
        int ix = cx + 8;
        for (String id : ICONS[page]) {   // the page's picture: a few blocks / items in frames
            ItemStack st = Materials.stackFor(id);
            g.fill(ix, y + 8, ix + 22, y + 30, 0xFF2A2A36);
            if (!st.isEmpty()) g.renderItem(st, ix + 3, y + 11);
            ix += 26;
        }
        if (page == 4) { g.drawString(font, "->", cx + 8 + 22 + 1, y + 15, UiKit.GOLD, true); }
        g.drawString(font, TITLES[page], ix + 6, y + 15, UiKit.WHITE, true);
        drawPicture(g, cx + cw - 70, y + 6);
        int ty = y + 40, tw = cw - 16;
        for (String line : TEXT[page]) {
            if (ty > height - 46) break;
            if (line.startsWith("key:")) {
                String[] k = line.split(":", 3);
                UiKit.key(g, font, cx + 10, ty + 1, k[1], k[2]);
                ty += 15;
                continue;
            }
            for (String l : wrap(line, tw)) { g.drawString(font, l, cx + 8, ty, l.startsWith("-") || l.startsWith("  ") ? 0xFFCCCCCC : UiKit.TEXT, false); ty += 11; }
            ty += 4;
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    /** A tiny drawing in the top right of some pages. */
    private void drawPicture(GuiGraphics g, int x, int y) {
        switch (page) {
            case 1 -> {   // arrow keys
                UiKit.key(g, font, x + 24, y + 2, "^", "");
                UiKit.key(g, font, x + 8, y + 15, "<", "");
                UiKit.key(g, font, x + 24, y + 15, "v", "");
                UiKit.key(g, font, x + 40, y + 15, ">", "");
            }
            case 3 -> {   // three layers, the middle one being built
                int[] cols = {0x6088C0FF, 0xFF55E055, 0x4088C0FF};
                for (int i = 0; i < 3; i++) {
                    int ly = y + 22 - i * 8, lx = x + 6 + i * 6;
                    g.fill(lx, ly, lx + 46, ly + 6, i == 1 ? cols[1] : cols[i]);
                    g.fill(lx, ly, lx + 46, ly + 1, 0x80FFFFFF);
                }
            }
            case 5 -> {   // a copy box around a little house
                g.fill(x + 18, y + 14, x + 44, y + 28, 0xFF9A6B3A);
                g.fill(x + 14, y + 8, x + 48, y + 14, 0xFF7A4A2A);
                g.fill(x + 28, y + 20, x + 34, y + 28, 0xFF4A3018);
                int bx0 = x + 8, by0 = y + 2, bx1 = x + 54, by1 = y + 32, c = UiKit.CYAN;
                g.fill(bx0, by0, bx1, by0 + 1, c); g.fill(bx0, by1 - 1, bx1, by1, c);
                g.fill(bx0, by0, bx0 + 1, by1, c); g.fill(bx1 - 1, by0, bx1, by1, c);
            }
            default -> { }
        }
    }
}
