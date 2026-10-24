package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Stand-in for Fabric's client tick events, backed by NeoForge's ClientTickEvent. */
public final class ClientTickEvents {
    private ClientTickEvents() {}
    public interface StartTick { void onStartTick(Minecraft mc); }
    public interface EndTick { void onEndTick(Minecraft mc); }
    public static final class Event<T> {
        final List<T> list = new ArrayList<>();
        public void register(T cb) { hook(); list.add(cb); }
    }
    public static final Event<StartTick> START_CLIENT_TICK = new Event<>();
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>();
    private static boolean hooked;
    private static void hook() {
        if (hooked) return;
        hooked = true;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> { Minecraft mc = Minecraft.getInstance(); for (StartTick t : START_CLIENT_TICK.list) t.onStartTick(mc); });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> { Minecraft mc = Minecraft.getInstance(); for (EndTick t : END_CLIENT_TICK.list) t.onEndTick(mc); });
    }
}
