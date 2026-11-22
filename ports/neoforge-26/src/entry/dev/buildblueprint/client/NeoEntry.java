package dev.buildblueprint.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge's way in. On a server it only starts the server part (shared builds / invites); everything else is
 * client-side and only loaded on the client.
 */
@Mod("buildblueprint")
public class NeoEntry {
    public static IEventBus modBus;
    public NeoEntry(IEventBus bus, Dist dist) {
        modBus = bus;
        dev.buildblueprint.net.NeoNet.init(bus);
        if (dist.isClient()) {
            new BuildBlueprintClient().onInitializeClient();
            dev.buildblueprint.net.NeoNetClient.init(bus);
        }
    }
}
