package dev.buildblueprint.net;

import dev.buildblueprint.core.sync.Proto;
import dev.buildblueprint.core.sync.Relay;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Build Blueprint server part on Fabric (1.16.4 - 1.20.4; also inside singleplayer and LAN worlds): passes shared
 * builds and invites between players who have the mod. If this Fabric API is too old for it, it just stays off.
 */
public final class NetInit implements ModInitializer {
    public static final ResourceLocation ID = new ResourceLocation("buildblueprint", "sync");
    private static MinecraftServer server;
    private static Relay<ServerPlayer> relay;

    @Override public void onInitialize() {
        try {
            ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; relay = new Relay<>(new Bridge(), "fabric"); });
            ServerLifecycleEvents.SERVER_STOPPED.register(s -> { server = null; relay = null; });
            ServerPlayNetworking.registerGlobalReceiver(ID, (s, player, handler, buf, sender) -> {
                byte[] d = new byte[buf.readableBytes()];
                buf.readBytes(d);
                s.execute(() -> { if (relay != null) relay.receive(player, d); });
            });
            ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> { ServerPlayer p = handler.player; s.execute(() -> { if (relay != null) relay.quit(p); }); });
        } catch (Throwable t) { System.out.println("[Build Blueprint] server part off (this Fabric API is too old): " + t); }
    }

    static FriendlyByteBuf buf(byte[] d) { return new FriendlyByteBuf(Unpooled.wrappedBuffer(d)); }

    private static final class Bridge implements Relay.Bridge<ServerPlayer> {
        @Override public void send(ServerPlayer p, byte[] msg) { if (ServerPlayNetworking.canSend(p, ID)) ServerPlayNetworking.send(p, ID, buf(msg)); }
        @Override public String name(ServerPlayer p) { return p.getName().getString(); }
        @Override public UUID id(ServerPlayer p) { return p.getUUID(); }
        @Override public ServerPlayer byName(String n) { return server == null ? null : server.getPlayerList().getPlayerByName(n); }
        @Override public void log(String s) { System.out.println("[Build Blueprint] " + s); }
    }

    static { if (!Proto.CHANNEL.equals("buildblueprint:sync")) throw new IllegalStateException(); }
}
