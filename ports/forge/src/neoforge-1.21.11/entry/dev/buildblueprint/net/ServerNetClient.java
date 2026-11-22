package dev.buildblueprint.net;

import dev.buildblueprint.client.SyncClient;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** The client end on NeoForge 1.21.6+: only loaded on the client. */
public final class ServerNetClient {
    private ServerNetClient() {}

    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(BBPayload.TYPE, (payload, ctx) -> Minecraft.getInstance().execute(() -> SyncClient.receive(payload.data()))));
        SyncClient.canSend = () -> { var c = Minecraft.getInstance().getConnection(); return c != null && c.hasChannel(BBPayload.TYPE); };
        SyncClient.sender = d -> ClientPacketDistributor.sendToServer(new BBPayload(d));
    }
}
