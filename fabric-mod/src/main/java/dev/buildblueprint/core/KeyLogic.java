package dev.buildblueprint.core;

/** Which move a keybind means. Holding the vertical modifier (Ctrl by default) turns forward/back into up/down. */
public final class KeyLogic {
    private KeyLogic() {}

    public enum Key { LEFT, RIGHT, FORWARD, BACK }

    /** Returns the direction word understood by {@link Session#move}. */
    public static String direction(Key key, boolean verticalModifier) {
        return switch (key) {
            case LEFT -> "left";
            case RIGHT -> "right";
            case FORWARD -> verticalModifier ? "up" : "forward";   // default key: Up arrow   (Ctrl+Up = up)
            case BACK -> verticalModifier ? "down" : "back";       // default key: Down arrow (Ctrl+Down = down)
        };
    }
}
