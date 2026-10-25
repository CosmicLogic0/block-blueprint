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
        if (++tick == 40) System.out.println("BBUITEST shot " + STEPS[step]);   // screenshot after the screen has settled
        if (tick < 160) return;   // time for the test to press F2 and save the picture
        tick = 0;
        if (++step >= STEPS.length) return;
        open(STEPS[step]);
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
