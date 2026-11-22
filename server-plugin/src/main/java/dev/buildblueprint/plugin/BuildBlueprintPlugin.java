package dev.buildblueprint.plugin;

import dev.buildblueprint.core.sync.Proto;
import dev.buildblueprint.core.sync.Relay;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Build Blueprint server part for Paper / Spigot / Purpur. Players with the mod can see each other's placed builds
 * and invite each other in game (Build with friends > Invite & join on this server). It never changes the world;
 * players without the mod notice nothing.
 */
public final class BuildBlueprintPlugin extends JavaPlugin implements Listener {
    private Relay<Player> relay;

    @Override public void onEnable() {
        relay = new Relay<>(new Relay.Bridge<Player>() {
            @Override public void send(Player p, byte[] msg) { if (p.isOnline()) p.sendPluginMessage(BuildBlueprintPlugin.this, Proto.CHANNEL, msg); }
            @Override public String name(Player p) { return p.getName(); }
            @Override public UUID id(Player p) { return p.getUniqueId(); }
            @Override public Player byName(String n) { return Bukkit.getPlayerExact(n); }
            @Override public void log(String s) { getLogger().info(s); }
        }, "paper");
        getServer().getMessenger().registerOutgoingPluginChannel(this, Proto.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, Proto.CHANNEL, (channel, player, data) -> relay.receive(player, data));
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Build Blueprint server part is on: players with the mod can share builds and invite each other.");
    }

    @Override public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { relay.quit(e.getPlayer()); }
}
