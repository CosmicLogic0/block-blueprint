package dev.buildblueprint.net;

import dev.buildblueprint.core.sync.Relay;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.Channel;

/**
 * The Build Blueprint server part on Forge (dedicated servers, and singleplayer/LAN worlds): passes shared builds and
 * invites between players who have the mod. Optional channel: players and servers without the mod still connect.
 */
public final class ForgeNet {
    private ForgeNet() {}

    private static MinecraftServer server;
    private static Relay<ServerPlayer> relay;
    public static volatile java.util.function.Consumer<byte[]> toClient;
    public static Channel<CustomPacketPayload> CHANNEL;

    public static void init() {
        CHANNEL = ChannelBuilder.named(BBPayload.TYPE.id()).optional().payloadChannel().any().bidirectional()
                .add(BBPayload.TYPE, BBPayload.CODEC, (payload, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    ctx.enqueueWork(() -> {
                        if (sp != null) { if (relay != null) relay.receive(sp, payload.data()); }
                        else if (toClient != null) toClient.accept(payload.data());
                    });
                    ctx.setPacketHandled(true);
                }).build();
        ServerStartedEvent.BUS.addListener(e -> { server = e.getServer(); relay = new Relay<>(new Bridge(), "forge"); });
        ServerStoppedEvent.BUS.addListener(e -> { server = null; relay = null; });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> { if (relay != null && e.getEntity() instanceof ServerPlayer sp) relay.quit(sp); });
    }

    private static final class Bridge implements Relay.Bridge<ServerPlayer> {
        @Override public void send(ServerPlayer p, byte[] msg) { if (CHANNEL.isRemotePresent(p.connection.getConnection())) CHANNEL.send(new BBPayload(msg), PacketDistributor.PLAYER.with(p)); }
        @Override public String name(ServerPlayer p) { return p.getName().getString(); }
        @Override public UUID id(ServerPlayer p) { return p.getUUID(); }
        @Override public ServerPlayer byName(String n) { return server == null ? null : server.getPlayerList().getPlayerByName(n); }
        @Override public void log(String s) { System.out.println("[Build Blueprint] " + s); }
    }
}
