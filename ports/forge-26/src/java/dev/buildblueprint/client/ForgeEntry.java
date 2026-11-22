package dev.buildblueprint.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge's way in. On a server it only starts the server part (shared builds / invites); the rest is client-side. */
@Mod("buildblueprint")
public class ForgeEntry {
    public ForgeEntry(FMLJavaModLoadingContext context) {
        dev.buildblueprint.net.ForgeNet.init();
        if (FMLEnvironment.dist == Dist.CLIENT) ClientStart.run();
    }
    static final class ClientStart {   // kept in its own class so servers never load client code
        static void run() { new BuildBlueprintClient().onInitializeClient(); dev.buildblueprint.net.ForgeNetClient.init(); }
    }
}
