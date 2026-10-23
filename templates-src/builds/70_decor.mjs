// Statues, landmarks and decor.
import { tex, FAM, STYLE, tower, roofs } from '../house2.mjs';
import { hash3, noise3 } from '../lib.mjs';
import { hangingLantern } from '../pieces.mjs';

const STATUE = { main: 'andesite', mix: [['stone', 0.18], ['calcite', 0.06], ['dead_fire_coral_block', 0.05]], low: [['mossy_cobblestone', 0.25], ['tuff', 0.2]] };

export default [
  {
    id: 'dino_skeleton', name: 'T-Rex Museum Skeleton', cat: 'Statues & Decor', seed: 301,
    desc: 'Life-size T-rex skeleton in bone blocks on a polished museum plinth with chains, iron supports and display lights.',
    build(b) {
      // plinth
      for (let x = -14; x <= 14; x++) for (let z = -8; z <= 8; z++) {
        const edge = Math.abs(x) === 14 || Math.abs(z) === 8;
        b.set(x, 0, z, edge ? 'polished_andesite' : 'light_gray_concrete');
        if (edge) b.set(x, 1, z, (x + z) % 4 === 0 ? 'polished_andesite' : 'iron_chain', (x + z) % 4 === 0 ? undefined : { axis: Math.abs(x) === 14 ? 'z' : 'x' });
        if (!edge && (x % 6 === 0 && z % 5 === 0)) b.set(x, 1, z, 'sea_lantern');
      }
      // spine curve: head high at +x, tail sweeping down to -x
      const spine = (t) => [Math.round(-18 + t * 34), Math.round(11 + 3 * Math.sin(t * Math.PI) - (t < 0.25 ? (0.25 - t) * 24 : 0) + (t > 0.85 ? (t - 0.85) * 10 : 0)), Math.round(Math.sin(t * 5) * 1.2)];
      let prev = null;
      for (let i = 0; i <= 60; i++) {
        const t = i / 60, [x, y, z] = spine(t);
        b.set(x, y, z, 'bone_block', { axis: 'x' });
        if (i % 3 === 0 && t > 0.15 && t < 0.85) b.set(x, y + 1, z, 'bone_block', { axis: 'y' });
        if (prev && Math.abs(prev[1] - y) > 1) b.set(x, y + Math.sign(prev[1] - y), z, 'bone_block', { axis: 'y' });
        prev = [x, y, z];
        // ribs in the torso
        if (t > 0.45 && t < 0.78 && i % 2 === 0) for (const sg of [-1, 1]) { b.set(x, y - 1, z + sg * 2, 'bone_block', { axis: 'z' }); b.set(x, y - 2, z + sg * 3, 'bone_block', { axis: 'y' }); b.set(x, y - 4, z + sg * 3, 'bone_block', { axis: 'y' }); b.set(x, y - 5, z + sg * 2, 'bone_block', { axis: 'z' }); b.set(x, y - 3, z + sg * 3, 'bone_block', { axis: 'y' }); b.set(x, y - 1, z + sg * 1, 'bone_block', { axis: 'z' }); }
      }
      // skull with open jaw
      const [hx, hy] = spine(1);
      b.fill(hx, hy - 1, -2, hx + 6, hy + 2, 2, 'bone_block');
      b.clear(hx + 1, hy, -1, hx + 5, hy + 1, 1);
      b.set(hx + 2, hy + 1, -2, 'air'); b.set(hx + 2, hy + 1, 2, 'air');
      b.fill(hx + 1, hy - 4, -1, hx + 6, hy - 3, 1, 'bone_block'); b.clear(hx + 2, hy - 3, 0, hx + 5, hy - 3, 0);
      for (let x = hx + 2; x <= hx + 6; x += 2) { b.set(x, hy - 2, -1, 'end_rod', { facing: 'down' }); b.set(x, hy - 2, 1, 'end_rod', { facing: 'down' }); b.set(x + 1, hy - 2, 0, 'end_rod', { facing: 'up' }); }
      b.set(hx, hy - 2, 0, 'bone_block', { axis: 'y' }); b.set(hx, hy - 3, 0, 'bone_block', { axis: 'y' });
      // legs
      for (const sg of [-1, 1]) {
        const lx = -2;
        b.line(lx, 10, sg * 3, lx - 2, 6, sg * 4, 'bone_block'); b.line(lx - 2, 6, sg * 4, lx + 1, 2, sg * 4, 'bone_block');
        b.fill(lx, 1, sg * 4 - 1, lx + 3, 1, sg * 4 + 1, 'bone_block');
        b.line(6, 8, sg * 2, 8, 6, sg * 3, 'bone_block'); b.set(9, 6, sg * 3, 'end_rod', { facing: 'east' });
      }
      // supports
      for (const x of [-10, 4]) for (let y = 2; y <= 9; y++) b.set(x, y, 0, 'iron_bars');
    },
  },
  {
    id: 'angel_statue', name: 'Winged Guardian Statue', cat: 'Statues & Decor', seed: 302,
    desc: 'Tall weathered stone guardian with wide feathered wings and a great sword, standing on a moss-streaked carved pedestal.',
    build(b) {
      // pedestal
      for (let y = 0; y <= 4; y++) for (let x = -6; x <= 6; x++) for (let z = -4; z <= 4; z++) {
        const edge = Math.abs(x) === 6 || Math.abs(z) === 4; if (!edge && y > 0 && y < 4) continue;
        b.set(x, y, z, y === 4 ? 'polished_andesite' : y === 0 ? 'stone_bricks' : tex(FAM.stonebrick, x, y, z, 2, y === 1 ? 1 : 0));
      }
      for (let x = -6; x <= 6; x++) for (const z of [-5, 5]) b.stairs(x, 4, z, 'polished_andesite_stairs', z > 0 ? 'north' : 'south', 'top');
      const S = (x, y, z) => tex(STATUE, x, y, z, 7, y < 14 ? 0.6 : 0);
      // legs, robe, torso, shoulders, head
      for (const sg of [-1, 1]) for (let y = 5; y <= 13; y++) b.ellipsoid(sg * 2, y, 0, 1.4, 0.6, 1.4, S);
      for (let y = 9; y <= 15; y++) b.ellipsoid(0, y, 0, 3.4 - (15 - y) * 0.15, 0.6, 2.2, S);
      for (let y = 15; y <= 22; y++) b.ellipsoid(0, y, 0, 3.2 + (y > 19 ? 0.6 : 0), 0.6, 2, S);
      b.ellipsoid(0, 25, 0, 2.2, 2.4, 2.2, S);
      b.ellipsoid(0, 27, 0, 2.4, 1, 2.4, (x, y, z) => (hash3(x, y, z, 2) < 0.5 ? 'tuff' : 'cobbled_deepslate'));
      // arms down to the sword hilt in front
      for (const sg of [-1, 1]) b.line(sg * 4, 21, 0, sg * 1, 15, 3, 'andesite');
      for (let y = 5; y <= 17; y++) b.set(0, y, 4, y > 15 ? 'stone_bricks' : 'iron_block');
      b.set(-1, 15, 4, 'stone_bricks'); b.set(1, 15, 4, 'stone_bricks'); b.set(0, 18, 4, 'stone_bricks');
      // wings: feathered layers of calcite / diorite fading to andesite near the back
      for (const sg of [-1, 1]) for (let i = 0; i < 18; i++) for (let j = 0; j < 12 - Math.floor(i * 0.5); j++) {
        const x = sg * (3 + i), y = 16 + Math.round(i * 0.7) - j, z = -3 - Math.floor(i / 6);
        if (y < 8) continue;
        const feather = j < 2 ? 'calcite' : j < 5 ? (hash3(x, y, z, 4) < 0.6 ? 'diorite' : 'calcite') : (hash3(x, y, z, 5) < 0.5 ? 'andesite' : 'diorite');
        b.set(x, y, z, feather);
        if (j === 11 - Math.floor(i * 0.5) && i % 2 === 0) b.stairs(x, y - 1, z, 'diorite_stairs', sg > 0 ? 'west' : 'east', 'top');
      }
      // moss streaks
      b.each((x, y, z, n) => { if ((n === 'andesite' || n === 'stone') && noise3(x, y, z, 2, 9) > 0.78) b.set(x, y, z, 'mossy_cobblestone'); });
    },
  },
  {
    id: 'lighthouse', name: 'Striped Lighthouse', cat: 'Statues & Decor', seed: 303,
    desc: 'Tapered red-and-white lighthouse on a rocky stone base, with a railed gallery, glass lamp room, glowing light and a copper dome.',
    build(b) {
      for (let y = 0; y <= 2; y++) b.disc(0, y, 0, 7 - y, (x, yy, z) => tex(FAM.cobble, x, yy, z, 3, 1 - yy / 3));
      const H = 28;
      for (let y = 3; y <= H; y++) {
        const r = 4.6 - (y - 3) * 0.06;
        b.disc(0, y, 0, r, Math.floor((y - 3) / 4) % 2 ? 'red_terracotta' : 'white_terracotta', {}, true);
        if (y % 6 === 0) { b.set(Math.round(r), y, 0, 'glass_pane'); b.set(-Math.round(r), y + 2, 0, 'glass_pane'); }
      }
      b.disc(0, H + 1, 0, 5, 'polished_andesite'); b.disc(0, H + 2, 0, 5, 'iron_bars', {}, true);
      for (let y = H + 2; y <= H + 5; y++) b.disc(0, y, 0, 2.6, 'glass', {}, true);
      b.set(0, H + 3, 0, 'sea_lantern'); b.set(0, H + 4, 0, 'glowstone');
      b.disc(0, H + 6, 0, 3, 'waxed_copper_block'); b.disc(0, H + 7, 0, 2, 'waxed_copper_block'); b.set(0, H + 8, 0, 'waxed_copper_block'); b.set(0, H + 9, 0, 'lightning_rod', { facing: 'up' });
      b.door(0, 3, 4, 'spruce_door', 'south');
    },
  },
  {
    id: 'windmill', name: 'Countryside Windmill', cat: 'Statues & Decor', seed: 304,
    desc: 'Tapering stone-and-timber windmill with a rotated shingle cap and four lattice sails with white cloth.',
    build(b) {
      for (let y = 0; y <= 16; y++) {
        const r = 5 - y * 0.15;
        for (let x = -6; x <= 6; x++) for (let z = -6; z <= 6; z++) {
          const d = Math.max(Math.abs(x), Math.abs(z)) * 0.75 + Math.min(Math.abs(x), Math.abs(z)) * 0.35;
          if (d > r + 0.4 || d < r - 0.6) continue;
          b.set(x, y, z, y < 6 ? tex(FAM.cobble, x, y, z, 2, y < 2 ? 1 : 0) : (x + z) % 4 === 0 ? 'stripped_spruce_log' : 'white_terracotta');
        }
      }
      for (let i = 0; i <= 3; i++) for (let x = -4 + i; x <= 4 - i; x++) for (let z = -4; z <= 4; z++) { b.stairs(x, 17 + i, z, 'dark_oak_stairs', z > 0 ? 'north' : 'south'); }
      for (let z = -4; z <= 4; z++) b.slab(0, 21, z, 'dark_oak_slab');
      // hub + sails on the south face
      const hy = 15, hz = 5;
      b.set(0, hy, hz, 'stripped_dark_oak_log', { axis: 'z' }); b.set(0, hy, hz + 1, 'dark_oak_planks');
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const px = -dy, py = dx; // sail cloth sits on one side of each arm (they turn the same way)
        for (let k = 1; k <= 11; k++) {
          const x = dx * k, y = hy + dy * k; if (y < 1) continue;
          b.set(x, y, hz + 1, 'spruce_fence');
          if (k >= 3) for (let w = 1; w <= 3; w++) { const sx = x + px * w, sy = y + py * w; if (sy < 1) continue; b.set(sx, sy, hz + 1, w === 3 || k % 4 === 3 ? 'spruce_fence' : 'white_wool'); }
        }
      }
      b.door(0, 1, 5, 'spruce_door', 'south');
    },
  },
  {
    id: 'wizard_tower', name: 'Wizard Tower', cat: 'Statues & Decor', seed: 305,
    desc: 'Tall round stone tower with a purple-shingle spire, a side turret, balconies with lanterns and glowing windows.',
    build(b) {
      const S = { ...STYLE.slate, roof: 'purpur', trim: 'dark_oak', tower: FAM.stonebrick };
      tower(b, { ...S, roof: 'dark_oak' }, 0, 0, 0, 26, 4, 5);
      b.each((x, y, z, n) => { if (/^dark_oak_stairs$/.test(n) && y > 26) b.set(x, y, z, 'purpur_stairs', { facing: 'north' }); });
      tower(b, S, 5, 3, 8, 12, 2, 6);
      for (let y = 5; y <= 25; y += 7) { for (let k = -2; k <= 2; k++) b.slab(k, y, 5, 'dark_oak_slab', 'top'); for (let k = -2; k <= 2; k++) b.set(k, y + 1, 6, 'dark_oak_fence'); hangingLantern(b, 2, y + 3, 6, 0); }
      b.door(0, 0, 4, 'dark_oak_door', 'south');
      for (let y = 3; y <= 24; y += 4) b.set(-4, y, 0, 'amethyst_block');
    },
  },
  {
    id: 'market_stall', name: 'Market Stalls', cat: 'Statues & Decor', seed: 306,
    desc: 'Three wooden market stalls with striped canopies, counters, barrels, crates, hay and hanging lanterns.',
    build(b) {
      const cols = [['red', 'white'], ['yellow', 'white'], ['blue', 'white']];
      cols.forEach(([c1, c2], i) => {
        const ox = i * 7;
        for (const [x, z] of [[0, 0], [4, 0], [0, 3], [4, 3]]) for (let y = 0; y <= 3; y++) b.set(ox + x, y, z, 'spruce_fence');
        for (let x = -1; x <= 5; x++) for (let z = -1; z <= 4; z++) { const y = 4 + (z > 1 ? 0 : 0) - (z === -1 ? 1 : 0); b.set(ox + x, y, z, (x + 10) % 2 ? `${c1}_wool` : `${c2}_wool`); }
        for (let x = 1; x <= 3; x++) { b.set(ox + x, 0, 0, 'spruce_planks'); b.slab(ox + x, 1, 0, 'spruce_slab'); }
        b.set(ox + 1, 2, 0, 'melon'); b.set(ox + 3, 2, 0, 'pumpkin');
        b.set(ox + 1, 0, 3, 'barrel', { facing: 'up' }); if (i === 1) b.set(ox + 3, 0, 3, 'hay_block', { axis: 'y' }); else b.set(ox + 3, 0, 3, 'barrel', { facing: 'up' });
        hangingLantern(b, ox + 2, 3, 1, 0);
      });
    },
  },
  {
    id: 'campsite', name: 'Forest Campsite', cat: 'Statues & Decor', seed: 307,
    desc: 'Canvas A-frame tent, log benches around a stone fire ring, a lantern post, wood pile and supplies.',
    build(b) {
      for (let i = 0; i <= 3; i++) for (let x = -3; x <= 3; x++) { b.stairs(x, i, -3 + i, 'birch_stairs', 'south'); b.stairs(x, i, 3 - i, 'birch_stairs', 'north'); }
      for (let x = -3; x <= 3; x++) for (let z = -2; z <= 2; z++) for (let y = 0; y <= 2; y++) if (Math.abs(z) <= 2 - y && (x === -3)) b.set(x, y, z, 'white_wool');
      for (let x = -4; x <= 4; x++) b.set(x, 4, 0, 'spruce_fence');
      b.set(4, 0, 2, 'white_wool'); b.set(3, 0, 0, 'red_bed' === 'x' ? 'x' : 'white_carpet');
      b.disc(0, 0, 8, 1.5, 'cobblestone_slab', {}, true); b.set(0, 0, 8, 'campfire', { lit: 'true', facing: 'north' });
      for (const [x, z, ax] of [[-3, 8, 'z'], [3, 8, 'z'], [0, 11, 'x']]) { b.log(x, 0, z, 'stripped_oak_log', ax); b.log(x + (ax === 'x' ? 1 : 0), 0, z + (ax === 'z' ? 1 : 0), 'stripped_oak_log', ax); }
      for (let y = 0; y <= 2; y++) b.set(5, y, 6, 'spruce_fence'); b.lantern(5, 3, 6);
      for (let x = 6; x <= 7; x++) for (let y = 0; y <= 1; y++) b.log(x, y, 9, 'oak_log', 'z');
      b.set(-5, 0, 5, 'barrel', { facing: 'up' }); b.set(-5, 1, 5, 'chest', { facing: 'east' });
    },
  },
  {
    id: 'mushroom_house', name: 'Giant Mushroom House', cat: 'Statues & Decor', seed: 308,
    desc: 'Fairy-tale house inside a giant red mushroom: spotted cap, round stem walls with windows, a door and a lantern.',
    build(b) {
      for (let y = 0; y <= 8; y++) b.disc(0, y, 0, 3.4 - y * 0.08, 'calcite', {}, true);
      b.ellipsoid(0, 11, 0, 8, 4, 8, (x, y, z) => (hash3(x, y, z, 3) < 0.13 && y > 11 ? 'white_concrete' : 'red_concrete'), {}, (x, y, z, d) => d > 0.6 && y >= 9);
      b.disc(0, 9, 0, 7.6, 'red_terracotta', {}, true);
      for (let y = 0; y <= 8; y++) b.disc(0, y, 0, 2.3, 'air');
      b.door(0, 0, 3, 'dark_oak_door', 'south');
      for (const [x, z] of [[3, 0], [-3, 0], [0, -3]]) { b.set(x, 3, z, 'glass_pane'); b.set(x, 4, z, 'glass_pane'); }
      b.set(1, 2, 4, 'lantern', { hanging: 'false' });
    },
  },
];
