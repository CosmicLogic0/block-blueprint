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

    /** A key as people call it, from how Minecraft saves it ("key.keyboard.page.down" -> "Page Down"). */
    public static String pretty(String save) {
        if (save == null) return "?";
        boolean mouse = save.startsWith("key.mouse.");
        String s = save.replaceFirst("^key\\.(keyboard|mouse)\\.", "");
        switch (s) {
            case "unknown": return "not set";
            case "left.control": return "Ctrl";
            case "right.control": return "Right Ctrl";
            case "left.shift": return "Shift";
            case "right.shift": return "Right Shift";
            case "left.alt": return "Alt";
            case "right.alt": return "Right Alt";
            case "equal": return "=";
            case "minus": return "-";
            case "left.bracket": return "[";
            case "right.bracket": return "]";
            case "comma": return ",";
            case "period": return ".";
            case "slash": return "/";
            case "backslash": return "\\";
            case "semicolon": return ";";
            case "apostrophe": return "'";
            case "grave.accent": return "`";
            default: break;
        }
        if (mouse) {
            switch (s) {
                case "left": return "Left Click";
                case "right": return "Right Click";
                case "middle": return "Middle Click";
                default: return "Mouse " + s;
            }
        }
        if (s.startsWith("keypad.")) s = "numpad." + s.substring(7);
        StringBuilder b = new StringBuilder();
        for (String p : s.split("[._]")) {
            if (p.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return b.toString();
    }
}
