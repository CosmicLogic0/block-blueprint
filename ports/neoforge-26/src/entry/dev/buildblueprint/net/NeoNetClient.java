package dev.buildblueprint.net;

import dev.buildblueprint.client.SyncClient;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** The client end on NeoForge: only loaded on the client. */
public final class NeoNetClient {
    private NeoNetClient() {}

    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent e) ->
                e.register(BBPayload.TYPE, (payload, ctx) -> Minecraft.getInstance().execute(() -> SyncClient.receive(payload.data()))));
        SyncClient.canSend = () -> { var c = Minecraft.getInstance().getConnection(); return c != null && c.hasChannel(BBPayload.TYPE); };
        SyncClient.sender = d -> ClientPacketDistributor.sendToServer(new BBPayload(d));
    }
}
