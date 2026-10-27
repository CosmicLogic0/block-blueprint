#!/usr/bin/env python3
"""
Makes the 1.16-1.21.8 (and Forge/NeoForge) copy of the in-game UI from the Minecraft 26.x copy, so every UI change is
written once. Reads fabric-mod/src/menu (and the few shared client files listed below), writes ports/1.21/src/main/java.
The older copy goes through ports/shared.gradle filters at build time for 1.16-1.20 (old buttons, old text API).
Usage: python3 tools/port_ui.py [--check]   (--check only shows what would change)
"""
import re, sys, pathlib, difflib
ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / 'fabric-mod/src/menu/java/dev/buildblueprint/client'
DST = ROOT / 'ports/1.21/src/main/java/dev/buildblueprint/client'
MOD = ROOT / 'fabric-mod/src'
FILES = [(SRC, n) for n in ['MaterialsScreen', 'SettingsScreen', 'CopyScreen', 'SwapScreen', 'SavedBuildsScreen', 'MenuModule', 'UiKit', 'HudModule', 'UiTest', 'HelpScreen', 'ChestHighlight', 'CoopScreen', 'TemplatesScreen']] + \
        [(MOD / 'keys/java/dev/buildblueprint/client', 'KeysModule'), (MOD / 'main/java/dev/buildblueprint/client', 'Copier'),
         (MOD / 'main/java/dev/buildblueprint/client', 'Swapper')] + \
        [(MOD / 'main/java/dev/buildblueprint/client', n) for n in ['Materials', 'Settings', 'Coop', 'Mistakes', 'Hide', 'Stats']]
GLFW = {'LCONTROL': 'LEFT_CONTROL', 'RCONTROL': 'RIGHT_CONTROL', 'LSHIFT': 'LEFT_SHIFT', 'PAGEDOWN': 'PAGE_DOWN', 'PAGEUP': 'PAGE_UP', 'LALT': 'LEFT_ALT'}

SUBS = [
    # keys
    (r'import net\.fabricmc\.fabric\.api\.client\.keymapping\.v1\.KeyMappingHelper;', 'import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;'),
    (r'private static final KeyMapping\.Category CATEGORY = KeyMapping\.Category\.register\([^;]*\);', 'private static final String CATEGORY = "key.category.buildblueprint.main";'),
    (r'KeyMappingHelper\.registerKeyMapping\(new KeyMapping\(("[^"]*" \+ \w+), Mc\.keyboard\(\), (\w+), CATEGORY\)\)', r'KeyBindingHelper.registerKeyBinding(V.key(\1, \2))'),
    (r'\bmc\.hasShiftDown\(\)', 'V.shift()'), (r'\bmc\.hasAltDown\(\)', 'V.alt()'), (r'\bmc\.hasControlDown\(\)', 'V.ctrl()'),
    # copier (world reading)
    (r'BlockPos\.containing\(mc\.player\.getX\(\), mc\.player\.getY\(\), mc\.player\.getZ\(\)\)', 'new BlockPos((int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()))'),
    (r'BuiltInRegistries\.BLOCK\.getKey\((\w+)\.getBlock\(\)\)\.getPath\(\)', r'V.blockId(\1.getBlock()).replaceFirst("^[^:]*:", "")'),
    (r'mc\.level\.getMinY\(\)', 'V.minY(mc.level)'),
    (r'BlockState (\w+) = mc\.level\.getBlockState\((\w+)\.setY\((\w+)\)\);', r'\2.setY(\3); BlockState \1 = mc.level.getBlockState(\2);'),
    (r'BlockStateParser\.serialize\((\w+)\)', r'V.stateString(\1)'),
    (r'(\w+)\.isSolidRender\(\)', r'V.solid(\1)'),
    (r'import net\.minecraft\.client\.gui\.GuiGraphicsExtractor;', 'import net.minecraft.client.gui.GuiGraphics;'),
    (r'\bGuiGraphicsExtractor\b', 'GuiGraphics'),
    (r'^import net\.minecraft\.client\.input\.(KeyEvent|MouseButtonEvent);\n', ''),
    (r'net\.minecraft\.client\.input\.MouseButtonEvent', 'MouseButtonEvent'),
    (r'net\.minecraft\.client\.input\.KeyEvent', 'KeyEvent'),
    (r'^import net\.minecraft\.client\.gui\.screens\.options\.controls\.KeyBindsScreen;\n', ''),
    (r'new KeyBindsScreen\(this, mc\.options\)', 'V.keyBinds(this)'),
    (r'new KeyBindsScreen\(this, Minecraft\.getInstance\(\)\.options\)', 'V.keyBinds(this)'),
    (r'extends Screen \{', 'extends BBScreen {'),
    (r'\bg\.text\(', 'g.drawString('),
    (r'\bg\.item\(', 'g.renderItem('),
    (r'Modules\.menuKey\.matches\(e\)', 'V.keyMatches(Modules.menuKey, e.key(), e.scancode())'),
    (r'g\.setComponentTooltipForNextFrame\(font, ', 'V.tooltip(g, font, '),
    (r'List\.of\(Component\.literal\(hover\)\)', 'java.util.Collections.singletonList(Component.literal(hover))'),
    (r'BuiltInRegistries\.BLOCK\.getValue\(Identifier\.parse\((\w+)\)\)', r'V.block(\1)'),
    (r'BlockStateParser\.parseForBlock\(BuiltInRegistries\.BLOCK, (\w+), false\)\.blockState\(\)', r'V.parseState(\1)'),
    (r'\.isBlank\(\)', '.trim().isEmpty()'),
    # screen hook (chest highlight): Fabric's afterExtract on 26.x, V.onScreen on older versions and Forge
    (r'ScreenEvents\.AFTER_INIT\.register\(\(mc, screen, w, h\) -> ScreenEvents\.afterExtract\(screen\)\.register\(\(s, g, mx, my, d\) -> (.*)\)\);$', r'V.onScreen((s, g) -> \1);'),
    (r'^import net\.fabricmc\.fabric\.api\.client\.screen\.v1\.ScreenEvents;\n', ''),
    # registry access (Swapper and friends)
    (r'BuiltInRegistries\.BLOCK\.containsKey\(Identifier\.parse\((\w+)\)\)', r'V.blockExists(\1)'),
    (r'for \(Item (\w+) : BuiltInRegistries\.ITEM\)', r'for (Item \1 : V.allItems())'),
    (r'\b(\w+)\.canBeReplaced\(\)', r'V.replaceable(\1)'),
    (r'for \(Block (\w+) : BuiltInRegistries\.BLOCK\)', r'for (Block \1 : V.allBlocks())'),
    (r'BuiltInRegistries\.BLOCK\.getKey\((\w+)\)\.toString\(\)', r'V.blockId(\1)'),
    (r'BuiltInRegistries\.ITEM\.getKey\((\w+)\)\.toString\(\)', r'V.itemId(\1)'),
    (r'\b(\w+) instanceof net\.minecraft\.world\.level\.block\.TransparentBlock', r'V.glass(\1)'),
    (r'pr\.getValue\(v\)\.isEmpty\(\)', '!pr.getValue(v).isPresent()'),
    (r'blk\.defaultBlockState\(\)\.hasBlockEntity\(\)', 'V.hasBE(blk.defaultBlockState())'),
    (r'new HashSet<>\(List\.of\(', 'new HashSet<>(java.util.Arrays.asList('),
    (r'return (\w+)\.getMapColor\(net\.minecraft\.world\.level\.EmptyBlockGetter\.INSTANCE, net\.minecraft\.core\.BlockPos\.ZERO\)\.col;', r'return V.mapColor(\1);'),
]
# MapColor lookup in the saved-builds preview
MAPCOLOR_NEW = '''                MapColor mc = bs.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (mc == MapColor.NONE) return st.contains("glass") ? 0xFFA8D8F0 : 0xFF9A9A9A; // glass, panes, small decor
                return mc.col | 0xFF000000;'''
MAPCOLOR_OLD = '''                int col = V.mapColor(bs);
                if (col == 0) return st.contains("glass") ? 0xFFA8D8F0 : 0xFF9A9A9A; // glass, panes, small decor
                return col | 0xFF000000;'''
DROP_IMPORTS = ['net.minecraft.commands.arguments.blocks.BlockStateParser', 'net.minecraft.core.registries.BuiltInRegistries',
                'net.minecraft.world.level.material.MapColor', 'net.minecraft.resources.Identifier']

HUD_NEW = '''        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("buildblueprint", "materials"), (g, delta) -> Safe.run('''
HUD_OLD = '''        V.onHud(g -> Safe.run('''

def convert(s):
    s = s.replace(HUD_NEW, HUD_OLD)
    s = re.sub(r'^import net\.fabricmc\.fabric\.api\.client\.rendering\.v1\.hud\.\w+;\n', '', s, flags=re.M)
    s = re.sub(r'g\.pose\(\)\.pushMatrix\(\);\n(\s*)g\.pose\(\)\.scale\((\w+), \2\);', r'V.hudPush(g, \2);', s)
    s = s.replace('g.pose().popMatrix();', 'V.hudPop(g);')
    s = s.replace(MAPCOLOR_NEW, MAPCOLOR_OLD)
    for a, b in SUBS: s = re.sub(a, b, s, flags=re.M)
    s = re.sub(r'InputConstants\.KEY_(\w+)', lambda m: 'org.lwjgl.glfw.GLFW.GLFW_KEY_' + GLFW.get(m.group(1), m.group(1)), s)
    for imp in DROP_IMPORTS:
        short = imp.rsplit('.', 1)[1]
        body = re.sub(r'^import .*\n', '', s, flags=re.M)
        if not re.search(r'\b' + short + r'\b', body): s = s.replace('import ' + imp + ';\n', '')
    return s

def main():
    check = '--check' in sys.argv
    for d, f in FILES:
        src = d / (f + '.java')
        if not src.exists(): continue
        out = convert(src.read_text())
        dst = DST / (f + '.java')
        old = dst.read_text() if dst.exists() else ''
        if check:
            d = list(difflib.unified_diff(old.splitlines(), out.splitlines(), lineterm='', n=0))
            print(f'== {f}: {len([l for l in d if l[:1] in "+-" and not l.startswith(("+++", "---"))])} changed lines'); print('\n'.join(d[:40]))
        elif out != old: dst.write_text(out); print('wrote', dst.relative_to(ROOT))
main()
