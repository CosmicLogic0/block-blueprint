package dev.buildblueprint.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Screenshot tour for the automatic launch test only: when the game is started with -Dbuildblueprint.uitest=1 it waits for
 * the title screen, then opens each Build Blueprint screen in turn and prints "BBUITEST shot <name>" so the test can take a
 * screenshot. Does nothing in normal play (the property is never set).
 */
public final class UiTest {
    private UiTest() {}

    private static int tick = 0, step = -1;
    private static final String[] STEPS = {"settings", "settings-more", "saved", "build", "done"};

    public static void init() {
        if (!"1".equals(System.getProperty("buildblueprint.uitest"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("ui test", UiTest::tick));
    }

    private static void tick() {
        var sc = Mc.screen();
        if (step < 0) {   // wait until the game has shown its first menu (title screen, or the first-launch welcome) for a while
            if (sc == null) { tick = 0; return; }
            if (++tick == 1) System.out.println("BBUITEST waiting on " + sc.getClass().getSimpleName());
            if (tick < 200) return;
            step = 0; tick = 0; open(STEPS[0]);
            return;
        }
        if (++tick == 40) pressF2();                                              // Minecraft's own screenshot, once the screen has settled
        if (tick == 70) System.out.println("BBUITEST shot " + STEPS[step]);     // the test picks up the newest screenshot now
        if (tick < 110) return;
        tick = 0;
        if (++step >= STEPS.length) return;
        open(STEPS[step]);
    }

    /** Presses F2 through Minecraft's keyboard handler (works the same on every version, in the test's dev build). */
    private static void pressF2() {
        try {
            Object mc = net.minecraft.client.Minecraft.getInstance();
            Object kb = mc.getClass().getField("keyboardHandler").get(mc);
            Object win = mc.getClass().getMethod("getWindow").invoke(mc);
            long handle = 0;
            for (String n : new String[]{"getWindow", "handle"}) {
                try { handle = (Long) win.getClass().getMethod(n).invoke(win); break; } catch (ReflectiveOperationException ignored) {}
            }
            for (java.lang.reflect.Method m : kb.getClass().getDeclaredMethods()) {
                if (!m.getName().equals("keyPress")) continue;
                m.setAccessible(true);
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 5) { m.invoke(kb, handle, 291, 0, 1, 0); m.invoke(kb, handle, 291, 0, 0, 0); return; }   // (window, key, scancode, action, mods)
                if (p.length == 3) {                                                                                     // (window, action, KeyEvent) on newer versions
                    Object ev = p[2].getConstructor(int.class, int.class, int.class).newInstance(291, 0, 0);
                    m.invoke(kb, handle, 1, ev); m.invoke(kb, handle, 0, ev); return;
                }
            }
            System.out.println("BBUITEST no keyPress method found");
        } catch (Throwable t) { System.out.println("BBUITEST F2 failed: " + t); }
    }

    private static void open(String what) {
        switch (what) {
            case "settings" -> { SettingsScreen.more = false; Mc.setScreen(new SettingsScreen(null)); }
            case "settings-more" -> { SettingsScreen.more = true; Mc.setScreen(new SettingsScreen(null)); }
            case "saved" -> Mc.setScreen(new SavedBuildsScreen());
            case "build" -> {
                try {
                    var list = SavedBuilds.list();
                    if (!list.isEmpty()) BuildBlueprintClient.SESSION.load(SavedBuilds.blueprint(list.get(0)), 0, 64, 0, 0f);
                } catch (Exception ignored) {}
                Mc.setScreen(new MaterialsScreen(true));
            }
            default -> { System.out.println("BBUITEST done"); }
        }
    }
}
