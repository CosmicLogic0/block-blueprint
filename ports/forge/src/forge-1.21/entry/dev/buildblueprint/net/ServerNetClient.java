package dev.buildblueprint.net;

import dev.buildblueprint.client.SyncClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.PacketDistributor;

/** The client end on Forge 1.20.5+: only loaded on the client. */
public final class ServerNetClient {
    private ServerNetClient() {}

    public static void init() {
        if (ServerNet.CHANNEL == null) return;
        ServerNet.toClient = SyncClient::receive;
        SyncClient.canSend = () -> { var c = Minecraft.getInstance().getConnection(); return c != null && ServerNet.CHANNEL.isRemotePresent(c.getConnection()); };
        SyncClient.sender = d -> ServerNet.CHANNEL.send(new BBPayload(d), PacketDistributor.SERVER.noArg());
    }
}
