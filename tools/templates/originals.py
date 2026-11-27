#!/usr/bin/env python3
"""Build Blueprint's own free builds (designed for the mod, no outside license needed).
usage: originals.py <out dir for .nbt files> <tsv lines out>
Every block used exists in Minecraft 1.14, so the builds load on every version the mod supports.
Writes Minecraft structure files (.nbt) and the lines for renderer/templates/mod-index.tsv."""
import math, sys, os
import nbtlib
from nbtlib.tag import Compound, List, Int, String

BY = 'CosmicLogic'


class B:
    """A build: a grid of block states. x = east, y = up, z = south."""
    def __init__(self, name, cat, desc=''):
        self.name, self.cat, self.desc, self.g = name, cat, desc, {}

    def set(self, x, y, z, s):
        if s is None or s in ('air', 'minecraft:air'): self.g.pop((x, y, z), None)
        else: self.g[(x, y, z)] = s if ':' in s.split('[')[0] else 'minecraft:' + s

    def get(self, x, y, z): return self.g.get((x, y, z))

    def box(self, x0, y0, z0, x1, y1, z1, s):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1): self.set(x, y, z, s)

    def walls(self, x0, y0, z0, x1, y1, z1, s):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): self.set(x, y, z0, s); self.set(x, y, z1, s)
            for z in range(z0, z1 + 1): self.set(x0, y, z, s); self.set(x1, y, z, s)

    def frame(self, x0, y0, z0, x1, y1, z1, s):
        """Corner posts and top / bottom rings."""
        for y in range(y0, y1 + 1):
            for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)): self.set(x, y, z, s)

    def gable(self, x0, x1, z0, z1, y, stairs, fill, overhang=1, ridge=None):
        """A roof sloping down to the north and south (ridge along x)."""
        n = 0
        while z0 + n <= z1 - n:
            for x in range(x0 - overhang, x1 + overhang + 1):
                if z0 + n == z1 - n:
                    self.set(x, y + n, z0 + n, ridge or (stairs.split('_stairs')[0] + '_slab[type=bottom]'))
                else:
                    self.set(x, y + n, z0 + n - overhang, f'{stairs}[facing=south,half=bottom]')
                    self.set(x, y + n, z1 - n + overhang, f'{stairs}[facing=north,half=bottom]')
            for z in range(z0 + n - overhang + 1, z1 - n + overhang):   # gable ends
                if z0 + n - overhang < z < z1 - n + overhang:
                    for x in (x0, x1):
                        if self.get(x, y + n, z) is None: self.set(x, y + n, z, fill)
            n += 1
        return y + n

    def size(self):
        xs = [p[0] for p in self.g]; ys = [p[1] for p in self.g]; zs = [p[2] for p in self.g]
        return min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)

    def save(self, path):
        x0, y0, z0, x1, y1, z1 = self.size()
        pal, idx, blocks = [], {}, []
        for (x, y, z), s in sorted(self.g.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            if s not in idx:
                idx[s] = len(pal)
                name, _, props = s.partition('[')
                e = {'Name': String(name)}
                if props:
                    e['Properties'] = Compound({k: String(v) for k, v in (p.split('=') for p in props.rstrip(']').split(','))})
                pal.append(Compound(e))
            blocks.append(Compound({'pos': List[Int]([Int(x - x0), Int(y - y0), Int(z - z0)]), 'state': Int(idx[s])}))
        root = Compound({'DataVersion': Int(1976), 'size': List[Int]([Int(x1 - x0 + 1), Int(y1 - y0 + 1), Int(z1 - z0 + 1)]),
                         'palette': List[Compound](pal), 'blocks': List[Compound](blocks), 'entities': List[Compound]([])})
        nbtlib.File(root, root_name='').save(path, gzipped=True)
        return (x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1), len(self.g)


def door(b, x, y, z, wood, facing):
    b.set(x, y, z, f'{wood}_door[facing={facing},half=lower,hinge=left,open=false,powered=false]')
    b.set(x, y + 1, z, f'{wood}_door[facing={facing},half=upper,hinge=left,open=false,powered=false]')


def bed(b, x, y, z, color, facing):
    dx, dz = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[facing]
    b.set(x, y, z, f'{color}_bed[facing={facing},part=foot,occupied=false]')
    b.set(x + dx, y, z + dz, f'{color}_bed[facing={facing},part=head,occupied=false]')


# ---------------------------------------------------------------- starter houses

def starter_cabin():
    b = B('Starter Cabin', 'Starter Houses', 'A small first-night cabin: log frame, oak walls, a bed, chest, crafting table and furnace.')
    b.box(0, 0, 0, 6, 0, 8, 'cobblestone')
    b.walls(0, 1, 0, 6, 3, 8, 'oak_planks')
    b.frame(0, 1, 0, 6, 3, 8, 'spruce_log[axis=y]')
    b.box(1, 1, 1, 5, 3, 7, 'air')
    for x in range(0, 7): b.set(x, 4, 0, 'spruce_log[axis=x]'); b.set(x, 4, 8, 'spruce_log[axis=x]')
    for z in range(0, 9): b.set(0, 4, z, 'spruce_log[axis=z]'); b.set(6, 4, z, 'spruce_log[axis=z]')
    b.box(1, 4, 1, 5, 4, 7, 'air')
    door(b, 3, 1, 0, 'spruce', 'south')
    for x, z in ((1, 0), (5, 0), (0, 4), (6, 4), (3, 8)): b.set(x, 2, z, 'glass_pane')
    b.gable(0, 6, 0, 8, 5, 'spruce_stairs', 'spruce_planks')
    bed(b, 5, 1, 6, 'red', 'south'); b.set(5, 1, 5, None); bed(b, 5, 1, 6, 'red', 'south')
    b.set(1, 1, 7, 'crafting_table'); b.set(2, 1, 7, 'furnace[facing=south,lit=false]')
    b.set(1, 1, 5, 'chest[facing=east,type=single,waterlogged=false]')
    b.set(3, 3, 1, 'wall_torch[facing=south]')
    b.set(3, 0, -1, 'cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
    return b


def cozy_cottage():
    b = B('Cozy Cottage', 'Starter Houses', 'A storybook cottage: stone base, white walls with dark oak beams, flower boxes and a stone chimney.')
    W, D = 10, 8
    b.box(0, 0, 0, W, 0, D, 'stone_bricks')
    b.walls(0, 1, 0, W, 1, D, 'stone_bricks')
    b.walls(0, 2, 0, W, 4, D, 'white_terracotta')
    b.frame(0, 2, 0, W, 4, D, 'dark_oak_log[axis=y]')
    for x in (3, 7):
        for y in (2, 3, 4): b.set(x, y, 0, 'dark_oak_log[axis=y]'); b.set(x, y, D, 'dark_oak_log[axis=y]')
    for x in range(0, W + 1): b.set(x, 5, 0, 'dark_oak_log[axis=x]'); b.set(x, 5, D, 'dark_oak_log[axis=x]')
    for z in range(0, D + 1): b.set(0, 5, z, 'dark_oak_log[axis=z]'); b.set(W, 5, z, 'dark_oak_log[axis=z]')
    b.box(1, 1, 1, W - 1, 5, D - 1, 'air')
    b.box(1, 0, 1, W - 1, 0, D - 1, 'spruce_planks')
    door(b, 5, 1, 0, 'dark_oak', 'south')
    for x in (1, 2, 8, 9):
        b.set(x, 3, 0, 'glass_pane'); b.set(x, 3, D, 'glass_pane')
    for x in (1, 2, 8, 9):   # flower boxes under the front windows
        b.set(x, 2, -1, 'dark_oak_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')
        b.set(x, 3, -1, ['poppy', 'dandelion', 'cornflower', 'oxeye_daisy'][x % 4])
    for z in (3, 5): b.set(0, 3, z, 'glass_pane'); b.set(W, 3, z, 'glass_pane')
    top = b.gable(0, W, 0, D, 6, 'dark_oak_stairs', 'white_terracotta')
    for y in range(1, top + 2): b.set(W - 2, y, D + 1, 'cobblestone') if y < top + 1 else b.set(W - 2, y, D + 1, 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    bed(b, 2, 1, 6, 'light_blue', 'south'); b.set(2, 1, 7, None); bed(b, 2, 1, 6, 'light_blue', 'south')
    b.set(1, 1, 7, 'barrel[facing=up,open=false]'); b.set(9, 1, 7, 'crafting_table'); b.set(8, 1, 7, 'smoker[facing=south,lit=false]')
    b.set(5, 4, 4, 'lantern[hanging=true]')
    b.set(5, 0, -1, 'stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
    for x in (3, 7): b.set(x, 1, -1, 'lantern[hanging=false]')
    return b


def starter_base():
    b = B('Survival Starter Base', 'Starter Houses', 'Two rooms and a porch: a bedroom, a work room with storage, and a little garden out front.')
    b.box(0, 0, 0, 14, 0, 9, 'cobblestone')
    b.walls(0, 1, 0, 14, 4, 9, 'spruce_planks')
    b.frame(0, 1, 0, 14, 4, 9, 'oak_log[axis=y]')
    for y in range(1, 5): b.set(7, y, 0, 'oak_log[axis=y]'); b.set(7, y, 9, 'oak_log[axis=y]')
    b.box(1, 1, 1, 13, 4, 8, 'air')
    b.box(1, 0, 1, 13, 0, 8, 'oak_planks')
    for z in range(1, 9): b.set(7, 1, z, 'spruce_planks'); b.set(7, 2, z, 'spruce_planks'); b.set(7, 3, z, 'spruce_planks'); b.set(7, 4, z, 'spruce_planks')
    door(b, 7, 1, 4, 'oak', 'east')
    door(b, 3, 1, 0, 'oak', 'south'); door(b, 11, 1, 0, 'oak', 'south')
    for x in (1, 5, 9, 13): b.set(x, 2, 0, 'glass_pane'); b.set(x, 3, 0, 'glass_pane')
    for x in (2, 5, 9, 12): b.set(x, 2, 9, 'glass_pane'); b.set(x, 3, 9, 'glass_pane')
    b.box(-1, 5, -1, 15, 5, 10, 'spruce_slab[type=bottom,waterlogged=false]')
    b.box(0, 5, 0, 14, 5, 9, 'spruce_planks')
    b.walls(-1, 5, -1, 15, 5, 10, 'spruce_slab[type=bottom,waterlogged=false]')
    bed(b, 1, 1, 7, 'red', 'south'); b.set(1, 1, 8, None); bed(b, 1, 1, 7, 'red', 'south')
    bed(b, 3, 1, 7, 'blue', 'south'); b.set(3, 1, 8, None); bed(b, 3, 1, 7, 'blue', 'south')
    for x in range(8, 14, 2): b.set(x, 1, 8, 'chest[facing=north,type=single,waterlogged=false]')
    b.set(13, 1, 1, 'crafting_table'); b.set(13, 1, 2, 'furnace[facing=west,lit=false]'); b.set(13, 1, 3, 'blast_furnace[facing=west,lit=false]')
    b.set(9, 1, 1, 'smithing_table')
    for x in (3, 11): b.set(x, 4, 4, 'lantern[hanging=true]')
    # porch and garden
    b.box(0, 0, -4, 14, 0, -1, 'grass_block[snowy=false]')
    b.box(2, 0, -3, 12, 0, -2, 'farmland[moisture=7]')
    b.box(2, 1, -3, 6, 1, -2, 'wheat[age=7]'); b.box(8, 1, -3, 12, 1, -2, 'carrots[age=7]')
    b.box(7, 0, -3, 7, 0, -2, 'water[level=0]')
    for x in range(0, 15):
        b.set(x, 1, -4, 'oak_fence[east=true,north=false,south=false,waterlogged=false,west=true]')
    b.set(3, 1, -4, 'oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]')
    b.set(11, 1, -4, 'oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]')
    b.set(0, 1, -4, 'oak_fence[east=true,north=false,south=true,waterlogged=false,west=false]')
    b.set(14, 1, -4, 'oak_fence[east=false,north=false,south=true,waterlogged=false,west=true]')
    for z in (-3, -2, -1): b.set(0, 1, z, 'oak_fence[east=false,north=true,south=true,waterlogged=false,west=false]'); b.set(14, 1, z, 'oak_fence[east=false,north=true,south=true,waterlogged=false,west=false]')
    b.set(0, 2, -4, 'lantern[hanging=false]'); b.set(14, 2, -4, 'lantern[hanging=false]')
    return b


# ---------------------------------------------------------------- farms

def crop_farm():
    b = B('Crop Farm', 'Farms', 'A 9x9 field of wheat, carrots, potatoes and beetroot around one water block, fenced in with a composter and lights.')
    b.box(0, 0, 0, 10, 0, 10, 'grass_block[snowy=false]')
    crops = ['wheat[age=7]', 'carrots[age=7]', 'potatoes[age=7]', 'beetroots[age=3]']
    for x in range(1, 10):
        for z in range(1, 10):
            b.set(x, 0, z, 'farmland[moisture=7]')
            q = (0 if x < 5 else 1) + (0 if z < 5 else 2)
            b.set(x, 1, z, crops[q])
    b.set(5, 1, 5, 'lily_pad')
    for x in range(0, 11):
        for z in (0, 10): b.set(x, 1, z, f'oak_fence[east={str(x < 10).lower()},north={str(z == 10).lower()},south={str(z == 0).lower()},waterlogged=false,west={str(x > 0).lower()}]')
    for z in range(1, 10):
        for x in (0, 10): b.set(x, 1, z, 'oak_fence[east=false,north=true,south=true,waterlogged=false,west=false]')
    b.set(5, 1, 0, 'oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]')
    for x, z in ((0, 0), (10, 0), (0, 10), (10, 10)): b.set(x, 2, z, 'lantern[hanging=false]')
    b.set(4, 1, -1, 'composter[level=0]'); b.set(6, 1, -1, 'barrel[facing=up,open=false]')
    b.box(4, 0, -1, 6, 0, -1, 'grass_block[snowy=false]')
    return b


def animal_pens():
    b = B('Animal Pens', 'Farms', 'Three fenced pens for cows, sheep and pigs, with gates, hay, water troughs and a small roofed feed corner.')
    W, D = 16, 8
    b.box(0, 0, 0, W, 0, D, 'grass_block[snowy=false]')
    def fence(x, z):
        n = lambda a, c: str(0 <= a <= W and 0 <= c <= D and ((a in (0, 6, 11, W)) or c in (0, D))).lower()
        b.set(x, 1, z, f'oak_fence[east={n(x + 1, z) if z in (0, D) else "false"},north={"true" if x in (0, 6, 11, W) and z > 0 else "false"},south={"true" if x in (0, 6, 11, W) and z < D else "false"},waterlogged=false,west={n(x - 1, z) if z in (0, D) else "false"}]')
    for x in range(0, W + 1):
        for z in (0, D): fence(x, z)
    for x in (0, 6, 11, W):
        for z in range(0, D + 1): fence(x, z)
    for gx in (3, 8, 13): b.set(gx, 1, 0, 'oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]')
    for x0 in (1, 7, 12):
        b.set(x0, 1, D - 1, 'hay_block[axis=y]'); b.set(x0 + 1, 1, D - 1, 'hay_block[axis=x]')
        b.set(x0 + 2, 1, D - 1, 'cauldron')
    for x in (0, 6, 11, W): b.set(x, 2, 0, 'torch')
    # feed corner roof
    b.box(-3, 0, 0, -1, 0, 4, 'oak_planks')
    for x, z in ((-3, 0), (-1, 0), (-3, 4), (-1, 4)): b.box(x, 1, z, x, 3, z, 'oak_log[axis=y]')
    b.box(-4, 4, -1, 0, 4, 5, 'spruce_slab[type=bottom,waterlogged=false]')
    b.set(-2, 1, 4, 'chest[facing=south,type=single,waterlogged=false]'); b.set(-3, 1, 2, 'composter[level=0]'); b.set(-2, 3, 2, 'lantern[hanging=true]')
    return b


def sugar_cane_farm():
    b = B('Sugar Cane Farm', 'Farms', 'Rows of sugar cane next to water channels on sand: walk along and break the top two blocks, it grows back.')
    b.box(0, 0, 0, 12, 0, 10, 'stone_bricks')
    for x in range(1, 12):
        for z in range(1, 10):
            b.set(x, 0, z, 'water[level=0]' if x % 3 == 0 else 'sand')
            if x % 3 != 0: b.set(x, 1, z, 'sugar_cane[age=0]'); b.set(x, 2, z, 'sugar_cane[age=0]')
    b.walls(0, 1, 0, 12, 1, 10, 'stone_brick_slab[type=bottom,waterlogged=false]')
    b.set(6, 1, 0, 'air')
    for x, z in ((0, 0), (12, 0), (0, 10), (12, 10)): b.set(x, 2, z, 'lantern[hanging=false]')
    return b


def melon_pumpkin_farm():
    b = B('Pumpkin & Melon Farm', 'Farms', 'Stems in rows with free space beside each one for pumpkins and melons to grow, watered from the middle.')
    b.box(0, 0, 0, 12, 0, 8, 'grass_block[snowy=false]')
    for z in range(1, 8):
        b.set(6, 0, z, 'water[level=0]')
        for x in (2, 4, 8, 10): b.set(x, 0, z, 'farmland[moisture=7]')
        b.set(2, 1, z, 'pumpkin_stem[age=7]'); b.set(4, 1, z, 'pumpkin_stem[age=7]')
        b.set(8, 1, z, 'melon_stem[age=7]'); b.set(10, 1, z, 'melon_stem[age=7]')
        if z % 2: b.set(3, 1, z, 'pumpkin'); b.set(9, 1, z, 'melon')
    b.walls(0, 1, 0, 12, 1, 8, 'oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]')
    b.set(6, 1, 0, 'oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]')
    for x, z in ((0, 0), (12, 0), (0, 8), (12, 8)): b.set(x, 2, z, 'torch')
    return b


# ---------------------------------------------------------------- medieval

def watch_tower():
    b = B('Watch Tower', 'Medieval', 'A tall stone watch tower: round-cornered walls, arrow slits, a ladder all the way up, battlements and a spruce roof.')
    R = 3   # 7x7 with cut corners
    H = 16
    for y in range(0, H + 1):
        for x in range(-R, R + 1):
            for z in range(-R, R + 1):
                edge = abs(x) == R or abs(z) == R
                corner = abs(x) == R and abs(z) == R
                if corner: continue
                if y == 0: b.set(x, y, z, 'cobblestone'); continue
                if edge: b.set(x, y, z, 'mossy_stone_bricks' if (x * 7 + y * 3 + z) % 9 == 0 else ('cracked_stone_bricks' if (x + y * 5 + z * 3) % 13 == 0 else 'stone_bricks'))
    for y in (4, 8, 12):   # floors
        b.box(-R + 1, y, -R + 1, R - 1, y, R - 1, 'spruce_planks')
    for y in range(1, H + 1): b.set(0, y, R - 1, 'ladder[facing=north,waterlogged=false]')
    for y in (4, 8, 12): b.set(0, y, R - 1, 'ladder[facing=north,waterlogged=false]')
    for y in (2, 6, 10, 14):   # arrow slits
        for x, z in ((0, -R), (R, 0), (-R, 0)): b.set(x, y, z, 'air'); b.set(x, y + 1, z, 'air') if y == 14 else None
    door(b, 0, 1, -R, 'spruce', 'south')
    # battlements platform
    b.box(-R - 1, H + 1, -R - 1, R + 1, H + 1, R + 1, 'stone_bricks')
    for x in range(-R - 1, R + 2):
        for z in range(-R - 1, R + 2):
            if abs(x) == R + 1 or abs(z) == R + 1:
                if (x + z) % 2 == 0: b.set(x, H + 2, z, 'stone_bricks')
    b.set(0, H + 1, R - 1, 'ladder[facing=north,waterlogged=false]')
    # roof
    for x, z in ((-R + 1, -R + 1), (R - 1, -R + 1), (-R + 1, R - 1), (R - 1, R - 1)): b.box(x, H + 2, z, x, H + 4, z, 'spruce_log[axis=y]')
    for n in range(0, R + 1):
        y = H + 5 + n
        for x in range(-R + n, R - n + 1):
            b.set(x, y, -R + n, 'spruce_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
            b.set(x, y, R - n, 'spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
        for z in range(-R + n + 1, R - n):
            b.set(-R + n, y, z, 'spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
            b.set(R - n, y, z, 'spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
    b.set(0, H + 5 + R, 0, 'spruce_planks')
    b.set(0, H + 4, 0, 'lantern[hanging=true]')
    return b


def castle_gate():
    b = B('Castle Wall & Gate', 'Medieval', 'A stretch of castle wall with two square towers and a gatehouse: a raised iron-bar gate, banners and a walkway on top.')
    L, H = 26, 8
    for x in range(0, L + 1):
        for z in range(0, 3):
            for y in range(0, H): b.set(x, y, z, 'stone_bricks' if (x * 3 + y * 7 + z) % 11 else 'cobblestone')
        for z in (0, 2):
            if x % 2 == 0: b.set(x, H, z, 'stone_bricks')
    b.box(0, H - 1, 1, L, H - 1, 1, 'stone_brick_slab[type=top,waterlogged=false]')
    for cx in (0, L):   # towers
        for y in range(0, H + 5):
            for x in range(cx - 2, cx + 3):
                for z in range(-2, 5):
                    edge = x in (cx - 2, cx + 2) or z in (-2, 4)
                    if edge: b.set(x, y, z, 'stone_bricks')
                    elif y in (0, H - 1): b.set(x, y, z, 'spruce_planks')
                    else: b.set(x, y, z, 'air') if b.get(x, y, z) else None
        for x in range(cx - 2, cx + 3):
            for z in range(-2, 5):
                if (x in (cx - 2, cx + 2) or z in (-2, 4)) and (x + z) % 2 == 0: b.set(x, H + 5, z, 'stone_bricks')
        for y in (3, 7, 10): b.set(cx, y, -2, 'air')
    # gatehouse opening in the middle
    m = L // 2
    for x in range(m - 2, m + 3):
        for y in range(0, 5):
            for z in range(0, 3): b.set(x, y, z, 'air')
    for x in range(m - 2, m + 3):
        for y in (3, 4): b.set(x, y, 0, 'iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]')
    b.box(m - 3, 5, 0, m + 3, 5, 2, 'stone_bricks')
    b.set(m - 3, 4, -1, 'wall_torch[facing=north]'); b.set(m + 3, 4, -1, 'wall_torch[facing=north]')
    for x in (m - 4, m + 4): b.set(x, 6, -1, 'red_wall_banner[facing=north]'); b.set(x, 5, -1, 'air')
    return b


def windmill():
    b = B('Windmill', 'Medieval', 'A countryside windmill: stone base, a plank tower narrowing to the top, a cap roof and four big sails.')
    for y in range(0, 4):
        b.walls(-3, y, -3, 3, y, 3, 'cobblestone' if y == 0 else 'stone_bricks')
    b.box(-2, 0, -2, 2, 0, 2, 'spruce_planks')
    for y in range(4, 13):
        r = 3 if y < 7 else 2
        b.walls(-r, y, -r, r, y, r, 'oak_planks')
        b.frame(-r, y, -r, r, y, r, 'spruce_log[axis=y]')
    door(b, 0, 1, -3, 'spruce', 'south')
    for y in (5, 9): b.set(0, y, -3 if y < 7 else -2, 'glass_pane'); b.set(3 if y < 7 else 2, y, 0, 'glass_pane')
    for y in range(13, 16):   # cap
        n = y - 13
        for x in range(-3 + n, 4 - n):
            b.set(x, y, -3 + n, 'dark_oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
            b.set(x, y, 3 - n, 'dark_oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
        for z in range(-2 + n, 3 - n):
            b.set(-3 + n, y, z, 'dark_oak_planks'); b.set(3 - n, y, z, 'dark_oak_planks')
    b.box(0, 16, -1, 0, 16, 1, 'dark_oak_slab[type=bottom,waterlogged=false]')
    # four sails on the south side: an arm going up, right, down and left from the hub, cloth on one side of each
    hx, hy, hz = 0, 11, 4
    b.set(0, 11, 3, 'spruce_log[axis=z]'); b.set(hx, hy, hz, 'spruce_log[axis=z]')
    arms = [((0, 1), (1, 0)), ((1, 0), (0, -1)), ((0, -1), (-1, 0)), ((-1, 0), (0, 1))]   # (arm direction, cloth side)
    for (ax, ay), (sx, sy) in arms:
        for i in range(1, 8):
            x, y = hx + ax * i, hy + ay * i
            b.set(x, y, hz, 'spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]')
            if i >= 2:
                for k in (1, 2): b.set(x + sx * k, y + sy * k, hz, 'white_wool')
    return b


def blacksmith():
    b = B('Blacksmith', 'Medieval', 'An open forge with a stone chimney, anvil, grindstone, smithing table, blast furnace and a weapon rack.')
    W, D = 10, 7
    b.box(0, 0, 0, W, 0, D, 'cobblestone')
    b.box(1, 0, 1, W - 1, 0, D - 1, 'stone')
    for x, z in ((0, 0), (W, 0), (0, D), (W, D), (5, 0)): b.box(x, 1, z, x, 4, z, 'spruce_log[axis=y]')
    b.walls(0, 1, D, W, 3, D, 'cobblestone'); b.walls(0, 1, 0, 0, 3, D, 'cobblestone')
    b.set(0, 1, 0, 'spruce_log[axis=y]')
    for z in range(0, D + 1): b.set(W, 1, z, 'spruce_fence[east=false,north=true,south=true,waterlogged=false,west=false]') if 0 < z < D else None
    b.gable(0, W, 0, D, 5, 'spruce_stairs', 'spruce_planks')
    # forge + chimney
    b.box(1, 1, D - 2, 3, 2, D - 1, 'bricks')
    b.set(2, 1, D - 2, 'campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
    for y in range(3, 11): b.set(2, y, D - 1, 'bricks'); b.set(1, y, D - 1, 'bricks') if y < 5 else None
    b.set(4, 1, D - 1, 'blast_furnace[facing=north,lit=false]'); b.set(5, 1, D - 1, 'furnace[facing=north,lit=false]')
    b.set(4, 1, 3, 'anvil[facing=east]'); b.set(6, 1, 3, 'grindstone[face=floor,facing=north]'); b.set(8, 1, D - 1, 'smithing_table')
    b.set(9, 1, D - 1, 'barrel[facing=up,open=false]'); b.set(9, 2, D - 1, 'barrel[facing=up,open=false]')
    b.set(1, 1, 1, 'cauldron')
    b.set(1, 2, z, 'lever[face=wall,facing=east,powered=false]') if False else None
    b.set(5, 4, 3, 'lantern[hanging=true]')
    return b


# ---------------------------------------------------------------- modern & fun

def modern_house():
    b = B('Modern House', 'Modern & Fun', 'A two-floor modern house: white concrete, big glass walls, flat roofs, a balcony and a small pool.')
    W, D = 16, 10
    b.box(-1, 0, -4, W + 1, 0, D + 1, 'smooth_stone')
    b.box(0, 1, 0, W, 4, D, 'white_concrete')
    b.box(1, 1, 1, W - 1, 4, D - 1, 'air')
    b.box(1, 0, 1, W - 1, 0, D - 1, 'quartz_block')
    for x in range(1, W):   # glass front, ground floor
        for y in (1, 2, 3): b.set(x, y, 0, 'glass') if x not in (8,) else None
    door(b, 8, 1, 0, 'birch', 'south')
    b.box(0, 5, 0, W, 5, D, 'white_concrete')
    # upper floor set back
    b.box(5, 6, 2, W, 9, D, 'white_concrete'); b.box(6, 6, 3, W - 1, 9, D - 1, 'air')
    for x in range(6, W): b.set(x, 7, 2, 'glass'); b.set(x, 8, 2, 'glass')
    b.box(4, 10, 1, W + 1, 10, D + 1, 'smooth_stone_slab[type=bottom,waterlogged=false]')
    b.box(5, 10, 2, W, 10, D, 'white_concrete')
    for x in range(0, 5): b.set(x, 6, 0, 'glass_pane'); b.set(x, 6, D, 'glass_pane')
    for z in range(0, D + 1): b.set(0, 6, z, 'glass_pane')
    for z in range(3, D - 1, 2): b.set(W, 2, z, 'glass'); b.set(W, 3, z, 'glass'); b.set(W, 7, z, 'glass')
    # pool in front
    b.box(10, 0, -4, 15, 0, -2, 'light_blue_concrete')
    b.box(11, 0, -3, 14, 0, -3, 'water[level=0]')
    b.set(x, 1, -2, 'potted_fern') if False else None
    b.set(3, 4, 5, 'sea_lantern'); b.set(12, 4, 5, 'sea_lantern'); b.set(10, 9, 6, 'sea_lantern')
    return b


def hot_air_balloon():
    b = B('Hot Air Balloon', 'Modern & Fun', 'A striped hot air balloon floating above its wicker basket, tied down with rope fences, a campfire burner in the middle.')
    cy, R = 12, 5.5
    cols = ['red_wool', 'white_wool', 'yellow_wool', 'white_wool']
    for x in range(-6, 7):
        for y in range(-6, 8):
            for z in range(-6, 7):
                ry = y / (1.15 if y > 0 else 0.95)
                d = math.sqrt(x * x + ry * ry + z * z)
                if R - 1 < d <= R:
                    ang = (math.atan2(z, x) + math.pi) / (2 * math.pi)
                    b.set(x, cy + y, z, cols[int(ang * 8) % 4])
    for x in range(-2, 3):   # bottom opening
        for z in range(-2, 3): b.set(x, cy - 6, z, None)
    # basket
    b.box(-2, 0, -2, 2, 0, 2, 'spruce_planks')
    b.walls(-2, 1, -2, 2, 1, 2, 'spruce_slab[type=bottom,waterlogged=false]')
    b.set(0, 1, 0, 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    for x, z in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        for y in range(2, cy - 5): b.set(x, y, z, 'spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]')
    return b


def duck_statue():
    b = B('Giant Rubber Duck', 'Modern & Fun', 'A big yellow rubber duck to put on a lake or in your town square.')
    for x in range(-6, 7):
        for y in range(0, 14):
            for z in range(-5, 6):
                body = (x / 6.0) ** 2 + ((y - 3) / 3.5) ** 2 + (z / 4.5) ** 2 <= 1 and y >= 0
                head = ((x - 3) / 3.0) ** 2 + ((y - 9) / 3.0) ** 2 + (z / 3.0) ** 2 <= 1
                tail = x <= -5 and 4 <= y <= 6 and abs(z) <= 1
                if body or head or tail: b.set(x, y, z, 'yellow_concrete' if y > 0 else 'yellow_terracotta')
    for y in (9, 10):
        for z in (-1, 0, 1): b.set(6, y, z, 'orange_concrete'); b.set(7, y, z, 'orange_concrete') if y == 9 else None
    b.set(5, 11, -2, 'black_concrete'); b.set(5, 11, 2, 'black_concrete')
    return b


def pixel_heart():
    b = B('Pixel Heart', 'Modern & Fun', 'A big pixel-art heart with a shine, standing up. Easy to make bigger with Build from a picture.')
    art = ['..XXX...XXX..',
           '.XWWXX.XXXXX.',
           'XXWXXXXXXXXXX',
           'XXXXXXXXXXXXX',
           'XXXXXXXXXXXXX',
           '.XXXXXXXXXXX.',
           '..XXXXXXXXX..',
           '...XXXXXXX...',
           '....XXXXX....',
           '.....XXX.....',
           '......X......']
    for r, row in enumerate(art):
        for c, ch in enumerate(row):
            if ch == '.': continue
            b.set(c, len(art) - 1 - r, 0, 'white_concrete' if ch == 'W' else 'red_concrete')
    edge = []
    for (x, y, z), s in list(b.g.items()):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy, 0) not in b.g: edge.append((x + dx, y + dy))
    for x, y in edge:
        if y >= 0: b.set(x, y, 0, 'black_concrete')
    return b


def rainbow():
    b = B('Rainbow Arch', 'Modern & Fun', 'A wool rainbow arch with white wool clouds at both ends.')
    cols = ['red_wool', 'orange_wool', 'yellow_wool', 'lime_wool', 'light_blue_wool', 'blue_wool', 'purple_wool']
    R = 14
    for x in range(-R - 1, R + 2):
        for y in range(0, R + 2):
            d = math.sqrt(x * x + y * y)
            for i, c in enumerate(cols):
                if R - i - 1 < d <= R - i: b.set(x, y, 0, c); b.set(x, y, 1, c)
    for cx in (-R + 3, R - 3):
        for x in range(cx - 4, cx + 5):
            for y in range(0, 4):
                for z in range(-2, 4):
                    if ((x - cx) / 4.5) ** 2 + (y / 3.2) ** 2 + ((z - 0.5) / 2.8) ** 2 <= 1: b.set(x, y, z, 'white_wool')
    return b


BUILDS = [starter_cabin, cozy_cottage, starter_base, crop_farm, animal_pens, sugar_cane_farm, melon_pumpkin_farm,
          watch_tower, castle_gate, windmill, blacksmith, modern_house, hot_air_balloon, duck_statue, pixel_heart, rainbow]

if __name__ == '__main__':
    out, tsv = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    lines = []
    for f in BUILDS:
        b = f()
        bid = 'bb_' + f.__name__
        size, n = b.save(os.path.join(out, bid + '.nbt'))
        lines.append('\t'.join([bid, bid + '.nbt', b.name, b.cat, 'x'.join(map(str, size)), str(n), BY, 'Build Blueprint', 'https://modrinth.com/mod/build-blueprint']))
        print(f'{b.name:28} {b.cat:16} {"x".join(map(str, size)):10} {n} blocks')
    open(tsv, 'w').write('\n'.join(lines) + '\n')
