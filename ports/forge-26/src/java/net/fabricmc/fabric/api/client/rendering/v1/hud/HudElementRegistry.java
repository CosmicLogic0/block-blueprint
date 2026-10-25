package net.fabricmc.fabric.api.client.rendering.v1.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

/** Stand-in for Fabric's HUD registry, backed by Forge's AddGuiOverlayLayersEvent (chat is in the "post sleep" stack). */
public final class HudElementRegistry {
    private HudElementRegistry() {}
    private record Entry(Identifier before, Identifier id, HudElement element) {}
    private static final List<Entry> LIST = new ArrayList<>();
    private static boolean hooked;
    public static void attachElementBefore(Identifier before, Identifier id, HudElement element) {
        if (!hooked) { hooked = true; AddGuiOverlayLayersEvent.BUS.addListener(e -> { for (Entry x : LIST) e.getLayeredDraw().addBelow(ForgeLayeredDraw.POST_SLEEP_STACK, x.id(), x.before(), x.element()); }); }
        LIST.add(new Entry(before, id, element));
    }
}
