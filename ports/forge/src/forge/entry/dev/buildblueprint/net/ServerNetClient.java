package dev.buildblueprint.net;

import dev.buildblueprint.client.SyncClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;

/** The client end on Forge 1.15 - 1.20.1: only loaded on the client. */
public final class ServerNetClient {
    private ServerNetClient() {}

    public static void init() {
        if (ServerNet.channel == null) return;
        ServerNet.toClient = SyncClient::receive;
        SyncClient.sender = d -> { if (Minecraft.getInstance().getConnection() != null) Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(ServerNet.ID, ServerNet.buf(d))); };
    }
}
