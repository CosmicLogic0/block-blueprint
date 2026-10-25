package dev.buildblueprint.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** NeoForge's way in. Client-side only, so NeoForge only loads it on the client. */
@Mod(value = "buildblueprint", dist = Dist.CLIENT)
public class NeoEntry {
    public static IEventBus modBus;
    public NeoEntry(IEventBus bus) {
        modBus = bus;
        new BuildBlueprintClient().onInitializeClient();
    }
}
