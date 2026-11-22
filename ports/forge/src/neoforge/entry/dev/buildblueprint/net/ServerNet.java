package dev.buildblueprint.net;

import dev.buildblueprint.core.sync.Relay;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** The Build Blueprint server part on NeoForge 1.20.5 - 1.21.5 (optional channel: works with players and servers without it). */
public final class ServerNet {
    private ServerNet() {}

    private static Relay<ServerPlayer> relay;
    public static volatile java.util.function.Consumer<byte[]> toClient;

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").optional().playBidirectional(BBPayload.TYPE, BBPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) relay().receive(sp, payload.data());
            else if (toClient != null) toClient.accept(payload.data());
        })));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> { if (relay != null && e.getEntity() instanceof ServerPlayer sp) relay.quit(sp); });
    }

    static Relay<ServerPlayer> relay() {
        if (relay == null) relay = new Relay<>(new Relay.Bridge<ServerPlayer>() {
            @Override public void send(ServerPlayer p, byte[] msg) { if (p.connection.hasChannel(BBPayload.TYPE)) PacketDistributor.sendToPlayer(p, new BBPayload(msg)); }
            @Override public String name(ServerPlayer p) { return p.getName().getString(); }
            @Override public UUID id(ServerPlayer p) { return p.getUUID(); }
            @Override public ServerPlayer byName(String n) { var s = ServerLifecycleHooks.getCurrentServer(); return s == null ? null : s.getPlayerList().getPlayerByName(n); }
            @Override public void log(String s) { System.out.println("[Build Blueprint] " + s); }
        }, "neoforge");
        return relay;
    }
}
