package dev.buildblueprint.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings, kept short: "Basics" has what most people change (how the ghost blocks look, the corner list, the helpers),
 * "More" has the fine-tuning, "Extras" has helpers that are off until you turn them on. Every button says what it does
 * when you hover it.
 */
public class SettingsScreen extends BBScreen {
    private final Screen parent;
    static boolean more = false;   // which page, remembered while the game runs
    static boolean extras = false; // the "Extras" page: helpers that are off until you turn them on
    static boolean colors = false; // the "Colors" page

    public SettingsScreen(Screen parent) { super(Component.literal(Tr.t("Build Blueprint settings"))); this.parent = parent; }

    @Override public boolean isPauseScreen() { return false; }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }
    private static int next(int v, int... opts) { for (int i = 0; i < opts.length; i++) if (opts[i] == v) return opts[(i + 1) % opts.length]; return opts[0]; }

    private record Opt(Supplier<String> label, Runnable click, String tip) {}
    private final List<Opt> opts = new ArrayList<>();
    private void opt(Supplier<String> label, Runnable click, String tip) { opts.add(new Opt(label, click, tip)); }

    private int colW() { return Math.min(170, (width - 40) / 2); }
    private int cols() { return width >= 380 ? 2 : 1; }
    private int top() { return 58; }

    @Override protected void init() {
        opts.clear();
        if (colors) {
            opt(() -> "Ghost color: " + Colors.name(Settings.ghostColor), () -> Settings.ghostColor = Colors.next(Settings.ghostColor, true),
                    "Tint the see-through ghost blocks. Normal keeps each block's own colors.");
            opt(() -> "Mistake boxes: " + Colors.name(Settings.redColor), () -> Settings.redColor = Colors.next(Settings.redColor, false),
                    "The color of the boxes on blocks that are in the way or placed the wrong way round.");
            opt(() -> "Next block box: " + Colors.name(Settings.hintColor), () -> Settings.hintColor = Colors.next(Settings.hintColor, false),
                    "The color of the box on the next block to place. It turns green while you hold that block.");
            opt(() -> "Chest boxes: " + Colors.name(Settings.chestColor), () -> Settings.chestColor = Colors.next(Settings.chestColor, false),
                    "The color of the boxes on chests that hold blocks you still need (Chest finder).");
            opt(() -> "Scaffold columns: " + Colors.name(Settings.scaffoldColor), () -> Settings.scaffoldColor = Colors.next(Settings.scaffoldColor, false),
                    "The color of the scaffold hint columns (Settings > Extras).");
            opt(() -> "Reset colors", () -> { Settings.ghostColor = -1; Settings.redColor = 0; Settings.hintColor = 2; Settings.chestColor = 3; Settings.scaffoldColor = 5; Mc.setScreen(new SettingsScreen(parent)); },
                    "Back to the mod's usual colors.");
        } else if (extras) {
            opt(() -> "Raw materials: " + onOff(Settings.rawMats), () -> Settings.rawMats = !Settings.rawMats,
                    "Adds \"Raw materials\" to the build menu: what to gather for the blocks you still need - logs for planks and stairs, sand for glass, cobblestone to smelt into stone...");
            opt(() -> "Layer map: " + onOff(Settings.layerMap), () -> Settings.layerMap = !Settings.layerMap,
                    "A small map of the layer you're on, seen from above, under the corner list. Colours = still to place, dark green = done, red = wrong, white dot = you.");
            opt(() -> "Scaffold hints: " + onOff(Settings.scaffold), () -> { Settings.scaffold = !Settings.scaffold; Materials.refresh(); },
                    "Parts of a layer that float in the air (overhangs, roofs) get a light blue column showing where to put temporary blocks to reach them.");
            opt(() -> "Blank world: " + onOff(Settings.blankWorld), () -> { Settings.blankWorld = !Settings.blankWorld; Materials.refresh(); },
                    "For building in an empty world: anything inside the build's area that shouldn't be there - stray blocks, water - gets a red box and counts as a mistake to fix, and Auto-build clears it.");
            opt(() -> "Nearby layers only: " + onOff(Settings.nearLayers), () -> Settings.nearLayers = !Settings.nearLayers,
                    "Before you place a build, only draw the layers around your height (3 below your feet to 4 above) instead of all of it. Lighter for big builds, and you can see inside.");
        } else if (!more) {
            opt(() -> "Ghost blocks: " + Settings.ghostAlpha + "% solid", () -> Settings.ghostAlpha = next(Settings.ghostAlpha, 25, 40, 55, 70, 85), "How see-through the preview blocks are.");
            opt(() -> "Show preview: " + onOff(BuildBlueprintClient.SESSION.visible), () -> BuildBlueprintClient.SESSION.visible = !BuildBlueprintClient.SESSION.visible, "Hide or show the ghost blocks.");
            opt(() -> "Corner list: " + onOff(Settings.hudShow), () -> Settings.hudShow = !Settings.hudShow, "The list of blocks you still need, shown in a corner while you build.");
            opt(() -> "Corner: " + (Settings.hudRight ? "top right" : "top left"), () -> Settings.hudRight = !Settings.hudRight, "Which top corner of the screen the list sits in.");
            opt(() -> "List size: " + Settings.hudScale + "%", () -> Settings.hudScale = next(Settings.hudScale, 50, 75, 100, 125, 150), "Make the corner list smaller or bigger.");
            opt(() -> "Next block: " + onOff(Settings.nextHint), () -> Settings.nextHint = !Settings.nextHint, "A yellow box shows which block to place next (bottom layer first, closest first).");
            opt(() -> "Auto next layer: " + onOff(Settings.autoLayer), () -> Settings.autoLayer = !Settings.autoLayer, "When you finish a layer, go to the next one by itself.");
            opt(() -> "Chest finder: " + onOff(Settings.chestFinder), () -> Settings.chestFinder = !Settings.chestFinder, "Green boxes on chests that hold blocks you still need. Open a chest once so the mod knows what's inside.");
            opt(() -> "Sounds: " + onOff(Settings.sounds), () -> Settings.sounds = !Settings.sounds, "A little ding when you finish a layer, and a level-up sound when the build is done.");
            opt(() -> "Auto-build speed: " + new String[]{"slow", "normal", "fast", "very fast", "max"}[Math.max(0, Math.min(4, Settings.autoSpeed))], () -> Settings.autoSpeed = (Settings.autoSpeed + 1) % 5,
                    "How fast Auto-build (Creative) places blocks. Faster can make the game - or the server - lag while it builds. Slow is kinder to small servers.");
        } else {
            opt(() -> "List shows: " + switch (Settings.matMode) { case "layer" -> "this layer"; case "all" -> "whole build"; default -> "auto"; },
                    () -> Settings.matMode = switch (Settings.matMode) { case "auto" -> "layer"; case "layer" -> "all"; default -> "auto"; },
                    "Auto: the layer you're on while building layer by layer, otherwise the whole build.");
            opt(() -> "Blocks in list: " + Settings.hudLines, () -> Settings.hudLines = next(Settings.hudLines, 3, 5, 8, 12, 16), "How many block types the corner list shows before \"+ more\".");
            opt(() -> "Progress bars: " + onOff(Settings.hudBar), () -> Settings.hudBar = !Settings.hudBar, "Green bar = whole build, blue bar = the layer you're on.");
            opt(() -> "Build timer: " + onOff(Settings.hudStats), () -> Settings.hudStats = !Settings.hudStats, "How long you've been building and how many blocks a minute.");
            opt(() -> "Key hints: " + onOff(Settings.hudHints), () -> Settings.hudHints = !Settings.hudHints, "The small reminder of the keys under the corner list.");
            opt(() -> "Draw distance: " + Settings.range, () -> Settings.range = next(Settings.range, 32, 48, 64, 96, 128, 192), "How far away ghost blocks are still drawn. Lower = faster.");
            opt(() -> "Red boxes: " + onOff(Settings.redOutline), () -> Settings.redOutline = !Settings.redOutline, "Red outline on blocks that are in the way of the build, or placed the wrong way round, and the \"to fix\" count in the build menu.");
            opt(() -> "Check direction: " + onOff(Settings.checkFacing), () -> { Settings.checkFacing = !Settings.checkFacing; dev.buildblueprint.core.Orient.on = Settings.checkFacing; Materials.refresh(); },
                    "Blocks only count as done when they face the right way: stairs, slabs (top or bottom), logs, doors, buttons... A block placed the wrong way round gets a red box.");
            opt(() -> "Pick from ghost: " + onOff(Settings.ghostPick), () -> Settings.ghostPick = !Settings.ghostPick, "Middle-click a ghost block to put that block in your hand.");
            opt(() -> "Build picture: " + onOff(Settings.preview3d), () -> Settings.preview3d = !Settings.preview3d, "The picture of a build in Saved builds. Turn it off if the menu gets slow.");
            opt(() -> "Placed: start at layer 1: " + onOff(Settings.startLayer1), () -> Settings.startLayer1 = !Settings.startLayer1, "A new build shows fully built so you can move it. When you place it, it switches to layer 1 so you can start building.");
        }
        int cols = cols(), cw = cols == 2 ? colW() : Math.min(260, width - 40), gap = 6;
        int x0 = width / 2 - (cols * cw + (cols - 1) * gap) / 2;
        for (int i = 0; i < opts.size(); i++) {
            Opt o = opts.get(i);
            int c = i % cols, r = i / cols;
            addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, o.label().get(), cw - 8)), b -> { o.click().run(); Settings.save(); b.setMessage(Component.literal(UiKit.fit(font, o.label().get(), cw - 8))); })
                    .bounds(x0 + c * (cw + gap), top() + r * 24, cw, 20).tooltip(Tooltip.create(Component.literal(Tr.t(o.tip())))).build());
        }
        // page tabs
        int tw = Math.min(90, (width - 28) / 4);
        int[] tx = UiKit.row(width / 2, 4, tw, 4);
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Basics", tw - 8)), b -> { more = false; extras = false; colors = false; Mc.setScreen(new SettingsScreen(parent)); }).bounds(tx[0], 30, tw, 20).build()).active = more || extras || colors;
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "More", tw - 8)), b -> { more = true; extras = false; colors = false; Mc.setScreen(new SettingsScreen(parent)); }).bounds(tx[1], 30, tw, 20).build()).active = !more || extras || colors;
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Extras", tw - 8)), b -> { extras = true; colors = false; Mc.setScreen(new SettingsScreen(parent)); }).bounds(tx[2], 30, tw, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Extra helpers, off until you turn them on: raw materials, layer map, scaffold hints, blank world, nearby layers only.")))).build()).active = !extras || colors;
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Colors", tw - 8)), b -> { colors = true; Mc.setScreen(new SettingsScreen(parent)); }).bounds(tx[3], 30, tw, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Pick the colors of the ghost blocks and the boxes the mod draws.")))).build()).active = !colors;
        // bottom
        Minecraft mc = Minecraft.getInstance();
        int by = height - 26;
        int bw = Math.min(104, (width - 28) / 4);
        int[] bx = UiKit.row(width / 2, 4, bw, 4);
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Key binds...", bw - 8)), b -> Mc.setScreen(V.keyBinds(this))).bounds(bx[0], by, bw, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Change the keys (Options > Controls > Key Binds > Build Blueprint).")))).build());
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "How it works", bw - 8)), b -> Mc.setScreen(new HelpScreen(this, 0))).bounds(bx[1], by, bw, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("A short guide to everything the mod does.")))).build());
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Report a bug", bw - 8)), b -> {
                    boolean opened = BugReport.send();
                    b.setMessage(Component.literal(UiKit.fit(font, opened ? "Page opened" : "Copied", bw - 8)));
                    reported = opened ? Tr.t("The details are filled in on the GitHub page (and copied). Add what happened, then press Create.") : Tr.t("Copied the details. Paste them at github.com/CosmicLogic0/block-blueprint/issues");
                }).bounds(bx[2], by, bw, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Opens a bug report on GitHub with your Minecraft version, mod version and the last problem already filled in. Nothing is sent until you press Create on the page.")))).build());
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Done", bw - 8)), b -> onClose()).bounds(bx[3], by, bw, 20).build());
    }

    private String reported;

    @Override public void onClose() { Settings.save(); Mc.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { Safe.run("the settings menu", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        UiKit.center(g, font, "Build Blueprint settings", width / 2, 12, UiKit.GOLD);
        int cols = cols(), cw = cols == 2 ? colW() : Math.min(260, width - 40), rows = (opts.size() + cols - 1) / cols;
        int w = cols * cw + (cols - 1) * 6 + 12;
        UiKit.panel(g, width / 2 - w / 2, top() - 6, w, rows * 24 + 8, colors ? UiKit.CYAN : extras ? UiKit.GREEN : more ? UiKit.BLUE : UiKit.GOLD);
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (reported != null) UiKit.center(g, font, UiKit.fit(font, reported, width - 16), width / 2, height - 40, UiKit.GREEN);
        else UiKit.center(g, font, "Hover a button to see what it does.", width / 2, height - 40, UiKit.DIM);
    }
}
