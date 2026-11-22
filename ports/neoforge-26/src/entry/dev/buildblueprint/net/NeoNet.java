package dev.buildblueprint.net;

import dev.buildblueprint.core.sync.Relay;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The Build Blueprint server part on NeoForge (dedicated servers, and singleplayer/LAN worlds): passes shared builds and
 * invites between players who have the mod. Optional channel: players and servers without the mod still connect.
 */
public final class NeoNet {
    private NeoNet() {}

    private static MinecraftServer server;
    private static Relay<ServerPlayer> relay;

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("1").optional();
            // the server's handler here; the client's is registered on the client (NeoNetClient)
            r.playBidirectional(BBPayload.TYPE, BBPayload.CODEC, (payload, ctx) -> {
                if (ctx.player() instanceof ServerPlayer sp && relay != null) relay.receive(sp, payload.data());
            });
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> { server = e.getServer(); relay = new Relay<>(new Bridge(), "neoforge"); });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> { server = null; relay = null; });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> { if (relay != null && e.getEntity() instanceof ServerPlayer sp) relay.quit(sp); });
    }

    private static final class Bridge implements Relay.Bridge<ServerPlayer> {
        @Override public void send(ServerPlayer p, byte[] msg) { if (p.connection.hasChannel(BBPayload.TYPE)) PacketDistributor.sendToPlayer(p, new BBPayload(msg)); }
        @Override public String name(ServerPlayer p) { return p.getName().getString(); }
        @Override public UUID id(ServerPlayer p) { return p.getUUID(); }
        @Override public ServerPlayer byName(String n) { return server == null ? null : server.getPlayerList().getPlayerByName(n); }
        @Override public void log(String s) { System.out.println("[Build Blueprint] " + s); }
    }
}
