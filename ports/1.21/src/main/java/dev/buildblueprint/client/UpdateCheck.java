package dev.buildblueprint.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Once per game, after you join a world: if a newer Build Blueprint is out, says so in chat. Never blocks the game. */
public final class UpdateCheck {
    private UpdateCheck() {}

    private static volatile String latest;
    private static boolean asked, told;

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("update check", () -> {
            if (mc.player == null) return;
            if (!asked) { asked = true; fetch(); }
            if (!told && latest != null) {
                told = true;
                String mine = FabricLoader.getInstance().getModContainer("buildblueprint").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0");
                if (newer(latest, mine)) {
                    Chat.ok("Build Blueprint " + latest + " is out (you have " + mine + ").");
                    Chat.say("Update it in the CurseForge app, or download it from github.com/CosmicLogic0/block-blueprint/releases");
                }
            }
        }));
    }

    private static void fetch() {
        try {
            Thread t = new Thread(() -> {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://api.github.com/repos/CosmicLogic0/block-blueprint/releases/latest").openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(10000); c.setRequestProperty("User-Agent", "build-blueprint-mod");
                    StringBuilder body = new StringBuilder();
                    try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                        String line; while ((line = in.readLine()) != null) body.append(line);
                    }
                    Matcher m = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([0-9.]+)\"").matcher(body);
                    if (m.find()) latest = m.group(1);
                } catch (Exception ignored) {}
            }, "Build Blueprint update check");
            t.setDaemon(true); t.start();
        } catch (Exception ignored) {}
    }

    static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < 3; i++) {
            int p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }
    private static int parse(String s) { try { return Integer.parseInt(s.replaceAll("[^0-9].*$", "")); } catch (Exception e) { return 0; } }
}
