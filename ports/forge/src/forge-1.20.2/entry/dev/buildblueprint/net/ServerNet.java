package dev.buildblueprint.net;

import dev.buildblueprint.core.sync.Relay;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

/** The Build Blueprint server part on Forge 1.20.2 - 1.20.4 (optional channel: works with players and servers without it). */
public final class ServerNet {
    private ServerNet() {}

    public static final ResourceLocation ID = new ResourceLocation("buildblueprint", "sync");
    static EventNetworkChannel channel;
    private static Relay<ServerPlayer> relay;
    public static volatile java.util.function.Consumer<byte[]> toClient;

    /** Raw bytes as a vanilla custom payload (how Minecraft 1.20.2 - 1.20.4 sends them). */
    public record Raw(byte[] data) implements CustomPacketPayload {
        @Override public void write(FriendlyByteBuf b) { b.writeBytes(data); }
        @Override public ResourceLocation id() { return ID; }
    }

    public static void init() {
        channel = ChannelBuilder.named(ID).optional().eventNetworkChannel();
        channel.addListener(e -> {
            var ctx = e.getSource();
            FriendlyByteBuf b = e.getPayload();
            if (b == null) return;
            byte[] d = new byte[b.readableBytes()]; b.readBytes(d);
            ServerPlayer sp = ctx.getSender();
            ctx.enqueueWork(() -> { if (sp != null) relay().receive(sp, d); else if (toClient != null) toClient.accept(d); });
            ctx.setPacketHandled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> { if (relay != null && e.getEntity() instanceof ServerPlayer sp) relay.quit(sp); });
    }

    private static Relay<ServerPlayer> relay() {
        if (relay == null) relay = new Relay<>(new Relay.Bridge<ServerPlayer>() {
            @Override public void send(ServerPlayer p, byte[] msg) { p.connection.send(new ClientboundCustomPayloadPacket(new Raw(msg))); }
            @Override public String name(ServerPlayer p) { return p.getName().getString(); }
            @Override public UUID id(ServerPlayer p) { return p.getUUID(); }
            @Override public ServerPlayer byName(String n) { var s = ServerLifecycleHooks.getCurrentServer(); return s == null ? null : s.getPlayerList().getPlayerByName(n); }
            @Override public void log(String s) { System.out.println("[Build Blueprint] " + s); }
        }, "forge");
        return relay;
    }
}
