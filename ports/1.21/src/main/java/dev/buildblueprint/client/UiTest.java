package dev.buildblueprint.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Screenshot tour for the automatic launch tests only. Does nothing in normal play (the property is never set).
 * <ul>
 *   <li>{@code -Dbuildblueprint.uitest=1}: waits for the first menu, then opens each Build Blueprint screen.</li>
 *   <li>{@code -Dbuildblueprint.uitest=world}: waits for the title screen, joins the test's local server
 *       ({@code -Dbuildblueprint.uitest.server=host:port}) once the game has finished loading, waits until the player is in the world,
 *       loads a build in front of the player, places it (ghost blocks + corner list), opens every screen and starts a
 *       copy box - so the parts that only run in a world (drawing ghosts, the corner list, copy mode) are tested too.</li>
 * </ul>
 * Before each picture it prints "BBUITEST shot &lt;name&gt;"; the test script takes the screenshot.
 */
public final class UiTest {
    private UiTest() {}

    private static int tick = 0, step = -1;
    private static boolean world, joined;
    private static String[] steps;
    private static final String[] MENU = {"settings", "settings-more", "saved", "build", "done"};
    private static final String[] WORLD = {"world-ghost", "world-placed", "world-only", "build-hidden", "build", "swap", "swap-search", "remove", "build-missing", "help", "inventory", "settings-extras", "build-raw", "world-extras", "world-coop", "done-card", "templates", "settings", "settings-more", "saved", "world-special", "world-copy", "done"};

    public static void init() {
        String p = System.getProperty("buildblueprint.uitest");
        if (p == null) return;
        world = p.equals("world");
        steps = world ? WORLD : MENU;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("ui test", UiTest::tick));
    }

    private static void tick() {
        if (step < 0) {
            if (world) {   // join the server from the title screen, wait until we're in the world, then let the chunks load
                if (!PlayerInfo.inWorld()) {
                    if (joined) return;
                    if (Mc.screen() == null) { tick = 0; return; }
                    if (++tick == 1) System.out.println("BBUITEST waiting on " + Mc.screen().getClass().getSimpleName());
                    if (tick >= 200) { joined = true; tick = 0; connect(System.getProperty("buildblueprint.uitest.server", "127.0.0.1:25565")); }
                    return;
                }
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


    /**
     * Joins a server like the Multiplayer screen does. Found by parameter types, not names, so the same code works on
     * every version (1.16 opens the connect screen directly; 1.17+ has a static method that takes the address).
     */
    private static void connect(String address) {
        String host = address.contains(":") ? address.substring(0, address.indexOf(':')) : address;
        int port = address.contains(":") ? Integer.parseInt(address.substring(address.indexOf(':') + 1)) : 25565;
        System.out.println("BBUITEST joining " + host + ":" + port);
        try {
            Object mc = net.minecraft.client.Minecraft.getInstance();
            Object parent = Mc.screen();
            Class<?> cs = net.minecraft.client.gui.screens.ConnectScreen.class;
            Class<?> scr = net.minecraft.client.gui.screens.Screen.class, mcc = net.minecraft.client.Minecraft.class;
            Class<?> sd = net.minecraft.client.multiplayer.ServerData.class;
            for (java.lang.reflect.Method m : cs.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class || p.length < 4 || p[0] != scr || p[1] != mcc) continue;
                Object[] a = new Object[p.length];
                a[0] = parent; a[1] = mc;
                for (int i = 2; i < p.length; i++) {
                    if (p[i] == sd) a[i] = serverData(sd, host + ":" + port);
                    else if (p[i] == boolean.class) a[i] = false;
                    else {
                        try { a[i] = p[i].getConstructor(String.class, int.class).newInstance(host, port); } catch (NoSuchMethodException e) { a[i] = null; }
                    }
                }
                m.setAccessible(true);
                m.invoke(null, a);
                return;
            }
            for (java.lang.reflect.Constructor<?> c : cs.getDeclaredConstructors()) {   // 1.16: new ConnectScreen(parent, minecraft, host, port)
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 4 && p[0] == scr && p[1] == mcc && p[2] == String.class && p[3] == int.class) {
                    c.setAccessible(true);
                    Mc.setScreen((net.minecraft.client.gui.screens.Screen) c.newInstance(parent, mc, host, port));
                    return;
                }
            }
            System.out.println("BBUITEST connect failed: no way to join found");
        } catch (Throwable t) { System.out.println("BBUITEST connect failed: " + t); }
    }

    private static Object serverData(Class<?> sd, String ip) throws ReflectiveOperationException {
        for (java.lang.reflect.Constructor<?> c : sd.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length != 3 || p[0] != String.class || p[1] != String.class) continue;
            if (p[2] == boolean.class) return c.newInstance("Test", ip, false);
            if (p[2].isEnum()) { Object[] k = p[2].getEnumConstants(); return c.newInstance("Test", ip, k[k.length - 1]); }   // Type.OTHER
        }
        throw new NoSuchMethodException("ServerData constructor");
    }

    private static void loadTestBuild() {
        try {
            var list = SavedBuilds.list();
            if (list.isEmpty()) { System.out.println("BBUITEST no saved builds to load"); return; }
            var bp = SavedBuilds.blueprint(list.get(0));
            if (PlayerInfo.inWorld()) BuildBlueprintClient.SESSION.load(bp, PlayerInfo.x(), PlayerInfo.y(), PlayerInfo.z(), PlayerInfo.yaw());
            else BuildBlueprintClient.SESSION.load(bp, 0, 64, 0, 0f);
            System.out.println("BBUITEST loaded " + bp.blockCount() + " blocks");
            try {   // direction check: a stair placed upside down and facing south, against one that should face north
                String probe = "minecraft:oak_stairs[facing=south,half=top]";
                String seen = V.parseState(probe).toString();
                System.out.println("BBUITEST direction check: " + seen + " -> right way " + dev.buildblueprint.core.Orient.ok("minecraft:oak_stairs[facing=north,half=bottom]", seen)
                        + " (" + dev.buildblueprint.core.Orient.fix("minecraft:oak_stairs[facing=north,half=bottom]", seen) + "), same way " + dev.buildblueprint.core.Orient.ok(probe, seen));
            } catch (Exception e) { System.out.println("BBUITEST direction check failed: " + e); }
            System.out.println("BBUITEST renamed blocks: chain -> " + Swapper.fixState("minecraft:chain[axis=y]") + ", iron_chain -> " + Swapper.fixState("minecraft:iron_chain[axis=y]") + ", grass -> " + Swapper.fixState("minecraft:grass"));
        } catch (Exception e) { System.out.println("BBUITEST load failed: " + e); }
    }

    private static void open(String what) {
        switch (what) {
            case "world-ghost" -> { Mc.setScreen(null); loadTestBuild(); }
            case "world-placed" -> { Mc.setScreen(null); BuildBlueprintClient.setLocked(true); System.out.println("BBUITEST placed, layer " + (BuildBlueprintClient.SESSION.layer + 1)); }
            case "world-special" -> {   // blocks the game draws in a special way: their ghosts use look-alike blocks
                Mc.setScreen(null);
                String[] pal = {"minecraft:chest[facing=north,type=single,waterlogged=false]", "minecraft:ender_chest[facing=north,waterlogged=false]",
                        "minecraft:oak_sign[rotation=8,waterlogged=false]", "minecraft:red_bed[facing=north,occupied=false,part=foot]",
                        "minecraft:white_banner[rotation=8]", "minecraft:skeleton_skull[rotation=8]", "minecraft:shulker_box[facing=up]", "minecraft:trapped_chest[facing=north,type=single,waterlogged=false]"};
                int[] cells = new int[pal.length * 2];
                for (int i = 0; i < pal.length; i++) cells[i * 2] = i + 1;
                BuildBlueprintClient.setLocked(false);
                BuildBlueprintClient.SESSION.load(new dev.buildblueprint.core.Blueprint("Special blocks", pal.length * 2, 1, 1, pal, cells), PlayerInfo.x(), PlayerInfo.y(), PlayerInfo.z(), PlayerInfo.yaw());
                StringBuilder sb = new StringBuilder();
                for (String st : pal) sb.append(dev.buildblueprint.core.MaterialMap.blockId(st)).append(" -> ").append(String.join("/", dev.buildblueprint.core.Standin.of(dev.buildblueprint.core.MaterialMap.blockId(st)))).append("; ");
                System.out.println("BBUITEST stand-ins: " + sb);
            }
            case "world-copy" -> {
                Mc.setScreen(null);
                BuildBlueprintClient.setLocked(false); BuildBlueprintClient.SESSION.clear();
                Copier.start();
                if (Copier.c1 != null) Copier.c2 = Copier.c1.offset(6, 4, 6);
            }
            case "world-only" -> {   // the eye: shift-click shows only one block's ghosts
                Mc.setScreen(null);
                Materials.refresh();
                var rows = Materials.rows();
                if (rows.isEmpty()) { System.out.println("BBUITEST nothing to hide"); return; }
                java.util.List<String> all = new java.util.ArrayList<>();
                for (Materials.Row r : rows) all.add(r.id());
                Hide.only(rows.get(0).id(), all);
                System.out.println("BBUITEST showing only " + rows.get(0).id() + ", " + Hide.count() + " hidden");
            }
            case "build-hidden" -> Mc.setScreen(new MaterialsScreen(false));   // the list with the closed eyes
            case "settings" -> { SettingsScreen.more = false; Mc.setScreen(new SettingsScreen(null)); }
            case "settings-more" -> { SettingsScreen.more = true; Mc.setScreen(new SettingsScreen(null)); }
            case "saved" -> Mc.setScreen(new SavedBuildsScreen());
            case "build" -> {
                Hide.showAll();
                if (!BuildBlueprintClient.SESSION.loaded()) loadTestBuild();
                Mc.setScreen(new MaterialsScreen(true));
            }
            case "swap", "swap-search" -> {   // the swap screen for the most used block, then with a search typed
                Materials.refresh();
                var rows = Materials.rows();
                if (rows.isEmpty()) { System.out.println("BBUITEST no materials to swap"); return; }
                SwapScreen sw = new SwapScreen(new MaterialsScreen(), rows.get(0).id());
                Mc.setScreen(sw);
                System.out.println("BBUITEST swap options " + Swapper.allOptions().size() + " similar " + Swapper.similar(rows.get(0).id()).size());
                if (what.equals("swap-search")) sw.testSearch("stairs");
            }
            case "build-missing" -> {   // pretend the build has a block this version doesn't have (made in a newer version)
                var bp = BuildBlueprintClient.SESSION.blueprint();
                if (bp != null && bp.palette.length > 0) {
                    String victim = dev.buildblueprint.core.MaterialMap.blockId(bp.palette[bp.palette.length - 1]);
                    BuildBlueprintClient.SESSION.mapPalette(st -> dev.buildblueprint.core.MaterialMap.blockId(st).equals(victim) ? "futureblocks:glow_brick" : st);
                    Materials.refresh();
                    System.out.println("BBUITEST not in game " + Swapper.notInGame());
                }
                BuildBlueprintClient.setLocked(false);   // unplaced: the list shows the whole build, with the missing block in it
                Mc.setScreen(new MaterialsScreen(true));
            }
            case "help" -> Mc.setScreen(new HelpScreen(null, 3));
            case "settings-extras" -> { SettingsScreen.more = false; SettingsScreen.extras = true; Mc.setScreen(new SettingsScreen(null)); }
            case "build-raw" -> {   // Extras on: raw materials view, layer map, scaffold hints
                SettingsScreen.extras = false;
                Settings.rawMats = true; Settings.layerMap = true; Settings.scaffold = true;
                Materials.refresh();
                java.util.Map<String, Integer> need = new java.util.LinkedHashMap<>();
                for (Materials.Row r : Materials.rows()) if (r.missing() > 0) need.put(r.id(), r.missing());
                StringBuilder sb = new StringBuilder();
                for (dev.buildblueprint.core.Recipes.Raw r : dev.buildblueprint.core.Recipes.breakDown(need, id -> !Materials.stackFor(id).isEmpty()))
                    sb.append(r.count()).append(' ').append(r.id).append(r.smelt ? " (smelt)" : "").append("; ");
                System.out.println("BBUITEST raw materials: " + sb);
                System.out.println("BBUITEST mistakes " + Mistakes.count() + ", scaffold " + Materials.scaffold().size() + ", layer map " + Materials.mapW() + "x" + Materials.mapD());
                MaterialsScreen.testRaw(true);
                Mc.setScreen(new MaterialsScreen(false));
            }
            case "world-extras" -> { MaterialsScreen.testRaw(false); Mc.setScreen(null); if (Mistakes.count() > 0) Mistakes.show(); }
            case "world-coop" -> { Mistakes.stop(); Coop.set(2, 1); Materials.refresh(); System.out.println("BBUITEST co-op: " + Coop.where(BuildBlueprintClient.SESSION.blueprint()) + ", my blocks " + Materials.totalBlocks()); Mc.setScreen(null); }
            case "done-card" -> {
                Coop.off(); Settings.rawMats = false; Settings.layerMap = false; Settings.scaffold = false; Materials.refresh();
                Stats.completeAt = System.currentTimeMillis(); Stats.completeBlocks = Materials.totalBlocks(); Stats.completeMs = 754000; Stats.completePerMin = 23;
                Mc.setScreen(null);
            }
            case "templates" -> Mc.setScreen(new TemplatesScreen(null));
            case "remove" -> {   // take the least used block out of the build, show the list, then put it back
                Materials.refresh();
                var rows = Materials.rows();
                if (rows.isEmpty()) { System.out.println("BBUITEST nothing to remove"); return; }
                int before = BuildBlueprintClient.SESSION.blueprint().blockCount();
                int n = Swapper.remove(rows.get(rows.size() - 1).id());
                int after = BuildBlueprintClient.SESSION.blueprint().blockCount();
                boolean undone = BuildBlueprintClient.SESSION.undoSwap();
                System.out.println("BBUITEST removed " + n + " blocks (" + before + " -> " + after + "), undo " + undone + " -> " + BuildBlueprintClient.SESSION.blueprint().blockCount());
                Materials.refresh();
                Mc.setScreen(new MaterialsScreen(true));
            }
            case "inventory" -> {   // any container screen: the chest highlight hooks in here
                var pl = net.minecraft.client.Minecraft.getInstance().player;
                if (pl != null) Mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(pl));
            }
            default -> { if (world) { Copier.cancel(); Mc.setScreen(null); } System.out.println("BBUITEST done"); }
        }
    }
}
