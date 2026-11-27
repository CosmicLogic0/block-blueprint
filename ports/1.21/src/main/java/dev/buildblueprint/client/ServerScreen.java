package dev.buildblueprint.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Build with friends > Invite & join on this server (only when the server has the Build Blueprint server part):
 * left, the players here who have the mod - click one to invite them to your placed build; right, the builds other
 * players share and the invites you got - click one to join it (it appears in their spot, as a co-op build); and the
 * server's own builds (its gallery) - click one to get it into Saved builds. Ops can add the build they have loaded.
 */
public class ServerScreen extends BBScreen {
    private final Screen parent;
    private String msg = "";
    private int shownPlayers = -1, shownBuilds = -1, shownGallery = -1, wait;

    public ServerScreen(Screen parent) { super(Component.literal(Tr.t("On this server"))); this.parent = parent; }

    @Override public boolean isPauseScreen() { return false; }

    private int pw() { return Math.min(560, width - 20); }
    private int col() { return (pw() - 20) / 3; }
    private int px() { return (width - pw()) / 2; }

    @Override protected void init() {
        SyncClient.askPlayers();
        if (shownGallery < 0) SyncClient.askGallery();
        int x = px(), w = pw(), col = col(), top = 58, max = Math.max(1, (height - top - 40) / 22);
        List<String> ps = SyncClient.players();
        for (int i = 0; i < Math.min(max, ps.size()); i++) {
            String name = ps.get(i);
            addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Invite " + name, col - 8)), b -> { msg = SyncClient.invite(name); })
                    .bounds(x, top + i * 22, col, 20).tooltip(Tooltip.create(Component.literal(Tr.t("They get a message and can join your placed build in one click.")))).build());
        }
        List<SyncClient.Other> os = SyncClient.others();
        // invites first, then every other shared build
        java.util.List<SyncClient.Other> list = new java.util.ArrayList<>();
        for (String[] inv : SyncClient.invites()) { SyncClient.Other o = SyncClient.byOwner(inv[0], inv[1]); if (o != null && !list.contains(o)) list.add(o); }
        for (SyncClient.Other o : os) if (!list.contains(o)) list.add(o);
        for (int i = 0; i < Math.min(max, list.size()); i++) {
            SyncClient.Other o = list.get(i);
            boolean invited = false;
            for (String[] inv : SyncClient.invites()) if (inv[0].equals(o.owner)) invited = true;
            String label = invited ? "Join " + o.owner + "'s " + o.name : o.owner + ": " + o.name;
            addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, label, col - 8)), b -> SyncClient.join(o)).bounds(x + col + 10, top + i * 22, col, 20)
                    .tooltip(Tooltip.create(Component.literal(Tr.t("Puts " + o.owner + "'s build in their spot, placed, so you can build it together.")))).build());
        }
        // the server's builds
        List<String[]> gal = SyncClient.gallery();
        int gx = x + 2 * (col + 10), gmax = SyncClient.canAddToGallery() ? max - 1 : max;
        for (int i = 0; i < Math.min(gmax, gal.size()); i++) {
            String file = gal.get(i)[0];
            addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Get " + SyncClient.galleryName(file), col - 8)), b -> msg = SyncClient.getFromGallery(file)).bounds(gx, top + i * 22, col, 20)
                    .tooltip(Tooltip.create(Component.literal(Tr.t("Puts this server build in your Saved builds.")))).build());
        }
        if (SyncClient.canAddToGallery())
            addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "+ Add my build", col - 8)), b -> msg = SyncClient.addToGallery()).bounds(gx, top + Math.min(gmax, gal.size()) * 22, col, 20)
                    .tooltip(Tooltip.create(Component.literal(Tr.t("Ops: adds the build you have loaded to the server builds, for everyone with the mod. Server owners can also put build files in the server's gallery folder.")))).build());
        shownPlayers = ps.size(); shownBuilds = list.size(); shownGallery = gal.size() + (SyncClient.canAddToGallery() ? 1000 : 0);
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Back", 100 - 8)), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override public void tick() {   // the player list arrives a moment after we ask: rebuild the buttons when it changes
        if (++wait % 20 == 0) SyncClient.askPlayers();
        if (wait % 100 == 0) SyncClient.askGallery();
        int nb = SyncClient.others().size() + SyncClient.invites().size();
        String note = SyncClient.takeNote();
        if (!note.isEmpty()) msg = note;
        int ng = SyncClient.gallery().size() + (SyncClient.canAddToGallery() ? 1000 : 0);
        if (SyncClient.players().size() != shownPlayers || (nb != shownBuilds || ng != shownGallery) && wait % 10 == 0) { ServerScreen n = new ServerScreen(parent); n.msg = msg; n.wait = wait; n.shownGallery = shownGallery; Mc.setScreen(n); }
    }

    @Override public void onClose() { Mc.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { Safe.run("the server screen", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = px(), w = pw(), col = col();
        UiKit.panel(g, x - 8, 14, w + 16, height - 50, UiKit.CYAN);
        g.drawString(font, Tr.t("On this server"), x, 22, UiKit.GOLD, true);
        g.drawString(font, Tr.t("Players with Build Blueprint"), x, 44, UiKit.TEXT, false);
        g.drawString(font, Tr.t(UiKit.fit(font, "Builds you can join", col)), x + col + 10, 44, UiKit.TEXT, false);
        g.drawString(font, Tr.t(UiKit.fit(font, "Server builds", col)), x + 2 * (col + 10), 44, UiKit.TEXT, false);
        if (SyncClient.gallery().isEmpty() && !SyncClient.canAddToGallery()) g.drawString(font, Tr.t(UiKit.fit(font, "None yet.", col)), x + 2 * (col + 10), 62, UiKit.MUTED, false);
        if (SyncClient.players().isEmpty()) g.drawString(font, Tr.t(UiKit.fit(font, "Nobody else here has the mod yet.", col)), x, 62, UiKit.MUTED, false);
        if (shownBuilds == 0) g.drawString(font, Tr.t(UiKit.fit(font, "No shared builds yet.", col)), x + col + 10, 62, UiKit.MUTED, false);
        if (!msg.isEmpty()) UiKit.center(g, font, msg, width / 2, height - 44, msg.startsWith("Invited") || msg.startsWith("Saved") || msg.startsWith("Added") ? UiKit.GREEN : UiKit.ORANGE);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
}
