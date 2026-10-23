package dev.buildblueprint.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Calls that differ between Minecraft versions (this copy: 1.21.5). */
public final class V {
    private V() {}
    public static ResourceLocation id(String s) { return ResourceLocation.parse(s); }
    public static Block block(String id) { return BuiltInRegistries.BLOCK.getValue(id(id)); }
    public static boolean blockExists(String id) { return BuiltInRegistries.BLOCK.containsKey(id(id)); }
    public static HolderLookup<Block> blocks() { return BuiltInRegistries.BLOCK; }
    public static boolean solid(BlockState s) { return s.isSolidRender(); }
    public static int minY(Level l) { return l.getMinY(); }
    public static void chat(LocalPlayer p, Component c) { p.displayClientMessage(c, false); }
    public static int selected(Inventory inv) { return inv.getSelectedSlot(); }
    public static void select(Inventory inv, int slot) { inv.setSelectedSlot(slot); }
    public static boolean shift() { return Screen.hasShiftDown(); }
    public static boolean alt() { return Screen.hasAltDown(); }
    public static void tooltip(GuiGraphics g, Font f, List<? extends Component> lines, int x, int y) { g.renderComponentTooltip(f, List.copyOf(lines), x, y); }
    public static void hudPush(GuiGraphics g, float sc) { g.pose().pushPose(); g.pose().scale(sc, sc, 1f); }
    public static void hudPop(GuiGraphics g) { g.pose().popPose(); }
    public static Screen keyBinds(Screen parent) { return new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(parent, Minecraft.getInstance().options); }
    public static double reach(Minecraft mc) { return mc.player.blockInteractionRange(); }
    public static boolean glass(Block b) { return b instanceof net.minecraft.world.level.block.TransparentBlock; }
}
