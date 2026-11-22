package dev.buildblueprint.net;

import dev.buildblueprint.client.SyncClient;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** The client end on NeoForge 1.20.5 - 1.21.5: only loaded on the client. */
public final class ServerNetClient {
    private ServerNetClient() {}

    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        ServerNet.toClient = SyncClient::receive;
        SyncClient.canSend = () -> { var c = Minecraft.getInstance().getConnection(); return c != null && c.hasChannel(BBPayload.TYPE); };
        SyncClient.sender = d -> PacketDistributor.sendToServer(new BBPayload(d));
    }
}
