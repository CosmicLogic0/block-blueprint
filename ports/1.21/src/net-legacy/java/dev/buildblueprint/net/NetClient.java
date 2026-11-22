package dev.buildblueprint.net;

import dev.buildblueprint.client.SyncClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The client end (1.16.4 - 1.20.4): hands Build Blueprint messages from the server to SyncClient. */
public final class NetClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        try {
            ClientPlayNetworking.registerGlobalReceiver(NetInit.ID, (client, handler, buf, sender) -> {
                byte[] d = new byte[buf.readableBytes()];
                buf.readBytes(d);
                client.execute(() -> SyncClient.receive(d));
            });
            SyncClient.canSend = () -> ClientPlayNetworking.canSend(NetInit.ID);
            SyncClient.sender = d -> ClientPlayNetworking.send(NetInit.ID, NetInit.buf(d));
        } catch (Throwable t) { System.out.println("[Build Blueprint] server part off (this Fabric API is too old): " + t); }
    }
}
