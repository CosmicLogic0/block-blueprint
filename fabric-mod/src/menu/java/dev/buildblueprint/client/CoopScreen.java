package dev.buildblueprint.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Co-op: build one blueprint together. Copy a code for your friends (it puts the build in this exact spot for them),
 * then everyone picks how many builders there are and which one they are. Each player only sees and counts their own part.
 */
public class CoopScreen extends Screen {
    private final Screen parent;
    private String msg = "";
    private Button builders, me;

    public CoopScreen(Screen parent) { super(Component.literal(Tr.t("Build with friends"))); this.parent = parent; }

    @Override public boolean isPauseScreen() { return false; }

    private int pw() { return Math.min(340, width - 20); }
    private int px() { return (width - pw()) / 2; }

    @Override protected void init() {
        int x = px(), w = pw(), y = 50;
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Copy co-op code", w - 8)), b -> copy()).bounds(x, y + 22, w, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Send this code to your friends. They paste it in Saved builds > Paste, and the build appears in this exact spot for them too.")))).build());
        int half = (w - 4) / 2;
        builders = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> { int p = Coop.parts >= 4 ? 1 : Coop.parts + 1; Coop.set(p, Math.min(Coop.me, p)); after(); })
                .bounds(x, y + 82, half, 20).tooltip(Tooltip.create(Component.literal(Tr.t("How many people build it. Everyone should pick the same number.")))).build());
        me = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> { Coop.set(Coop.parts, Coop.me >= Coop.parts ? 1 : Coop.me + 1); after(); })
                .bounds(x + half + 4, y + 82, w - half - 4, 20).tooltip(Tooltip.create(Component.literal(Tr.t("Which builder you are. Each friend picks a different number.")))).build());
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Done", 100 - 8)), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
        labels();
    }

    private void labels() {
        builders.setMessage(Component.literal(Tr.t("Builders: " + (Coop.on() ? String.valueOf(Coop.parts) : "just me"))));
        me.setMessage(Component.literal(Tr.t(Coop.on() ? "I'm builder " + Coop.me : "I'm builder -")));
        me.active = Coop.on();
    }
    private void after() { labels(); Materials.refresh(); SessionStore.dirty(); }

    private void copy() {
        var s = BuildBlueprintClient.SESSION;
        if (!s.loaded()) { msg = "Load a build first."; return; }
        if (!s.locked) { msg = "Place the build first (Delete), so friends get it in the same spot."; return; }
        String code = "BBCOOP:" + s.ox + "," + s.oy + "," + s.oz + "," + Math.max(1, Coop.parts) + " " + Copier.appCode(s.blueprint());
        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(code);
        msg = "Copied! Send it to your friends.";
    }

    /** Paste side: "BBCOOP:x,y,z,parts BB1:..." -> {x, y, z, parts}, or null if it isn't a co-op code. */
    static int[] parseHeader(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("BBCOOP:(-?\\d+),(-?\\d+),(-?\\d+),(\\d+)").matcher(text);
        if (!m.find()) return null;
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))};
    }

    @Override public void onClose() { Mc.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { Safe.run("the co-op screen", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = px(), w = pw(), y = 50;
        UiKit.panel(g, x - 8, 14, w + 16, 168, UiKit.CYAN);
        g.text(font, Tr.t("Build with friends"), x, 22, UiKit.GOLD, true);
        g.text(font, Tr.t("1. Send them the build. It appears in this exact spot."), x, y + 8, UiKit.TEXT, false);
        g.text(font, Tr.t("2. Split the work. Everyone picks the same number of"), x, y + 52, UiKit.TEXT, false);
        g.text(font, Tr.t("   builders, and a different builder number."), x, y + 64, UiKit.TEXT, false);
        var s = BuildBlueprintClient.SESSION;
        String part = Coop.on() && s.loaded() ? "You build " + Coop.where(s.blueprint()) + ". You only see your part." : "Everyone sees the whole build.";
        g.text(font, Tr.t(UiKit.fit(font, part, w)), x, y + 110, UiKit.CYAN, false);
        if (!msg.isEmpty()) UiKit.center(g, font, msg, width / 2, y + 126, msg.startsWith("Copied") ? UiKit.GREEN : UiKit.ORANGE);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
}
