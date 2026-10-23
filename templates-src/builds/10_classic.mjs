// Fantasy / medieval houses, detailed exteriors, empty interiors.
import { STYLE, roofs, storey, plinth, chimney, tower, porch, dormer, rubble } from '../house2.mjs';
const STONEY = (n) => /stone|cobble|tuff|andesite|deepslate|brick/.test(n) && !/stairs|slab|button|wall$/.test(n);

export default [
  {
    id: 'fantasy_cottage', name: 'Red Roof Fantasy Cottage', cat: 'Fantasy & Medieval', seed: 4,
    desc: 'Steep layered mangrove roof, cross-gabled front wing, round stone tower, jettied upper floor and a tall stone chimney.',
    build(b) {
      const S = STYLE.red, sd = 4;
      plinth(b, S, -6, -4, 6, 4, 0, 1, sd); plinth(b, S, -2, 4, 2, 10, 0, 1, sd);
      storey(b, S, -6, -4, 6, 4, 1, 4, { seed: sd, bay: 4, boxes: true });
      storey(b, S, -2, 4, 2, 10, 1, 4, { seed: sd + 1, bay: 4, skipSides: ['north'], doors: [{ side: 'south', at: 2 }] });
      // jettied upper floor overhangs the long sides by one
      storey(b, S, -6, -5, 6, 5, 5, 4, { seed: sd + 2, bay: 4, sillBeam: true, winOff: 1 });
      storey(b, S, -2, 5, 2, 10, 5, 4, { seed: sd + 3, bay: 4, skipSides: ['north'] });
      for (let x = -6; x <= 6; x += 4) for (const [z, f] of [[-5, 'south'], [5, 'north']]) if (!(z === 5 && Math.abs(x) <= 2)) b.stairs(x, 4, z, 'spruce_stairs', f, 'top');
      roofs(b, S, [
        { x0: -6, z0: -5, x1: 6, z1: 5, y: 9, axis: 'x', pitch: 1.5, ov: 1, endOv: 1 },
        { x0: -2, z0: 4, x1: 2, z1: 10, y: 9, axis: 'z', pitch: 1.5, ov: 1, endOv: 1, skipEnd: 0 },
      ], sd);
      tower(b, S, 7, -5, 1, 15, 3, sd);
      chimney(b, S, -4, -2, 9, 21, sd);
      rubble(b, STONEY, -10, 0, -9, 11, 16, 12, 0.04, sd);
      // door canopy
      for (const x of [-1, 0, 1]) b.stairs(x, 4, 11, 'mangrove_stairs', 'north');
      b.stairs(-1, 3, 11, 'spruce_stairs', 'north', 'top'); b.stairs(1, 3, 11, 'spruce_stairs', 'north', 'top');
      b.lantern(-2, 3, 11, false); b.set(-2, 2, 11, 'spruce_fence'); b.set(2, 2, 11, 'spruce_fence'); b.lantern(2, 3, 11, false);
    },
  },
  {
    id: 'medieval_townhouse', name: 'Medieval Townhouse', cat: 'Fantasy & Medieval', seed: 9,
    desc: 'Narrow three-floor townhouse whose gable faces the street, each floor jutting out further, with a chimney through the roof.',
    build(b) {
      const S = STYLE.oak, sd = 9;
      plinth(b, S, -3, -5, 3, 4, 0, 1, sd);
      storey(b, S, -3, -5, 3, 4, 1, 4, { seed: sd, bay: 3, every: 1, doors: [{ side: 'south', at: 3 }] });
      storey(b, S, -3, -5, 3, 5, 5, 4, { seed: sd + 1, bay: 3, every: 1, sillBeam: true });
      storey(b, S, -3, -5, 3, 6, 9, 4, { seed: sd + 2, bay: 3, every: 1, sillBeam: true });
      for (const x of [-3, 0, 3]) { b.stairs(x, 4, 5, 'dark_oak_stairs', 'north', 'top'); b.stairs(x, 8, 6, 'dark_oak_stairs', 'north', 'top'); }
      roofs(b, S, [{ x0: -3, z0: -5, x1: 3, z1: 6, y: 13, axis: 'z', pitch: 1.5, ov: 1, endOv: 1 }], sd);
      chimney(b, S, -2, -3, 13, 20, sd);
      rubble(b, STONEY, -5, 0, -7, 5, 1, 7, 0.05, sd);
      b.stairs(-1, 3, 6, 'spruce_stairs', 'north', 'top'); b.stairs(1, 3, 6, 'spruce_stairs', 'north', 'top'); b.set(0, 3, 6, 'spruce_slab', { type: 'top' });
      b.set(2, 3, 6, 'wall_torch', { facing: 'south' });
    },
  },
  {
    id: 'stone_cottage', name: 'Stone Mountain Cottage', cat: 'Fantasy & Medieval', seed: 12,
    desc: 'L-shaped single-storey stone cottage with a deepslate tile roof, spruce framing, a porch in the corner and rubble weathering.',
    build(b) {
      const S = STYLE.slate, sd = 12;
      plinth(b, S, -7, -3, 5, 3, 0, 2, sd); plinth(b, S, 1, 3, 5, 9, 0, 2, sd);
      storey(b, S, -7, -3, 5, 3, 2, 5, { seed: sd, bay: 4, doors: [{ side: 'south', at: 4 }] });
      storey(b, S, 1, 3, 5, 9, 2, 5, { seed: sd + 1, bay: 3, skipSides: ['north'] });
      roofs(b, S, [
        { x0: -7, z0: -3, x1: 5, z1: 3, y: 7, axis: 'x', pitch: 1, ov: 1, endOv: 1 },
        { x0: 1, z0: 3, x1: 5, z1: 9, y: 7, axis: 'z', pitch: 1, ov: 1, endOv: 1, skipEnd: 0 },
      ], sd);
      porch(b, S, -6, 4, -1, 6, 2, 'south');
      for (let x = -6; x <= -1; x++) b.stairs(x, 1, 7, 'cobblestone_stairs', 'north');
      chimney(b, S, -8, -1, 0, 11, sd);
      rubble(b, STONEY, -10, 0, -5, 7, 7, 11, 0.06, sd);
    },
  },
  {
    id: 'birch_seaside_house', name: 'Seaside Birch House', cat: 'Fantasy & Medieval', seed: 15,
    desc: 'Bright white two-storey house with a dark prismarine roof, cross gable, round white lookout tower and flower boxes.',
    build(b) {
      const S = STYLE.birch, sd = 15;
      plinth(b, S, -6, -4, 6, 4, 0, 1, sd); plinth(b, S, -2, 4, 2, 8, 0, 1, sd);
      storey(b, S, -6, -4, 6, 4, 1, 5, { seed: sd, bay: 4, boxes: true });
      storey(b, S, -2, 4, 2, 8, 1, 5, { seed: sd, bay: 4, skipSides: ['north'], doors: [{ side: 'south', at: 2 }] });
      storey(b, S, -6, -4, 6, 4, 6, 4, { seed: sd + 1, bay: 4, winOff: 1 });
      storey(b, S, -2, 4, 2, 8, 6, 4, { seed: sd + 1, bay: 4, skipSides: ['north'] });
      roofs(b, S, [
        { x0: -6, z0: -4, x1: 6, z1: 4, y: 10, axis: 'x', pitch: 1, ov: 1 },
        { x0: -2, z0: 4, x1: 2, z1: 8, y: 10, axis: 'z', pitch: 1, ov: 1, skipEnd: 0 },
      ], sd);
      tower(b, S, -7, 4, 1, 13, 2, sd);
      b.stairs(-1, 4, 9, 'spruce_stairs', 'north', 'top'); b.stairs(1, 4, 9, 'spruce_stairs', 'north', 'top'); b.slab(0, 4, 9, 'dark_prismarine_slab', 'top');
    },
  },
  {
    id: 'tavern', name: 'The Crooked Tavern', cat: 'Fantasy & Medieval', seed: 21,
    desc: 'Big three-storey tavern: mud-brick walls in dark oak framing, a cross wing, porch, hanging sign, kegs and a brick tower.',
    build(b) {
      const S = STYLE.dark, sd = 21;
      plinth(b, S, -8, -5, 8, 5, 0, 1, sd); plinth(b, S, 3, 5, 8, 11, 0, 1, sd);
      storey(b, S, -8, -5, 8, 5, 1, 5, { seed: sd, bay: 4, doors: [{ side: 'south', at: 6 }] });
      storey(b, S, 3, 5, 8, 11, 1, 5, { seed: sd + 1, bay: 3, skipSides: ['north'] });
      storey(b, S, -8, -6, 8, 6, 6, 4, { seed: sd + 2, bay: 4, sillBeam: true, winOff: 1 });
      storey(b, S, 3, 6, 8, 11, 6, 4, { seed: sd + 3, bay: 3, skipSides: ['north'], sillBeam: true });
      storey(b, S, -8, -6, 8, 6, 10, 4, { seed: sd + 4, bay: 4, sillBeam: true });
      for (let x = -8; x <= 8; x += 4) for (const [z, f] of [[-6, 'south'], [6, 'north']]) if (!(z === 6 && x >= 3)) b.stairs(x, 5, z, 'dark_oak_stairs', f, 'top');
      roofs(b, S, [
        { x0: -8, z0: -6, x1: 8, z1: 6, y: 14, axis: 'x', pitch: 1.25, ov: 1 },
        { x0: 3, z0: 6, x1: 8, z1: 11, y: 10, axis: 'z', pitch: 1.25, ov: 1, skipEnd: 0 },
      ], sd);
      tower(b, { ...S, tower: { main: 'bricks', mix: [['granite', 0.08]], low: [['packed_mud', 0.3]] } }, -9, 6, 1, 17, 2, sd);
      porch(b, S, -5, 6, 0, 8, 1, 'south');
      chimney(b, S, 4, -3, 14, 24, sd);
      for (const x of [-7, -6]) { b.set(x, 1, 7, 'barrel', { facing: 'south' }); b.set(x, 2, 7, 'barrel', { facing: 'up' }); }
      b.set(1, 4, 7, 'dark_oak_fence'); b.set(1, 4, 8, 'dark_oak_fence'); b.lantern(1, 3, 8, true);
      rubble(b, STONEY, -11, 0, -8, 10, 3, 13, 0.05, sd);
    },
  },
  {
    id: 'cherry_cottage', name: 'Cherry Blossom Cottage', cat: 'Fantasy & Medieval', seed: 27,
    desc: 'Storybook cottage with a steep cherry roof, mangrove bargeboards, white plaster and a little round tower.',
    build(b) {
      const S = STYLE.cherry, sd = 27;
      plinth(b, S, -5, -3, 5, 3, 0, 1, sd);
      storey(b, S, -5, -3, 5, 3, 1, 5, { seed: sd, bay: 4, doors: [{ side: 'south', at: 5 }], boxes: true, every: 1 });
      roofs(b, S, [{ x0: -5, z0: -3, x1: 5, z1: 3, y: 6, axis: 'x', pitch: 2, ov: 1 }], sd);
      tower(b, S, 6, 3, 1, 9, 2, sd);
      chimney(b, S, -3, -1, 6, 15, sd);
      for (const x of [4, 5, 6]) b.stairs(x, 3, 4, 'cherry_stairs', 'north');
    },
  },
  {
    id: 'desert_merchant_house', name: 'Desert Merchant House', cat: 'Fantasy & Medieval', seed: 33,
    desc: 'Sandstone house framed in jungle wood under a low acacia roof, with a shaded porch and a sandstone lookout tower.',
    build(b) {
      const S = STYLE.desert, sd = 33;
      plinth(b, S, -6, -4, 6, 4, 0, 1, sd);
      storey(b, S, -6, -4, 6, 4, 1, 5, { seed: sd, bay: 4, doors: [{ side: 'south', at: 6 }] });
      storey(b, S, -6, -4, 6, 4, 6, 4, { seed: sd + 1, bay: 4, winOff: 1, sillBeam: true });
      roofs(b, S, [{ x0: -6, z0: -4, x1: 6, z1: 4, y: 10, axis: 'x', pitch: 1, ov: 2 }], sd);
      porch(b, S, -4, 5, 4, 7, 1, 'south');
      tower(b, S, 7, -4, 1, 14, 2, sd);
      for (const x of [-3, 3]) { b.set(x, 1, 8, 'decorated_pot' in {} ? 'air' : 'flower_pot'); }
    },
  },
];
