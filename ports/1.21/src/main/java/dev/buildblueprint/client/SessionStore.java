package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.BlueprintCodec;
import dev.buildblueprint.core.Session;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Remembers the blueprint you were building in each world (position, layer, placed or not, swaps, rotation) so it
 * is still there when you leave and come back. One small file per world / server in config/buildblueprint/worlds.
 */
public final class SessionStore {
    private SessionStore() {}

    private static Object level;
    private static int wait = -1, tick;
    private static String lastKey, lastPrint = "";
    private static boolean dirty;

    public static void dirty() { dirty = true; }

    static void init() { ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("saving your blueprint", () -> tick(mc))); }

    /** A name for the current world: the save name in singleplayer, the server address in multiplayer. */
    private static String key(Minecraft mc) {
        try {
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) return "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName();
            if (mc.getCurrentServer() != null) return "mp_" + mc.getCurrentServer().ip;
        } catch (Exception ignored) {}
        return null;
    }

    private static Path file(String key) {
        Path d = FabricLoader.getInstance().getConfigDir().resolve("buildblueprint").resolve("worlds");
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return d.resolve(key.replaceAll("[^A-Za-z0-9._-]+", "_") + ".properties");
    }

    private static void tick(Minecraft mc) {
        Session s = BuildBlueprintClient.SESSION;
        if (mc.level != level) {
            if (level != null && lastKey != null) { save(lastKey); s.clear(); }   // left the world: keep it for next time
            level = mc.level; lastKey = null; lastPrint = "";
            wait = mc.level != null ? 40 : -1;                                     // let the world settle first
            return;
        }
        if (mc.level == null || mc.player == null) return;
        if (wait > 0 && --wait == 0) { lastKey = key(mc); if (lastKey != null && !s.loaded()) restore(lastKey); return; }
        if (lastKey == null || ++tick % 100 != 0) return;                         // every 5 seconds
        String print = s.loaded() ? System.identityHashCode(s.blueprint()) + ":" + s.ox + "," + s.oy + "," + s.oz + ":" + s.layer + ":" + s.locked : "none";
        if (dirty || !print.equals(lastPrint)) { save(lastKey); lastPrint = print; dirty = false; }
    }

    private static void save(String key) {
        Session s = BuildBlueprintClient.SESSION;
        try {
            Path f = file(key);
            if (!s.loaded()) { Files.deleteIfExists(f); return; }
            Properties p = new Properties();
            p.setProperty("data", BlueprintCodec.encode(s.blueprint()));
            p.setProperty("x", "" + s.ox); p.setProperty("y", "" + s.oy); p.setProperty("z", "" + s.oz);
            p.setProperty("layer", "" + s.layer); p.setProperty("locked", "" + s.locked);
            try (Writer w = Files.newBufferedWriter(f)) { p.store(w, "Build Blueprint: the blueprint you were building here"); }
        } catch (Exception ignored) {}
    }

    private static void restore(String key) {
        try {
            Path f = file(key);
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(f)) { p.load(r); }
            Blueprint bp = BlueprintCodec.decode(p.getProperty("data"));
            BuildBlueprintClient.SESSION.restore(bp, Integer.parseInt(p.getProperty("x")), Integer.parseInt(p.getProperty("y")), Integer.parseInt(p.getProperty("z")),
                    Integer.parseInt(p.getProperty("layer", "-1")), Boolean.parseBoolean(p.getProperty("locked", "false")));
            Materials.refresh();
            Chat.ok("Welcome back! Your blueprint \"" + bp.name + "\" is still here" + (BuildBlueprintClient.SESSION.locked ? " (placed)." : " - move it, then Place it.") + " Page Up for the menu.");
        } catch (Exception e) {
            Chat.warn("Couldn't bring back the blueprint you had here last time.");
        }
    }
}
