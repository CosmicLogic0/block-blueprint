package net.fabricmc.fabric.api.client.keymapping.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Stand-in for Fabric's key mapping helper, backed by NeoForge's RegisterKeyMappingsEvent. */
public final class KeyMappingHelper {
    private KeyMappingHelper() {}
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static boolean hooked;
    public static KeyMapping registerKeyMapping(KeyMapping k) {
        if (!hooked) { hooked = true; dev.buildblueprint.client.NeoEntry.modBus.addListener((RegisterKeyMappingsEvent e) -> KEYS.forEach(e::register)); }
        KEYS.add(k);
        return k;
    }
}
