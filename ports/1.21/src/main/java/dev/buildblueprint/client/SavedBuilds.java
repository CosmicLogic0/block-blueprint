package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.BlueprintCodec;
import dev.buildblueprint.core.BlueprintException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Builds the player has pasted in, copied or sent from the app, saved on this computer in <home>/BlockBlueprint/builds.
 * Each file: line 1 = display name, line 2 = the blueprint data (the joined parts of the app's /buildblueprint load lines).
 */
public final class SavedBuilds {
    private SavedBuilds() {}

    public record Entry(String file, String name, String data, long modified) {}

    private static final Pattern LOAD = Pattern.compile("load\\s+([A-Za-z0-9_-]+)\\.(\\d+)\\.(\\d+)\\s+([A-Za-z0-9_=-]+)");
    private static final Map<String, Blueprint> CACHE = new HashMap<>();

    /**
     * Shared library: <home>/BlockBlueprint/builds. Every modpack / launcher on this PC uses the same folder,
     * and the desktop app's "Send to Minecraft" writes here, so it never has to find where the modpack is installed.
     */
    public static Path dir() {
        Path d = java.nio.file.Paths.get(System.getProperty("user.home"), "BlockBlueprint", "builds");
        try { Files.createDirectories(d); } catch (IOException e) { return legacyDir(); }
        return d;
    }

    /** Where older versions kept builds (inside this instance's config folder). Still read so nothing is lost. */
    private static Path legacyDir() {
        Path d = FabricLoader.getInstance().getConfigDir().resolve("buildblueprint").resolve("builds");
        try { Files.createDirectories(d); } catch (IOException ignored) {}
        return d;
    }

    private static Path pathOf(Entry e) { return java.nio.file.Paths.get(e.file()); }

    public static List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        Path shared = dir(), old = legacyDir();
        for (Path folder : shared.equals(old) ? java.util.Collections.singletonList(shared) : java.util.Arrays.asList(shared, old)) {
            try (var s = Files.list(folder)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    if (!p.getFileName().toString().endsWith(".bbp")) continue;
                    try {
                        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                        if (lines.size() < 2) continue;
                        String data = lines.get(1).trim();
                        if (!seen.add(data)) continue;   // same build in both folders: show it once
                        out.add(new Entry(p.toString(), lines.get(0).trim(), data, Files.getLastModifiedTime(p).toMillis()));
                    } catch (IOException ignored) {}
                }
            } catch (IOException ignored) {}
        }
        out.sort((a, b) -> Long.compare(b.modified(), a.modified()));
        return out;
    }

    public static Blueprint blueprint(Entry e) throws BlueprintException {
        Blueprint bp = CACHE.get(e.data());
        if (bp == null) { bp = BlueprintCodec.decode(e.data()); CACHE.put(e.data(), bp); }
        return bp;
    }

    /**
     * Reads pasted text: one or more "/buildblueprint load id.part.total data" lines (in any order, extra text ignored).
     * Returns the joined blueprint data, or throws with a friendly message.
     */
    public static String parsePasted(String text) throws BlueprintException {
        if (text != null && text.contains("BB1:")) return fromAppCode(text);
        Matcher m = LOAD.matcher(text == null ? "" : text);
        Map<String, TreeMap<Integer, String>> byId = new HashMap<>();
        Map<String, Integer> totals = new HashMap<>();
        String last = null;
        while (m.find()) {
            String id = m.group(1);
            byId.computeIfAbsent(id, k -> new TreeMap<>()).put(Integer.parseInt(m.group(2)), m.group(4));
            totals.put(id, Integer.parseInt(m.group(3)));
            last = id;
        }
        if (last == null) throw new BlueprintException("Nothing to paste. Copy a build code (BB1:...) or the app's Export > \"Copy all\" lines, then paste here.");
        TreeMap<Integer, String> parts = byId.get(last);
        int total = totals.get(last);
        if (parts.size() < total) throw new BlueprintException("Only " + parts.size() + " of " + total + " parts were pasted. Use \"Copy all\" in the app.");
        String joined = String.join("", parts.values());
        BlueprintCodec.decode(joined); // validates
        return joined;
    }

    private static final Pattern BB1 = Pattern.compile("BB1:([A-Za-z0-9_-]+)");

    /** A "BB1:..." share code from the app (or from Copy in this mod) -> blueprint data. */
    public static String fromAppCode(String text) throws BlueprintException {
        Matcher m = BB1.matcher(text.replaceAll("\\s+", ""));
        if (!m.find()) throw new BlueprintException("That isn't a build code.");
        String json;
        try {
            byte[] z = java.util.Base64.getUrlDecoder().decode(m.group(1));
            java.util.zip.Inflater inf = new java.util.zip.Inflater(true);
            inf.setInput(z);
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!inf.finished()) { int n = inf.inflate(buf); if (n == 0 && inf.needsInput()) break; o.write(buf, 0, n); if (o.size() > 60_000_000) throw new BlueprintException("That build is too big."); }
            inf.end();
            json = o.toString(StandardCharsets.UTF_8);
        } catch (BlueprintException e) { throw e; } catch (Exception e) { throw new BlueprintException("That build code is broken - copy it again."); }
        try {
            com.google.gson.JsonObject root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
            com.google.gson.JsonArray blocks = root.getAsJsonArray("blocks");
            String name = root.has("name") ? root.get("name").getAsString() : "Imported build";
            if (blocks == null || blocks.size() == 0) throw new BlueprintException("That build code has no blocks in it.");
            int[] mn = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, mx = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            List<int[]> pos = new ArrayList<>(); List<String> st = new ArrayList<>();
            for (var el : blocks) {
                var a = el.getAsJsonArray();
                int[] p = {(int) Math.round(a.get(0).getAsDouble()), (int) Math.round(a.get(1).getAsDouble()), (int) Math.round(a.get(2).getAsDouble())};
                String id = a.get(3).getAsString(), label = a.size() > 4 ? a.get(4).getAsString() : "default";
                int r = a.size() > 5 && !a.get(5).isJsonNull() ? a.get(5).getAsInt() : 0;
                String state = (id.contains(":") ? id : "minecraft:" + id) + (label.isEmpty() || label.equals("default") ? "" : "[" + label + "]");
                if (r != 0) state = dev.buildblueprint.core.StateRotator.rotate(state, ((r % 4) + 4) % 4);
                pos.add(p); st.add(state);
                for (int i = 0; i < 3; i++) { mn[i] = Math.min(mn[i], p[i]); mx[i] = Math.max(mx[i], p[i]); }
            }
            int sx = mx[0] - mn[0] + 1, sy = mx[1] - mn[1] + 1, sz = mx[2] - mn[2] + 1;
            if (sx > 512 || sy > 512 || sz > 512 || (long) sx * sy * sz > 8_000_000) throw new BlueprintException("That build is too big.");
            Map<String, Integer> idx = new HashMap<>(); List<String> pal = new ArrayList<>();
            int[] cells = new int[sx * sy * sz];
            for (int i = 0; i < pos.size(); i++) {
                int[] p = pos.get(i);
                Integer k = idx.get(st.get(i));
                if (k == null) { pal.add(st.get(i)); k = pal.size(); idx.put(st.get(i), k); }
                cells[((p[1] - mn[1]) * sz + (p[2] - mn[2])) * sx + (p[0] - mn[0])] = k;
            }
            return BlueprintCodec.encode(new Blueprint(name, sx, sy, sz, pal.toArray(new String[0]), cells));
        } catch (BlueprintException e) { throw e; } catch (Exception e) { throw new BlueprintException("That build code couldn't be read."); }
    }

    public static Entry save(String name, String data) throws IOException {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (slug.isEmpty()) slug = "build";
        Path p = dir().resolve(slug + ".bbp");
        for (int i = 2; Files.exists(p); i++) p = dir().resolve(slug + "_" + i + ".bbp");
        Files.write(p, (name + "\n" + data + "\n").getBytes(StandardCharsets.UTF_8));
        return new Entry(p.toString(), name, data, System.currentTimeMillis());
    }

    public static void rename(Entry e, String name) throws IOException {
        Files.write(pathOf(e), (name + "\n" + e.data() + "\n").getBytes(StandardCharsets.UTF_8));
    }

    public static void delete(Entry e) throws IOException { Files.deleteIfExists(pathOf(e)); }
}
