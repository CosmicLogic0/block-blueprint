package dev.buildblueprint.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Calls that differ between Minecraft versions (this copy: 1.15.x). */
public final class V {
    private V() {}
    public static ResourceLocation id(String s) { return new ResourceLocation(s); }
    public static Block block(String id) { return Registry.BLOCK.get(id(id)); }
    public static boolean blockExists(String id) { return Registry.BLOCK.containsKey(id(id)); }
    public static boolean solid(BlockState s) { return s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO); }
    public static int minY(Level l) { return 0; }
    public static void chat(LocalPlayer p, Component c) { p.displayClientMessage(c, false); }
    public static int selected(Inventory inv) { return inv.selected; }
    public static void select(Inventory inv, int slot) { inv.selected = slot; }
    public static boolean shift() { return Screen.hasShiftDown(); }
    public static boolean alt() { return Screen.hasAltDown(); }
    public static void tooltip(GuiGraphics g, Font f, List<? extends Component> lines, int x, int y) { g.renderComponentTooltip(f, new java.util.ArrayList<>(lines), x, y); }
    public static void hudPush(GuiGraphics g, float sc) { com.mojang.blaze3d.systems.RenderSystem.pushMatrix(); com.mojang.blaze3d.systems.RenderSystem.scalef(sc, sc, 1f); }
    public static void hudPop(GuiGraphics g) { com.mojang.blaze3d.systems.RenderSystem.popMatrix(); }
    public static Screen keyBinds(Screen parent) { return new net.minecraft.client.gui.screens.controls.ControlsScreen(parent, Minecraft.getInstance().options); }
    public static double reach(Minecraft mc) { return mc.gameMode.getPickRange(); }
    public static boolean glass(Block b) { return b instanceof net.minecraft.world.level.block.HalfTransparentBlock; }
    public static String blockId(Block b) { return Registry.BLOCK.getKey(b).toString(); }
    public static String itemId(net.minecraft.world.item.Item i) { return Registry.ITEM.getKey(i).toString(); }
    public static Iterable<Block> allBlocks() { return Registry.BLOCK; }
    public static Iterable<net.minecraft.world.item.Item> allItems() { return Registry.ITEM; }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState parseState(String s) throws Exception {
        int b = s.indexOf('[');
        Block blk = block(b < 0 ? s : s.substring(0, b));
        BlockState st = blk.defaultBlockState();
        if (b >= 0) for (String kv : s.substring(b + 1, s.lastIndexOf(']')).split(",")) {
            String[] p = kv.split("=", 2); if (p.length < 2) continue;
            net.minecraft.world.level.block.state.properties.Property prop = blk.getStateDefinition().getProperty(p[0].trim());
            if (prop == null) continue;
            java.util.Optional val = prop.getValue(p[1].trim());
            if (val.isPresent()) st = st.setValue(prop, (Comparable) val.get());
        }
        return st;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String stateString(BlockState s) {
        StringBuilder sb = new StringBuilder(blockId(s.getBlock()));
        if (!s.getValues().isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (var e : s.getValues().entrySet()) {
                if (!first) sb.append(','); first = false;
                net.minecraft.world.level.block.state.properties.Property p = e.getKey();
                sb.append(p.getName()).append('=').append(p.getName((Comparable) e.getValue()));
            }
            sb.append(']');
        }
        return sb.toString();
    }
    public static net.minecraft.world.item.ItemStack copyN(net.minecraft.world.item.ItemStack s, int n) { net.minecraft.world.item.ItemStack c = s.copy(); c.setCount(n); return c; }
    public static net.minecraft.client.gui.components.EditBox editBox(Font f, int x, int y, int w, int h, Component c) { return new net.minecraft.client.gui.components.EditBox(f, x, y, w, h, c.getString()); }
    public static void setMessage(net.minecraft.client.gui.components.AbstractWidget b, Component c) { b.setMessage(c.getString()); }
    public static void hint(net.minecraft.client.gui.components.EditBox box, Component c) { box.setSuggestion(c.getString()); }
    public static void command(LocalPlayer p, String cmd) { p.chat("/" + cmd); }
    /** Draw on top of every screen: this version's Fabric API has no screen events, so the chest highlight is off. */
    public static void onScreen(java.util.function.BiConsumer<Screen, GuiGraphics> body) { }
    public static void onHud(java.util.function.Consumer<GuiGraphics> body) {
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(delta -> body.accept(new GuiGraphics(null, null)));
    }
    public static int mapColor(BlockState s) { var c = s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO); return c == net.minecraft.world.level.material.MaterialColor.NONE ? 0 : c.col; }
    public static boolean replaceable(BlockState s) { return s.getMaterial().isReplaceable(); }
    public static boolean hasBE(BlockState s) { return s.getBlock() instanceof net.minecraft.world.level.block.EntityBlock; }
    public static net.minecraft.client.KeyMapping key(String id, int key) { return new net.minecraft.client.KeyMapping(id, Mc.keyboard(), key, "key.category.buildblueprint.main"); }
    public static boolean keyMatches(net.minecraft.client.KeyMapping k, int key, int scancode) { return k.matches(key, scancode); }
}
