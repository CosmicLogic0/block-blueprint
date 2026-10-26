package net.fabricmc.fabric.api.client.screen.v1;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Stand-in for Fabric's screen events (Forge 1.20+): AFTER_INIT and afterRender(screen), backed by the loader's own
 * "screen opened" and "screen drawn" events. The event names changed a lot between versions, so they're looked up by
 * name and the right one is used; the screen and the drawing context are found by their type.
 */
public final class ScreenEvents {
    private ScreenEvents() {}
    public interface AfterInit { void afterInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight); }
    public interface AfterRender { void afterRender(Screen screen, GuiGraphics g, int mouseX, int mouseY, float delta); }
    public static final class Event<T> {
        final List<T> list = new ArrayList<>();
        public void register(T cb) { hook(); list.add(cb); }
    }
    public static final Event<AfterInit> AFTER_INIT = new Event<>();
    private static final Map<Screen, Event<AfterRender>> DRAW = new WeakHashMap<>();
    public static Event<AfterRender> afterRender(Screen screen) {
        hook();
        Event<AfterRender> e = DRAW.get(screen);
        if (e == null) { e = new Event<>(); DRAW.put(screen, e); }
        return e;
    }

    private static final String[] INIT = {"net.minecraftforge.client.event.ScreenEvent$Init$Post", "net.minecraftforge.client.event.ScreenEvent$InitScreenEvent$Post", "net.minecraftforge.client.event.GuiScreenEvent$InitGuiEvent$Post"};
    private static final String[] RENDER = {"net.minecraftforge.client.event.ScreenEvent$Render$Post", "net.minecraftforge.client.event.ScreenEvent$DrawScreenEvent$Post", "net.minecraftforge.client.event.GuiScreenEvent$DrawScreenEvent$Post"};
    private static boolean hooked;
    private static void hook() {
        if (hooked) return;
        hooked = true;
        listen(INIT, ev -> {
            Screen s = screenOf(ev);
            if (s == null) return;
            DRAW.remove(s);   // like Fabric: a screen's draw listeners start over each time it's set up
            Minecraft mc = Minecraft.getInstance();
            for (AfterInit c : new ArrayList<>(AFTER_INIT.list)) c.afterInit(mc, s, s.width, s.height);
        });
        listen(RENDER, ev -> {
            Screen s = screenOf(ev);
            Event<AfterRender> e = s == null ? null : DRAW.get(s);
            if (e == null || e.list.isEmpty()) return;
            Object g = call(ev, GuiGraphics.class);
            if (g == null) return;
            for (AfterRender c : e.list) c.afterRender(s, (GuiGraphics) g, 0, 0, 0f);
        });
    }

    private static Screen screenOf(Object ev) { Object s = call(ev, Screen.class); return s instanceof Screen ? (Screen) s : null; }

    /** Calls the event's getter that returns this type (getScreen / getGui, getGuiGraphics / getPoseStack / getMatrixStack...). */
    private static Object call(Object ev, Class<?> type) {
        try {
            for (Method m : ev.getClass().getMethods())
                if (m.getParameterTypes().length == 0 && type.isAssignableFrom(m.getReturnType()) && m.getDeclaringClass() != Object.class) return m.invoke(ev);
        } catch (Throwable ignored) {}
        return null;
    }

    /** Listens to the first of these events that exists, on whichever kind of event bus this loader has. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void listen(String[] names, Consumer<Object> fn) {
        for (String n : names) {
            Class<?> c;
            try { c = Class.forName(n); } catch (Throwable t) { continue; }
            try {   // newer Forge: one bus per event (Event.BUS)
                Object bus = c.getField("BUS").get(null);
                Method m = find(bus.getClass(), 1);
                if (m != null) { m.invoke(bus, fn); return; }
            } catch (Throwable ignored) {}
            for (String holder : new String[]{"net.minecraftforge.common.MinecraftForge", "net.neoforged.neoforge.common.NeoForge"}) {
                try {   // the shared game bus: addListener(priority, receiveCancelled, type, listener)
                    Object bus = Class.forName(holder).getField("EVENT_BUS").get(null);
                    Method m = find(bus.getClass(), 4);
                    if (m == null) continue;
                    Class<? extends Enum> pri = (Class<? extends Enum>) m.getParameterTypes()[0];
                    m.invoke(bus, Enum.valueOf(pri, "NORMAL"), false, c, fn);
                    return;
                } catch (Throwable ignored) {}
            }
        }
        System.out.println("[Build Blueprint] no screen event found (" + names[0] + "), chest highlight is off");
    }

    /** addListener with this many parameters, taken from a public interface or class so it can be called from here. */
    private static Method find(Class<?> k, int params) {
        List<Class<?>> todo = new ArrayList<>();
        todo.add(k);
        for (int i = 0; i < todo.size(); i++) {
            Class<?> c = todo.get(i);
            if (java.lang.reflect.Modifier.isPublic(c.getModifiers())) {
                for (Method m : c.getMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (!m.getName().equals("addListener") || p.length != params || p[params - 1] != Consumer.class) continue;
                    if (params == 4 && !(p[0].isEnum() && p[1] == boolean.class && p[2] == Class.class)) continue;
                    return m;
                }
            }
            for (Class<?> x : c.getInterfaces()) todo.add(x);
            if (c.getSuperclass() != null) todo.add(c.getSuperclass());
        }
        return null;
    }
}
