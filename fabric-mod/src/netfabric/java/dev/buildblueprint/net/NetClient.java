package dev.buildblueprint.net;

import dev.buildblueprint.client.SyncClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The client end: hands Build Blueprint messages from the server to SyncClient, and lets it send its own. */
public final class NetClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BBPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> SyncClient.receive(payload.data())));
        SyncClient.canSend = () -> ClientPlayNetworking.canSend(BBPayload.TYPE);
        SyncClient.sender = d -> ClientPlayNetworking.send(new BBPayload(d));
    }
}
