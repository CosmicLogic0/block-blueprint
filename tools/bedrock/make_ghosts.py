#!/usr/bin/env python3
"""Ghost blocks for the Bedrock add-on: one hidden custom block per Bedrock block ("bb:g_<name>"), drawn with that
block's own textures from the player's game (we only name the texture paths - no Minecraft pictures are copied),
shrunk a little, no collision, can't be clicked, and replaced the moment a real block is placed there.

usage: make_ghosts.py <bedrock-samples resource_pack dir> <addon dir>
Reads Mojang's blocks.json + terrain_texture.json (names and paths only) and renderer/bedrock-blocks.json (which
Bedrock blocks builds can contain). Writes BP/blocks/ghost/*.json, RP/textures/terrain_texture.json and
BP/scripts/ghosts.js (the list of blocks that have a ghost)."""
import json, os, re, shutil, sys

rp, addon = sys.argv[1], sys.argv[2]
root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
load = lambda p: json.loads(re.sub(r'//[^\n]*', '', open(p, encoding='utf-8').read()))
BLOCKS = load(os.path.join(rp, 'blocks.json'))
TEX = load(os.path.join(rp, 'textures/terrain_texture.json'))['texture_data']
MAP = json.load(open(os.path.join(root, 'renderer/bedrock-blocks.json')))

ALIAS = {'grass_block': 'grass', 'iron_chain': 'chain', 'sea_lantern': 'seaLantern', 'trip_wire': 'tripWire'}
SKIP = {'air', 'water', 'flowing_water', 'lava', 'flowing_lava', 'structure_void', 'moving_block', 'unknown', 'barrier',
        'piston_arm_collision', 'sticky_piston_arm_collision', 'portal', 'end_portal', 'end_gateway', 'fire', 'soul_fire',
        'bubble_column', 'light_block', 'jigsaw', 'structure_block', 'command_block', 'chain_command_block', 'repeating_command_block'}
SCALE = 0.7

names = set()
for e in MAP.values():
    for v in e['m'].values(): names.add(v[0] or None)
for jid, e in MAP.items():
    for v in e['m'].values():
        if not v[0]: names.add(jid[10:])
names.discard(None)

def words(s): return set(w for w in re.split(r'[^a-z0-9]+', s.lower()) if w)

def pick_path(short, block):
    """terrain_texture shortname -> one texture path (lists hold variants: pick the one that fits the block's name)."""
    e = TEX.get(short)
    if not e: return None
    t = e.get('textures')
    if isinstance(t, str): return t
    if isinstance(t, dict): return t.get('path')
    if isinstance(t, list) and t:
        paths = [x if isinstance(x, str) else x.get('path') for x in t]
        bw = words(block) - {'block', 'minecraft'}
        best = max(paths, key=lambda p: (len(words(p.split('/')[-1]) & bw), -paths.index(p)))
        return best
    return None

shorts, out_tex = {}, {}
def short_for(path):
    if path not in shorts:
        shorts[path] = 'bbg_%d' % len(shorts)
        out_tex[shorts[path]] = {'textures': path}
    return shorts[path]

FOLIAGE = ('leaves', 'vine', 'fern', 'tall_grass', 'short_grass', 'tallgrass', 'grass_top', 'seagrass')
def tint(path, block):
    p = path.split('/')[-1]
    if 'leaves' in p and not any(x in p for x in ('cherry', 'azalea', 'pale', 'mangrove')): return 'default_foliage'
    if any(x in p for x in ('vine', 'fern', 'tallgrass', 'grass_top', 'double_plant_grass')) and 'twisting' not in p and 'weeping' not in p: return 'grass'
    return None

bdir = os.path.join(addon, 'BP/blocks/ghost'); shutil.rmtree(bdir, ignore_errors=True); os.makedirs(bdir)
made = []
for name in sorted(names):
    if name in SKIP or name.startswith('element_') or 'command_block' in name: continue
    spec = BLOCKS.get(ALIAS.get(name, name), {}).get('textures')
    if not spec: continue
    faces = {}
    if isinstance(spec, str): faces['*'] = spec
    else:
        side = spec.get('side') or spec.get('north') or spec.get('up')
        faces['*'] = side
        for f in ('up', 'down', 'north', 'south', 'east', 'west'):
            if spec.get(f) and spec.get(f) != side: faces[f] = spec[f]
    mats = {}
    for f, short in faces.items():
        path = pick_path(short, name)
        if not path: continue
        m = {'texture': short_for(path), 'render_method': 'alpha_test', 'face_dimming': True, 'ambient_occlusion': 1.0}
        t = tint(path, name)
        if t: m['tint_method'] = t
        mats[f] = m
    if '*' not in mats: continue
    block = {'format_version': '1.21.80', 'minecraft:block': {
        'description': {'identifier': 'bb:g_' + name, 'menu_category': {'category': 'none', 'is_hidden_in_commands': True}},
        'components': {
            'minecraft:geometry': 'minecraft:geometry.full_block',
            'minecraft:material_instances': mats,
            'minecraft:transformation': {'scale': [SCALE, SCALE, SCALE]},
            'minecraft:collision_box': False,
            'minecraft:selection_box': False,
            'minecraft:light_dampening': 0,
            'minecraft:replaceable': {},
            'minecraft:destructible_by_mining': {'seconds_to_destroy': 0},
            'minecraft:destructible_by_explosion': False,
            'minecraft:loot': 'loot_tables/empty.json',
        }}}
    json.dump(block, open(os.path.join(bdir, name + '.json'), 'w'), separators=(',', ':'))
    made.append(name)

os.makedirs(os.path.join(addon, 'BP/loot_tables'), exist_ok=True)
json.dump({'pools': []}, open(os.path.join(addon, 'BP/loot_tables/empty.json'), 'w'))
json.dump({'resource_pack_name': 'build_blueprint', 'texture_name': 'atlas.terrain', 'padding': 8, 'num_mip_levels': 4, 'texture_data': out_tex},
          open(os.path.join(addon, 'RP/textures/terrain_texture.json'), 'w'), indent=1)
open(os.path.join(addon, 'BP/scripts/ghosts.js'), 'w').write(
    '// Blocks that have a ghost block (bb:g_<name>). Made by tools/bedrock/make_ghosts.py.\n'
    'export const GHOSTS = new Set(' + json.dumps(made) + ');\n')
print(len(made), 'ghost blocks,', len(out_tex), 'texture names; no ghost for', len(names) - len(made))
