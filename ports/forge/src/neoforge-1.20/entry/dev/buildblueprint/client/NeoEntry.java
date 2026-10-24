package dev.buildblueprint.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** NeoForge's way in (1.20.2-1.20.4). Client-side only. */
@Mod("buildblueprint")
public class NeoEntry {
    public static IEventBus modBus;
    public NeoEntry(IEventBus bus) {
        modBus = bus;
        if (FMLEnvironment.dist == Dist.CLIENT) ClientStart.run();
    }
    static final class ClientStart { static void run() { new BuildBlueprintClient().onInitializeClient(); } }
}
