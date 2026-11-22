package dev.buildblueprint.net;

import dev.buildblueprint.core.sync.Relay;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The Build Blueprint server part on Forge 1.15 - 1.20.1 (dedicated servers, and singleplayer/LAN worlds): passes shared
 * builds and invites between players who have the mod. The channel accepts players and servers without the mod.
 */
public final class ServerNet {
    private ServerNet() {}

    public static final ResourceLocation ID = new ResourceLocation("buildblueprint", "sync");
    static EventNetworkChannel channel;
    private static Relay<ServerPlayer> relay;
    public static volatile java.util.function.Consumer<byte[]> toClient;

    public static void init() {
        try {
            channel = NetworkRegistry.newEventChannel(ID, () -> "1", v -> true, v -> true);
            channel.addListener((NetworkEvent.ClientCustomPayloadEvent e) -> {   // from a player
                NetworkEvent.Context ctx = e.getSource().get();
                ServerPlayer p = ctx.getSender();
                byte[] d = bytes(e.getPayload());
                if (p != null) ctx.enqueueWork(() -> relay().receive(p, d));
                ctx.setPacketHandled(true);
            });
            channel.addListener((NetworkEvent.ServerCustomPayloadEvent e) -> {   // from the server (on the client)
                NetworkEvent.Context ctx = e.getSource().get();
                byte[] d = bytes(e.getPayload());
                if (toClient != null) ctx.enqueueWork(() -> toClient.accept(d));
                ctx.setPacketHandled(true);
            });
            MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> { if (relay != null && e.getEntity() instanceof ServerPlayer) relay.quit((ServerPlayer) e.getEntity()); });
        } catch (Throwable t) { System.out.println("[Build Blueprint] server part off: " + t); }
    }

    static byte[] bytes(FriendlyByteBuf b) { byte[] d = new byte[b.readableBytes()]; b.readBytes(d); return d; }
    static FriendlyByteBuf buf(byte[] d) { return new FriendlyByteBuf(Unpooled.wrappedBuffer(d)); }

    private static Relay<ServerPlayer> relay() {
        if (relay == null) relay = new Relay<>(new Relay.Bridge<ServerPlayer>() {
            @Override public void send(ServerPlayer p, byte[] msg) { p.connection.send(new ClientboundCustomPayloadPacket(ID, buf(msg))); }
            @Override public String name(ServerPlayer p) { return p.getName().getString(); }
            @Override public UUID id(ServerPlayer p) { return p.getUUID(); }
            @Override public ServerPlayer byName(String n) { return ServerLifecycleHooks.getCurrentServer() == null ? null : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerByName(n); }
            @Override public void log(String s) { System.out.println("[Build Blueprint] " + s); }
        }, "forge");
        return relay;
    }
}
