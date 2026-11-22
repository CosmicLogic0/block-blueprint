package dev.buildblueprint.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** NeoForge's way in. On a server it only runs the server part (shared builds / invites); the rest is client-side. */
@Mod("buildblueprint")
public class NeoEntry {
    public static IEventBus modBus;
    public NeoEntry(IEventBus bus) {
        modBus = bus;
        dev.buildblueprint.net.ServerNet.init(bus);
        if (FMLEnvironment.getDist() == Dist.CLIENT) ClientStart.run(bus);
    }
    static final class ClientStart {   // its own class so servers never load client code
        static void run(IEventBus bus) { new BuildBlueprintClient().onInitializeClient(); dev.buildblueprint.net.ServerNetClient.init(bus); }
    }
}
