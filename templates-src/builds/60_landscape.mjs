// Landscaping pieces: trees, bushes, paths, walls, lights, fountain, gazebo, bridge, well, garden.
import { oakTree, birchTree, spruceTree, cherryTree, darkOakTree, bush, path, steppingStones, streetLampClassic, streetLampModern, hangingLantern, flowerBed } from '../pieces.mjs';
import { tex, FAM, STAIR_OF } from '../house2.mjs';
import { palm } from './40_city.mjs';

export default [
  { id: 'tree_big_oak', name: 'Big Oak Tree', cat: 'Landscaping', seed: 201, desc: 'Wide custom oak with branching limbs, root flare and layered leaf clusters.',
    build(b) { oakTree(b, 0, 0, 0, 8, { seed: 5, wide: 1.25 }); } },
  { id: 'tree_birch_pair', name: 'Birch Pair', cat: 'Landscaping', seed: 202, desc: 'Two slender birches of different heights - great for gardens and forest edges.',
    build(b) { birchTree(b, 0, 0, 0, 9, { seed: 3 }); birchTree(b, 4, 0, 2, 7, { seed: 8 }); } },
  { id: 'tree_spruce', name: 'Tall Spruce', cat: 'Landscaping', seed: 203, desc: 'Layered conical spruce with alternating wide and tight rings.',
    build(b) { spruceTree(b, 0, 0, 0, 14); } },
  { id: 'tree_cherry', name: 'Cherry Blossom Tree', cat: 'Landscaping', seed: 204, desc: 'Spreading cherry tree with arching branches and drooping blossom.',
    build(b) { cherryTree(b, 0, 0, 0, 6); } },
  { id: 'tree_dark_oak', name: 'Dark Oak Canopy', cat: 'Landscaping', seed: 205, desc: '2x2 dark oak with a broad, two-layer flat canopy.',
    build(b) { darkOakTree(b, 0, 0, 0, 6); } },
  { id: 'tree_palm', name: 'Palm Trees', cat: 'Landscaping', seed: 206, desc: 'Two leaning palms for beaches and resorts.',
    build(b) { palm(b, 0, 0, 0, 8); palm(b, 4, 0, 3, 6); } },
  { id: 'bush_set', name: 'Bush & Shrub Set', cat: 'Landscaping', seed: 207, desc: 'Six natural bushes in different sizes and leaf mixes, with flowering azalea.',
    build(b) { bush(b, 0, 0, 0, 2); bush(b, 6, 0, 0, 1, 'azalea'); bush(b, 11, 0, 1, 1, 'oak'); bush(b, 0, 0, 6, 1, 'spruce'); bush(b, 5, 0, 6, 2, 'azalea'); bush(b, 11, 0, 6, 0); } },
  { id: 'path_cobble', name: 'Winding Stone Path', cat: 'Landscaping', seed: 208, desc: 'Curved path that blends from worn dirt in the middle to mossy cobble at the ragged edges.',
    build(b) { path(b, [[0, 0], [6, 3], [12, 2], [18, 6], [22, 12]], 0, 4, 'stone'); } },
  { id: 'path_garden', name: 'Garden Path with Stepping Stones', cat: 'Landscaping', seed: 209, desc: 'Gravel garden path with stone-slab stepping stones and flower beds along both sides.',
    build(b) { path(b, [[0, 0], [16, 0]], 0, 3, 'gravel'); steppingStones(b, [[0, 0], [16, 0]], 0); flowerBed(b, 2, 3, 6, 4, 0); flowerBed(b, 9, -4, 14, -3, 0); } },
  { id: 'stone_wall_section', name: 'Garden Wall with Pillars', cat: 'Landscaping', seed: 210, desc: 'Textured stone wall: pillars with lantern caps, recessed panels, a capping row and rubble at the base.',
    build(b) {
      const L = 17;
      for (let x = 0; x < L; x++) for (let y = 0; y <= 2; y++) {
        const pillar = x % 4 === 0;
        b.set(x, y, 0, pillar ? tex(FAM.stonebrick, x, y, 0, 3, y === 0 ? 1 : 0) : tex(FAM.cobble, x, y, 0, 4, y === 0 ? 1 : 0.3));
        if (pillar) { b.set(x, y, 1, tex(FAM.stonebrick, x, y, 1, 3)); b.set(x, y, -1, tex(FAM.stonebrick, x, y, -1, 3)); }
      }
      for (let x = 0; x < L; x++) { if (x % 4 === 0) { b.set(x, 3, 0, 'stone_bricks'); b.set(x, 4, 0, 'lantern'); for (const z of [-1, 1]) b.stairs(x, 3, z, 'stone_brick_stairs', z > 0 ? 'north' : 'south'); } else b.set(x, 3, 0, 'stone_brick_wall'); }
      for (let x = 1; x < L; x += 3) b.set(x, 0, 1, 'stone_button', { face: 'floor', facing: 'north' });
    } },
  { id: 'hedge_wall', name: 'Trimmed Hedge Row', cat: 'Landscaping', seed: 211, desc: 'Formal hedge on a stone curb with topiary balls and a wooden gate in the middle.',
    build(b) {
      for (let x = -9; x <= 9; x++) { b.slab(x, 0, 0, 'stone_brick_slab', 'top'); if (Math.abs(x) <= 1) continue; b.set(x, 1, 0, 'oak_leaves'); b.set(x, 2, 0, 'oak_leaves'); }
      for (const x of [-9, -5, 5, 9]) { b.set(x, 3, 0, 'azalea_leaves'); }
      b.set(0, 1, 0, 'spruce_fence_gate', { facing: 'south' }); b.set(-1, 1, 0, 'spruce_fence'); b.set(1, 1, 0, 'spruce_fence');
      for (const x of [-2, 2]) { b.set(x, 3, 0, 'spruce_fence'); b.lantern(x, 4, 0); }
    } },
  { id: 'street_lights', name: 'Street Light Set', cat: 'Landscaping', seed: 212, desc: 'Three styles: modern pole light, classic lantern post and a double hanging lamp.',
    build(b) {
      streetLampModern(b, 0, 0, 0, 'south', 6);
      streetLampClassic(b, 5, 0, 0, 4);
      for (let y = 0; y <= 5; y++) b.set(10, y, 0, y === 0 ? 'stone_brick_wall' : 'dark_oak_fence');
      b.set(9, 5, 0, 'dark_oak_fence'); b.set(11, 5, 0, 'dark_oak_fence'); hangingLantern(b, 9, 4, 0, 0); hangingLantern(b, 11, 4, 0, 0);
      b.set(10, 6, 0, 'dark_oak_trapdoor', { facing: 'north', half: 'bottom', open: 'false' });
    } },
  { id: 'fountain_plaza', name: 'Tiered Fountain', cat: 'Landscaping', seed: 213, desc: 'Round two-tier stone fountain with a lit basin, water spouts and benches around it.',
    build(b) {
      b.disc(0, 0, 0, 6, 'polished_andesite');
      b.disc(0, 1, 0, 5, 'stone_bricks', {}, true); b.disc(0, 1, 0, 4.2, 'water'); b.disc(0, 0, 0, 4.2, 'smooth_stone');
      b.set(2, 0, 2, 'sea_lantern'); b.set(-2, 0, -2, 'sea_lantern'); b.set(-2, 0, 2, 'sea_lantern'); b.set(2, 0, -2, 'sea_lantern');
      for (let y = 1; y <= 4; y++) b.set(0, y, 0, 'stone_brick_wall');
      b.disc(0, 4, 0, 2, 'stone_bricks', {}, true); b.disc(0, 4, 0, 1.2, 'water');
      b.set(0, 5, 0, 'chiseled_stone_bricks'); b.set(0, 6, 0, 'water');
      for (const [x, z, f] of [[0, 8, 'south'], [0, -8, 'north'], [8, 0, 'east'], [-8, 0, 'west']]) { b.stairs(x, 1, z, 'spruce_stairs', f); const l = f === 'south' || f === 'north'; b.stairs(x + (l ? 1 : 0), 1, z + (l ? 0 : 1), 'spruce_stairs', f); b.slab(x, 0, z, 'stone_brick_slab', 'top'); }
    } },
  { id: 'gazebo', name: 'Garden Gazebo', cat: 'Landscaping', seed: 214, desc: 'Octagonal spruce gazebo with a layered pointed roof, hanging lantern and benches.',
    build(b) {
      b.disc(0, 0, 0, 4, 'spruce_planks');
      for (const [x, z] of [[3, 1], [3, -1], [-3, 1], [-3, -1], [1, 3], [-1, 3], [1, -3], [-1, -3]]) for (let y = 1; y <= 3; y++) b.log(x, y, z, 'stripped_spruce_log');
      for (let k = 0; k <= 4; k++) { const rr = 4.5 - k; for (let x = -5; x <= 5; x++) for (let z = -5; z <= 5; z++) { const d = Math.hypot(x, z); if (d > rr + 0.3 || d < rr - 0.8) continue; const f = Math.abs(x) >= Math.abs(z) ? (x > 0 ? 'west' : 'east') : (z > 0 ? 'north' : 'south'); b.stairs(x, 4 + k, z, 'dark_oak_stairs', f); } }
      b.set(0, 9, 0, 'dark_oak_fence'); hangingLantern(b, 0, 7, 0, 2);
      for (const [x, z, f] of [[2, 0, 'west'], [-2, 0, 'east']]) b.stairs(x, 1, z, 'spruce_stairs', f);
    } },
  { id: 'stone_bridge', name: 'Arched Stone Bridge', cat: 'Landscaping', seed: 215, desc: 'Arched stone bridge with textured walls, stair-smoothed arch and lanterns on posts.',
    build(b) {
      const L = 17;
      for (let x = 0; x < L; x++) {
        const t = x / (L - 1), h = Math.round(Math.sin(t * Math.PI) * 3);
        for (let z = -2; z <= 2; z++) {
          b.set(x, h + 1, z, Math.abs(z) === 2 ? tex(FAM.stonebrick, x, h + 1, z, 2) : 'spruce_planks');
          if (Math.abs(z) === 2) b.set(x, h + 2, z, 'stone_brick_wall');
          for (let y = 0; y <= h; y++) if (y >= h - 1 || x < 2 || x > L - 3) b.set(x, y, z, tex(FAM.stonebrick, x, y, z, 2, y === 0 ? 1 : 0));
        }
      }
      for (const x of [0, L - 1]) for (const z of [-2, 2]) { b.set(x, 2, z, 'stone_bricks'); b.lantern(x, 3, z); }
    } },
  { id: 'village_well', name: 'Village Well', cat: 'Landscaping', seed: 216, desc: 'Round cobble well with a water bucket chain, timber frame and little shingle roof.',
    build(b) {
      for (let y = 0; y <= 1; y++) b.disc(0, y, 0, 2.2, (y ? 'mossy_cobblestone' : 'cobblestone'), {}, true);
      b.disc(0, 0, 0, 1.2, 'water');
      for (const x of [-2, 2]) for (let y = 2; y <= 4; y++) b.log(x, y, 0, 'stripped_spruce_log');
      b.log(-1, 4, 0, 'spruce_log', 'x'); b.log(0, 4, 0, 'spruce_log', 'x'); b.log(1, 4, 0, 'spruce_log', 'x'); b.chain(0, 3, 0); b.set(0, 2, 0, 'cauldron');
      for (let x = -3; x <= 3; x++) { b.stairs(x, 5, -1, 'spruce_stairs', 'south'); b.stairs(x, 5, 1, 'spruce_stairs', 'north'); b.slab(x, 6, 0, 'spruce_slab'); }
    } },
  { id: 'flower_garden', name: 'Flower Garden Beds', cat: 'Landscaping', seed: 217, desc: 'Raised flower beds with trapdoor borders, a bench, a bird bath and a small tree.',
    build(b) {
      flowerBed(b, 0, 0, 5, 2, 0); flowerBed(b, 0, 5, 5, 7, 0); flowerBed(b, 8, 0, 10, 7, 0);
      b.set(7, 1, 4, 'stone_brick_wall'); b.set(7, 2, 4, 'cauldron');
      b.stairs(3, 0, 4, 'spruce_stairs', 'north'); b.stairs(4, 0, 4, 'spruce_stairs', 'north');
      bush(b, -2, 0, 4, 1, 'azalea');
    } },
];
