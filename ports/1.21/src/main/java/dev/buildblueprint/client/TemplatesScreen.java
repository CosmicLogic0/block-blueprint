package dev.buildblueprint.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Free builds to download (the same open-license community builds as in the Block Blueprint app), straight from the game:
 * search, pick one to see a picture of it, press Get it, and it's in Saved builds. Every build shows who made it and its license.
 */
public class TemplatesScreen extends BBScreen {
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
    private Button getBtn, kindBtn, sizeBtn, sortBtn, randBtn;
    private static String kind = "";            // "" = all kinds
    private static int sizeF = 0;                // 0 any, 1 small (under 500 blocks), 2 medium (500-3,000), 3 big (3,000+)
    private static int sort = 0;                // 0 name, 1 fewest blocks, 2 most blocks
    private volatile String msg = "";
    private volatile boolean busy;

    /** A build fetched for its picture (kept, so Get it doesn't download it again). */
    private static final class Got { volatile byte[] data; volatile dev.buildblueprint.core.Blueprint bp; volatile boolean failed; }
    private static final Map<String, Got> GOT = java.util.Collections.synchronizedMap(new LinkedHashMap<String, Got>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Got> e) { return size() > 8; }   // only the last few, big builds take memory
    });
    private final BuildPicture pic = new BuildPicture();
    private int turn = 0;
    private boolean autoPick;
    /** Self-test: picks the first build once the list is there, so its picture shows. */
    public TemplatesScreen autoPick() { autoPick = true; return this; }

    public TemplatesScreen(Screen parent) { super(Component.literal(Tr.t("Get free builds"))); this.parent = parent; }

    @Override public boolean isPauseScreen() { return false; }

    private int pw() { return Math.min(660, width - 16); }
    private boolean side() { return pw() >= 400; }                                  // room for the picture next to the list
    private int lw() { return side() ? pw() - pvW() - 6 : pw(); }                  // list width
    private int pvW() { return Math.max(140, Math.min(260, pw() * 2 / 5)); }
    private int pvX() { return px() + pw() - pvW(); }
    private int px() { return (width - pw()) / 2; }
    private int listTop() { return 84; }
    private int listBottom() { return height - 60; }
    private int rowH() { return 14; }
    private int visRows() { return Math.max(1, (listBottom() - listTop()) / rowH()); }

    @Override protected void init() {
        if (all == null && !loading) load();
        int x = px(), w = pw();
        String q = search == null ? "" : search.getValue();
        search = new EditBox(font, x, 36, w - 124, 18, Component.literal(Tr.t("Search")));
        search.setHint(Component.literal(Tr.t("Search by name or creator...")));
        search.setValue(q);
        search.setResponder(t -> { scroll = 0; refill(); });
        addRenderableWidget(search);
        kindBtn = addRenderableWidget(Button.builder(Component.literal(Tr.t(kindLabel())), b -> { kind = nextKind(); b.setMessage(Component.literal(Tr.t(kindLabel()))); scroll = 0; refill(); })
                .bounds(x + w - 120, 35, 120, 20).tooltip(Tooltip.create(Component.literal(Tr.t("Show one kind of build: houses, towers, ships...")))).build());
        int fw = (w - 8) / 3;   // second row: size, sort, random
        sizeBtn = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, sizeLabel(), fw - 8)), b -> { sizeF = (sizeF + 1) % 4; b.setMessage(Component.literal(UiKit.fit(font, sizeLabel(), fw - 8))); scroll = 0; refill(); })
                .bounds(x, 58, fw, 20).tooltip(Tooltip.create(Component.literal(Tr.t("Show only small, medium or big builds (by how many blocks they have).")))).build());
        sortBtn = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, sortLabel(), fw - 8)), b -> { sort = (sort + 1) % 3; b.setMessage(Component.literal(UiKit.fit(font, sortLabel(), fw - 8))); scroll = 0; refill(); })
                .bounds(x + fw + 4, 58, fw, 20).build());
        randBtn = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Random build", w - 2 * fw - 16)), b -> random())
                .bounds(x + 2 * fw + 8, 58, w - 2 * fw - 8, 20).tooltip(Tooltip.create(Component.literal(Tr.t("Pick a random build from the list, to find something new.")))).build());
        int by = height - 26, bw = (w - 4) / 2;
        getBtn = addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Get it", bw - 8)), b -> get()).bounds(x, by, bw, 20)
                .tooltip(Tooltip.create(Component.literal(Tr.t("Download it into Saved builds.")))).build());
        addRenderableWidget(Button.builder(Component.literal(UiKit.fit(font, "Back", (w - bw - 4) - 8)), b -> onClose()).bounds(x + bw + 4, by, w - bw - 4, 20).build());
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
    private static String sizeLabel() { return sizeF == 1 ? "Small (under 500)" : sizeF == 2 ? "Medium (500 to 3,000)" : sizeF == 3 ? "Big (3,000+)" : "Any size"; }
    private static String sortLabel() { return sort == 1 ? "Sort: fewest blocks" : sort == 2 ? "Sort: most blocks" : "Sort: A to Z"; }
    private static boolean fits(T t) { return sizeF == 0 || (sizeF == 1 ? t.blocks < 500 : sizeF == 2 ? t.blocks >= 500 && t.blocks < 3000 : t.blocks >= 3000); }

    private void random() {
        if (shown.isEmpty()) return;
        int i = new java.util.Random().nextInt(shown.size());
        pick = shown.get(i); turn = 0; msg = "";
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - visRows()), i - visRows() / 2));   // show it in the list
        fetchPicture(pick);
    }

    private static String kindLabel() { return kind.isEmpty() ? "All kinds" : kind; }

    private void refill() {
        List<T> out = new ArrayList<>();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (all != null) for (T t : all) {
            if (!kind.isEmpty() && !t.cat.equals(kind)) continue;
            if (!fits(t)) continue;
            if (!q.isEmpty() && !(t.name.toLowerCase(Locale.ROOT).contains(q) || t.by.toLowerCase(Locale.ROOT).contains(q) || t.cat.toLowerCase(Locale.ROOT).contains(q))) continue;
            out.add(t);
        }
        if (sort == 1) out.sort((a, b) -> Integer.compare(a.blocks, b.blocks));
        else if (sort == 2) out.sort((a, b) -> Integer.compare(b.blocks, a.blocks));
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

    // the same files through jsDelivr: for places where GitHub is blocked or very slow (mainland China and others)
    private static final String[] MIRRORS = {BASE, "https://cdn.jsdelivr.net/gh/CosmicLogic0/block-blueprint@main/renderer/templates/",
            "https://fastly.jsdelivr.net/gh/CosmicLogic0/block-blueprint@main/renderer/templates/", "https://gcore.jsdelivr.net/gh/CosmicLogic0/block-blueprint@main/renderer/templates/"};
    private static volatile int good = 0;   // the one that worked last, tried first

    /** Downloads one file of the free builds, trying the backup addresses when the first doesn't answer. */
    private static byte[] fetch(String url) throws Exception {
        if (!url.startsWith(BASE)) return fetchOne(url);
        String rest = url.substring(BASE.length());
        Exception last = null;
        for (int k = 0; k < MIRRORS.length; k++) {
            int i = (good + k) % MIRRORS.length;
            try { byte[] b = fetchOne(MIRRORS[i] + rest); good = i; return b; } catch (Exception e) { last = e; }
        }
        throw last;
    }

    private static byte[] fetchOne(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000); c.setReadTimeout(15000);
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
                Got got = GOT.get(t.file);
                byte[] data = got != null && got.data != null ? got.data : fetch(BASE + "schem/" + t.file);
                String safe = (t.name + " - by " + t.by).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                Path p = SavedBuilds.dir().resolve(safe + ".nbt");
                Files.write(p, data);
                msg = "Got it! \"" + t.name + "\" is in Saved builds.";
            } catch (Exception e) { msg = "Download failed - check your internet connection."; }
            busy = false;
        }, "Build Blueprint download");
        th.setDaemon(true); th.start();
    }

    /** Downloads the picked build in the background to show its picture. */
    private static void fetchPicture(T t) {
        if (t == null || !Settings.preview3d || GOT.containsKey(t.file)) return;
        Got got = new Got();
        GOT.put(t.file, got);
        Thread th = new Thread(() -> {
            try {
                byte[] data = fetch(BASE + "schem/" + t.file);
                got.data = data;
                got.bp = dev.buildblueprint.core.SchemCodec.read(data, t.file);
            } catch (Throwable e) { got.failed = true; GOT.remove(t.file); }
        }, "Build Blueprint picture");
        th.setDaemon(true); th.start();
    }

    // --- input
    private int hit(double mx, double my) {
        if (mx < px() || mx >= px() + lw() - 6 || my < listTop() || my >= listTop() + visRows() * rowH()) return -1;
        int i = (int) ((my - listTop()) / rowH()) + scroll;
        return i < shown.size() ? i : -1;
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) return true;
        int i = hit(e.x(), e.y());
        if (i >= 0) { if (pick != shown.get(i)) turn = 0; pick = shown.get(i); msg = ""; fetchPicture(pick); if (dbl) get(); return true; }
        if (side() && pick != null && e.x() >= pvX() && e.x() < pvX() + pvW() && e.y() >= listTop() - 2 && e.y() < listBottom()) { turn = (turn + 1) % 4; return true; }   // click the picture to turn it
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
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float delta) { Safe.run("the templates screen", () -> draw(g, mouseX, mouseY, delta)); }

    private void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (all != null && shown.isEmpty() && search.getValue().isEmpty() && kind.isEmpty()) refill();
        if (autoPick && pick == null && !shown.isEmpty()) { autoPick = false; pick = shown.get(0); fetchPicture(pick); }
        int x = px(), w = pw(), top = listTop(), bot = listBottom();
        UiKit.panel(g, x - 6, 4, w + 12, height - 8, UiKit.GREEN);
        g.drawString(font, Tr.t("Get free builds"), x, 10, UiKit.GOLD, true);
        String sub = all == null ? "" : all.size() + " free builds from the Minecraft community";
        g.drawString(font, Tr.t(sub), x, 22, UiKit.MUTED, false);
        int lw = lw();
        g.fill(x, top - 2, x + lw, bot, 0xFF15151C);
        if (side()) drawPicture(g, pvX(), top - 2, pvW(), bot - top + 2);
        if (all == null) UiKit.center(g, font, loadError != null ? loadError : "Loading the list...", x + lw / 2, top + 20, loadError != null ? UiKit.ORANGE : UiKit.MUTED);
        T hover = null;
        int rows = visRows(), y = top;
        for (int i = scroll; i < Math.min(shown.size(), scroll + rows); i++, y += rowH()) {
            T t = shown.get(i);
            boolean sel = t == pick, hov = mouseX >= x && mouseX < x + lw - 6 && mouseY >= y && mouseY < y + rowH();
            if (hov) hover = t;
            if (sel || hov) g.fill(x, y, x + lw - 6, y + rowH() - 1, sel ? 0xFF2F4A2A : 0xFF2A2A38);
            String right = t.size + "  ·  " + t.blocks + " blocks";
            g.drawString(font, Tr.t(UiKit.fit(font, t.name, lw - 30 - font.width(Tr.t(right)) - 90)), x + 4, y + 3, sel ? UiKit.WHITE : UiKit.TEXT, false);
            g.drawString(font, Tr.t(UiKit.fit(font, t.cat, 84)), x + lw - 14 - font.width(Tr.t(right)) - 90, y + 3, 0xFF8C7CC8, false);
            g.drawString(font, Tr.t(right), x + lw - 10 - font.width(Tr.t(right)), y + 3, UiKit.MUTED, false);
        }
        if (all != null && shown.isEmpty()) UiKit.center(g, font, "No build matches.", x + lw / 2, top + 20, UiKit.MUTED);
        if (shown.size() > rows) {   // scroll bar
            int track = bot - top - 2, sh = Math.max(12, track * rows / shown.size()), sy = top + (track - sh) * scroll / Math.max(1, shown.size() - rows);
            g.fill(x + lw - 4, top, x + lw - 1, bot - 2, 0xFF22222C);
            g.fill(x + lw - 4, sy, x + lw - 1, sy + sh, 0xFFAAAAAA);
        }
        // the picked build: who made it and its license
        int iy = bot + 6;
        if (!msg.isEmpty()) UiKit.center(g, font, msg, x + w / 2, iy + 4, msg.startsWith("Got it") ? UiKit.GREEN : msg.startsWith("Download") ? UiKit.MUTED : UiKit.ORANGE);
        else if (pick != null) {
            g.drawString(font, Tr.t(UiKit.fit(font, pick.name, w)), x, iy, UiKit.WHITE, true);
            g.drawString(font, Tr.t(UiKit.fit(font, "by " + pick.by + "  ·  " + pick.license + " license", w)), x, iy + 11, UiKit.MUTED, false);
        } else UiKit.center(g, font, "Click a build, then Get it  ·  double-click gets it right away", x + w / 2, iy + 4, UiKit.MUTED);
        getBtn.active = pick != null && !busy;
        kindBtn.active = all != null;
        sizeBtn.active = sortBtn.active = all != null;
        randBtn.active = !shown.isEmpty();
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(Tr.t(hover.name)));
            tip.add(Component.literal(Tr.t("By " + hover.by + " (" + hover.license + ")")));
            if (!hover.link.isEmpty()) tip.add(Component.literal(Tr.t(hover.link)));
            V.tooltip(g, font, tip, mouseX, mouseY);
        }
    }

    private void drawPicture(GuiGraphics g, int bx, int by, int bw, int bh) {
        g.fill(bx, by, bx + bw, by + bh, 0xFF101016);
        int cx = bx + bw / 2, cy = by + bh / 2 - 4;
        if (!Settings.preview3d) { UiKit.center(g, font, UiKit.fit(font, "Build picture is off (Settings > More)", bw - 8), cx, cy, UiKit.MUTED); return; }
        if (pick == null) { UiKit.center(g, font, UiKit.fit(font, "Click a build to see it here", bw - 8), cx, cy, UiKit.MUTED); return; }
        Got got = GOT.get(pick.file);
        if (got == null) { fetchPicture(pick); got = GOT.get(pick.file); }
        if (got == null || got.bp == null) { UiKit.center(g, font, UiKit.fit(font, got == null ? "Couldn't load the picture." : "Loading the picture...", bw - 8), cx, cy, got == null ? UiKit.ORANGE : UiKit.MUTED); return; }
        pic.draw(g, got.bp, turn, 0, 1f, 0, 0, bx + 2, by + 2, bw - 4, bh - 16);
        UiKit.center(g, font, UiKit.fit(font, "Click the picture to turn it", bw - 8), cx, by + bh - 11, UiKit.MUTED);
    }
}
