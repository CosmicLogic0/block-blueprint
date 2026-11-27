#!/usr/bin/env python3
"""Average texture color of every full, solid, plain cube block, for "Build from a picture".
usage: block_colors.py <minecraft client jar> <out.txt>
Out lines: "minecraft:block<TAB>#rrggbb<TAB>basic|all". basic = wool, concrete and terracotta (cheap, many colors).
Only the colors (numbers) are kept, nothing of the textures themselves."""
import io, json, sys, zipfile
from PIL import Image

jar = zipfile.ZipFile(sys.argv[1])
names = set(jar.namelist())

def js(p):
    return json.loads(jar.read(p)) if p in names else None

def model(ref):
    ref = ref.split(':')[-1]
    return js(f'assets/minecraft/models/{ref}.json')

def plain_cube(m):
    """The 'all' texture of a cube_all model (following parents), else None."""
    tex = {}
    for _ in range(8):
        if m is None: return None
        for k, v in (m.get('textures') or {}).items(): tex.setdefault(k, v)
        parent = (m.get('parent') or '').split(':')[-1]
        if parent == 'block/cube_all': return tex.get('all')
        if not parent: return None
        m = model(parent)
    return None

SKIP_WORDS = ('sand', 'gravel', 'concrete_powder', 'suspicious_', 'infested_', 'tnt', 'slime', 'honey', 'redstone_block',
              'magma', 'spawner', 'barrier', 'structure', 'jigsaw', 'command', 'light', 'budding', 'sculk_catalyst', 'test_')
out = []
for p in sorted(names):
    if not (p.startswith('assets/minecraft/blockstates/') and p.endswith('.json')): continue
    block = p.split('/')[-1][:-5]
    if any(w in block for w in SKIP_WORDS): continue
    bs = js(p)
    v = (bs.get('variants') or {})
    if list(v.keys()) != ['']: continue                      # one plain look, no facing / axis
    var = v['']
    if isinstance(var, list): var = var[0]
    t = plain_cube(model(var['model']))
    if not t or t.startswith('#'): continue
    tp = 'assets/minecraft/textures/' + t.split(':')[-1] + '.png'
    if tp not in names: continue
    im = Image.open(io.BytesIO(jar.read(tp))).convert('RGBA')
    w = im.width
    im = im.crop((0, 0, w, w))                              # animated: first frame
    px = list(im.getdata())
    if any(a < 250 for *_, a in px): continue               # see-through (glass, leaves...)
    r = sum(c[0] for c in px) // len(px); g = sum(c[1] for c in px) // len(px); b = sum(c[2] for c in px) // len(px)
    basic = block.endswith('_wool') or (block.endswith('_concrete')) or block.endswith('terracotta') and 'glazed' not in block
    out.append(f'minecraft:{block}\t#{r:02x}{g:02x}{b:02x}\t{"basic" if basic else "all"}')
open(sys.argv[2], 'w').write('# Average texture colors of plain full blocks (made by tools/colors/block_colors.py from the Minecraft client).\n' + '\n'.join(out) + '\n')
print(len(out), 'blocks,', sum(1 for l in out if l.endswith('basic')), 'basic')
