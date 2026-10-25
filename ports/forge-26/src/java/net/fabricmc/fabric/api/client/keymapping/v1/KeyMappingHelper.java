package net.fabricmc.fabric.api.client.keymapping.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/** Stand-in for Fabric's key mapping helper (Forge 26.x). */
public final class KeyMappingHelper {
    private KeyMappingHelper() {}
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static boolean hooked;
    public static KeyMapping registerKeyMapping(KeyMapping k) {
        if (!hooked) { hooked = true; RegisterKeyMappingsEvent.BUS.addListener(e -> { for (KeyMapping m : KEYS) e.register(m); }); }
        KEYS.add(k);
        return k;
    }
}
