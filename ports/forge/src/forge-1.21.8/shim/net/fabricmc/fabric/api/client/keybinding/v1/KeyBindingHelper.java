package net.fabricmc.fabric.api.client.keybinding.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Stand-in for Fabric's key binding helper (Forge 1.21.6+). */
public final class KeyBindingHelper {
    private KeyBindingHelper() {}
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static boolean hooked;
    public static KeyMapping registerKeyBinding(KeyMapping k) {
        if (!hooked) { hooked = true; RegisterKeyMappingsEvent.getBus(FMLJavaModLoadingContext.get().getModBusGroup()).addListener(e -> { for (KeyMapping m : KEYS) e.register(m); }); }
        KEYS.add(k);
        return k;
    }
}
