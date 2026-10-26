package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.MaterialMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Block types you've hidden with the eye in the build menu: their ghost blocks (and red boxes) aren't drawn, so you
 * can look at one kind of block at a time. Only changes what you see; the list still counts them. Forgotten when
 * another build is loaded.
 */
public final class Hide {
    private Hide() {}

    private static final Set<String> HIDDEN = new HashSet<>();   // item ids
    private static final Map<String, String> ITEM = new HashMap<>();   // block state -> item id
    private static String forBuild;
    private static int ver;
    /** Goes up whenever what's hidden changes (so the ghosts are worked out again straight away). */
    public static int version() { check(); return ver; }

    private static void check() {
        Blueprint bp = BuildBlueprintClient.SESSION.blueprint();
        String name = bp == null ? null : bp.name + "|" + bp.sx + "x" + bp.sy + "x" + bp.sz;
        if (name == null ? forBuild != null : !name.equals(forBuild)) { HIDDEN.clear(); forBuild = name; ver++; }
    }

    public static boolean any() { check(); return !HIDDEN.isEmpty(); }
    public static int count() { check(); return HIDDEN.size(); }
    public static boolean item(String itemId) { check(); return HIDDEN.contains(itemId); }

    /** Is this block (a state from the blueprint) hidden? */
    public static boolean state(String st) {
        if (HIDDEN.isEmpty()) return false;
        String id = ITEM.get(st);
        if (id == null) {
            MaterialMap.Req r = MaterialMap.of(st);
            id = r == null ? MaterialMap.blockId(st) : r.itemId();
            if (ITEM.size() > 4096) ITEM.clear();
            ITEM.put(st, id);
        }
        return HIDDEN.contains(id);
    }

    public static void toggle(String itemId) { check(); if (!HIDDEN.remove(itemId)) HIDDEN.add(itemId); ver++; }

    /** Show only this block (hide all the others). Again on the same block: show everything. */
    public static void only(String itemId, Collection<String> all) {
        check();
        boolean already = !HIDDEN.contains(itemId) && HIDDEN.size() >= all.size() - 1 && all.size() > 1;
        HIDDEN.clear();
        if (!already) for (String id : all) if (!id.equals(itemId)) HIDDEN.add(id);
        ver++;
    }

    public static void showAll() { HIDDEN.clear(); ver++; }
}
