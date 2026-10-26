package dev.buildblueprint.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/**
 * Settings, kept short: "Basics" has what most people change (how the ghost blocks look, the corner list, the helpers),
 * "More" has the fine-tuning. Every button says what it does when you hover it.
 */
public class SettingsScreen extends Screen {
    private final Screen parent;
    static boolean more = false;   // which page, remembered while the game runs

    public SettingsScreen(Screen parent) { super(Component.literal("Build Blueprint settings")); this.parent = parent; }

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
        if (!more) {
            opt(() -> "Ghost blocks: " + Settings.ghostAlpha + "% solid", () -> Settings.ghostAlpha = next(Settings.ghostAlpha, 25, 40, 55, 70, 85), "How see-through the preview blocks are.");
            opt(() -> "Show preview: " + onOff(BuildBlueprintClient.SESSION.visible), () -> BuildBlueprintClient.SESSION.visible = !BuildBlueprintClient.SESSION.visible, "Hide or show the ghost blocks.");
            opt(() -> "Corner list: " + onOff(Settings.hudShow), () -> Settings.hudShow = !Settings.hudShow, "The list of blocks you still need, shown in a corner while you build.");
            opt(() -> "Corner: " + (Settings.hudRight ? "top right" : "top left"), () -> Settings.hudRight = !Settings.hudRight, "Which top corner of the screen the list sits in.");
            opt(() -> "List size: " + Settings.hudScale + "%", () -> Settings.hudScale = next(Settings.hudScale, 50, 75, 100, 125, 150), "Make the corner list smaller or bigger.");
            opt(() -> "Next block: " + onOff(Settings.nextHint), () -> Settings.nextHint = !Settings.nextHint, "A yellow box shows which block to place next (bottom layer first, closest first).");
            opt(() -> "Auto next layer: " + onOff(Settings.autoLayer), () -> Settings.autoLayer = !Settings.autoLayer, "When you finish a layer, go to the next one by itself.");
            opt(() -> "Chest finder: " + onOff(Settings.chestFinder), () -> Settings.chestFinder = !Settings.chestFinder, "Green boxes on chests that hold blocks you still need. Open a chest once so the mod knows what's inside.");
            opt(() -> "Sounds: " + onOff(Settings.sounds), () -> Settings.sounds = !Settings.sounds, "A little ding when you finish a layer, and a level-up sound when the build is done.");
        } else {
            opt(() -> "List shows: " + switch (Settings.matMode) { case "layer" -> "this layer"; case "all" -> "whole build"; default -> "auto"; },
                    () -> Settings.matMode = switch (Settings.matMode) { case "auto" -> "layer"; case "layer" -> "all"; default -> "auto"; },
                    "Auto: the layer you're on while building layer by layer, otherwise the whole build.");
            opt(() -> "Blocks in list: " + Settings.hudLines, () -> Settings.hudLines = next(Settings.hudLines, 3, 5, 8, 12, 16), "How many block types the corner list shows before \"+ more\".");
            opt(() -> "Progress bars: " + onOff(Settings.hudBar), () -> Settings.hudBar = !Settings.hudBar, "Green bar = whole build, blue bar = the layer you're on.");
            opt(() -> "Build timer: " + onOff(Settings.hudStats), () -> Settings.hudStats = !Settings.hudStats, "How long you've been building and how many blocks a minute.");
            opt(() -> "Key hints: " + onOff(Settings.hudHints), () -> Settings.hudHints = !Settings.hudHints, "The small reminder of the keys under the corner list.");
            opt(() -> "Draw distance: " + Settings.range, () -> Settings.range = next(Settings.range, 32, 48, 64, 96, 128, 192), "How far away ghost blocks are still drawn. Lower = faster.");
            opt(() -> "Red boxes: " + onOff(Settings.redOutline), () -> Settings.redOutline = !Settings.redOutline, "Red outline on blocks that are in the way of the build, or placed the wrong way round.");
            opt(() -> "Check direction: " + onOff(Settings.checkFacing), () -> { Settings.checkFacing = !Settings.checkFacing; dev.buildblueprint.core.Orient.on = Settings.checkFacing; Materials.refresh(); },
                    "Blocks only count as done when they face the right way: stairs, slabs (top or bottom), logs, doors, buttons... A block placed the wrong way round gets a red box.");
            opt(() -> "Pick from ghost: " + onOff(Settings.ghostPick), () -> Settings.ghostPick = !Settings.ghostPick, "Middle-click a ghost block to put that block in your hand.");
            opt(() -> "Placed: start at layer 1: " + onOff(Settings.startLayer1), () -> Settings.startLayer1 = !Settings.startLayer1, "A new build shows fully built so you can move it. When you place it, it switches to layer 1 so you can start building.");
        }
        int cols = cols(), cw = cols == 2 ? colW() : Math.min(260, width - 40), gap = 6;
        int x0 = width / 2 - (cols * cw + (cols - 1) * gap) / 2;
        for (int i = 0; i < opts.size(); i++) {
            Opt o = opts.get(i);
            int c = i % cols, r = i / cols;
            addRenderableWidget(Button.builder(Component.literal(o.label().get()), b -> { o.click().run(); Settings.save(); b.setMessage(Component.literal(o.label().get())); })
                    .bounds(x0 + c * (cw + gap), top() + r * 24, cw, 20).tooltip(Tooltip.create(Component.literal(o.tip()))).build());
        }
        // page tabs
        int tw = 90;
        addRenderableWidget(Button.builder(Component.literal("Basics"), b -> { more = false; Mc.setScreen(new SettingsScreen(parent)); }).bounds(width / 2 - tw - 2, 30, tw, 20).build()).active = more;
        addRenderableWidget(Button.builder(Component.literal("More"), b -> { more = true; Mc.setScreen(new SettingsScreen(parent)); }).bounds(width / 2 + 2, 30, tw, 20).build()).active = !more;
        // bottom
        Minecraft mc = Minecraft.getInstance();
        int by = height - 26;
        int bw = Math.min(110, (width - 24) / 3);
        int[] bx = UiKit.row(width / 2, 3, bw, 4);
        addRenderableWidget(Button.builder(Component.literal("Key binds..."), b -> Mc.setScreen(new KeyBindsScreen(this, mc.options))).bounds(bx[0], by, bw, 20)
                .tooltip(Tooltip.create(Component.literal("Change the keys (Options > Controls > Key Binds > Build Blueprint)."))).build());
        addRenderableWidget(Button.builder(Component.literal("How it works"), b -> Mc.setScreen(new HelpScreen(this, 0))).bounds(bx[1], by, bw, 20)
                .tooltip(Tooltip.create(Component.literal("A short guide to everything the mod does."))).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(bx[2], by, bw, 20).build());
    }

    @Override public void onClose() { Settings.save(); Mc.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { Safe.run("the settings menu", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        UiKit.center(g, font, "Build Blueprint settings", width / 2, 12, UiKit.GOLD);
        int cols = cols(), cw = cols == 2 ? colW() : Math.min(260, width - 40), rows = (opts.size() + cols - 1) / cols;
        int w = cols * cw + (cols - 1) * 6 + 12;
        UiKit.panel(g, width / 2 - w / 2, top() - 6, w, rows * 24 + 8, more ? UiKit.BLUE : UiKit.GOLD);
        super.extractRenderState(g, mouseX, mouseY, delta);
        UiKit.center(g, font, "Hover a button to see what it does.", width / 2, height - 40, UiKit.DIM);
    }
}
