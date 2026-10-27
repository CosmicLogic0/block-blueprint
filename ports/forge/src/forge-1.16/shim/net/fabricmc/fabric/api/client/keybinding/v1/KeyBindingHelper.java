package net.fabricmc.fabric.api.client.keybinding.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Stand-in for Fabric's key binding helper (older Forge: ClientRegistry during client setup). */
public final class KeyBindingHelper {
    private KeyBindingHelper() {}
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static boolean hooked;
    public static KeyMapping registerKeyBinding(KeyMapping k) {
        if (!hooked) { hooked = true; FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e) -> {
            Runnable r = () -> KEYS.forEach(ClientRegistry::registerKeyBinding);
            try { e.getClass().getMethod("enqueueWork", Runnable.class).invoke(e, r); }   // Forge 1.16.3+
            catch (Throwable old) {   // Forge for 1.15-1.16.2 doesn't have enqueueWork yet
                try { Class.forName("net.minecraftforge.fml.DeferredWorkQueue").getMethod("runLater", Runnable.class).invoke(null, r); } catch (Throwable t) { r.run(); }
            }
        }); }
        KEYS.add(k);
        return k;
    }
}
