package net.fabricmc.fabric.api.client.rendering.v1.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Stand-in for Fabric's HUD registry, backed by NeoForge's RegisterGuiLayersEvent. */
public final class HudElementRegistry {
    private HudElementRegistry() {}
    private record Entry(Identifier before, Identifier id, HudElement element) {}
    private static final List<Entry> LIST = new ArrayList<>();
    private static boolean hooked;
    public static void attachElementBefore(Identifier before, Identifier id, HudElement element) {
        if (!hooked) { hooked = true; dev.buildblueprint.client.NeoEntry.modBus.addListener((RegisterGuiLayersEvent e) -> { for (Entry x : LIST) e.registerBelow(x.before(), x.id(), x.element()); }); }
        LIST.add(new Entry(before, id, element));
    }
}
