package dev.buildblueprint.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Decodes the text produced by the Block Blueprint app (see renderer/export.js):
 * base64url( crc32(deflated) BE || raw-deflate( v1 | size | name | palette | RLE cells ) ).
 */
public final class BlueprintCodec {
    private BlueprintCodec() {}

    private static final int MAX_DIM = 512;
    private static final int MAX_CELLS = 8_000_000;
    private static final int MAX_BYTES = 40_000_000;

    private static final class Reader {
        final byte[] b; int p;
        Reader(byte[] b) { this.b = b; }
        int u8() throws BlueprintException {
            if (p >= b.length) throw new BlueprintException("Blueprint data ended early.");
            return b[p++] & 0xff;
        }
        int varint() throws BlueprintException {
            int result = 0, shift = 0;
            for (int i = 0; i < 5; i++) {
                int v = u8();
                result |= (v & 0x7f) << shift;
                if ((v & 0x80) == 0) return result;
                shift += 7;
            }
            throw new BlueprintException("Blueprint data is corrupted (bad number).");
        }
        String str() throws BlueprintException {
            int n = varint();
            if (n < 0 || p + n > b.length) throw new BlueprintException("Blueprint data is corrupted (bad text).");
            String s = new String(b, p, n, StandardCharsets.UTF_8);
            p += n;
            return s;
        }
    }

    public static Blueprint decode(String text) throws BlueprintException {
        byte[] packed;
        try {
            packed = Base64.getUrlDecoder().decode(text.trim());
        } catch (IllegalArgumentException e) {
            throw new BlueprintException("The pasted text isn't valid blueprint data.");
        }
        if (packed.length < 6) throw new BlueprintException("The pasted blueprint data is too short.");
        int expected = ((packed[0] & 0xff) << 24) | ((packed[1] & 0xff) << 16) | ((packed[2] & 0xff) << 8) | (packed[3] & 0xff);
        CRC32 crc = new CRC32();
        crc.update(packed, 4, packed.length - 4);
        if ((int) crc.getValue() != expected)
            throw new BlueprintException("Checksum failed - a line was cut off or mistyped. Paste all lines again.");

        byte[] raw = inflate(packed, 4, packed.length - 4);
        Reader r = new Reader(raw);
        int version = r.u8();
        if (version != 1) throw new BlueprintException("This blueprint was made by a newer app version (format " + version + ").");
        int sx = r.varint(), sy = r.varint(), sz = r.varint();
        if (sx < 1 || sy < 1 || sz < 1 || sx > MAX_DIM || sy > MAX_DIM || sz > MAX_DIM || (long) sx * sy * sz > MAX_CELLS)
            throw new BlueprintException("Blueprint size is out of range.");
        String name = r.str();
        int pc = r.varint();
        if (pc < 0 || pc > 100_000) throw new BlueprintException("Blueprint data is corrupted (palette).");
        String[] palette = new String[pc];
        for (int i = 0; i < pc; i++) palette[i] = r.str();

        int total = sx * sy * sz;
        int[] cells = new int[total];
        int i = 0;
        while (i < total) {
            int run = r.varint(), val = r.varint();
            if (run < 1 || i + run > total || val < 0 || val > pc) throw new BlueprintException("Blueprint data is corrupted (cells).");
            if (val != 0) java.util.Arrays.fill(cells, i, i + run, val);
            i += run;
        }
        return new Blueprint(name, sx, sy, sz, palette, cells);
    }

    /** The reverse of {@link #decode}: the same text the app makes, so a copied build can be saved and pasted anywhere. */
    public static String encode(Blueprint bp) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.write(1);
        varint(raw, bp.sx); varint(raw, bp.sy); varint(raw, bp.sz);
        str(raw, bp.name == null ? "" : bp.name);
        varint(raw, bp.palette.length);
        for (String p : bp.palette) str(raw, p);
        int[] c = bp.cells;
        for (int i = 0; i < c.length; ) {
            int v = c[i], j = i + 1;
            while (j < c.length && c[j] == v) j++;
            varint(raw, j - i); varint(raw, v);
            i = j;
        }
        java.util.zip.Deflater def = new java.util.zip.Deflater(9, true);
        def.setInput(raw.toByteArray()); def.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!def.finished()) { int n = def.deflate(buf); z.write(buf, 0, n); }
        def.end();
        byte[] d = z.toByteArray();
        CRC32 crc = new CRC32(); crc.update(d);
        long v = crc.getValue();
        byte[] out = new byte[d.length + 4];
        out[0] = (byte) (v >>> 24); out[1] = (byte) (v >>> 16); out[2] = (byte) (v >>> 8); out[3] = (byte) v;
        System.arraycopy(d, 0, out, 4, d.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
    }

    private static void varint(ByteArrayOutputStream o, int n) { while ((n & ~0x7f) != 0) { o.write((n & 0x7f) | 0x80); n >>>= 7; } o.write(n); }
    private static void str(ByteArrayOutputStream o, String s) { byte[] b = s.getBytes(StandardCharsets.UTF_8); varint(o, b.length); o.write(b, 0, b.length); }

    private static byte[] inflate(byte[] data, int off, int len) throws BlueprintException {
        Inflater inf = new Inflater(true); // raw deflate, as produced by CompressionStream('deflate-raw')
        inf.setInput(data, off, len);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, len * 4));
        byte[] buf = new byte[8192];
        try {
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new BlueprintException("Blueprint is too large.");
            }
        } catch (DataFormatException e) {
            throw new BlueprintException("Blueprint data is corrupted.");
        } finally {
            inf.end();
        }
        return out.toByteArray();
    }
}
