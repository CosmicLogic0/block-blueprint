package dev.buildblueprint.client;

import dev.buildblueprint.core.ServersDat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod maker's own server, Logic Survival. It is only added to the multiplayer list when the player presses
 * "Add to my servers" (How it works > More helpers) - never by itself, and never again after they delete it.
 */
public final class CreatorServer {
    private CreatorServer() {}

    public static final String NAME = "Logic Survival";
    public static final String IP = "play.logicsurvival.fyi";

    private static Path file() { return FabricLoader.getInstance().getConfigDir().getParent().resolve("servers.dat"); }

    /** Already in the player's multiplayer list? */
    public static boolean added() {
        try { Path f = file(); return Files.exists(f) && ServersDat.has(Files.readAllBytes(f), IP); }
        catch (Exception e) { return false; }
    }

    /** Adds it to the end of the multiplayer list. Returns a short message for the player. */
    public static String add() {
        try {
            Path f = file();
            byte[] old = Files.exists(f) ? Files.readAllBytes(f) : null;
            if (old != null && ServersDat.has(old, IP)) return "Logic Survival is already in your server list.";
            byte[] now = ServersDat.withServer(old, NAME, IP);
            Path tmp = f.resolveSibling("servers.dat.bbtmp");
            Files.write(tmp, now);
            if (old != null) Files.write(f.resolveSibling("servers.dat_old"), old);   // the game's own backup name
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            return "Added! Open Multiplayer to join Logic Survival.";
        } catch (Exception e) {
            return "Couldn't add it - add " + IP + " in Multiplayer > Add Server.";
        }
    }
}
