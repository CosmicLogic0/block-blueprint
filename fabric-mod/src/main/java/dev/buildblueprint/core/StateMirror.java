package dev.buildblueprint.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Flips a block-state string east <-> west (x -> -x), fixing facing, stair shapes, door hinges, connections, rails and signs. */
public final class StateMirror {
    private StateMirror() {}

    private static String swapEW(String v) { return v.equals("east") ? "west" : v.equals("west") ? "east" : v; }
    private static String swapLR(String v) {
        return switch (v) {
            case "left" -> "right"; case "right" -> "left";
            case "inner_left" -> "inner_right"; case "inner_right" -> "inner_left";
            case "outer_left" -> "outer_right"; case "outer_right" -> "outer_left";
            default -> v;
        };
    }

    public static String mirror(String state) {
        int b = state.indexOf('[');
        if (b < 0 || !state.endsWith("]")) return state;
        Map<String, String> kv = new LinkedHashMap<>();
        for (String p : state.substring(b + 1, state.length() - 1).split(",")) {
            int i = p.indexOf('=');
            if (i > 0) kv.put(p.substring(0, i), p.substring(i + 1));
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : kv.entrySet()) {
            String k = e.getKey(), v = e.getValue();
            switch (k) {
                case "facing", "orientation" -> v = v.replace("east", "\u0001").replace("west", "east").replace("\u0001", "west");
                case "shape" -> {
                    if (v.contains("_left") || v.contains("_right")) v = swapLR(v);
                    else v = v.replace("east", "\u0001").replace("west", "east").replace("\u0001", "west"); // rails
                }
                case "hinge", "type" -> v = swapLR(v);              // doors, chests (type=left/right)
                case "rotation" -> { try { v = String.valueOf((16 - Integer.parseInt(v)) % 16); } catch (NumberFormatException ignored) {} }
                case "east" -> k = "west";
                case "west" -> k = "east";
                default -> {}
            }
            out.put(k, v);
        }
        StringBuilder sb = new StringBuilder(state.substring(0, b)).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : out.entrySet()) { if (!first) sb.append(','); first = false; sb.append(e.getKey()).append('=').append(e.getValue()); }
        return sb.append(']').toString();
    }
}
