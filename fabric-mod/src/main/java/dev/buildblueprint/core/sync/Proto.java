package dev.buildblueprint.core.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The messages the Build Blueprint client and the server part (Paper plugin, Fabric/Forge/NeoForge server mod) send each
 * other on the plugin channel {@link #CHANNEL}. Plain Java, so every side uses the very same code.
 * A shared build is its co-op code ("BBCOOP:x,y,z,parts BB1:..."), sent in parts so big builds fit in the packets.
 */
public final class Proto {
    private Proto() {}

    public static final String CHANNEL = "buildblueprint:sync";
    public static final int VERSION = 1;
    /** Bytes of build data per message: client-to-server packets are limited to 32767 bytes. */
    public static final int PART = 30000;
    /** The biggest build (compressed) a player can share, and how many at once. */
    public static final int MAX_BUILD = 16 * 1024 * 1024, MAX_BUILDS = 4;

    public static final int HELLO = 1, PLAYERS_REQ = 2, PLAYERS = 3, SHARE = 4, UNSHARE = 5, BUILD = 6, REMOVED = 7, INVITE = 8, INVITED = 9;

    /** One decoded message. Fields not used by a type are left empty. */
    public static final class Msg {
        public int type, version, part, total;
        public String a = "", b = "";          // HELLO: server kind | PLAYERS: - | SHARE/UNSHARE: build id | BUILD/REMOVED: owner, id | INVITE: target, id | INVITED: from, id
        public byte[] data = new byte[0];
        public List<String> names = new ArrayList<>();

        @Override public String toString() { return "Msg{" + type + " " + a + " " + b + " " + part + "/" + total + " " + data.length + "b " + names + "}"; }
    }

    public static byte[] hello(String kind) { return enc(HELLO, w -> { w.writeInt(VERSION); w.writeUTF(kind); }); }
    public static byte[] playersReq() { return enc(PLAYERS_REQ, w -> {}); }
    public static byte[] players(List<String> names) { return enc(PLAYERS, w -> { w.writeInt(names.size()); for (String n : names) w.writeUTF(n); }); }
    public static byte[] share(String id, int part, int total, byte[] data) { return enc(SHARE, w -> { w.writeUTF(id); w.writeInt(part); w.writeInt(total); bytes(w, data); }); }
    public static byte[] unshare(String id) { return enc(UNSHARE, w -> w.writeUTF(id)); }
    public static byte[] build(String owner, String id, int part, int total, byte[] data) { return enc(BUILD, w -> { w.writeUTF(owner); w.writeUTF(id); w.writeInt(part); w.writeInt(total); bytes(w, data); }); }
    public static byte[] removed(String owner, String id) { return enc(REMOVED, w -> { w.writeUTF(owner); w.writeUTF(id); }); }
    public static byte[] invite(String target, String id) { return enc(INVITE, w -> { w.writeUTF(target); w.writeUTF(id); }); }
    public static byte[] invited(String from, String id) { return enc(INVITED, w -> { w.writeUTF(from); w.writeUTF(id); }); }

    public static Msg decode(byte[] raw) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
        Msg m = new Msg();
        m.type = in.readUnsignedByte();
        switch (m.type) {
            case HELLO: m.version = in.readInt(); m.a = in.readUTF(); break;
            case PLAYERS_REQ: break;
            case PLAYERS: { int n = in.readInt(); if (n < 0 || n > 10000) throw new IOException("bad list"); for (int i = 0; i < n; i++) m.names.add(in.readUTF()); break; }
            case SHARE: m.a = in.readUTF(); m.part = in.readInt(); m.total = in.readInt(); m.data = readBytes(in); break;
            case UNSHARE: m.a = in.readUTF(); break;
            case BUILD: m.a = in.readUTF(); m.b = in.readUTF(); m.part = in.readInt(); m.total = in.readInt(); m.data = readBytes(in); break;
            case REMOVED: case INVITE: case INVITED: m.a = in.readUTF(); m.b = in.readUTF(); break;
            default: throw new IOException("unknown message " + m.type);
        }
        return m;
    }

    /** Splits data into parts of at most PART bytes (at least one part, even for empty data). */
    public static List<byte[]> split(byte[] data) {
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i == 0 || i < data.length; i += PART) out.add(java.util.Arrays.copyOfRange(data, i, Math.min(data.length, i + PART)));
        return out;
    }

    /** Collects the parts of one build until all have come. */
    public static final class Assembler {
        private final byte[][] parts;
        private int have, size;
        public Assembler(int total) {
            if (total < 1 || total > MAX_BUILD / PART + 1) throw new IllegalArgumentException("too many parts");
            parts = new byte[total][];
        }
        /** Adds one part; returns the whole data once every part is in, else null. */
        public byte[] add(int part, byte[] data) {
            if (part < 0 || part >= parts.length || parts[part] != null) return null;
            if ((size += data.length) > MAX_BUILD) throw new IllegalArgumentException("build too big");
            parts[part] = data; have++;
            if (have < parts.length) return null;
            byte[] all = new byte[size]; int at = 0;
            for (byte[] p : parts) { System.arraycopy(p, 0, all, at, p.length); at += p.length; }
            return all;
        }
    }

    public static byte[] gzip(String s) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream z = new java.util.zip.GZIPOutputStream(b)) { z.write(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            return b.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static String gunzip(byte[] d) throws IOException {
        try (java.util.zip.GZIPInputStream z = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(d))) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n; long tot = 0;
            while ((n = z.read(buf)) > 0) { if ((tot += n) > 64L * 1024 * 1024) throw new IOException("build too big"); b.write(buf, 0, n); }
            return new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private interface W { void w(DataOutputStream o) throws IOException; }
    private static byte[] enc(int type, W body) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeByte(type); body.w(o); o.flush();
            return b.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private static void bytes(DataOutputStream o, byte[] d) throws IOException { o.writeInt(d.length); o.write(d); }
    private static byte[] readBytes(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > PART * 40) throw new IOException("bad length");
        byte[] d = new byte[n]; in.readFully(d); return d;
    }
}
