package dev.buildblueprint.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge's way in. Build Blueprint is client-side only, so it does nothing on a server. */
@Mod("buildblueprint")
public class ForgeEntry {
    public ForgeEntry(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) ClientStart.run();
    }
    static final class ClientStart {   // kept in its own class so servers never load client code
        static void run() { new BuildBlueprintClient().onInitializeClient(); }
    }
}
