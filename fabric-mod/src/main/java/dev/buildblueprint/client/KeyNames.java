package dev.buildblueprint.client;

/**
 * What the mod's keys are set to right now (Options > Controls > Key Binds > Build Blueprint), so the key hints on
 * screen show the player's own keys. Kept up to date by the keys module; these are the defaults.
 */
public final class KeyNames {
    private KeyNames() {}

    public static volatile String forward = "Up", back = "Down", left = "Left", right = "Right", up = "=", down = "-",
            vertical = "Ctrl", rotate = "Page Down", menu = "Page Up", layerUp = "Home", layerDown = "End",
            copy = "Insert", place = "Delete", fast = "Shift", shrink = "Alt";

    /** The four move keys, or null while they're still the arrow keys. */
    private static String arrows() {
        return "Up".equals(forward) && "Down".equals(back) && "Left".equals(left) && "Right".equals(right) ? null : forward + "/" + back + "/" + left + "/" + right;
    }

    /** A key name in a hint written for the default keys, as the keys are set now. */
    public static String live(String k) {
        String a = arrows();
        switch (k) {
            case "Page Up": return menu;
            case "Page Down": return rotate;
            case "Delete": return place;
            case "Insert": return copy;
            case "Shift": return fast;
            case "Home / End": return layerUp + " / " + layerDown;
            case "Ctrl + Up / Down": case "Ctrl + Up/Down": return up + " / " + down;
            case "Arrow keys": case "Arrows": return a == null ? k : a;
            case "Alt + arrows": return a == null && "Alt".equals(shrink) ? k : shrink + " + " + (a == null ? "arrows" : a);
            default: return k;
        }
    }
}
