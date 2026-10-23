package dev.buildblueprint.client;

import java.util.HashSet;
import java.util.Set;

/**
 * Safety net: anything the mod does each frame / tick goes through here. If something goes wrong it is written to
 * the log and mentioned once in chat - the game keeps running instead of crashing.
 */
public final class Safe {
    private Safe() {}

    private static final Set<String> told = new HashSet<>();

    public static void run(String where, Runnable r) {
        try { r.run(); } catch (Throwable t) { report(where, t); }
    }

    public static void report(String where, Throwable t) {
        System.err.println("[Build Blueprint] problem in " + where + ": " + t);
        t.printStackTrace();
        if (told.add(where)) {
            try { Chat.err("Build Blueprint had a problem (" + where + ") and skipped it so the game keeps running. " + t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "")); }
            catch (Throwable ignored) {}
        }
    }
}
