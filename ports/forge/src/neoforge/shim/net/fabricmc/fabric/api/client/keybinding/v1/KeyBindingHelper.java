package net.fabricmc.fabric.api.client.keybinding.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Stand-in for Fabric's key binding helper, backed by NeoForge's RegisterKeyMappingsEvent. */
public final class KeyBindingHelper {
    private KeyBindingHelper() {}
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static boolean hooked;
    public static KeyMapping registerKeyBinding(KeyMapping k) {
        if (!hooked) { hooked = true; dev.buildblueprint.client.NeoEntry.modBus.addListener((RegisterKeyMappingsEvent e) -> KEYS.forEach(e::register)); }
        KEYS.add(k);
        return k;
    }
}
