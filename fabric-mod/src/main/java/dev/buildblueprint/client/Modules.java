package dev.buildblueprint.client;

import java.lang.reflect.Method;

/**
 * Optional parts (the menu screen, the 3D preview and the keybinds) live in src/menu, src/preview and src/keys and are only
 * compiled when enableMenu / enablePreview / enableKeys are true in gradle.properties. They are found by name at
 * runtime, so the core mod builds and works without them.
 */
final class Modules {
    private Modules() {}

    private static Method call(String cls, String method) {
        try { return Class.forName(cls).getMethod(method); } catch (ReflectiveOperationException | LinkageError e) { return null; }
    }

    /** True when the keybind module was compiled in and started. */
    static boolean keysActive = false;

    /** The "materials menu" key (set by the keybind module), so the menu can close with the same key. */
    static net.minecraft.client.KeyMapping menuKey;

    private static boolean run(String cls) {
        Method m = call("dev.buildblueprint.client." + cls, "init");
        if (m == null) return false;
        try { m.invoke(null); return true; } catch (ReflectiveOperationException | LinkageError e) { return false; }
    }

    static void init() {
        Settings.load();
        Materials.init();
        AutoBuild.init();
        GhostPick.init();
        Chests.init();
        SessionStore.init();
        run("PreviewModule");
        run("HudModule");
        keysActive = run("KeysModule");
    }

    /** Opens the materials screen. Returns false when the screen module isn't compiled in. */
    static boolean openMenu() {
        Method m = call("dev.buildblueprint.client.MenuModule", "open");
        if (m == null) return false;
        try { m.invoke(null); return true; } catch (ReflectiveOperationException | LinkageError e) { return false; }
    }
}
