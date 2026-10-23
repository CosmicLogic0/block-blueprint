package dev.buildblueprint.client;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/** Build Blueprint settings in three panels: the corner list, the preview & layers, and the building helpers. */
public class SettingsScreen extends BBScreen {
    private final Screen parent;

    public SettingsScreen(Screen parent) { super(Component.literal("Build Blueprint settings")); this.parent = parent; }

    @Override public boolean isPauseScreen() { return false; }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }
    private static int next(int v, int... opts) { for (int i = 0; i < opts.length; i++) if (opts[i] == v) return opts[(i + 1) % opts.length]; return opts[0]; }

    private static final String[] HEADS = {"Corner list", "Preview & layers", "Helpers"};
    private static final int[] HEAD_COL = {0xFFFFD75A, 0xFF7FC8FF, 0xFF7CE07C};

    private int colW() { return Math.min(150, (width - 40) / 3); }
    private int colX(int c) { int w = colW(), total = w * 3 + 16; return width / 2 - total / 2 + c * (w + 8); }
    private int rowY(int r) { return 52 + r * 23; }

    private void opt(int col, int row, Supplier<String> label, Runnable click, String tip) {
        addRenderableWidget(Button.builder(Component.literal(label.get()), b -> { click.run(); Settings.save(); b.setMessage(Component.literal(label.get())); })
                .bounds(colX(col), rowY(row), colW(), 20).tooltip(Tooltip.create(Component.literal(tip))).build());
    }

    @Override protected void init() {
        opt(0, 0, () -> "Corner list: " + onOff(Settings.hudShow), () -> Settings.hudShow = !Settings.hudShow, "The list of blocks you still need, shown while you build.");
        opt(0, 1, () -> "Corner: " + (Settings.hudRight ? "Top right" : "Top left"), () -> Settings.hudRight = !Settings.hudRight, "Which top corner of the screen the list sits in.");
        opt(0, 2, () -> "Size: " + Settings.hudScale + "%", () -> Settings.hudScale = next(Settings.hudScale, 50, 75, 100, 125, 150), "Make the corner list smaller or bigger.");
        opt(0, 3, () -> "Blocks shown: " + Settings.hudLines, () -> Settings.hudLines = next(Settings.hudLines, 3, 5, 8, 12, 16), "How many block types the list shows before \"+ more\".");
        opt(0, 4, () -> "Progress bars: " + onOff(Settings.hudBar), () -> Settings.hudBar = !Settings.hudBar, "Green bar = whole build, blue bar = the layer you're on.");
        opt(0, 5, () -> "Build timer: " + onOff(Settings.hudStats), () -> Settings.hudStats = !Settings.hudStats, "Shows how long you've been building and how many blocks a minute.");
        opt(0, 6, () -> "Key hints: " + onOff(Settings.hudHints), () -> Settings.hudHints = !Settings.hudHints, "The small reminder of the keys at the bottom of the list.");
        opt(0, 7, () -> "Shows: " + switch (Settings.matMode) { case "layer" -> "This layer"; case "all" -> "Whole build"; default -> "Auto"; },
                () -> Settings.matMode = switch (Settings.matMode) { case "auto" -> "layer"; case "layer" -> "all"; default -> "auto"; },
                "Auto: the layer you're on while building layer by layer, otherwise the whole build.");

        opt(1, 0, () -> "Ghosts: " + Settings.ghostAlpha + "% solid", () -> Settings.ghostAlpha = next(Settings.ghostAlpha, 25, 40, 55, 70, 85), "How see-through the preview blocks are.");
        opt(1, 1, () -> "Distance: " + Settings.range + " blocks", () -> Settings.range = next(Settings.range, 32, 48, 64, 96, 128, 192), "How far away preview blocks are still drawn. Lower = faster.");
        opt(1, 2, () -> "Red boxes: " + onOff(Settings.redOutline), () -> Settings.redOutline = !Settings.redOutline, "Red outline on blocks that are in the way of the build.");
        opt(1, 3, () -> "Auto next layer: " + onOff(Settings.autoLayer), () -> Settings.autoLayer = !Settings.autoLayer, "When you finish a layer, go to the next one by itself.");
        opt(1, 4, () -> "Placed: go to layer 1: " + onOff(Settings.startLayer1), () -> Settings.startLayer1 = !Settings.startLayer1, "A new build shows fully built so you can move it. When you place it (Delete key), it switches to layer 1 so you can start building.");
        opt(1, 5, () -> "Show preview: " + onOff(BuildBlueprintClient.SESSION.visible), () -> BuildBlueprintClient.SESSION.visible = !BuildBlueprintClient.SESSION.visible, "Hide or show the ghost blocks.");

        opt(2, 0, () -> "Next block: " + onOff(Settings.nextHint), () -> Settings.nextHint = !Settings.nextHint, "A yellow box shows which block to place next (bottom layer first, closest first).");
        opt(2, 1, () -> "Pick from ghost: " + onOff(Settings.ghostPick), () -> Settings.ghostPick = !Settings.ghostPick, "Middle-click a ghost block to put that block in your hand.");
        opt(2, 2, () -> "Chest finder: " + onOff(Settings.chestFinder), () -> Settings.chestFinder = !Settings.chestFinder, "Green boxes on chests that hold blocks you still need. Open a chest once so the mod knows what's inside.");
        opt(2, 3, () -> "Sounds: " + onOff(Settings.sounds), () -> Settings.sounds = !Settings.sounds, "A little ding when you finish a layer, and a level-up sound when the build is done.");
        Minecraft mc = Minecraft.getInstance();
        addRenderableWidget(Button.builder(Component.literal("Key binds..."), b -> Mc.setScreen(new KeyBindsScreen(this, mc.options)))
                .bounds(colX(2), rowY(5), colW(), 20).tooltip(Tooltip.create(Component.literal("Change the keys (Options > Controls > Key Binds > Build Blueprint)."))).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 75, rowY(8) + 14, 150, 20).build());
    }

    @Override public void onClose() { Settings.save(); Mc.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { Safe.run("the settings menu", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        String t = "Build Blueprint settings";
        g.drawString(font, t, width / 2 - font.width(t) / 2, 14, 0xFFFFFFFF, true);
        int[] rows = {8, 6, 6};
        for (int c = 0; c < 3; c++) {
            int x = colX(c), w = colW(), top = 34, bot = rowY(rows[c]) + 2;
            g.fill(x - 4, top, x + w + 4, bot, 0xC0101018);
            g.fill(x - 4, top, x + w + 4, top + 1, HEAD_COL[c]);
            g.drawString(font, HEADS[c], x + w / 2 - font.width(HEADS[c]) / 2, top + 5, HEAD_COL[c], true);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
        String note = "Hover a button to see what it does.";
        g.drawString(font, note, width / 2 - font.width(note) / 2, height - 14, 0xFF888888, false);
    }
}
