package dev.buildblueprint.client;

import dev.buildblueprint.core.MaterialMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Auto-swap (Settings > More): when the stack in your hand runs out while you build, the next block the build needs
 * moves into your hand - more of the same block first, then the next block to place, then any block still needed.
 * Only for blocks the build needs, and never while a screen is open.
 */
public final class AutoSwap {
    private AutoSwap() {}

    private static String lastId;          // what was in your hand last tick
    private static boolean lastNeeded;     // ...and whether the build still needed it
    private static int lastSlot = -1, cooldown = 0;

    static void init() { ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("auto-swap", () -> tick(mc))); }

    private static void tick(Minecraft mc) {
        if (cooldown > 0) cooldown--;
        var player = mc.player;
        if (player == null || mc.gameMode == null) { lastId = null; return; }
        Inventory inv = player.getInventory();
        int sel = inv.getSelectedSlot();
        ItemStack hand = inv.getItem(sel);
        if (!hand.isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(hand.getItem()).toString();
            if (!id.equals(lastId) || sel != lastSlot) lastNeeded = needed(id);
            lastId = id; lastSlot = sel;
            return;
        }
        String was = lastId;
        boolean same = sel == lastSlot, wasNeeded = lastNeeded;
        lastId = null; lastNeeded = false;
        if (was == null || !same || !wasNeeded || cooldown > 0 || !Settings.autoSwap || Mc.screen() != null || !BuildBlueprintClient.SESSION.loaded()) return;
        int slot = find(inv, was);
        if (slot < 0 && Hint.state != null) { MaterialMap.Req r = MaterialMap.of(Hint.state); if (r != null) slot = find(inv, r.itemId()); }
        if (slot < 0) for (Materials.Row row : Materials.rows()) if (row.left() > 0) { slot = find(inv, row.id()); if (slot >= 0) break; }
        if (slot < 0) return;
        String name = inv.getItem(slot).getHoverName().getString();
        if (Inventory.isHotbarSlot(slot)) inv.setSelectedSlot(slot);
        else mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, sel, ContainerInput.SWAP, player);
        cooldown = 4;
        Chat.bar("Auto-swap: holding " + name);
    }

    private static boolean needed(String item) {
        if (!BuildBlueprintClient.SESSION.loaded()) return false;
        for (Materials.Row r : Materials.rows()) if (r.left() > 0 && r.id().equals(item)) return true;
        return false;
    }

    /** First slot (hotbar 0-8, then the rest of your inventory 9-35) holding this item, or -1. */
    private static int find(Inventory inv, String id) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(s.getItem()).toString())) return i;
        }
        return -1;
    }
}
