package dev.buildblueprint.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Free builds to download (the same open-license community builds as in the Block Blueprint app), straight from the game:
 * search, pick one, press Get it, and it's in Saved builds. Every build shows who made it and its license.
 */
public class TemplatesScreen extends Screen {
    private static final String BASE = "https://raw.githubusercontent.com/CosmicLogic0/block-blueprint/main/renderer/templates/";

    /** One build: file, name, kind, size, blocks, creator, license, link. */
    private static final class T {
        final String file, name, cat, size, by, license, link; final int blocks;
        T(String[] c) { file = c[1]; name = c[2]; cat = c[3]; size = c[4]; blocks = parse(c[5]); by = c[6]; license = c[7]; link = c.length > 8 ? c[8] : ""; }
    }
    private static int parse(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }

    private static volatile List<T> all;          // loaded once per game
    private static volatile String loadError;
    private static volatile boolean loading;

    private final Screen parent;
    private List<T> shown = new ArrayList<>();
    private T pick;
    private int scroll;
    private EditBox search;
    private Button getBtn, kindBtn;
    private static String kind = "";            // "" = all kinds
    private volatile String msg = "";
    private volatile boolean busy;

    public TemplatesScreen(Screen parent) { super(Component.literal("Get a build")); this.parent = parent; }

    @Override public boolean isPauseScreen() { return false; }

    private int pw() { return Math.min(440, width - 16); }
    private int px() { return (width - pw()) / 2; }
    private int listTop() { return 62; }
    private int listBottom() { return height - 60; }
    private int rowH() { return 14; }
    private int visRows() { return Math.max(1, (listBottom() - listTop()) / rowH()); }

    @Override protected void init() {
        if (all == null && !loading) load();
        int x = px(), w = pw();
        String q = search == null ? "" : search.getValue();
        search = new EditBox(font, x, 36, w - 124, 18, Component.literal("Search"));
        search.setHint(Component.literal("Search by name or creator..."));
        search.setValue(q);
        search.setResponder(t -> { scroll = 0; refill(); });
        addRenderableWidget(search);
        kindBtn = addRenderableWidget(Button.builder(Component.literal(kindLabel()), b -> { kind = nextKind(); b.setMessage(Component.literal(kindLabel())); scroll = 0; refill(); })
                .bounds(x + w - 120, 35, 120, 20).tooltip(Tooltip.create(Component.literal("Show one kind of build: houses, towers, ships..."))).build());
        int by = height - 26, bw = (w - 4) / 2;
        getBtn = addRenderableWidget(Button.builder(Component.literal("Get it"), b -> get()).bounds(x, by, bw, 20)
                .tooltip(Tooltip.create(Component.literal("Download it into Saved builds."))).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(x + bw + 4, by, w - bw - 4, 20).build());
        refill();
        setFocused(search);
    }

    private static List<String> kinds() {
        List<String> k = new ArrayList<>();
        if (all != null) for (T t : all) if (!t.cat.isEmpty() && !k.contains(t.cat)) k.add(t.cat);
        java.util.Collections.sort(k);
        return k;
    }
    private static String nextKind() {
        List<String> k = kinds();
        if (k.isEmpty()) return "";
        int i = k.indexOf(kind);
        return i + 1 >= k.size() ? "" : k.get(i + 1);
    }
    private static String kindLabel() { return kind.isEmpty() ? "All kinds" : kind; }

    private void refill() {
        List<T> out = new ArrayList<>();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (all != null) for (T t : all) {
            if (!kind.isEmpty() && !t.cat.equals(kind)) continue;
            if (!q.isEmpty() && !(t.name.toLowerCase(Locale.ROOT).contains(q) || t.by.toLowerCase(Locale.ROOT).contains(q) || t.cat.toLowerCase(Locale.ROOT).contains(q))) continue;
            out.add(t);
        }
        shown = out;
        if (pick != null && !shown.contains(pick)) pick = null;
    }

    private static void load() {
        loading = true; loadError = null;
        Thread th = new Thread(() -> {
            try {
                String text = new String(fetch(BASE + "mod-index.tsv"), StandardCharsets.UTF_8);
                List<T> list = new ArrayList<>();
                for (String line : text.split("\n")) { String[] c = line.split("\t", -1); if (c.length >= 8) list.add(new T(c)); }
                all = list;
            } catch (Exception e) { loadError = "Couldn't reach the build list - check your internet connection."; }
            loading = false;
        }, "Build Blueprint templates");
        th.setDaemon(true); th.start();
    }

    private static byte[] fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "build-blueprint-mod");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); if (out.size() > 40_000_000) throw new Exception("too big"); }
            return out.toByteArray();
        }
    }

    private void get() {
        final T t = pick;
        if (t == null || busy) return;
        busy = true; msg = "Downloading " + t.name + "...";
        Thread th = new Thread(() -> {
            try {
                byte[] data = fetch(BASE + "schem/" + t.file);
                String safe = (t.name + " - by " + t.by).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                Path p = SavedBuilds.dir().resolve(safe + ".nbt");
                Files.write(p, data);
                msg = "Got it! \"" + t.name + "\" is in Saved builds.";
            } catch (Exception e) { msg = "Download failed - check your internet connection."; }
            busy = false;
        }, "Build Blueprint download");
        th.setDaemon(true); th.start();
    }

    // --- input
    private int hit(double mx, double my) {
        if (mx < px() || mx >= px() + pw() - 6 || my < listTop() || my >= listTop() + visRows() * rowH()) return -1;
        int i = (int) ((my - listTop()) / rowH()) + scroll;
        return i < shown.size() ? i : -1;
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) return true;
        int i = hit(e.x(), e.y());
        if (i >= 0) { pick = shown.get(i); msg = ""; if (dbl) get(); return true; }
        return false;
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - visRows()), scroll - (int) Math.signum(dy) * 3));
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) { onClose(); return true; }
        if ((e.key() == 257 || e.key() == 335) && pick != null) { get(); return true; }
        return super.keyPressed(e);
    }
    @Override public void onClose() { Mc.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { Safe.run("the templates screen", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (all != null && shown.isEmpty() && search.getValue().isEmpty() && kind.isEmpty()) refill();
        int x = px(), w = pw(), top = listTop(), bot = listBottom();
        UiKit.panel(g, x - 6, 4, w + 12, height - 8, UiKit.GREEN);
        g.text(font, "Get a build", x, 10, UiKit.GOLD, true);
        String sub = all == null ? "" : all.size() + " free builds from the Minecraft community";
        g.text(font, sub, x, 22, UiKit.MUTED, false);
        g.fill(x, top - 2, x + w, bot, 0xFF15151C);
        if (all == null) UiKit.center(g, font, loadError != null ? loadError : "Loading the list...", x + w / 2, top + 20, loadError != null ? UiKit.ORANGE : UiKit.MUTED);
        T hover = null;
        int rows = visRows(), y = top;
        for (int i = scroll; i < Math.min(shown.size(), scroll + rows); i++, y += rowH()) {
            T t = shown.get(i);
            boolean sel = t == pick, hov = mouseX >= x && mouseX < x + w - 6 && mouseY >= y && mouseY < y + rowH();
            if (hov) hover = t;
            if (sel || hov) g.fill(x, y, x + w - 6, y + rowH() - 1, sel ? 0xFF2F4A2A : 0xFF2A2A38);
            String right = t.size + "  ·  " + t.blocks + " blocks";
            g.text(font, UiKit.fit(font, t.name, w - 30 - font.width(right) - 90), x + 4, y + 3, sel ? UiKit.WHITE : UiKit.TEXT, false);
            g.text(font, UiKit.fit(font, t.cat, 84), x + w - 14 - font.width(right) - 90, y + 3, 0xFF8C7CC8, false);
            g.text(font, right, x + w - 10 - font.width(right), y + 3, UiKit.MUTED, false);
        }
        if (all != null && shown.isEmpty()) UiKit.center(g, font, "No build matches.", x + w / 2, top + 20, UiKit.MUTED);
        if (shown.size() > rows) {   // scroll bar
            int track = bot - top - 2, sh = Math.max(12, track * rows / shown.size()), sy = top + (track - sh) * scroll / Math.max(1, shown.size() - rows);
            g.fill(x + w - 4, top, x + w - 1, bot - 2, 0xFF22222C);
            g.fill(x + w - 4, sy, x + w - 1, sy + sh, 0xFFAAAAAA);
        }
        // the picked build: who made it and its license
        int iy = bot + 6;
        if (!msg.isEmpty()) UiKit.center(g, font, msg, x + w / 2, iy + 4, msg.startsWith("Got it") ? UiKit.GREEN : msg.startsWith("Download") ? UiKit.MUTED : UiKit.ORANGE);
        else if (pick != null) {
            g.text(font, UiKit.fit(font, pick.name, w), x, iy, UiKit.WHITE, true);
            g.text(font, UiKit.fit(font, "by " + pick.by + "  ·  " + pick.license + " license", w), x, iy + 11, UiKit.MUTED, false);
        } else UiKit.center(g, font, "Click a build, then Get it  ·  double-click gets it right away", x + w / 2, iy + 4, UiKit.MUTED);
        getBtn.active = pick != null && !busy;
        kindBtn.active = all != null;
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hover.name));
            tip.add(Component.literal("By " + hover.by + " (" + hover.license + ")"));
            if (!hover.link.isEmpty()) tip.add(Component.literal(hover.link));
            g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }
}
