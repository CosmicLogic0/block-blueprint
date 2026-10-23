// Modern houses: stacked white volumes, glass walls, dark wood cladding, flat roofs, pools.
import { MOD, volume, pool, planter, railing, downlights, slats, tree } from '../modern.mjs';

export default [
  {
    id: 'modern_white_villa', name: 'White Cantilever Villa', cat: 'Modern Houses', seed: 41,
    desc: 'Two stacked white volumes with the upper floor cantilevered over the terrace, full-height glass, dark oak slat cladding, roof terrace and an infinity pool.',
    build(b) {
      const M = MOD.white;
      // ground volume
      volume(b, M, -9, 1, -5, 5, 4, 4, { south: 'glass', east: 'glass', north: 'band', west: 'solid' });
      // upper volume, cantilevered east over the pool deck
      volume(b, M, -5, 6, -6, 11, 9, 2, { south: 'glass', east: 'glass', north: 'band', west: 'wood' });
      for (let x = 6; x <= 11; x++) for (let z = -6; z <= 2; z++) b.set(x, 5, z, M.wall2);
      downlights(b, 7, -5, 11, 1, 5, M, 2);
      slats(b, M, 'north', -9, -3, -5, 1, 4);
      // entry: recessed door with a canopy
      b.door(-6, 1, 4, 'dark_oak_door', 'south'); b.door(-7, 1, 4, 'dark_oak_door', 'south', 'right');
      for (let x = -9; x <= -4; x++) b.slab(x, 4, 5, 'smooth_quartz_slab', 'top');
      // roof terrace over the ground floor west part with glass railing
      railing(b, -10, -6, -10, 5, 6); railing(b, -10, 5, -6, 5, 6);
      planter(b, -9, -4, -9, 3, 6, 'polished_deepslate', 'azalea_leaves');
      // pool and deck
      for (let x = -10; x <= 12; x++) for (let z = 5; z <= 13; z++) b.set(x, 0, z, (x + z) % 2 ? 'stripped_dark_oak_wood' : 'dark_oak_planks');
      pool(b, M, -3, 7, 10, 11, 0, 2);
      for (let x = 6; x <= 12; x++) for (let z = -7; z <= 4; z++) if (!b.has(x, 0, z)) b.set(x, 0, z, 'smooth_quartz');
      for (const x of [-8, -6]) { b.stairs(x, 1, 9, 'quartz_stairs', 'west'); b.set(x + 1, 1, 9, 'white_carpet'); }
      planter(b, -10, 12, -6, 13, 1, 'polished_deepslate', 'oak_leaves');
      for (const [x, z] of [[11, 12], [-9, 6]]) b.set(x, 1, z, 'end_rod', { facing: 'up' });
      // steel columns under the cantilever corner
      for (let y = 1; y <= 4; y++) b.set(11, y, 2, 'iron_bars');
      tree(b, 13, 1, -3, 4);
    },
  },
  {
    id: 'glass_cube_house', name: 'Black Frame Glass House', cat: 'Modern Houses', seed: 43,
    desc: 'Two-floor glass box with a black steel frame, a dark wood core volume pushing through it and a sheltered deck.',
    build(b) {
      const M = MOD.dark;
      volume(b, { ...M, glass: 'glass' }, -6, 1, -4, 6, 4, 4, { north: 'glass', south: 'glass', east: 'glass', west: 'glass' }, { mull: 3 });
      volume(b, { ...M, glass: 'glass' }, -6, 6, -4, 6, 9, 4, { north: 'glass', south: 'glass', east: 'glass', west: 'glass' }, { mull: 3 });
      // wood core volume poking out of the west side
      volume(b, { ...MOD.warm, wall: 'stripped_spruce_wood', wall2: 'spruce_planks' }, -10, 1, -2, -5, 9, 2, { north: 'solid', south: 'solid', west: 'wood' }, { roof: true, lipOver: 0 });
      for (let y = 1; y <= 9; y++) for (const z of [-2, 2]) b.log(-10, y, z, 'stripped_spruce_log');
      b.door(0, 1, 4, 'iron_door', 'south');
      for (let x = -7; x <= 8; x++) for (let z = 5; z <= 8; z++) b.set(x, 0, z, 'spruce_planks');
      for (let x = -7; x <= 8; x++) b.slab(x, 5, 6, 'polished_blackstone_slab', 'top');
      for (const x of [-7, 8]) for (let y = 1; y <= 4; y++) b.set(x, y, 6, 'iron_bars');
      planter(b, 6, 7, 8, 8, 1, 'polished_blackstone', 'azalea_leaves');
      downlights(b, -5, -3, 5, 3, 5, M, 3);
    },
  },
  {
    id: 'modern_starter_house', name: 'Modern Starter House', cat: 'Modern Houses', seed: 45,
    desc: 'Compact white house with a wood-clad entry block, corner glass and a flat roof with an overhang - easy first modern build.',
    build(b) {
      const M = MOD.white;
      volume(b, M, -5, 1, -4, 5, 4, 3, { south: 'band', east: 'glass', north: 'solid', west: 'solid' });
      volume(b, { ...M, wall: 'stripped_dark_oak_wood' }, -6, 1, 1, -2, 5, 6, { south: 'wood', west: 'wood', east: 'solid' });
      b.door(-4, 1, 6, 'dark_oak_door', 'south');
      for (let x = -6; x <= 6; x++) for (let z = 4; z <= 9; z++) if (!b.has(x, 0, z)) b.set(x, 0, z, 'polished_andesite');
      planter(b, 0, 5, 5, 5, 1, 'polished_deepslate', 'oak_leaves');
      b.set(-1, 3, 7, 'end_rod', { facing: 'down' });
    },
  },
  {
    id: 'modern_beach_house', name: 'Stilt Beach House', cat: 'Modern Houses', seed: 47,
    desc: 'Warm sandstone-and-spruce beach house raised on log stilts, wrap-around deck, glass sea front and an outdoor shower.',
    build(b) {
      const M = MOD.warm;
      for (const x of [-7, -2, 3, 8]) for (const z of [-5, 0, 5]) for (let y = 0; y <= 4; y++) b.log(x, y, z, 'stripped_spruce_log');
      for (let x = -9; x <= 10; x++) for (let z = -6; z <= 8; z++) b.set(x, 5, z, (x % 2) ? 'spruce_planks' : 'stripped_spruce_wood');
      volume(b, M, -7, 6, -5, 8, 9, 5, { south: 'glass', east: 'glass', west: 'wood', north: 'solid' });
      for (let x = -9; x <= 10; x++) { b.set(x, 6, 8, 'spruce_fence'); } for (let z = -6; z <= 8; z++) { b.set(-9, 6, z, 'spruce_fence'); b.set(10, 6, z, 'spruce_fence'); }
      for (let i = 0; i < 6; i++) b.stairs(11 + i, 5 - i, 2, 'spruce_stairs', 'west');
      for (let y = 6; y <= 9; y++) b.set(10, y, -6, 'iron_bars');
      b.door(0, 6, 5, 'spruce_door', 'south');
      for (const x of [-6, 4]) { b.stairs(x, 6, 7, 'spruce_stairs', 'north'); b.stairs(x + 1, 6, 7, 'spruce_stairs', 'north'); }
      for (let x = -8; x <= 9; x++) for (const z of [-6, 6]) b.slab(x, 10, z + (z > 0 ? 1 : -1), 'spruce_slab', 'top');
    },
  },
  {
    id: 'modern_aframe_cabin', name: 'Modern A-Frame Cabin', cat: 'Modern Houses', seed: 49,
    desc: 'Steep black A-frame with a full glass gable, spruce deck, loft balcony and a chimney flue.',
    build(b) {
      const h = 11;
      for (let z = -6; z <= 6; z++) for (let i = 0; i < h; i++) {
        const xl = -h + i, xr = h - i;
        b.stairs(xl, i, z, 'blackstone_stairs', 'east'); b.stairs(xr, i, z, 'blackstone_stairs', 'west');
        if (i > 0) { b.stairs(xl + 1, i - 1, z, 'spruce_stairs', 'west', 'top'); b.stairs(xr - 1, i - 1, z, 'spruce_stairs', 'east', 'top'); }
        if (z === -6 || z === 6) for (let x = xl + 1; x < xr; x++) b.set(x, i, z, z === 6 ? ((x % 3 === 0) ? 'black_concrete' : 'glass') : 'stripped_spruce_wood');
      }
      for (let z = -6; z <= 6; z++) b.slab(0, h, z, 'blackstone_slab');
      for (let x = -10; x <= 10; x++) for (let z = -6; z <= 10; z++) if (!b.has(x, 0, z) || z > 6) b.set(x, 0, z, z > 6 ? 'spruce_planks' : 'polished_deepslate');
      for (let x = -6; x <= 6; x++) b.set(x, 5, 5, 'spruce_planks'); for (let x = -6; x <= 6; x++) b.set(x, 6, 7, 'glass_pane');
      for (let x = -6; x <= 6; x++) for (let z = 3; z <= 7; z++) b.set(x, 5, z, 'spruce_planks');
      b.door(0, 1, 6, 'spruce_door', 'south');
      for (let y = 0; y <= 13; y++) b.set(-4, y, -3, y > 8 ? 'black_concrete' : 'polished_blackstone_bricks');
      for (let x = -10; x <= 10; x++) b.set(x, 1, 10, 'spruce_fence');
    },
  },
  {
    id: 'modern_farmhouse', name: 'Modern Farmhouse', cat: 'Modern Houses', seed: 51,
    desc: 'White board-and-batten farmhouse with a black metal gable roof, wraparound porch with black posts and big black-framed windows.',
    build(b) {
      for (let y = 1; y <= 8; y++) for (let x = -8; x <= 8; x++) for (let z = -5; z <= 5; z++) {
        const edge = x === -8 || x === 8 || z === -5 || z === 5; if (!edge) continue;
        const a = (z === -5 || z === 5) ? x : z;
        b.set(x, y, z, a % 2 === 0 ? 'white_concrete' : 'smooth_quartz');
      }
      for (let x = -8; x <= 8; x++) for (let z = -5; z <= 5; z++) { b.set(x, 0, z, 'polished_andesite'); b.set(x, 4, z, 'spruce_planks'); }
      // windows: tall black frames
      for (const x of [-6, -2, 2, 6]) for (const z of [-5, 5]) for (let y = 1; y <= 7; y++) if (y !== 4) b.set(x, y, z, y === 1 || y === 7 ? 'black_concrete' : 'glass_pane');
      for (const z of [-2, 2]) for (const x of [-8, 8]) for (let y = 5; y <= 7; y++) b.set(x, y, z, 'glass_pane');
      // black standing-seam roof
      for (let i = 0; i <= 6; i++) for (let x = -9; x <= 9; x++) {
        b.stairs(x, 9 + i, -6 + i, 'blackstone_stairs', 'south'); b.stairs(x, 9 + i, 6 - i, 'blackstone_stairs', 'north');
        if (i > 0) { b.stairs(x, 8 + i, -6 + i, 'polished_blackstone_stairs', 'north', 'top'); b.stairs(x, 8 + i, 6 - i, 'polished_blackstone_stairs', 'south', 'top'); }
      }
      for (let x = -9; x <= 9; x++) b.slab(x, 16, 0, 'polished_blackstone_slab');
      for (const x of [-8, 8]) for (let i = 0; i < 6; i++) for (let z = -5 + i; z <= 5 - i; z++) b.set(x, 9 + i, z, Math.abs(z) % 2 === 0 ? 'white_concrete' : 'smooth_quartz');
      for (const x of [-8, 8]) b.set(x, 11, 0, 'glass_pane');
      // porch
      for (let x = -9; x <= 9; x++) for (let z = 6; z <= 9; z++) b.set(x, 0, z, 'spruce_planks');
      for (const x of [-9, -3, 3, 9]) for (let y = 1; y <= 3; y++) b.set(x, y, 9, 'polished_blackstone_wall');
      for (let x = -9; x <= 9; x++) for (let z = 6; z <= 9; z++) b.slab(x, 4, z, 'blackstone_slab');
      b.door(0, 1, 5, 'spruce_door', 'south');
      for (const x of [-5, 5]) b.lantern(x, 3, 8, true);
      for (const x of [-7, -6, 6, 7]) b.stairs(x, 1, 7, 'spruce_stairs', 'north');
      for (let y = 9; y <= 17; y++) b.set(5, y, -2, 'bricks'); b.set(5, 18, -2, 'brick_wall');
    },
  },
];
