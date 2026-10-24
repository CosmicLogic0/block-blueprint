package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

/** Stand-in for Fabric's client tick events (NeoForge 1.20.2-1.20.4: TickEvent with phases). */
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
        NeoForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (e.phase == TickEvent.Phase.START) for (StartTick t : START_CLIENT_TICK.list) t.onStartTick(mc);
            else for (EndTick t : END_CLIENT_TICK.list) t.onEndTick(mc);
        });
    }
}
