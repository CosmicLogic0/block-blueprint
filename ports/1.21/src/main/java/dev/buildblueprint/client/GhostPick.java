package dev.buildblueprint.client;

import dev.buildblueprint.core.Blueprint;
import dev.buildblueprint.core.MaterialMap;
import dev.buildblueprint.core.Session;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Middle-click (pick block) on a see-through ghost block puts that block in your hand: from your hotbar, from
 * your inventory (swapped into the hotbar), or in Creative straight from the creative inventory.
 */
public final class GhostPick {
    private GhostPick() {}

    public record Target(BlockPos pos, String state) {}

    static void init() {
        // START of the tick: before vanilla handles the pick-block key, so a ghost hit takes priority.
        ClientTickEvents.START_CLIENT_TICK.register(mc -> Safe.run("pick from ghost", () -> {
            if (!Settings.ghostPick || mc.player == null || Mc.screen() != null) return;
            if (!BuildBlueprintClient.SESSION.loaded() || !BuildBlueprintClient.SESSION.visible) return;
            if (!mc.options.keyPickItem.isDown()) return;
            Target t = look(mc);
            if (t == null) return;
            boolean clicked = false;
            while (mc.options.keyPickItem.consumeClick()) clicked = true;   // vanilla won't see this click
            if (clicked) pick(mc, t);
        }));
    }

    /** The first ghost cell along your view, if it is closer than the real block you're looking at. */
    public static Target look(Minecraft mc) {
        Session s = BuildBlueprintClient.SESSION;
        if (!s.loaded() || mc.player == null || mc.level == null) return null;
        Blueprint bp = s.blueprint();
        Vec3 eye = mc.player.getEyePosition(), dir = mc.player.getViewVector(1f);
        double reach = Math.max(5, mc.player.blockInteractionRange() + 1);
        double real = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK ? mc.hitResult.getLocation().distanceTo(eye) : reach;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (double d = 0; d <= Math.min(reach, real + 0.6); d += 0.05) {
            int x = (int) Math.floor(eye.x + dir.x * d), y = (int) Math.floor(eye.y + dir.y * d), z = (int) Math.floor(eye.z + dir.z * d);
            if (x == lx && y == ly && z == lz) continue;
            lx = x; ly = y; lz = z;
            int bx = x - s.ox, by = y - s.oy, bz = z - s.oz;
            if (!s.layerShown(by)) continue;
            String st = bp.stateAt(bx, by, bz);
            if (st == null) continue;
            BlockState here = mc.level.getBlockState(p.set(x, y, z));
            if (BuiltInRegistries.BLOCK.getKey(here.getBlock()).toString().equals(MaterialMap.blockId(st))) continue; // built already
            if (!(here.isAir() || here.canBeReplaced())) continue;
            return new Target(p.immutable(), st);
        }
        return null;
    }

    static void pick(Minecraft mc, Target t) {
        MaterialMap.Req r = MaterialMap.of(t.state());
        if (r == null) return;
        ItemStack want = Materials.stackFor(r.itemId());
        if (want.isEmpty()) return;
        var player = mc.player;
        Inventory inv = player.getInventory();
        String name = want.getHoverName().getString();
        int slot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() == want.getItem()) { slot = i; break; }
        if (slot >= 0 && Inventory.isHotbarSlot(slot)) { V.select(inv, slot); Chat.bar("Holding " + name); return; }
        if (slot >= 9 && slot < 36) {
            // swap it into the hotbar slot you have selected (works in Survival too)
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, V.selected(inv), ClickType.SWAP, player);
            Chat.bar("Holding " + name + " (from your inventory)");
            return;
        }
        if (player.isCreative()) {
            int target = V.selected(inv);
            for (int i = 0; i < 9; i++) if (inv.getItem(i).isEmpty()) { target = i; break; }
            ItemStack give = want.copyWithCount(want.getMaxStackSize());
            inv.setItem(target, give);
            mc.gameMode.handleCreativeModeItemAdd(give, 36 + target);
            V.select(inv, target);
            Chat.bar("Holding " + name);
            return;
        }
        Chat.bar("You don't have any " + name + " - check the chest finder (green boxes)");
    }
}
