package dev.buildblueprint.net;

import dev.buildblueprint.client.SyncClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** The client end on Forge 1.20.2 - 1.20.4: only loaded on the client. */
public final class ServerNetClient {
    private ServerNetClient() {}

    public static void init() {
        if (ServerNet.channel == null) return;
        ServerNet.toClient = SyncClient::receive;
        SyncClient.sender = d -> { var c = Minecraft.getInstance().getConnection(); if (c != null) c.send(new ServerboundCustomPayloadPacket(new ServerNet.Raw(d))); };
    }
}
