package dev.buildblueprint.net;

import dev.buildblueprint.core.sync.Relay;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server part of Build Blueprint on Fabric (also runs inside singleplayer and LAN worlds): passes shared builds and
 * invites between the players who have the mod. Nothing here touches the world, and players without the mod see nothing.
 */
public final class NetInit implements ModInitializer {
    private static MinecraftServer server;
    private static Relay<ServerPlayer> relay;

    @Override public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(BBPayload.TYPE, BBPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BBPayload.TYPE, BBPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; relay = new Relay<>(new Bridge(), "fabric"); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { server = null; relay = null; });
        ServerPlayNetworking.registerGlobalReceiver(BBPayload.TYPE, (payload, ctx) -> {
            ServerPlayer p = ctx.player();
            ctx.server().execute(() -> { if (relay != null) relay.receive(p, payload.data()); });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> s.execute(() -> { if (relay != null) relay.quit(handler.getPlayer()); }));
    }

    private static final class Bridge implements Relay.Bridge<ServerPlayer> {
        @Override public void send(ServerPlayer p, byte[] msg) { if (ServerPlayNetworking.canSend(p, BBPayload.TYPE)) ServerPlayNetworking.send(p, new BBPayload(msg)); }
        @Override public String name(ServerPlayer p) { return p.getName().getString(); }
        @Override public UUID id(ServerPlayer p) { return p.getUUID(); }
        @Override public ServerPlayer byName(String n) { return server == null ? null : server.getPlayerList().getPlayerByName(n); }
        @Override public void log(String s) { System.out.println("[Build Blueprint] " + s); }
    }
}
