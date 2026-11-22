package dev.buildblueprint.net;

import dev.buildblueprint.client.SyncClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.PacketDistributor;

/** The client end on Forge: only loaded on the client. */
public final class ForgeNetClient {
    private ForgeNetClient() {}

    public static void init() {
        ForgeNet.toClient = d -> Minecraft.getInstance().execute(() -> SyncClient.receive(d));
        SyncClient.canSend = () -> { var c = Minecraft.getInstance().getConnection(); return c != null && ForgeNet.CHANNEL.isRemotePresent(c.getConnection()); };
        SyncClient.sender = d -> ForgeNet.CHANNEL.send(new BBPayload(d), PacketDistributor.SERVER.noArg());
    }
}
