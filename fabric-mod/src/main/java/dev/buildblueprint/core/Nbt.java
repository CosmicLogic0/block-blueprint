package dev.buildblueprint.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A tiny reader / writer for Minecraft's NBT file format (used by .schem, .litematic and structure .nbt files).
 * Plain Java with no Minecraft classes, so it works the same on every Minecraft version.
 * Compounds become LinkedHashMap, lists become ArrayList, arrays stay byte[] / int[] / long[].
 */
public final class Nbt {
    private Nbt() {}

    private static final int MAX_DEPTH = 64;
    private static final int MAX_ARRAY = 64_000_000;

    /** A typed value for writing (so a short stays a short and a list knows its element type). */
    public static final class Tag {
        final int type; final Object value;
        Tag(int type, Object value) { this.type = type; this.value = value; }
    }
    public static Tag ofShort(int v) { return new Tag(2, (short) v); }
    public static Tag ofInt(int v) { return new Tag(3, v); }
    public static Tag ofString(String v) { return new Tag(8, v); }
    public static Tag ofBytes(byte[] v) { return new Tag(7, v); }
    public static Tag ofInts(int[] v) { return new Tag(11, v); }
    public static Tag ofCompound(Map<String, Tag> v) { return new Tag(10, v); }
    public static Tag ofEmptyList(int elementType) { return new Tag(9, new int[]{elementType}); }

    /** Reads a whole file (gzip-compressed or not). Returns the root compound. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> read(byte[] file) throws BlueprintException {
        try {
            InputStream in = new ByteArrayInputStream(file);
            if (file.length > 2 && (file[0] & 0xff) == 0x1f && (file[1] & 0xff) == 0x8b) in = new GZIPInputStream(in);
            DataInputStream d = new DataInputStream(in);
            int type = d.readUnsignedByte();
            if (type != 10) throw new BlueprintException("That file isn't a schematic (no NBT compound inside).");
            d.readUTF();
            return (Map<String, Object>) payload(d, 10, 0);
        } catch (BlueprintException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BlueprintException("That schematic file is broken or not a schematic.");
        }
    }

    private static Object payload(DataInputStream d, int type, int depth) throws IOException, BlueprintException {
        if (depth > MAX_DEPTH) throw new BlueprintException("That schematic file is nested too deeply.");
        switch (type) {
            case 1: return d.readByte();
            case 2: return d.readShort();
            case 3: return d.readInt();
            case 4: return d.readLong();
            case 5: return d.readFloat();
            case 6: return d.readDouble();
            case 7: { int n = len(d); byte[] a = new byte[n]; d.readFully(a); return a; }
            case 8: return d.readUTF();
            case 9: {
                int et = d.readUnsignedByte(); int n = d.readInt();
                if (n < 0 || n > MAX_ARRAY) throw new BlueprintException("That schematic file is broken (bad list).");
                List<Object> l = new ArrayList<>(Math.min(n, 4096));
                for (int i = 0; i < n; i++) l.add(payload(d, et, depth + 1));
                return l;
            }
            case 10: {
                Map<String, Object> m = new LinkedHashMap<>();
                while (true) {
                    int t = d.readUnsignedByte();
                    if (t == 0) return m;
                    String name = d.readUTF();
                    m.put(name, payload(d, t, depth + 1));
                }
            }
            case 11: { int n = len(d); int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = d.readInt(); return a; }
            case 12: { int n = len(d); long[] a = new long[n]; for (int i = 0; i < n; i++) a[i] = d.readLong(); return a; }
            default: throw new BlueprintException("That schematic file is broken (unknown tag " + type + ").");
        }
    }

    private static int len(DataInputStream d) throws IOException, BlueprintException {
        int n = d.readInt();
        if (n < 0 || n > MAX_ARRAY) throw new BlueprintException("That schematic file is broken (bad array).");
        return n;
    }

    /** Writes a gzip-compressed NBT file with the given root name and compound. */
    public static byte[] write(String rootName, Map<String, Tag> root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(new GZIPOutputStream(bytes))) {
            d.writeByte(10);
            d.writeUTF(rootName);
            writePayload(d, new Tag(10, root));
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(DataOutputStream d, Tag t) throws IOException {
        switch (t.type) {
            case 2: d.writeShort((Short) t.value); break;
            case 3: d.writeInt((Integer) t.value); break;
            case 7: { byte[] a = (byte[]) t.value; d.writeInt(a.length); d.write(a); break; }
            case 8: d.writeUTF((String) t.value); break;
            case 9: { int[] et = (int[]) t.value; d.writeByte(et[0]); d.writeInt(0); break; }   // only empty lists are written
            case 10: {
                for (Map.Entry<String, Tag> e : ((Map<String, Tag>) t.value).entrySet()) {
                    d.writeByte(e.getValue().type);
                    d.writeUTF(e.getKey());
                    writePayload(d, e.getValue());
                }
                d.writeByte(0);
                break;
            }
            case 11: { int[] a = (int[]) t.value; d.writeInt(a.length); for (int v : a) d.writeInt(v); break; }
            default: throw new IOException("Unsupported NBT tag " + t.type);
        }
    }
}
