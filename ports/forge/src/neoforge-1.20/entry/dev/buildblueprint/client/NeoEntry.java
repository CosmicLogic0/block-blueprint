package dev.buildblueprint.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** NeoForge's way in (1.20.2-1.20.4). On a server it only runs the server part. */
@Mod("buildblueprint")
public class NeoEntry {
    public static IEventBus modBus;
    public NeoEntry(IEventBus bus) {
        modBus = bus;
        dev.buildblueprint.net.ServerNet.init(bus);   // the server part (shared builds / invites), on servers too
        if (FMLEnvironment.dist == Dist.CLIENT) ClientStart.run(bus);
    }
    static final class ClientStart { static void run(IEventBus bus) { new BuildBlueprintClient().onInitializeClient(); dev.buildblueprint.net.ServerNetClient.init(bus); } }
}
