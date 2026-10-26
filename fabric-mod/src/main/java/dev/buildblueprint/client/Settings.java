package dev.buildblueprint.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Player preferences, saved in .minecraft/config/buildblueprint/settings.properties. */
public final class Settings {
    private Settings() {}

    public static boolean hudShow = true;      // the list in the corner
    public static boolean hudRight = false;    // which corner
    public static int hudScale = 50;           // percent
    public static int hudLines = 8;            // item rows in the list
    public static boolean hudBar = true;       // progress bar
    public static boolean hudHints = true;     // key hints under the list
    public static String matMode = "auto";     // auto | layer | all
    public static int ghostAlpha = 55;         // percent opacity of the see-through blocks
    public static int range = 96;              // preview distance in blocks
    public static boolean redOutline = true;   // red boxes on blocks in the way
    public static boolean checkFacing = true;  // blocks only count when they face the right way (stairs, logs, slabs...)
    public static boolean autoLayer = true;    // move to the next layer when one is finished
    public static boolean startLayer1 = true;  // loading a build starts on layer 1
    public static boolean nextHint = true;     // highlight the next block to place
    public static boolean ghostPick = true;    // middle-click a ghost block to hold it
    public static boolean chestFinder = true;  // highlight chests that hold blocks you need
    public static boolean hudStats = true;
    public static boolean sounds = true;       // ding when a layer / the build is finished     // build timer + blocks/minute in the corner list

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("buildblueprint").resolve("settings.properties"); }

    public static void load() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file())) { p.load(r); } catch (IOException e) { return; }
        hudShow = b(p, "hudShow", hudShow); hudRight = b(p, "hudRight", hudRight); hudScale = i(p, "hudScale", hudScale);
        if (p.getProperty("settingsVersion") == null && hudScale == 100) hudScale = 50;   // 1.2.8: the old default of 100% was too big
        hudLines = i(p, "hudLines", hudLines); hudBar = b(p, "hudBar", hudBar); hudHints = b(p, "hudHints", hudHints);
        matMode = p.getProperty("matMode", matMode); ghostAlpha = i(p, "ghostAlpha", ghostAlpha); range = i(p, "range", range);
        redOutline = b(p, "redOutline", redOutline); autoLayer = b(p, "autoLayer", autoLayer); startLayer1 = b(p, "startLayer1", startLayer1);
        nextHint = b(p, "nextHint", nextHint); ghostPick = b(p, "ghostPick", ghostPick); chestFinder = b(p, "chestFinder", chestFinder); checkFacing = b(p, "checkFacing", checkFacing); dev.buildblueprint.core.Orient.on = checkFacing; hudStats = b(p, "hudStats", hudStats); sounds = b(p, "sounds", sounds);
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("settingsVersion", "2");
        p.setProperty("hudShow", "" + hudShow); p.setProperty("hudRight", "" + hudRight); p.setProperty("hudScale", "" + hudScale);
        p.setProperty("hudLines", "" + hudLines); p.setProperty("hudBar", "" + hudBar); p.setProperty("hudHints", "" + hudHints);
        p.setProperty("matMode", matMode); p.setProperty("ghostAlpha", "" + ghostAlpha); p.setProperty("range", "" + range);
        p.setProperty("redOutline", "" + redOutline); p.setProperty("autoLayer", "" + autoLayer); p.setProperty("startLayer1", "" + startLayer1);
        p.setProperty("nextHint", "" + nextHint); p.setProperty("ghostPick", "" + ghostPick); p.setProperty("chestFinder", "" + chestFinder); p.setProperty("checkFacing", "" + checkFacing); p.setProperty("hudStats", "" + hudStats); p.setProperty("sounds", "" + sounds);
        try { Files.createDirectories(file().getParent()); try (Writer w = Files.newBufferedWriter(file())) { p.store(w, "Build Blueprint settings"); } } catch (IOException ignored) {}
    }

    private static boolean b(Properties p, String k, boolean d) { String v = p.getProperty(k); return v == null ? d : v.equals("true"); }
    private static int i(Properties p, String k, int d) { try { return Integer.parseInt(p.getProperty(k)); } catch (Exception e) { return d; } }
}
