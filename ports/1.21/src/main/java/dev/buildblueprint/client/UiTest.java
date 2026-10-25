package dev.buildblueprint.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Screenshot tour for the automatic launch tests only. Does nothing in normal play (the property is never set).
 * <ul>
 *   <li>{@code -Dbuildblueprint.uitest=1}: waits for the first menu, then opens each Build Blueprint screen.</li>
 *   <li>{@code -Dbuildblueprint.uitest=world}: waits until the player is in a world (the test joins a local server),
 *       loads a build in front of the player, places it (ghost blocks + corner list), opens every screen and starts a
 *       copy box - so the parts that only run in a world (drawing ghosts, the corner list, copy mode) are tested too.</li>
 * </ul>
 * Before each picture it prints "BBUITEST shot &lt;name&gt;"; the test script takes the screenshot.
 */
public final class UiTest {
    private UiTest() {}

    private static int tick = 0, step = -1;
    private static boolean world;
    private static String[] steps;
    private static final String[] MENU = {"settings", "settings-more", "saved", "build", "done"};
    private static final String[] WORLD = {"world-ghost", "world-placed", "build", "settings", "settings-more", "saved", "world-copy", "done"};

    public static void init() {
        String p = System.getProperty("buildblueprint.uitest");
        if (p == null) return;
        world = p.equals("world");
        steps = world ? WORLD : MENU;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("ui test", UiTest::tick));
    }

    private static void tick() {
        if (step < 0) {
            if (world) {   // wait until we're in the world with nothing on screen, then let the chunks load
                if (!PlayerInfo.inWorld()) { tick = 0; return; }
                if (Mc.screen() != null) Mc.setScreen(null);
                if (++tick == 1) System.out.println("BBUITEST in world");
                if (tick < 300) return;
            } else {       // wait until the game has shown its first menu (title screen, or the first-launch welcome) for a while
                var sc = Mc.screen();
                if (sc == null) { tick = 0; return; }
                if (++tick == 1) System.out.println("BBUITEST waiting on " + sc.getClass().getSimpleName());
                if (tick < 200) return;
            }
            step = 0; tick = 0; open(steps[0]);
            return;
        }
        if (++tick == 40) pressF2();                                              // Minecraft's own screenshot, once the screen has settled
        if (tick == 70) System.out.println("BBUITEST shot " + steps[step]);     // the test picks up the newest screenshot now
        if (tick < 110) return;
        tick = 0;
        if (++step >= steps.length) return;
        open(steps[step]);
    }

    /** Presses F2 through Minecraft's keyboard handler. Only works in the developer setup (names are scrambled in the real game). */
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
        } catch (Throwable ignored) {}   // the test script takes the picture itself instead
    }

    private static void loadTestBuild() {
        try {
            var list = SavedBuilds.list();
            if (list.isEmpty()) { System.out.println("BBUITEST no saved builds to load"); return; }
            var bp = SavedBuilds.blueprint(list.get(0));
            if (PlayerInfo.inWorld()) BuildBlueprintClient.SESSION.load(bp, PlayerInfo.x(), PlayerInfo.y(), PlayerInfo.z(), PlayerInfo.yaw());
            else BuildBlueprintClient.SESSION.load(bp, 0, 64, 0, 0f);
            System.out.println("BBUITEST loaded " + bp.blockCount() + " blocks");
        } catch (Exception e) { System.out.println("BBUITEST load failed: " + e); }
    }

    private static void open(String what) {
        switch (what) {
            case "world-ghost" -> { Mc.setScreen(null); loadTestBuild(); }
            case "world-placed" -> { Mc.setScreen(null); BuildBlueprintClient.setLocked(true); System.out.println("BBUITEST placed, layer " + (BuildBlueprintClient.SESSION.layer + 1)); }
            case "world-copy" -> {
                Mc.setScreen(null);
                BuildBlueprintClient.setLocked(false); BuildBlueprintClient.SESSION.clear();
                Copier.start();
                if (Copier.c1 != null) Copier.c2 = Copier.c1.offset(6, 4, 6);
            }
            case "settings" -> { SettingsScreen.more = false; Mc.setScreen(new SettingsScreen(null)); }
            case "settings-more" -> { SettingsScreen.more = true; Mc.setScreen(new SettingsScreen(null)); }
            case "saved" -> Mc.setScreen(new SavedBuildsScreen());
            case "build" -> {
                if (!BuildBlueprintClient.SESSION.loaded()) loadTestBuild();
                Mc.setScreen(new MaterialsScreen(true));
            }
            default -> { if (world) { Copier.cancel(); Mc.setScreen(null); } System.out.println("BBUITEST done"); }
        }
    }
}
