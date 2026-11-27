package dev.buildblueprint.core.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server part, the same for the Paper plugin and the Fabric/Forge/NeoForge server mods: it remembers which players
 * have the mod, keeps the builds they share while they're online, passes them to the others and delivers invites.
 * Call it from the server thread only (or keep calls to one thread).
 */
public final class Relay<P> {
    /** What the platform does for the relay. */
    public interface Bridge<P> {
        void send(P player, byte[] msg);
        String name(P player);
        UUID id(P player);
        P byName(String name);
        void log(String s);
        /** The server's build gallery folder. Fabric / Forge / NeoForge servers: config/buildblueprint/gallery. */
        default java.nio.file.Path gallery() { return java.nio.file.Paths.get("config", "buildblueprint", "gallery"); }
        /** May this player add builds to the gallery? Default: they're an op (in the server's ops.json). */
        default boolean canEdit(P player) { return Ops.isOp(id(player)); }
    }

    /** Reads the server's ops.json (in the folder the server runs in) - the same list on every kind of server. */
    static final class Ops {
        private static long stamp = -1;
        private static java.util.Set<String> ids = new java.util.HashSet<>();
        static synchronized boolean isOp(UUID id) {
            try {
                java.nio.file.Path f = java.nio.file.Paths.get("ops.json");
                long m = java.nio.file.Files.exists(f) ? java.nio.file.Files.getLastModifiedTime(f).toMillis() : 0;
                if (m != stamp) {
                    stamp = m;
                    java.util.Set<String> s = new java.util.HashSet<>();
                    if (m != 0) {
                        java.util.regex.Matcher x = java.util.regex.Pattern.compile("\"uuid\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"").matcher(new String(java.nio.file.Files.readAllBytes(f), java.nio.charset.StandardCharsets.UTF_8));
                        while (x.find()) s.add(x.group(1).toLowerCase(java.util.Locale.ROOT));
                    }
                    ids = s;
                }
                return ids.contains(id.toString().toLowerCase(java.util.Locale.ROOT));
            } catch (Exception e) { return false; }
        }
    }

    /** Build files the gallery shows. */
    private static final String[] GALLERY_TYPES = {".bbp", ".schem", ".litematic", ".nbt", ".schematic", ".mcstructure"};

    private static final class Build { final UUID owner; final String ownerName, id; final byte[] data; Build(UUID o, String on, String i, byte[] d) { owner = o; ownerName = on; id = i; data = d; } }

    private final Bridge<P> bridge;
    private final String kind;
    private final Map<UUID, P> modded = new LinkedHashMap<>();
    private final Map<String, Build> builds = new LinkedHashMap<>();         // owner uuid + "/" + id
    private final Map<String, Proto.Assembler> uploads = new HashMap<>();

    public Relay(Bridge<P> bridge, String kind) { this.bridge = bridge; this.kind = kind; }

    public void receive(P from, byte[] raw) {
        Proto.Msg m;
        try { m = Proto.decode(raw); } catch (Exception e) { bridge.log("bad message from " + bridge.name(from) + ": " + e); return; }
        UUID me = bridge.id(from);
        switch (m.type) {
            case Proto.HELLO: {
                boolean isNew = modded.put(me, from) == null;
                bridge.send(from, Proto.hello(kind));
                for (Build b : builds.values()) if (!b.owner.equals(me)) sendBuild(from, b);
                if (isNew) bridge.log(bridge.name(from) + " has Build Blueprint");
                break;
            }
            case Proto.PLAYERS_REQ: {
                List<String> names = new ArrayList<>();
                for (Map.Entry<UUID, P> e : modded.entrySet()) if (!e.getKey().equals(me)) names.add(bridge.name(e.getValue()));
                bridge.send(from, Proto.players(names));
                break;
            }
            case Proto.SHARE: {
                if (!modded.containsKey(me)) return;
                String key = me + "/" + m.a;
                try {
                    if (m.part == 0) uploads.put(key, new Proto.Assembler(m.total));
                    Proto.Assembler a = uploads.get(key);
                    if (a == null) return;
                    byte[] all = a.add(m.part, m.data);
                    if (all == null) return;
                    uploads.remove(key);
                    int mine = 0;
                    for (Build b : builds.values()) if (b.owner.equals(me) && !b.id.equals(m.a)) mine++;
                    if (mine >= Proto.MAX_BUILDS) return;
                    Build b = new Build(me, bridge.name(from), m.a, all);
                    builds.put(key, b);
                    for (Map.Entry<UUID, P> e : modded.entrySet()) if (!e.getKey().equals(me)) sendBuild(e.getValue(), b);
                } catch (IllegalArgumentException e) { uploads.remove(key); bridge.log("build from " + bridge.name(from) + " refused: " + e.getMessage()); }
                break;
            }
            case Proto.UNSHARE: {
                Build b = builds.remove(me + "/" + m.a);
                if (b != null) for (Map.Entry<UUID, P> e : modded.entrySet()) if (!e.getKey().equals(me)) bridge.send(e.getValue(), Proto.removed(b.ownerName, b.id));
                break;
            }
            case Proto.GALLERY_REQ: {
                if (modded.containsKey(me)) bridge.send(from, Proto.gallery(bridge.canEdit(from), galleryList()));
                break;
            }
            case Proto.GALLERY_GET: {
                if (!modded.containsKey(me) || !galleryList().stream().anyMatch(n -> n.startsWith(m.a + "|"))) return;   // only files in the list: no paths
                try {
                    byte[] data = java.nio.file.Files.readAllBytes(bridge.gallery().resolve(m.a));
                    List<byte[]> parts = Proto.split(data);
                    for (int i = 0; i < parts.size(); i++) bridge.send(from, Proto.galleryFile(m.a, i, parts.size(), parts.get(i)));
                } catch (Exception e) { bridge.send(from, Proto.note("Couldn't read " + m.a + " on the server.")); }
                break;
            }
            case Proto.GALLERY_ADD: {
                if (!modded.containsKey(me)) return;
                if (!bridge.canEdit(from)) { if (m.part == 0) bridge.send(from, Proto.note("Only ops can add builds to the server builds.")); return; }
                String key = me + "/gallery";
                try {
                    if (m.part == 0) uploads.put(key, new Proto.Assembler(m.total));
                    Proto.Assembler a = uploads.get(key);
                    if (a == null) return;
                    byte[] all = a.add(m.part, m.data);
                    if (all == null) return;
                    uploads.remove(key);
                    String text = Proto.gunzip(all);   // "name\nbuild data", like a Saved builds .bbp file
                    String safe = m.a.replaceAll("[^A-Za-z0-9 _()-]", "_").trim();
                    if (safe.isEmpty()) safe = "build";
                    if (safe.length() > 60) safe = safe.substring(0, 60);
                    java.nio.file.Path dir = bridge.gallery();
                    java.nio.file.Files.createDirectories(dir);
                    java.nio.file.Path p = dir.resolve(safe + ".bbp");
                    for (int i = 2; java.nio.file.Files.exists(p); i++) p = dir.resolve(safe + " (" + i + ").bbp");
                    java.nio.file.Files.write(p, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    bridge.log(bridge.name(from) + " added " + p.getFileName() + " to the server builds");
                    bridge.send(from, Proto.note("Added \"" + m.a + "\" to the server builds."));
                    for (P o : modded.values()) bridge.send(o, Proto.gallery(bridge.canEdit(o), galleryList()));
                } catch (Exception e) { uploads.remove(key); bridge.send(from, Proto.note("Couldn't add it: " + e.getMessage())); }
                break;
            }
            case Proto.INVITE: {
                P to = bridge.byName(m.a);
                if (to != null && modded.containsKey(bridge.id(to))) bridge.send(to, Proto.invited(bridge.name(from), m.b));
                break;
            }
            default: break;
        }
    }

    /** A player left: forget them and take their builds away from everyone else. */
    public void quit(P p) {
        UUID me = bridge.id(p);
        if (modded.remove(me) == null) return;
        uploads.keySet().removeIf(k -> k.startsWith(me + "/"));
        List<Build> gone = new ArrayList<>();
        builds.values().removeIf(b -> { if (b.owner.equals(me)) { gone.add(b); return true; } return false; });
        for (Build b : gone) for (P o : modded.values()) bridge.send(o, Proto.removed(b.ownerName, b.id));
    }

    /** The gallery's build files, as "file|bytes", by name. */
    private List<String> galleryList() {
        List<String> out = new ArrayList<>();
        java.nio.file.Path dir = bridge.gallery();
        try {
            if (!java.nio.file.Files.isDirectory(dir)) { java.nio.file.Files.createDirectories(dir); return out; }
            try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.list(dir)) {
                s.filter(java.nio.file.Files::isRegularFile).sorted().forEach(p -> {
                    String n = p.getFileName().toString(), low = n.toLowerCase(java.util.Locale.ROOT);
                    if (n.contains("|")) return;
                    for (String t : GALLERY_TYPES) if (low.endsWith(t)) {
                        try { long size = java.nio.file.Files.size(p); if (size <= Proto.MAX_BUILD) out.add(n + "|" + size); } catch (java.io.IOException ignored) {}
                        return;
                    }
                });
            }
        } catch (Exception e) { bridge.log("couldn't read the server builds folder " + dir + ": " + e); }
        return out;
    }

    private void sendBuild(P to, Build b) {
        List<byte[]> parts = Proto.split(b.data);
        for (int i = 0; i < parts.size(); i++) bridge.send(to, Proto.build(b.ownerName, b.id, i, parts.size(), parts.get(i)));
    }

    public int players() { return modded.size(); }
    public int buildCount() { return builds.size(); }
}
