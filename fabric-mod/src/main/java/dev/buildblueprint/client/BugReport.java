package dev.buildblueprint.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * "Report a bug" (Settings): puts the mod version, Minecraft version, loader, Java, system, the loaded build and the
 * last problem the mod had into the clipboard, and opens a new GitHub issue with all of that already filled in.
 * Nothing is sent anywhere by itself: you read it on the page and press Submit if you want to.
 */
public final class BugReport {
    private BugReport() {}

    public static final String ISSUES = "https://github.com/CosmicLogic0/block-blueprint/issues/new";

    public static String text() {
        StringBuilder sb = new StringBuilder();
        sb.append("**What happened?** (please write what you did and what went wrong)\n\n\n\n");
        sb.append("---\n");
        sb.append("Build Blueprint: ").append(modVersion()).append('\n');
        sb.append("Minecraft: ").append(dev.buildblueprint.core.SchemCodec.minecraftVersion()).append(" (").append(loader()).append(")\n");
        sb.append("Java: ").append(System.getProperty("java.version")).append('\n');
        sb.append("System: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch")).append('\n');
        var s = BuildBlueprintClient.SESSION;
        if (s.loaded()) {
            var bp = s.blueprint();
            sb.append("Build loaded: ").append(bp.sx).append(" x ").append(bp.sy).append(" x ").append(bp.sz).append(", ").append(bp.blockCount()).append(" blocks, ")
                    .append(s.locked ? "placed" : "not placed").append(s.layer >= 0 ? ", layer " + (s.layer + 1) : "").append('\n');
        } else sb.append("Build loaded: none\n");
        String last = Safe.last();
        sb.append("Last problem: ").append(last == null ? "none" : "\n```\n" + last + "\n```").append('\n');
        return sb.toString();
    }

    /** Copies the report and opens the GitHub page. True if the page could be opened. */
    public static boolean send() {
        String t = text();
        try { Minecraft.getInstance().keyboardHandler.setClipboard(t); } catch (Throwable ignored) {}
        String body = t.length() > 5000 ? t.substring(0, 5000) + "\n(cut short - the whole report is in your clipboard)" : t;
        String url = ISSUES + "?title=" + enc("Bug: ") + "&body=" + enc(body);
        return open(url);
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20"); } catch (Exception e) { return ""; }
    }

    /** Opens a web page in the normal browser (the same way on every Minecraft version). */
    static boolean open(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] cmd = os.contains("win") ? new String[]{"rundll32", "url.dll,FileProtocolHandler", url}
                : os.contains("mac") ? new String[]{"open", url} : new String[]{"xdg-open", url};
        try { new ProcessBuilder(cmd).start(); return true; } catch (Throwable t) { return false; }
    }

    private static String modVersion() {
        try { return FabricLoader.getInstance().getModContainer("buildblueprint").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"); }
        catch (Throwable t) { return "?"; }
    }

    private static String loader() {
        if (has("net.neoforged.fml.ModList")) return "NeoForge";
        if (has("net.minecraftforge.fml.ModList")) return "Forge";
        return "Fabric";
    }
    private static boolean has(String c) { try { Class.forName(c, false, BugReport.class.getClassLoader()); return true; } catch (Throwable t) { return false; } }
}
