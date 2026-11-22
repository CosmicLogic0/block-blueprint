package dev.buildblueprint.net;

import dev.buildblueprint.client.SyncClient;
import net.neoforged.neoforge.network.PacketDistributor;

/** The client end on NeoForge 1.20.2 - 1.20.4: only loaded on the client. */
public final class ServerNetClient {
    private ServerNetClient() {}

    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        ServerNet.toClient = SyncClient::receive;
        SyncClient.sender = d -> PacketDistributor.SERVER.noArg().send(new BBPayload(d));
    }
}
