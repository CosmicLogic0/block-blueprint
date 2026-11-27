package dev.buildblueprint.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * "How it works": eight short pages with a small picture each, one idea per page. Opened with the i button in the build
 * menu, the swap screen and Settings. Left: the page list. Right: the page. Bottom: Back / Next / Done.
 */
public class HelpScreen extends Screen {
    private final Screen parent;
    private int page;
    private Button prev, next;
    private final List<Button> tabs = new ArrayList<>();

    private static final String[] TITLES = {"Get a build", "Move and turn", "Place it", "Build layers", "Blocks and swaps", "Copy a build", "Good to know", "More helpers", "All shortcuts"};
    private static final String[][] ICONS = {
        {"minecraft:writable_book", "minecraft:chest", "minecraft:paper"}, {"minecraft:compass"}, {"minecraft:lodestone"},
        {"minecraft:oak_planks", "minecraft:cobblestone", "minecraft:stone_bricks"}, {"minecraft:blue_terracotta", "minecraft:cyan_terracotta"},
        {"minecraft:map"}, {"minecraft:command_block", "minecraft:clock"}, {"minecraft:bell", "minecraft:compass"}, {"minecraft:tripwire_hook", "minecraft:oak_sign"}};
    /** Each page: lines of text; a line starting with "key:" is a key hint "key:KEY:what it does". */
    private static final String[][] TEXT = {
        {"A build is what you want to make, shown as see-through ghost blocks in your world.",
         "Open the build menu, click Saved builds, pick one and press Load into world.",
         "Builds come from three places:",
         "- the free Block Blueprint app: make or pick a build and press Send to Minecraft",
         "- schematic files (.schem, .litematic, .nbt, old .schematic, Bedrock .mcstructure): drop them on the game window in Saved builds, paste a link to one, or put them in the BlockBlueprint folder in your user folder",
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
         "Blocks you placed correctly disappear from the ghost. A red outline means a different block is in the way, or the block is the wrong way round (stairs upside down, a log on its side) - break it and place it again."},
        {"The build menu lists every block the build needs: how many you have and how many are still to place.",
         "The small list in the corner shows the same while you build. Chests you opened nearby that hold blocks you need glow green.",
         "Don't have a block? Click it in the list and pick any other block in the game (blocks from other mods too).",
         "Every block of that kind in the build changes. Don't want a block at all (snow on a roof)? Press Remove from build.",
         "Undo swap puts it back.",
         "The eye next to a block hides its ghost blocks (click again to show them). Shift-click the eye to see only that block."},
        {"See a build you like in your world? Copy it and build it again anywhere.",
         "key:Insert:start a copy box on the block you look at",
         "key:Arrow keys:make the box bigger (Alt + arrows: smaller)",
         "key:Ctrl + Up / Down:make the box taller or shorter",
         "key:Page Down:stretch the box to the block you look at",
         "key:Insert:save it (again)",
         "Copies go to Saved builds, and into the app too."},
        {"In Creative mode, Auto-build places the whole build for you and Build layer places one layer. Undo build takes it back.",
         "key:Middle-click:hold the block of the ghost you look at",
         "Settings lets you change how see-through the ghosts are, the corner list and more.",
         "Every key can be changed in Settings -> Key binds (or Options > Controls > Key Binds > Build Blueprint)."},
        {"Build with friends: Co-op in the build menu gives you a code for your friends. The build appears in the same spot for them, and each of you builds your own part.",
         "Something placed wrong? The build menu shows how many to fix at the top. Click it to be shown each one, and click Stop fixing to stop.",
         "Saved builds > Get free builds has 1,300+ free builds from the Minecraft community.",
         "Settings > Extras has more helpers you can turn on: raw materials, a map of the layer you're on, and scaffold hints.",
         "The mod follows your game language: English, Spanish, Portuguese, German, French, Russian and Chinese.",
         "Holding a block lights up the spots near you that still need it (Settings: Spots for held block).",
         "Run out of a block while building? Auto-swap puts the next block the build needs in your hand (Settings > Extras).",
         "Want to play with the person who made this mod? Logic Survival is their survival server. It was added to the end of your Multiplayer list once, the first time the mod started. Delete it there if you don't want it - it won't come back. The button below adds it again."},
        {"Every Build Blueprint key, as they are set now:",
         "key:Page Up:open the build menu",
         "key:Arrow keys:move the build (hold Shift for 5 blocks)",
         "key:Ctrl + Up / Down:move it up or down",
         "key:Page Down:turn it",
         "key:Delete:place it / unlock it",
         "key:Home / End:go up or down a layer",
         "key:Insert:start or save a copy box",
         "key:Alt + arrows:make the copy box smaller",
         "key:Middle-click:hold the block of the ghost you look at",
         "Change any of them in Settings > Key binds."}};
    private static final int SERVER_PAGE = 7;   // the page with the "Add Logic Survival" button
    /** The first-time tour: these pages one after another, with Skip tour on every step. */
    private static final int[] TOUR = {0, 1, 2, 3, 4, 8};
    private final boolean tour;
    private int step;
    private Button done, again;
    private Button server;
    private String serverMsg;

    public HelpScreen(Screen parent, int page) { this(parent, page, false); }

    /** tour = the first-time tour: a few pages in order with Skip tour, and a chat line when it ends. */
    public HelpScreen(Screen parent, int page, boolean tour) {
        super(Component.literal(Tr.t(tour ? "Quick tour" : "How it works")));
        this.parent = parent; this.tour = tour; this.page = tour ? TOUR[0] : Math.max(0, Math.min(TITLES.length - 1, page));
    }

    /** Called by the build menu: the first time it opens, show the tour once. */
    public static boolean tourFirstTime(Screen menu) {
        if (Settings.tourDone) return false;
        Settings.tourDone = true;
        Settings.save();
        Mc.setScreen(new HelpScreen(menu, 0, true));
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }

    private boolean wide() { return width >= 400; }
    private int pw() { return Math.min(480, width - 16); }
    private int px() { return (width - pw()) / 2; }
    private int navW() { return wide() && !tour ? 128 : 0; }

    @Override protected void init() {
        tabs.clear();
        int x = px(), w = pw();
        if (navW() > 0) {
            for (int i = 0; i < TITLES.length; i++) {
                int p = i;
                tabs.add(addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, (i + 1) + ". " + Tr.t(TITLES[i]), navW() - 14)), b -> go(p)).bounds(x, 34 + i * 22, navW() - 6, 20).build()));
            }
        }
        int by = height - 26, bw = 80;
        prev = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "< Back", bw - 8)), b -> move(-1)).bounds(x + navW(), by, bw, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Next >", bw - 8)), b -> move(1)).bounds(x + navW() + bw + 4, by, bw, 20).build());
        done = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, tour ? "Skip tour" : "Done", bw - 8)), b -> onClose()).bounds(x + w - bw, by, bw, 20)
                .tooltip(tour ? Tooltip.create(Component.literal(Tr.t("End the tour. You can take it again any time: How it works > Take the tour."))) : null).build());
        int tw = Math.min(160, w - navW() - 16);
        again = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Take the tour", tw - 8)), b -> Mc.setScreen(new HelpScreen(parent, 0, true)))
                .bounds(x + navW() + 8, height - 58, tw, 20).build());
        boolean has = CreatorServer.added();
        int sw = Math.min(230, w - navW() - 16);
        server = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, has ? "Logic Survival is in your servers" : "Add Logic Survival to my servers", sw - 8)), b -> {
                    serverMsg = CreatorServer.add();
                    b.setMessage(Component.literal(UiKit.fit(font, "Logic Survival is in your servers", sw - 8)));
                    b.active = false;
                }).bounds(x + navW() + 8, height - 58, sw, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Adds " + CreatorServer.IP + " to Multiplayer. You can delete it there any time.")))).build());
        server.active = !has;
        go(page);
    }

    /** Back / Next: the next page, or in the tour the next step. */
    private void move(int d) {
        if (!tour) { go(page + d); return; }
        step = Math.max(0, Math.min(TOUR.length - 1, step + d));
        go(TOUR[step]);
    }

    private void go(int p) {
        page = Math.max(0, Math.min(TITLES.length - 1, p));
        if (tour) {
            if (prev != null) { prev.active = step > 0; next.active = step < TOUR.length - 1; }
            if (done != null) done.setMessage(Component.literal(UiKit.fit(font, step == TOUR.length - 1 ? "Finish" : "Skip tour", 72)));
            if (server != null) server.visible = false;
            if (again != null) again.visible = false;
            return;
        }
        if (again != null) again.visible = page == 0;
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).active = i != page;
        if (prev != null) { prev.active = page > 0; next.active = page < TITLES.length - 1; }
        if (server != null) server.visible = page == SERVER_PAGE;
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) { onClose(); return true; }
        if (e.key() == 262) { move(1); return true; }   // right arrow
        if (e.key() == 263) { move(-1); return true; }   // left arrow
        return super.keyPressed(e);
    }
    @Override public void onClose() {
        if (tour) Chat.say(net.minecraft.ChatFormatting.AQUA, step == TOUR.length - 1
                ? Tr.t("Tour done! Press Page Up to open the build menu. Take the tour again any time: How it works > Take the tour.")
                : Tr.t("Tour skipped. Take it any time: build menu > i (How it works) > Take the tour."));
        Mc.setScreen(parent);
    }

    /** Word wrap with this font. */
    private List<String> wrap(String s, int w) {
        List<String> out = new ArrayList<>();
        String indent = s.startsWith("- ") ? "  " : "";
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = line.length() == 0 ? word : line + " " + word;
            if (font.width(Tr.t(t)) > w && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(indent + word); }
            else line = new StringBuilder(t);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { Safe.run("the help screen", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = px(), w = pw();
        UiKit.panel(g, x - 6, 4, w + 12, height - 8, UiKit.CYAN);
        g.text(font, Tr.t(tour ? "Quick tour of Build Blueprint" : "How Build Blueprint works"), x, 12, UiKit.GOLD, true);
        String pg = tour ? (step + 1) + " / " + TOUR.length : (page + 1) + " / " + TITLES.length;
        g.text(font, Tr.t(pg), x + w - font.width(Tr.t(pg)), 12, UiKit.MUTED, false);
        if (navW() == 0 && !tour) { g.text(font, Tr.t(TITLES[page]), x, 24, UiKit.WHITE, true); }

        // the page
        int cx = x + navW(), cw = w - navW(), y = 34;
        g.fill(cx, y, cx + cw, height - 32, 0xFF15151C);
        g.fill(cx, y, cx + cw, y + 1, UiKit.CYAN);
        int ix = cx + 8;
        for (String id : ICONS[page]) {   // the page's picture: a few blocks / items in frames
            ItemStack st = Materials.stackFor(id);
            g.fill(ix, y + 8, ix + 22, y + 30, 0xFF2A2A36);
            if (!st.isEmpty()) g.item(st, ix + 3, y + 11);
            ix += 26;
        }
        if (page == 4) { g.text(font, Tr.t("->"), cx + 8 + 22 + 1, y + 15, UiKit.GOLD, true); }
        g.text(font, Tr.t(TITLES[page]), ix + 6, y + 15, UiKit.WHITE, true);
        drawPicture(g, cx + cw - 70, y + 6);
        int ty = y + 40, tw = cw - 16;
        for (String line : TEXT[page]) {
            if (ty > (page == SERVER_PAGE || (page == 0 && !tour) ? height - 70 : height - 46)) break;
            if (line.startsWith("key:")) {
                String[] k = line.split(":", 3);
                UiKit.key(g, font, cx + 10, ty + 1, k[1], k[2]);
                ty += 15;
                continue;
            }
            for (String l : wrap(Tr.t(line), tw)) { g.text(font, Tr.t(l), cx + 8, ty, l.startsWith("-") || l.startsWith("  ") ? 0xFFCCCCCC : UiKit.TEXT, false); ty += 11; }
            ty += 4;
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (page == SERVER_PAGE && serverMsg != null) g.text(font, Tr.t(UiKit.fit(font, serverMsg, cw - 16)), cx + 8, height - 68, UiKit.GREEN, true);
    }

    /** A tiny drawing in the top right of some pages. */
    private void drawPicture(GuiGraphicsExtractor g, int x, int y) {
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
