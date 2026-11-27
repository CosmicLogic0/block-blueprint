package dev.buildblueprint.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Schematic files from other tools, so players can use builds they already have:
 *   .schem      Sponge schematic v1, v2 and v3 (WorldEdit, FAWE, Axiom, Amulet...)  - read and write
 *   .litematic  Litematica                                                          - read
 *   .nbt        Minecraft structure block files                                     - read
 *   .schematic  old MCEdit / WorldEdit files (before Minecraft 1.13, numeric block ids)  - read
 *   .mcstructure Bedrock Edition structure files (blocks turned into their Java Edition names) - read
 * Plain Java with no Minecraft classes, so it works the same on every Minecraft version.
 */
public final class SchemCodec {
    private SchemCodec() {}

    // No real size limit any more: a big build just takes a while and lots of memory (the player chose to load it).
    private static final int MAX_DIM = 1_000_000;
    private static final long MAX_CELLS = 2_000_000_000L;   // what one Java array can hold

    /** File endings this class can read. */
    public static final String[] EXTENSIONS = {".schem", ".litematic", ".nbt", ".schematic", ".mcstructure"};

    public static boolean isSchematicFile(String fileName) {
        String f = fileName.toLowerCase(java.util.Locale.ROOT);
        for (String e : EXTENSIONS) if (f.endsWith(e)) return true;
        return false;
    }

    // ------------------------------------------------------------------ reading

    /** Reads any supported schematic file. fallbackName is used when the file has no name inside. */
    @SuppressWarnings("unchecked")
    public static Blueprint read(byte[] file, String fallbackName) throws BlueprintException {
        Map<String, Object> root;
        try {
            root = Nbt.read(file);
        } catch (BlueprintException e) {   // not Java Edition NBT: maybe a Bedrock .mcstructure (little-endian)
            Map<String, Object> le;
            try { le = Nbt.readLittleEndian(file); } catch (BlueprintException e2) { throw e; }
            if (le.get("structure") instanceof Map) return mcstructure(le, fallbackName);
            throw e;
        }
        // Sponge v3 (and some v2 writers) keep everything inside a "Schematic" compound
        if (root.get("Schematic") instanceof Map) root = (Map<String, Object>) root.get("Schematic");
        if (root.get("Regions") instanceof Map) return litematic(root, fallbackName);
        if (root.get("size") instanceof List && (root.get("palette") instanceof List || root.get("palettes") instanceof List)) return structure(root, fallbackName);
        if (root.get("Blocks") instanceof Map || root.get("Palette") instanceof Map || root.get("BlockData") instanceof byte[]) return sponge(root, fallbackName);
        if (root.get("Blocks") instanceof byte[]) return mcedit(root, fallbackName);
        throw new BlueprintException("That file isn't a schematic Build Blueprint knows.");
    }

    @SuppressWarnings("unchecked")
    private static Blueprint sponge(Map<String, Object> s, String fallbackName) throws BlueprintException {
        int w = u16(s.get("Width")), h = u16(s.get("Height")), l = u16(s.get("Length"));
        checkSize(w, h, l);
        Map<String, Object> palette; byte[] data;
        if (s.get("Blocks") instanceof Map) {                 // v3
            Map<String, Object> b = (Map<String, Object>) s.get("Blocks");
            palette = (Map<String, Object>) b.get("Palette");
            data = (byte[]) b.get("Data");
        } else {                                              // v1 / v2
            palette = (Map<String, Object>) s.get("Palette");
            data = (byte[]) s.get("BlockData");
        }
        if (palette == null || data == null) throw new BlueprintException("That .schem file has no blocks in it.");
        // schematic palette id -> our palette index (0 = air)
        Map<Integer, String> byId = new HashMap<>();
        for (Map.Entry<String, Object> e : palette.entrySet()) byId.put(num(e.getValue()), e.getKey());
        Pal pal = new Pal();
        int[] map = new int[maxKey(byId) + 1];
        for (Map.Entry<Integer, String> e : byId.entrySet()) if (e.getKey() >= 0) map[e.getKey()] = pal.of(e.getValue());
        int n = w * h * l;
        int[] cells = new int[n];
        int p = 0;
        for (int i = 0; i < n; i++) {
            int v = 0, shift = 0, b;
            do {
                if (p >= data.length) throw new BlueprintException("That .schem file ended early.");
                b = data[p++] & 0xff;
                v |= (b & 0x7f) << shift;
                shift += 7;
                if (shift > 35) throw new BlueprintException("That .schem file is broken (bad block number).");
            } while ((b & 0x80) != 0);
            cells[i] = v >= 0 && v < map.length ? map[v] : 0;   // Sponge order is x + z*W + y*W*L: the same as ours
        }
        String name = fallbackName;
        Object meta = s.get("Metadata");
        if (meta instanceof Map && ((Map<String, Object>) meta).get("Name") instanceof String) name = (String) ((Map<String, Object>) meta).get("Name");
        return new Blueprint(clean(name, fallbackName), w, h, l, pal.toArray(), cells);
    }

    @SuppressWarnings("unchecked")
    private static Blueprint litematic(Map<String, Object> root, String fallbackName) throws BlueprintException {
        Map<String, Object> regions = (Map<String, Object>) root.get("Regions");
        if (regions.isEmpty()) throw new BlueprintException("That .litematic file has no regions in it.");
        // first pass: overall bounds
        int[] mn = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, mx = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (Object o : regions.values()) {
            Map<String, Object> r = (Map<String, Object>) o;
            int[] pos = xyz(r.get("Position")), size = xyz(r.get("Size"));
            for (int a = 0; a < 3; a++) {
                int lo = size[a] < 0 ? pos[a] + size[a] + 1 : pos[a];
                int hi = lo + Math.abs(size[a]) - 1;
                mn[a] = Math.min(mn[a], lo); mx[a] = Math.max(mx[a], hi);
            }
        }
        int sx = mx[0] - mn[0] + 1, sy = mx[1] - mn[1] + 1, sz = mx[2] - mn[2] + 1;
        checkSize(sx, sy, sz);
        Pal pal = new Pal();
        int[] cells = new int[sx * sy * sz];
        for (Object o : regions.values()) {
            Map<String, Object> r = (Map<String, Object>) o;
            int[] pos = xyz(r.get("Position")), size = xyz(r.get("Size"));
            int ax = Math.abs(size[0]), ay = Math.abs(size[1]), az = Math.abs(size[2]);
            int ox = (size[0] < 0 ? pos[0] + size[0] + 1 : pos[0]) - mn[0];
            int oy = (size[1] < 0 ? pos[1] + size[1] + 1 : pos[1]) - mn[1];
            int oz = (size[2] < 0 ? pos[2] + size[2] + 1 : pos[2]) - mn[2];
            List<Object> plist = (List<Object>) r.get("BlockStatePalette");
            long[] bits = (long[]) r.get("BlockStates");
            if (plist == null || bits == null || plist.isEmpty()) continue;
            int[] map = new int[plist.size()];
            for (int i = 0; i < map.length; i++) map[i] = pal.of(stateOf((Map<String, Object>) plist.get(i)));
            int nb = Math.max(2, 32 - Integer.numberOfLeadingZeros(plist.size() - 1));
            long mask = (1L << nb) - 1;
            long total = (long) ax * ay * az;
            if (((total * nb) + 63) / 64 > bits.length) throw new BlueprintException("That .litematic file is broken (too little block data).");
            for (int y = 0; y < ay; y++) for (int z = 0; z < az; z++) for (int x = 0; x < ax; x++) {
                long i = ((long) y * az + z) * ax + x;
                long startBit = i * nb;
                int start = (int) (startBit >>> 6), end = (int) (((i + 1) * nb - 1) >>> 6), off = (int) (startBit & 63);
                long v = start == end ? (bits[start] >>> off) & mask : ((bits[start] >>> off) | (bits[end] << (64 - off))) & mask;
                int c = v < map.length ? map[(int) v] : 0;
                if (c != 0) cells[((oy + y) * sz + (oz + z)) * sx + (ox + x)] = c;
            }
        }
        String name = fallbackName;
        Object meta = root.get("Metadata");
        if (meta instanceof Map && ((Map<String, Object>) meta).get("Name") instanceof String) name = (String) ((Map<String, Object>) meta).get("Name");
        return new Blueprint(clean(name, fallbackName), sx, sy, sz, pal.toArray(), cells);
    }

    @SuppressWarnings("unchecked")
    private static Blueprint structure(Map<String, Object> root, String fallbackName) throws BlueprintException {
        List<Object> size = (List<Object>) root.get("size");
        int sx = num(size.get(0)), sy = num(size.get(1)), sz = num(size.get(2));
        checkSize(sx, sy, sz);
        List<Object> plist = root.get("palette") instanceof List ? (List<Object>) root.get("palette") : (List<Object>) ((List<Object>) root.get("palettes")).get(0);
        Pal pal = new Pal();
        int[] map = new int[plist.size()];
        for (int i = 0; i < map.length; i++) map[i] = pal.of(stateOf((Map<String, Object>) plist.get(i)));
        int[] cells = new int[sx * sy * sz];
        Object blocks = root.get("blocks");
        if (blocks instanceof List) for (Object o : (List<Object>) blocks) {
            Map<String, Object> b = (Map<String, Object>) o;
            List<Object> p = (List<Object>) b.get("pos");
            int x = num(p.get(0)), y = num(p.get(1)), z = num(p.get(2)), st = num(b.get("state"));
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz || st < 0 || st >= map.length) continue;
            cells[(y * sz + z) * sx + x] = map[st];
        }
        return new Blueprint(clean(fallbackName, "Structure"), sx, sy, sz, pal.toArray(), cells);
    }

    // ------------------------------------------------------------------ old MCEdit .schematic

    private static Map<String, String> legacy;
    /** "id:data" -> block state, for blocks from before Minecraft 1.13 (from PrismarineJS minecraft-data, MIT). */
    private static synchronized Map<String, String> legacy() throws BlueprintException {
        if (legacy == null) legacy = table("legacy-blocks.txt.gz", '=');
        return legacy;
    }

    /** A gzipped "key<sep>value" table from the mod's data folder. */
    private static Map<String, String> table(String file, char sep) throws BlueprintException {
        Map<String, String> m = new HashMap<>();
        try (java.io.InputStream raw = SchemCodec.class.getResourceAsStream("/assets/buildblueprint/data/" + file)) {
            if (raw == null) throw new BlueprintException("Build Blueprint is missing its " + file + " table - reinstall the mod.");
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(raw), java.nio.charset.StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                int i = line.indexOf(sep);
                if (i > 0) m.putIfAbsent(line.substring(0, i), line.substring(i + 1));
            }
        } catch (IOException e) {
            throw new BlueprintException("Build Blueprint couldn't read its " + file + " table.");
        }
        return m;
    }

    /** Old .schematic: Width/Height/Length, Blocks (+ AddBlocks for ids over 255) and Data, in y-z-x order like ours. */
    private static Blueprint mcedit(Map<String, Object> s, String fallbackName) throws BlueprintException {
        int w = u16(s.get("Width")), h = u16(s.get("Height")), l = u16(s.get("Length"));
        checkSize(w, h, l);
        byte[] blocks = (byte[]) s.get("Blocks");
        byte[] data = s.get("Data") instanceof byte[] ? (byte[]) s.get("Data") : new byte[0];
        byte[] add = s.get("AddBlocks") instanceof byte[] ? (byte[]) s.get("AddBlocks") : null;
        int n = w * h * l;
        if (blocks.length < n) throw new BlueprintException("That .schematic file ended early.");
        Map<String, String> table = legacy();
        Map<Integer, Integer> seen = new HashMap<>();
        Pal pal = new Pal();
        int[] cells = new int[n];
        int unknown = 0;
        for (int i = 0; i < n; i++) {
            int id = blocks[i] & 0xff;
            if (add != null && (i >> 1) < add.length) id |= ((i & 1) == 0 ? (add[i >> 1] & 0x0F) << 8 : (add[i >> 1] & 0xF0) << 4);
            if (id == 0) continue;
            int meta = i < data.length ? data[i] & 0x0f : 0, key = id << 4 | meta;
            Integer c = seen.get(key);
            if (c == null) {
                String st = table.get(id + ":" + meta);
                if (st == null) st = table.get(id + ":0");
                c = st == null ? 0 : pal.of(st);
                if (st == null) unknown++;
                seen.put(key, c);
            }
            cells[i] = c;
        }
        return new Blueprint(clean(fallbackName, "Old schematic"), w, h, l, pal.toArray(), cells);
    }

    // ------------------------------------------------------------------ Bedrock .mcstructure

    private static Map<String, String> bedrock;
    /** Bedrock name -> its "key=value" state lists and Java states, best first (for files with fewer states than the table). */
    private static Map<String, List<String[]>> bedrockByName;
    /** Bedrock block "name|key=value;..." (or "name|*" for the usual form) -> Java block state (made from GeyserMC's mappings, MIT). */
    private static synchronized Map<String, String> bedrock() throws BlueprintException {
        if (bedrock == null) {
            Map<String, String> m = table("bedrock-blocks.txt.gz", '\t');
            Map<String, List<String[]>> byName = new HashMap<>();
            try (java.io.InputStream raw = SchemCodec.class.getResourceAsStream("/assets/buildblueprint/data/bedrock-blocks.txt.gz")) {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(raw), java.nio.charset.StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    int bar = line.indexOf('|'), tab = line.indexOf('\t');
                    if (line.startsWith("#") || bar < 0 || tab < bar || line.charAt(bar + 1) == '*') continue;
                    byName.computeIfAbsent(line.substring(0, bar), k -> new ArrayList<>()).add(new String[]{";" + line.substring(bar + 1, tab) + ";", line.substring(tab + 1)});
                }
            } catch (IOException | RuntimeException e) { /* exact matches still work */ }
            bedrockByName = byName;
            bedrock = m;
        }
        return bedrock;
    }

    /** The Java Edition block for a Bedrock palette entry {name, states}, or null when there's none. */
    @SuppressWarnings("unchecked")
    static String fromBedrock(Map<String, Object> entry, Map<String, String> table) {
        Object n = entry.get("name");
        if (!(n instanceof String)) return null;
        String name = ((String) n).indexOf(':') < 0 ? "minecraft:" + n : (String) n;
        if (name.equals("minecraft:air") || name.equals("minecraft:structure_void")) return null;
        StringBuilder key = new StringBuilder(name).append('|');
        if (entry.get("states") instanceof Map) {
            boolean first = true;
            for (Map.Entry<String, Object> e : new TreeMap<>((Map<String, Object>) entry.get("states")).entrySet()) {
                Object v = e.getValue();
                if (!first) key.append(';');
                key.append(e.getKey()).append('=').append(v instanceof Number ? String.valueOf(((Number) v).longValue()) : String.valueOf(v));
                first = false;
            }
        }
        String j = table.get(key.toString());
        if (j != null) return j;
        // the file has fewer (or older) states than the table: the best line that agrees with every state it has
        List<String[]> rows = bedrockByName == null ? null : bedrockByName.get(name);
        if (rows != null && key.length() > name.length() + 1) {
            String[] want = key.substring(name.length() + 1).split(";");
            for (String[] row : rows) {
                boolean ok = true;
                for (String kv : want) if (!row[0].contains(";" + kv + ";")) { ok = false; break; }
                if (ok) return row[1];
            }
        }
        return table.get(name + "|*");
    }

    /** Bedrock .mcstructure: size, structure.block_indices (layer 0 = blocks; layer 1 = water in them) and the palette. Order: x, then y, then z. */
    @SuppressWarnings("unchecked")
    private static Blueprint mcstructure(Map<String, Object> root, String fallbackName) throws BlueprintException {
        if (!(root.get("size") instanceof List)) throw new BlueprintException("That .mcstructure file has no size.");
        List<Object> size = (List<Object>) root.get("size");
        int sx = num(size.get(0)), sy = num(size.get(1)), sz = num(size.get(2));
        checkSize(sx, sy, sz);
        Map<String, Object> st = (Map<String, Object>) root.get("structure");
        List<Object> layers = st.get("block_indices") instanceof List ? (List<Object>) st.get("block_indices") : null;
        Object palObj = st.get("palette") instanceof Map ? ((Map<String, Object>) st.get("palette")).get("default") : null;
        if (layers == null || layers.isEmpty() || !(palObj instanceof Map) || !(((Map<String, Object>) palObj).get("block_palette") instanceof List))
            throw new BlueprintException("That .mcstructure file has no blocks in it.");
        List<Object> bp = (List<Object>) ((Map<String, Object>) palObj).get("block_palette");
        Map<String, String> table = bedrock();
        Pal pal = new Pal();
        int[] map = new int[bp.size()];
        for (int i = 0; i < map.length; i++) {
            String j = bp.get(i) instanceof Map ? fromBedrock((Map<String, Object>) bp.get(i), table) : null;
            map[i] = j == null ? 0 : pal.of(j);
        }
        Object l0 = layers.get(0);
        int n = sx * sy * sz;
        int[] cells = new int[n];
        int idx = 0;
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++, idx++) {
                    int v;
                    if (l0 instanceof int[]) { int[] a = (int[]) l0; v = idx < a.length ? a[idx] : -1; }
                    else { List<Object> a = (List<Object>) l0; v = idx < a.size() ? num(a.get(idx)) : -1; }
                    if (v >= 0 && v < map.length) cells[(y * sz + z) * sx + x] = map[v];
                }
        return new Blueprint(clean(fallbackName, "Bedrock structure"), sx, sy, sz, pal.toArray(), cells);
    }

    // ------------------------------------------------------------------ writing

    /** Writes a Sponge schematic v2 .schem (read by WorldEdit 7.2+, FAWE, Axiom, Amulet and Litematica). */
    public static byte[] writeSchem(Blueprint bp, int dataVersion) throws IOException {
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        int[] ours = new int[bp.palette.length + 1];
        for (int i = 0; i < bp.palette.length; i++) {
            Integer id = palette.get(bp.palette[i]);
            if (id == null) { id = palette.size(); palette.put(bp.palette[i], id); }
            ours[i + 1] = id;
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream(bp.cells.length);
        for (int c : bp.cells) {
            int v = ours[c];
            while ((v & ~0x7f) != 0) { data.write((v & 0x7f) | 0x80); v >>>= 7; }
            data.write(v);
        }
        Map<String, Nbt.Tag> pal = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : palette.entrySet()) pal.put(e.getKey(), Nbt.ofInt(e.getValue()));
        Map<String, Nbt.Tag> meta = new LinkedHashMap<>();
        meta.put("Name", Nbt.ofString(bp.name == null || bp.name.trim().isEmpty() ? "Build" : bp.name));
        meta.put("Author", Nbt.ofString("Build Blueprint"));
        Map<String, Nbt.Tag> s = new LinkedHashMap<>();
        s.put("Version", Nbt.ofInt(2));
        s.put("DataVersion", Nbt.ofInt(dataVersion));
        s.put("Metadata", Nbt.ofCompound(meta));
        s.put("Width", Nbt.ofShort(bp.sx));
        s.put("Height", Nbt.ofShort(bp.sy));
        s.put("Length", Nbt.ofShort(bp.sz));
        s.put("Offset", Nbt.ofInts(new int[]{0, 0, 0}));
        s.put("PaletteMax", Nbt.ofInt(palette.size()));
        s.put("Palette", Nbt.ofCompound(pal));
        s.put("BlockData", Nbt.ofBytes(data.toByteArray()));
        s.put("BlockEntities", Nbt.ofEmptyList(10));
        return Nbt.write("Schematic", s);
    }

    // ------------------------------------------------------------------ Minecraft version of this build

    private static final String[][] DATA_VERSIONS = {
        // the lowest Minecraft version each mod file runs on comes first in its range, so use that one's number
        {"1.16.5", "2586"}, {"1.17", "2724"}, {"1.17.1", "2724"}, {"1.18", "2860"}, {"1.18.1", "2860"}, {"1.18.2", "2975"},
        {"1.19", "3105"}, {"1.19.1", "3117"}, {"1.19.2", "3120"}, {"1.19.3", "3218"}, {"1.19.4", "3337"},
        {"1.20", "3463"}, {"1.20.1", "3463"}, {"1.20.2", "3578"}, {"1.20.3", "3698"}, {"1.20.4", "3698"}, {"1.20.5", "3837"}, {"1.20.6", "3837"},
        {"1.21", "3953"}, {"1.21.1", "3953"}, {"1.21.2", "4080"}, {"1.21.3", "4082"}, {"1.21.4", "4189"}, {"1.21.5", "4325"},
        {"1.21.6", "4435"}, {"1.21.7", "4435"}, {"1.21.8", "4435"}, {"1.21.9", "4554"}, {"1.21.10", "4556"}, {"1.21.11", "4671"},
        {"26.1", "4786"}, {"26.1.1", "4786"}, {"26.1.2", "4786"}, {"26.2", "4903"}, {"26.3", "5023"},
    };

    /** The Minecraft version this mod file was built for (written into the jar at build time). */
    public static String minecraftVersion() {
        try (InputStream in = SchemCodec.class.getResourceAsStream("mc.txt")) {
            if (in != null) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[64]; int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                return new String(o.toByteArray(), StandardCharsets.UTF_8).trim();
            }
        } catch (IOException ignored) {}
        return "";
    }

    /** Minecraft's data version for this mod file's Minecraft version (tells WorldEdit which version wrote a .schem). */
    public static int dataVersion() {
        String mc = minecraftVersion();
        for (String[] e : DATA_VERSIONS) if (e[0].equals(mc)) return Integer.parseInt(e[1]);
        return 2586;   // unknown: say 1.16.5, the oldest we support; newer tools update old files fine
    }

    // ------------------------------------------------------------------ helpers

    /** Builds our palette: air-like blocks become 0, everything else gets an index from 1. */
    private static final class Pal {
        final Map<String, Integer> idx = new HashMap<>();
        final List<String> list = new ArrayList<>();
        int of(String state) {
            if (state == null) return 0;
            String id = state.indexOf('[') >= 0 ? state.substring(0, state.indexOf('[')) : state;
            if (!id.contains(":")) { id = "minecraft:" + id; state = "minecraft:" + state; }
            if (id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air") || id.equals("minecraft:structure_void")) return 0;
            Integer k = idx.get(state);
            if (k == null) { list.add(state); k = list.size(); idx.put(state, k); }
            return k;
        }
        String[] toArray() { return list.toArray(new String[0]); }
    }

    @SuppressWarnings("unchecked")
    private static String stateOf(Map<String, Object> entry) {
        Object n = entry.get("Name");                      // older files: Name / Properties
        if (!(n instanceof String)) n = entry.get("id");   // Minecraft 26.x structure files: id / properties
        if (!(n instanceof String)) return null;
        Object p = entry.containsKey("Properties") ? entry.get("Properties") : entry.get("properties");
        if (!(p instanceof Map) || ((Map<String, Object>) p).isEmpty()) return (String) n;
        StringBuilder sb = new StringBuilder((String) n).append('[');
        boolean first = true;
        for (Map.Entry<String, Object> e : new TreeMap<>((Map<String, Object>) p).entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }

    @SuppressWarnings("unchecked")
    private static int[] xyz(Object o) throws BlueprintException {
        if (!(o instanceof Map)) throw new BlueprintException("That .litematic file is broken (no position or size).");
        Map<String, Object> m = (Map<String, Object>) o;
        return new int[]{num(m.get("x")), num(m.get("y")), num(m.get("z"))};
    }

    private static int num(Object o) throws BlueprintException {
        if (o instanceof Number) return ((Number) o).intValue();
        throw new BlueprintException("That schematic file is broken (a number is missing).");
    }

    private static int u16(Object o) throws BlueprintException {
        if (o instanceof Short) return ((Short) o) & 0xffff;
        return num(o);
    }

    private static int maxKey(Map<Integer, String> m) {
        int mx = 0;
        for (int k : m.keySet()) mx = Math.max(mx, k);
        return mx;
    }

    private static void checkSize(int x, int y, int z) throws BlueprintException {
        if (x <= 0 || y <= 0 || z <= 0) throw new BlueprintException("That schematic is empty.");
        if (x > MAX_DIM || y > MAX_DIM || z > MAX_DIM || (long) x * y * z > MAX_CELLS)
            throw new BlueprintException("That schematic is too big to load (" + x + " x " + y + " x " + z + ").");
    }

    private static String clean(String name, String fallback) {
        String n = name == null ? "" : name.trim();
        return n.isEmpty() ? fallback : n;
    }
}
