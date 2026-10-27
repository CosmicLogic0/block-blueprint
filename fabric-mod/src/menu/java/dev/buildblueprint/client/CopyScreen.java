package dev.buildblueprint.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Opens when you press Insert the second time: name the copied build, choose what to leave out, and save it. */
public class CopyScreen extends Screen {
    private EditBox name;
    private int blocks = -1;
    private Button groundBtn, treesBtn;

    public CopyScreen() { super(Component.literal(Tr.t("Copy build"))); }

    @Override public boolean isPauseScreen() { return false; }

    private void recount() { var bp = Copier.capture("x"); blocks = bp == null ? 0 : bp.blockCount(); }

    @Override protected void init() {
        int cx = width / 2, y = height / 2 - 40;
        String keep = name == null ? "My copied build" : name.getValue();
        name = new EditBox(font, cx - 110, y, 220, 20, Component.literal(Tr.t("Name")));
        name.setMaxLength(40);
        name.setValue(keep);
        addRenderableWidget(name);
        setInitialFocus(name);
        groundBtn = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> { Copier.skipGround = !Copier.skipGround; label(); recount(); })
                .bounds(cx - 110, y + 26, 108, 20).tooltip(Tooltip.create(Component.literal(Tr.t("Leave out the dirt, grass and natural stone the build stands on.")))).build());
        treesBtn = addRenderableWidget(Button.builder(Component.literal(Tr.t("")), b -> { Copier.skipTrees = !Copier.skipTrees; label(); recount(); })
                .bounds(cx + 2, y + 26, 108, 20).tooltip(Tooltip.create(Component.literal(Tr.t("Leave out trees, leaves, grass and flowers around the build.")))).build());
        label();
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Save build", 108 - 8)), b -> save()).bounds(cx - 110, y + 52, 108, 20).build());
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Adjust the box", 108 - 8)), b -> onClose()).bounds(cx + 2, y + 52, 108, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Go back and change the box: arrows grow it, Alt + arrows shrink it, Page Down stretches it to where you look. Then press Insert again.")))).build());
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Cancel copy", 108 - 8)), b -> { Copier.cancel(); onClose(); }).bounds(cx - 54, y + 78, 108, 20).build());
        if (blocks < 0) recount();
    }

    private void label() {
        groundBtn.setMessage(Component.literal(UiKit.fit(font, "Ground: " + (Copier.skipGround ? "left out" : "kept"), 100)));
        treesBtn.setMessage(Component.literal(UiKit.fit(font, "Trees: " + (Copier.skipTrees ? "left out" : "kept"), 100)));
    }

    private void save() {
        SavedBuilds.Entry e = Copier.save(name.getValue());
        if (e != null) Mc.setScreen(new SavedBuildsScreen().select(e.file()));
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 257 || e.key() == 335) { save(); return true; }   // Enter
        return super.keyPressed(e);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { Safe.run("the copy screen", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int cx = width / 2, y = height / 2 - 40;
        UiKit.panel(g, cx - 124, y - 52, 248, 158, UiKit.CYAN);
        UiKit.center(g, font, "Save this build", cx, y - 44, UiKit.GOLD);
        int[] s = Copier.size();
        String info = s[0] + " x " + s[1] + " x " + s[2] + " box  ·  " + (blocks < 0 ? "?" : blocks) + " blocks";
        g.text(font, Tr.t(info), cx - font.width(Tr.t(info)) / 2, y - 30, blocks == 0 ? 0xFFFF6060 : 0xFFDDDDDD, true);
        g.text(font, Tr.t("Name:"), cx - 110, y - 12, 0xFFAAAAAA, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
        String h = blocks == 0 ? "Nothing left in the box - adjust it or keep the ground." : "Saves to Saved builds and copies its code to your clipboard.";
        g.text(font, Tr.t(h), cx - font.width(Tr.t(h)) / 2, y + 112, 0xFF999999, true);
    }
}
