package net.fabricmc.fabric.api.client.keybinding.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Stand-in for Fabric's key binding helper (older Forge: ClientRegistry during client setup). */
public final class KeyBindingHelper {
    private KeyBindingHelper() {}
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static boolean hooked;
    public static KeyMapping registerKeyBinding(KeyMapping k) {
        if (!hooked) { hooked = true; FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> KEYS.forEach(ClientRegistry::registerKeyBinding))); }
        KEYS.add(k);
        return k;
    }
}
