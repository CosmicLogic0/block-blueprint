package dev.buildblueprint.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.buildblueprint.core.KeyLogic;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Keyboard controls, all rebindable in Options > Controls > Key Binds > Build Blueprint.
 * Defaults: Up = forward, Down = back, Left/Right = sideways, = / - = up / down (also Ctrl+Up / Ctrl+Down),
 *           Page Down = rotate 90 degrees, Page Up = menus, Home / End = layer up / down, Insert = copy corner,
 *           Delete = place / unlock, Shift + arrows = move 5 blocks, Alt + arrows = shrink the copy box.
 * The keys you hold (Ctrl, Shift, Alt) aren't bound by default: Minecraft already uses them for sprint and sneak, and
 * in some versions two bindings on one key means only one of them works. Not set = the usual key; set one to use it.
 * Held keys are read straight from the keyboard for the same reason.
 * While copying a build (Insert), the arrows shape the copy box instead and Page Down stretches it to the block you look at.
 * The ids start with a_, b_, ... so Minecraft lists them in this order.
 */
public final class KeysModule {
    private KeysModule() {}

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("buildblueprint", "main"));
    private static KeyMapping left, right, forward, back, vertical, rotate, menu, layerUp, layerDown, copy, place, up, down, fast, shrink;
    private static final int REPEAT_DELAY = 8, REPEAT_EVERY = 2; // ticks: holding a key keeps moving it
    private static final int[] held = new int[9];
    private static int nameTick = 0;

    private static KeyMapping reg(String id, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.buildblueprint." + id, Mc.keyboard(), key, CATEGORY));
    }

    /** A key with nothing set (the player can set one). Built from Minecraft's own "no key", which changed in 26.3. */
    private static KeyMapping regNone(String id) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.buildblueprint." + id, InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY));
    }

    private static boolean unset(String save) { return save.equals(InputConstants.UNKNOWN.getName()) || save.endsWith(".unknown"); }

    public static void init() {
        forward = reg("a_forward", InputConstants.KEY_UP);
        back = reg("b_back", InputConstants.KEY_DOWN);
        left = reg("c_left", InputConstants.KEY_LEFT);
        right = reg("d_right", InputConstants.KEY_RIGHT);
        vertical = regNone("e_hold_vertical");   // not set = Ctrl (a new id, so the old Ctrl binding that clashed with Sprint is gone)
        rotate = reg("f_rotate", InputConstants.KEY_PAGEDOWN);
        menu = reg("g_menu", InputConstants.KEY_PAGEUP);
        layerUp = reg("h_layer_up", InputConstants.KEY_HOME);
        layerDown = reg("i_layer_down", InputConstants.KEY_END);
        copy = reg("j_copy", InputConstants.KEY_INSERT);
        place = reg("k_place", InputConstants.KEY_DELETE);
        up = reg("l_up", InputConstants.KEY_EQUALS);
        down = reg("m_down", InputConstants.KEY_MINUS);
        fast = regNone("n_fast");       // not set = Shift
        shrink = regNone("o_shrink");   // not set = Alt
        Modules.menuKey = menu;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("keys", () -> tick(mc)));
    }

    /** True on the tick the key is pressed, then repeatedly while it stays held. */
    private static boolean pulse(int slot, KeyMapping k) {
        boolean clicked = false;
        while (k.consumeClick()) clicked = true;
        if (!k.isDown()) { held[slot] = 0; return false; }
        int t = held[slot]++;
        return clicked || (t >= REPEAT_DELAY && (t - REPEAT_DELAY) % REPEAT_EVERY == 0);
    }

    /** Is this keyboard key held down right now (straight from the keyboard)? */
    private static boolean down(int key) { return Mc.keyDown(key); }

    /** Is this key held right now? Not set = either of the two usual keys (left/right Ctrl, Shift or Alt). */
    private static boolean hold(KeyMapping k, int usual1, int usual2) {
        String save = k.saveString();
        if (unset(save)) return down(usual1) || down(usual2);
        InputConstants.Key key = InputConstants.getKey(save);
        if (key.getType() == InputConstants.Type.MOUSE) return k.isDown();
        return (key.getValue() >= 0 && down(key.getValue())) || k.isDown();
    }

    private static String name(KeyMapping k, String usual) {
        String save = k.saveString();
        return unset(save) ? (usual != null ? usual : "not set") : KeyLogic.pretty(save);
    }

    /** Keeps the key hints on screen showing the keys as they're set now. */
    private static void names() {
        KeyNames.forward = name(forward, null); KeyNames.back = name(back, null); KeyNames.left = name(left, null); KeyNames.right = name(right, null);
        KeyNames.up = name(up, null); KeyNames.down = name(down, null); KeyNames.vertical = name(vertical, "Ctrl");
        KeyNames.rotate = name(rotate, null); KeyNames.menu = name(menu, null); KeyNames.layerUp = name(layerUp, null); KeyNames.layerDown = name(layerDown, null);
        KeyNames.copy = name(copy, null); KeyNames.place = name(place, null); KeyNames.fast = name(fast, "Shift"); KeyNames.shrink = name(shrink, "Alt");
    }

    private static boolean ctrl() { return hold(vertical, InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL); }
    private static boolean shift() { return hold(fast, InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT); }
    private static boolean alt() { return hold(shrink, InputConstants.KEY_LALT, InputConstants.KEY_RALT); }

    private static void tick(Minecraft mc) {
        if (nameTick++ % 20 == 0) Safe.run("key names", KeysModule::names);
        if (nameTick == 1) Safe.run("held keys", () -> { boolean ignored = shift(); });   // reads them once, so a problem shows up early
        if (mc.player == null) return;
        boolean noScreen = Mc.screen() == null;
        boolean l = pulse(0, left), r = pulse(1, right), f = pulse(2, forward), b = pulse(3, back);
        boolean rot = pulse(4, rotate);
        boolean u = pulse(7, up), dn = pulse(8, down);
        boolean lu = false, ld = false;
        while (layerUp.consumeClick()) lu = true;
        while (layerDown.consumeClick()) ld = true;
        boolean pl = false;
        while (place.consumeClick()) pl = true;
        boolean cp = false;
        while (copy.consumeClick()) cp = true;
        boolean openMenu = false;
        while (menu.consumeClick()) openMenu = true;
        if (!noScreen) return;
        if (openMenu) { BuildBlueprintClient.openMenuOrList(); return; }
        if (cp) { Copier.corner(); return; }
        Copier.tick();
        if (Copier.active) {   // copy mode: the arrow keys shape the selection box instead of moving the blueprint
            if (pl) { Copier.cancel(); return; }
            if (rot) { Copier.stretchTo(); return; }   // Page Down: reach the block you're looking at
            boolean modV = ctrl();
            String d = u ? "up" : dn ? "down" : l ? KeyLogic.direction(KeyLogic.Key.LEFT, modV) : r ? KeyLogic.direction(KeyLogic.Key.RIGHT, modV)
                    : f ? KeyLogic.direction(KeyLogic.Key.FORWARD, modV) : b ? KeyLogic.direction(KeyLogic.Key.BACK, modV) : null;
            if (d != null) Copier.nudge(d, shift() ? 5 : 1, alt(), PlayerInfo.yaw());
            return;
        }
        if (!BuildBlueprintClient.SESSION.loaded()) return;
        if (pl) { BuildBlueprintClient.setLocked(!BuildBlueprintClient.SESSION.locked); return; }
        if (lu) Layers.step(1);
        if (ld) Layers.step(-1);
        boolean mod = ctrl();
        String dir = u ? "up" : dn ? "down" : l ? KeyLogic.direction(KeyLogic.Key.LEFT, mod) : r ? KeyLogic.direction(KeyLogic.Key.RIGHT, mod)
                : f ? KeyLogic.direction(KeyLogic.Key.FORWARD, mod) : b ? KeyLogic.direction(KeyLogic.Key.BACK, mod) : null;
        var s = BuildBlueprintClient.SESSION;
        if ((dir != null || rot) && s.locked) { Chat.bar("Blueprint is placed (locked). Unlock it in the Page Up menu to move it."); return; }
        int step = shift() ? 5 : 1;   // hold Shift to move 5 blocks at a time
        if (dir != null) { s.move(dir, step, PlayerInfo.yaw()); Chat.bar("Moved " + dir + (step > 1 ? " 5" : "") + "  ·  Shift = 5 blocks  ·  Delete = place it"); }
        if (rot) { s.rotate(1); Chat.bar("Rotated to " + s.turns() * 90 + " degrees"); }
    }
}
