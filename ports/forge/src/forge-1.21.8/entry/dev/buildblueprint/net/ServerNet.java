package dev.buildblueprint.net;

import dev.buildblueprint.core.sync.Relay;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/** The Build Blueprint server part on Forge 1.21.6+ (optional channel: works with players and servers without it). */
public final class ServerNet {
    private ServerNet() {}

    static Channel<CustomPacketPayload> CHANNEL;
    private static Relay<ServerPlayer> relay;
    public static volatile java.util.function.Consumer<byte[]> toClient;

    public static void init() {
        CHANNEL = ChannelBuilder.named(BBPayload.TYPE.id()).optional().payloadChannel().any().bidirectional()
                .add(BBPayload.TYPE, BBPayload.CODEC, (payload, ctx) -> {
                    ServerPlayer sp = ctx.getSender();
                    ctx.enqueueWork(() -> {
                        if (sp != null) relay().receive(sp, payload.data());
                        else if (toClient != null) toClient.accept(payload.data());
                    });
                    ctx.setPacketHandled(true);
                }).build();
        quitHook();
    }

    static void quitHook() { PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> { if (relay != null && e.getEntity() instanceof ServerPlayer sp) relay.quit(sp); }); }

    private static Relay<ServerPlayer> relay() {
        if (relay == null) relay = new Relay<>(new Relay.Bridge<ServerPlayer>() {
            @Override public void send(ServerPlayer p, byte[] msg) { if (CHANNEL.isRemotePresent(p.connection.getConnection())) CHANNEL.send(new BBPayload(msg), PacketDistributor.PLAYER.with(p)); }
            @Override public String name(ServerPlayer p) { return p.getName().getString(); }
            @Override public UUID id(ServerPlayer p) { return p.getUUID(); }
            @Override public ServerPlayer byName(String n) { var s = ServerLifecycleHooks.getCurrentServer(); return s == null ? null : s.getPlayerList().getPlayerByName(n); }
            @Override public void log(String s) { System.out.println("[Build Blueprint] " + s); }
        }, "forge");
        return relay;
    }
}
