package dev.buildblueprint.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.buildblueprint.core.KeyLogic;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Keyboard controls, all rebindable in Options > Controls > Key Binds > Build Blueprint.
 * Defaults: Up = forward, Down = back, Left/Right = sideways, Ctrl+Up = up, Ctrl+Down = down,
 *           Page Down = rotate 90 degrees, Page Up = menus, Home / End = layer up / down, Insert = copy corner,
 *           Delete = place / unlock, Shift + arrows = move 5 blocks.
 * While copying a build (Insert), the arrows shape the copy box instead and Page Down stretches it to the block you look at.
 * The ids start with a_, b_, ... so Minecraft lists them in this order.
 */
public final class KeysModule {
    private KeysModule() {}

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("buildblueprint", "main"));
    private static KeyMapping left, right, forward, back, vertical, rotate, menu, layerUp, layerDown, copy, place;
    private static final int REPEAT_DELAY = 8, REPEAT_EVERY = 2; // ticks: holding a key keeps moving it
    private static final int[] held = new int[7];

    private static KeyMapping reg(String id, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.buildblueprint." + id, Mc.keyboard(), key, CATEGORY));
    }

    public static void init() {
        forward = reg("a_forward", InputConstants.KEY_UP);
        back = reg("b_back", InputConstants.KEY_DOWN);
        left = reg("c_left", InputConstants.KEY_LEFT);
        right = reg("d_right", InputConstants.KEY_RIGHT);
        vertical = reg("e_vertical", InputConstants.KEY_LCONTROL);
        rotate = reg("f_rotate", InputConstants.KEY_PAGEDOWN);
        menu = reg("g_menu", InputConstants.KEY_PAGEUP);
        layerUp = reg("h_layer_up", InputConstants.KEY_HOME);
        layerDown = reg("i_layer_down", InputConstants.KEY_END);
        copy = reg("j_copy", InputConstants.KEY_INSERT);
        place = reg("k_place", InputConstants.KEY_DELETE);
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

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        boolean noScreen = Mc.screen() == null;
        boolean l = pulse(0, left), r = pulse(1, right), f = pulse(2, forward), b = pulse(3, back);
        boolean rot = pulse(4, rotate);
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
            boolean modV = vertical.isDown();
            String d = l ? KeyLogic.direction(KeyLogic.Key.LEFT, modV) : r ? KeyLogic.direction(KeyLogic.Key.RIGHT, modV)
                    : f ? KeyLogic.direction(KeyLogic.Key.FORWARD, modV) : b ? KeyLogic.direction(KeyLogic.Key.BACK, modV) : null;
            if (d != null) Copier.nudge(d, mc.hasShiftDown() ? 5 : 1, mc.hasAltDown(), PlayerInfo.yaw());
            return;
        }
        if (!BuildBlueprintClient.SESSION.loaded()) return;
        if (pl) { BuildBlueprintClient.setLocked(!BuildBlueprintClient.SESSION.locked); return; }
        if (lu) Layers.step(1);
        if (ld) Layers.step(-1);
        boolean mod = vertical.isDown();
        String dir = l ? KeyLogic.direction(KeyLogic.Key.LEFT, mod) : r ? KeyLogic.direction(KeyLogic.Key.RIGHT, mod)
                : f ? KeyLogic.direction(KeyLogic.Key.FORWARD, mod) : b ? KeyLogic.direction(KeyLogic.Key.BACK, mod) : null;
        var s = BuildBlueprintClient.SESSION;
        if ((dir != null || rot) && s.locked) { Chat.bar("Blueprint is placed (locked). Unlock it in the Page Up menu to move it."); return; }
        int step = mc.hasShiftDown() ? 5 : 1;   // hold Shift to move 5 blocks at a time
        if (dir != null) { s.move(dir, step, PlayerInfo.yaw()); Chat.bar("Moved " + dir + (step > 1 ? " 5" : "") + "  ·  Shift = 5 blocks  ·  Delete = place it"); }
        if (rot) { s.rotate(1); Chat.bar("Rotated to " + s.turns() * 90 + " degrees"); }
    }
}
