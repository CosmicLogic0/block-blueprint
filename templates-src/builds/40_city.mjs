// City: skyscrapers, apartments, burger diner, parking lot, gas station, roads, street furniture.
import { MOD, planter } from '../modern.mjs';
import { streetLampModern } from '../pieces.mjs';

/** Glass tower: square floor plate, curtain walls with mullions, floor bands, a setback and a crown. */
function tower(b, o) {
  const { w = 13, h = 48, setAt = 34, setIn = 2, glass = 'light_blue_stained_glass', frame = 'light_gray_concrete', band = 'white_concrete', core = 'gray_concrete', crown = 'sea_lantern' } = o;
  const shell = (r0, y0, y1) => {
    for (let y = y0; y <= y1; y++) for (let x = -r0; x <= r0; x++) for (let z = -r0; z <= r0; z++) {
      const edge = Math.abs(x) === r0 || Math.abs(z) === r0; if (!edge) continue;
      const corner = Math.abs(x) === r0 && Math.abs(z) === r0;
      const a = Math.abs(x) === r0 ? z : x;
      const fl = (y - y0) % 4 === 0;
      b.set(x, y, z, corner ? frame : fl ? band : a % 3 === 0 ? frame : glass);
    }
    for (let y = y0; y <= y1; y += 4) for (let x = -r0 + 1; x < r0; x++) for (let z = -r0 + 1; z < r0; z++) b.set(x, y, z, Math.abs(x) <= 1 && Math.abs(z) <= 1 ? core : 'smooth_stone');
  };
  const r = Math.floor(w / 2);
  // lobby: double height, recessed glass behind columns, canopy
  for (let y = 1; y <= 7; y++) for (let x = -r; x <= r; x++) for (let z = -r; z <= r; z++) {
    const edge = Math.abs(x) === r || Math.abs(z) === r, inner = Math.abs(x) === r - 1 || Math.abs(z) === r - 1;
    if (edge && (Math.abs(x) % 3 === 0 && Math.abs(z) === r || Math.abs(z) % 3 === 0 && Math.abs(x) === r)) b.set(x, y, z, frame);
    else if (!edge && inner && Math.abs(x) <= r - 1 && Math.abs(z) <= r - 1) b.set(x, y, z, y === 7 ? band : 'glass');
  }
  for (let x = -r - 2; x <= r + 2; x++) for (let z = -r - 2; z <= r + 2; z++) b.set(x, 0, z, Math.abs(x) > r || Math.abs(z) > r ? 'smooth_stone' : 'polished_andesite');
  for (let x = -3; x <= 3; x++) for (let z = r; z <= r + 3; z++) b.slab(x, 6, z, 'smooth_quartz_slab', 'top');
  b.door(0, 1, r - 1, 'iron_door', 'south'); b.door(-1, 1, r - 1, 'iron_door', 'south', 'right');
  shell(r, 8, setAt); shell(r - setIn, setAt + 1, h);
  // crown: lit band, roof plant, antenna
  const rr = r - setIn;
  for (let x = -rr; x <= rr; x++) for (let z = -rr; z <= rr; z++) { b.set(x, h + 1, z, Math.abs(x) === rr || Math.abs(z) === rr ? crown : 'gray_concrete'); }
  for (let x = -rr; x <= rr; x++) for (let z = -rr; z <= rr; z++) if (Math.abs(x) === rr || Math.abs(z) === rr) b.set(x, h + 2, z, 'light_gray_concrete');
  b.fill(-2, h + 2, -2, 1, h + 4, 1, 'gray_concrete');
  for (let y = h + 5; y <= h + 14; y++) b.set(0, y, 0, 'iron_bars');
  b.set(0, h + 15, 0, 'redstone_lamp', { lit: 'true' });
  for (let x = -r; x <= r; x++) for (let z = -r; z <= r; z++) if ((Math.abs(x) === r || Math.abs(z) === r) && !b.has(x, setAt + 1, z)) b.slab(x, setAt + 1, z, 'smooth_stone_slab');
}

/**
 * High-rise in the style of modern Minecraft city builders: a stone base, then floors with a projecting slab band
 * (balcony edge) at each level, windows recessed behind solid piers, corner fins, and a rooftop garden.
 * o: { w, d, floors, fh (floor height), wall, pier, band (slab), glass, base, roof, fins, palms, setback: {at, by} }
 */
function highrise(b, o) {
  const { w = 7, d = 6, floors = 14, fh = 4, wall, pier, band, bandFull, glass = 'gray_stained_glass_pane', base = 'polished_andesite', fins = null, palms = 2, setback = null, rails = false } = o;
  const R = (f) => (setback && f >= setback.at ? setback.by : 0);
  // base: 2-storey stone podium with tall shop windows and an entrance canopy
  for (let y = 0; y <= 7; y++) for (let x = -w - 1; x <= w + 1; x++) for (let z = -d - 1; z <= d + 1; z++) {
    const edge = Math.abs(x) === w + 1 || Math.abs(z) === d + 1;
    if (y === 0) { b.set(x, 0, z, base); continue; }
    if (!edge) continue;
    const a = Math.abs(x) === w + 1 ? z : x;
    b.set(x, y, z, y === 7 ? bandFull : Math.abs(a) % 3 === 0 ? base : 'glass');
  }
  for (let x = -2; x <= 2; x++) for (let z = d + 2; z <= d + 4; z++) b.slab(x, 4, z, band, 'top');
  b.door(0, 1, d + 1, 'iron_door', 'south'); b.door(-1, 1, d + 1, 'iron_door', 'south', 'right');
  for (let f = 0; f < floors; f++) {
    const y0 = 8 + f * fh, r = R(f), W = w - r, D = d - r;
    // floor plate + projecting slab band
    for (let x = -W - 1; x <= W + 1; x++) for (let z = -D - 1; z <= D + 1; z++) {
      const lip = Math.abs(x) === W + 1 || Math.abs(z) === D + 1;
      if (lip) b.slab(x, y0, z, band, 'bottom'); else b.set(x, y0, z, bandFull);
    }
    if (rails) for (let x = -W - 1; x <= W + 1; x++) for (let z = -D - 1; z <= D + 1; z++) if ((Math.abs(x) === W + 1 || Math.abs(z) === D + 1) && (x + z + f) % 1 === 0 && Math.abs(z) === D + 1) b.set(x, y0 + 1, z, 'glass_pane');
    for (let y = y0 + 1; y < y0 + fh; y++) for (let x = -W; x <= W; x++) for (let z = -D; z <= D; z++) {
      const edge = Math.abs(x) === W || Math.abs(z) === D; if (!edge) { b.del(x, y, z); continue; }
      const corner = Math.abs(x) === W && Math.abs(z) === D;
      const a = Math.abs(x) === W ? z : x;
      const isPier = corner || Math.abs(a) % 3 === 0;
      if (isPier) b.set(x, y, z, pier);
      else if (y === y0 + fh - 1) b.set(x, y, z, wall);
      else b.set(x, y, z, glass);
    }
    // vertical fins on the corners for depth
    if (fins) for (const [x, z] of [[W + 1, D + 1], [-W - 1, D + 1], [W + 1, -D - 1], [-W - 1, -D - 1]]) for (let y = y0 + 1; y < y0 + fh; y++) b.set(x, y, z, fins);
    // interior lights so windows read at night
    b.set(0, y0 + fh - 1, 0, 'sea_lantern');
  }
  // roof: parapet, garden, palms, plant room
  const top = 8 + floors * fh, r = R(floors - 1), W = w - r, D = d - r;
  for (let x = -W - 1; x <= W + 1; x++) for (let z = -D - 1; z <= D + 1; z++) {
    const lip = Math.abs(x) === W + 1 || Math.abs(z) === D + 1;
    b.set(x, top, z, bandFull);
    if (lip) b.set(x, top + 1, z, 'glass_pane');
    else if (Math.abs(x) > 1 && Math.abs(z) > 1 && (Math.abs(x) === W || Math.abs(z) === D)) { b.set(x, top + 1, z, 'grass_block'); b.set(x, top + 2, z, (x + z) % 3 ? 'short_grass' : 'azalea'); }
  }
  b.fill(-2, top + 1, -2, 2, top + 3, 2, wall); b.fill(-2, top + 4, -2, 2, top + 4, 2, bandFull);
  const spots = [[W - 2, D - 2], [-W + 2, -D + 2], [W - 2, -D + 2]].slice(0, palms);
  for (const [px, pz] of spots) palm(b, px, top + 1, pz);
  if (setback) for (let x = -w - 1; x <= w + 1; x++) for (let z = -d - 1; z <= d + 1; z++) {
    const yS = 8 + setback.at * fh;
    if (Math.abs(x) > w - setback.by || Math.abs(z) > d - setback.by) { if (!b.has(x, yS + 1, z)) { b.set(x, yS + 1, z, 'grass_block'); if ((x * 3 + z) % 4 === 0) b.set(x, yS + 2, z, 'azalea'); } }
  }
}
/** palm tree: bent jungle trunk with drooping leaf fronds */
export function palm(b, x, y, z, h = 7) {
  for (let i = 0; i < h; i++) b.log(x + (i > h - 3 ? 1 : 0), y + i, z, 'jungle_log');
  const tx = x + 1, ty = y + h;
  b.set(tx, ty, z, 'jungle_leaves');
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]]) for (let k = 1; k <= 3; k++) b.set(tx + dx * k, ty - (k === 3 ? 1 : 0), z + dz * k, 'jungle_leaves');
}

export default [
  {
    id: 'highrise_white', name: 'White Balcony High-Rise', cat: 'City', seed: 191,
    desc: '15-floor white residential tower: projecting quartz balcony bands every floor, dark recessed windows behind piers, glass railings and a rooftop garden with palms.',
    build(b) { highrise(b, { w: 6, d: 5, floors: 15, wall: 'white_concrete', pier: 'white_concrete', band: 'smooth_quartz_slab', bandFull: 'smooth_quartz', glass: 'black_stained_glass_pane', base: 'gray_concrete', fins: 'light_gray_concrete', rails: true }); },
  },
  {
    id: 'highrise_brick', name: 'Brick Tower', cat: 'City', seed: 192,
    desc: 'Tall brick apartment tower with a stone podium, white window bays, slab cornices every floor and a planted roof.',
    build(b) { highrise(b, { w: 5, d: 5, floors: 13, wall: 'bricks', pier: 'bricks', band: 'smooth_stone_slab', bandFull: 'smooth_stone', glass: 'glass_pane', base: 'stone_bricks', fins: null, palms: 1 }); },
  },
  {
    id: 'highrise_sandstone', name: 'Sandstone Residences', cat: 'City', seed: 193,
    desc: 'Warm cream tower in smooth sandstone with birch slab balconies, a setback upper section and roof terraces.',
    build(b) { highrise(b, { w: 7, d: 5, floors: 12, wall: 'smooth_sandstone', pier: 'cut_sandstone', band: 'birch_slab', bandFull: 'birch_planks', glass: 'brown_stained_glass_pane', base: 'polished_andesite', setback: { at: 8, by: 2 }, palms: 2 }); },
  },
  {
    id: 'highrise_glass_fins', name: 'Blue Glass Office Tower', cat: 'City', seed: 194,
    desc: 'Sleek 18-floor office tower: blue glass between black piers, thin slab bands, corner fins and a crown setback.',
    build(b) { highrise(b, { w: 6, d: 6, floors: 18, wall: 'black_concrete', pier: 'black_concrete', band: 'polished_blackstone_slab', bandFull: 'polished_blackstone', glass: 'light_blue_stained_glass', base: 'polished_blackstone_bricks', fins: 'gray_concrete', setback: { at: 14, by: 1 }, palms: 0 }); },
  },
  {
    id: 'apartment_block', name: 'City Apartment Block', cat: 'City', seed: 93,
    desc: 'Six-storey brick-and-white apartment building with stacked glass balconies, a ground floor shop and a roof garden.',
    build(b) {
      const W = 9, D = 6, H = 6;
      for (let f = 0; f < H; f++) {
        const y0 = 1 + f * 4;
        for (let y = y0; y < y0 + 4; y++) for (let x = -W; x <= W; x++) for (let z = -D; z <= D; z++) {
          const edge = Math.abs(x) === W || Math.abs(z) === D; if (!edge) { if (y === y0) b.set(x, y - 1, z, 'smooth_stone'); continue; }
          const band = y === y0 + 3;
          const win = (y === y0 + 1 || y === y0 + 2) && ((Math.abs(z) === D && Math.abs(x) % 3 !== 0 && Math.abs(x) < W) || (Math.abs(x) === W && Math.abs(z) % 3 === 1));
          b.set(x, y, z, band ? 'white_concrete' : win ? (f === 0 && z === D ? 'glass' : 'glass_pane') : f === 0 ? 'gray_concrete' : 'bricks');
        }
        if (f > 0) for (const x of [-6, 0, 6]) for (let k = -1; k <= 1; k++) { b.set(x + k, y0 - 1, D + 1, 'smooth_quartz'); b.set(x + k, y0, D + 2, 'glass_pane'); b.set(x - 2, y0, D + 1, 'glass_pane'); b.set(x + 2, y0, D + 1, 'glass_pane'); b.set(x + k, y0, D + 1, k === 0 ? 'air' : 'air'); b.door(x, y0, D, 'spruce_door', 'south'); }
      }
      for (let x = -W; x <= W; x++) b.slab(x, 4, D + 1, 'blue_concrete' === '' ? 'x' : 'smooth_stone_slab', 'top');
      b.door(0, 1, D, 'iron_door', 'south');
      const top = 1 + H * 4;
      for (let x = -W; x <= W; x++) for (let z = -D; z <= D; z++) { b.set(x, top, z, 'gray_concrete'); if (Math.abs(x) === W || Math.abs(z) === D) b.slab(x, top + 1, z, 'smooth_stone_slab'); }
      planter(b, -6, -3, -2, 3, top + 1, 'spruce_planks', 'oak_leaves'); planter(b, 2, -3, 6, -1, top + 1, 'spruce_planks', 'azalea_leaves');
      b.fill(3, top + 1, 2, 5, top + 2, 4, 'light_gray_concrete');
    },
  },
  {
    id: 'burger_diner', name: 'Burger Diner with Drive-Thru', cat: 'City', seed: 94,
    desc: 'Retro fast-food diner: red and white walls, wide window strip, striped awning, rooftop sign, drive-thru window under a canopy, menu board and parking.',
    build(b) {
      for (let x = -10; x <= 14; x++) for (let z = -8; z <= 12; z++) b.set(x, 0, z, Math.abs(z - 9) <= 0 && x > -8 ? 'white_concrete' : 'gray_concrete');
      for (let y = 1; y <= 5; y++) for (let x = -7; x <= 7; x++) for (let z = -5; z <= 5; z++) {
        const edge = Math.abs(x) === 7 || Math.abs(z) === 5; if (!edge) { if (y === 1) b.set(x, 0, z, (x + z) % 2 ? 'white_concrete' : 'black_concrete'); continue; }
        const strip = (y === 2 || y === 3) && !((Math.abs(x) === 7) && (Math.abs(z) === 5)) && !(Math.abs(x) === 7 && Math.abs(z) < 2 && x > 0);
        b.set(x, y, z, y === 5 ? 'white_concrete' : y === 1 ? 'red_concrete' : strip ? 'glass' : 'red_concrete');
      }
      for (let x = -8; x <= 8; x++) for (let z = -6; z <= 6; z++) { b.set(x, 6, z, 'white_concrete'); if (Math.abs(x) === 8 || Math.abs(z) === 6) b.set(x, 6, z, 'red_concrete'); }
      // striped awning on the front
      for (let x = -7; x <= 7; x++) b.set(x, 4, 6, x % 2 ? 'red_carpet' : 'white_carpet') && b.trap(x, 4, 6, x % 2 ? 'iron_trapdoor' : 'iron_trapdoor', 'south', 'top');
      for (let x = -7; x <= 7; x++) b.slab(x, 4, 6, 'smooth_quartz_slab', 'top');
      b.door(0, 1, 5, 'iron_door', 'south'); b.door(-1, 1, 5, 'iron_door', 'south', 'right');
      // rooftop sign: a big burger made of blocks
      for (let x = -3; x <= 3; x++) { b.set(x, 7, 0, 'smooth_stone'); b.set(x, 8, 0, 'orange_terracotta'); b.set(x, 9, 0, 'lime_concrete'); b.set(x, 10, 0, 'brown_terracotta'); b.set(x, 11, 0, 'orange_terracotta'); }
      for (const x of [-2, -1, 0, 1, 2]) b.set(x, 12, 0, 'orange_terracotta');
      b.set(-4, 9, 0, 'yellow_concrete'); b.set(4, 9, 0, 'yellow_concrete');
      // drive-thru window, lane canopy and menu board
      b.set(7, 2, 0, 'glass'); b.set(7, 3, 0, 'glass');
      for (let z = -3; z <= 3; z++) { b.slab(8, 5, z, 'red_concrete' === 'x' ? 'x' : 'smooth_quartz_slab', 'top'); b.slab(9, 5, z, 'smooth_quartz_slab', 'top'); b.slab(10, 5, z, 'smooth_quartz_slab', 'top'); }
      for (const z of [-3, 3]) for (let y = 1; y <= 4; y++) b.set(11, y, z, 'iron_bars');
      for (let y = 1; y <= 3; y++) b.set(12, y, -6, y === 3 ? 'black_concrete' : 'iron_bars'); b.set(12, 3, -7, 'black_concrete'); b.set(12, 4, -6, 'sea_lantern');
      for (let z = -7; z <= 11; z++) b.set(9, 0, z, 'black_concrete'); for (let z = -7; z <= 11; z += 2) b.set(9, 1, z, 'yellow_carpet');
      // parking spots + outdoor tables
      for (let x = -9; x <= 7; x += 4) for (let z = 7; z <= 12; z++) if (z === 7) b.set(x, 1, z, 'white_carpet');
      for (const x of [-12, -12]) for (const z of [-2, 2]) { b.set(x, 1, z, 'red_concrete' === 'x' ? 'x' : 'iron_bars'); b.slab(x, 2, z, 'smooth_quartz_slab'); b.stairs(x, 1, z - 1, 'quartz_stairs', 'north'); b.stairs(x, 1, z + 1, 'quartz_stairs', 'south'); }
      for (let x = -13; x <= -11; x++) for (let z = -4; z <= 4; z++) if (!b.has(x, 0, z)) b.set(x, 0, z, 'smooth_stone');
      for (const x of [-9, 13]) streetLampModern(b, x, 1, 11, 'north', 5);
    },
  },
  {
    id: 'parking_lot', name: 'Parking Lot', cat: 'City', seed: 95,
    desc: 'Two-row asphalt car park with painted bays, curbs, tree islands, lamp posts, a cart corral and an entry lane with arrows.',
    build(b) {
      const X = 18, Z = 12;
      for (let x = -X; x <= X; x++) for (let z = -Z; z <= Z; z++) {
        const edge = Math.abs(x) === X || Math.abs(z) === Z;
        b.set(x, 0, z, edge ? 'smooth_stone' : ((x * 7 + z * 13) % 11 === 0 ? 'gray_concrete' : 'black_concrete'));
        if (edge) b.slab(x, 1, z, 'smooth_stone_slab');
      }
      // bay lines
      for (const zr of [[-Z + 1, -Z + 5], [Z - 5, Z - 1]]) for (let x = -X + 2; x <= X - 2; x += 3) for (let z = zr[0]; z <= zr[1]; z++) b.set(x, 1, z, 'white_carpet');
      for (const zr of [[-3, -1], [1, 3]]) for (let x = -X + 2; x <= X - 2; x += 3) for (let z = zr[0]; z <= zr[1]; z++) b.set(x, 1, z, 'white_carpet');
      for (let x = -X + 2; x <= X - 2; x++) b.set(x, 1, 0, 'yellow_carpet');
      // tree islands at the ends of the middle rows
      for (const x of [-X + 1, X - 1]) for (let z = -3; z <= 3; z++) { b.set(x, 0, z, 'grass_block'); b.slab(x, 1, z, 'smooth_stone_slab'); }
      for (const x of [-X + 1, X - 1]) { for (let y = 1; y <= 4; y++) b.log(x, y, 0, 'oak_log'); b.ellipsoid(x, 6, 0, 2, 1.6, 2, 'oak_leaves'); b.del(x, 1, 0); b.log(x, 1, 0, 'oak_log'); }
      for (const x of [-6, 6]) streetLampModern(b, x, 1, 0, x < 0 ? 'east' : 'west', 7);
      for (const x of [-6, 6]) { b.del(x, 1, 0); b.set(x, 1, 0, 'polished_blackstone_wall'); }
      // entry with arrows
      for (let z = -2; z <= 2; z++) { b.del(X, 1, z); b.set(X, 0, z, 'black_concrete'); }
      b.set(X - 1, 1, -1, 'white_carpet'); b.set(X - 2, 1, -1, 'white_carpet'); b.set(X - 2, 1, -2, 'white_carpet');
      // cart corral
      for (let x = 9; x <= 12; x++) { b.set(x, 1, -6, 'iron_bars'); b.set(x, 1, -8, 'iron_bars'); }
    },
  },
  {
    id: 'gas_station', name: 'Gas Station', cat: 'City', seed: 96,
    desc: 'Corner gas station: big lit canopy over two pump islands, a convenience store with glass front and an ice box, price sign and lamps.',
    build(b) {
      for (let x = -14; x <= 14; x++) for (let z = -10; z <= 10; z++) b.set(x, 0, z, (x + z) % 7 ? 'gray_concrete' : 'light_gray_concrete');
      // canopy on four columns
      for (const [x, z] of [[-8, -3], [-8, 3], [4, -3], [4, 3]]) for (let y = 1; y <= 5; y++) b.set(x, y, z, 'white_concrete');
      for (let x = -10; x <= 6; x++) for (let z = -5; z <= 5; z++) { b.set(x, 6, z, Math.abs(z) === 5 || x === -10 || x === 6 ? 'red_concrete' : 'white_concrete'); if ((x + 10) % 4 === 0 && Math.abs(z) % 3 === 0) b.set(x, 5, z, 'end_rod', { facing: 'down' }); }
      for (let x = -10; x <= 6; x++) for (let z = -5; z <= 5; z++) if (Math.abs(z) === 5 || x === -10 || x === 6) b.set(x, 7, z, 'red_concrete');
      // pump islands
      for (const x of [-6, 2]) { for (let z = -2; z <= 2; z++) b.slab(x, 1, z, 'smooth_stone_slab'); for (const z of [-1, 1]) { b.set(x, 2, z, 'light_gray_concrete'); b.set(x, 3, z, 'red_concrete'); b.set(x - 1, 2, z, 'iron_trapdoor', { facing: 'west', half: 'top', open: 'false' }); } }
      // store
      for (let y = 1; y <= 5; y++) for (let x = 7; x <= 13; x++) for (let z = -9; z <= 0; z++) {
        const edge = x === 7 || x === 13 || z === -9 || z === 0; if (!edge) continue;
        b.set(x, y, z, y === 5 ? 'red_concrete' : (z === 0 && y <= 3 && x > 7 && x < 13) ? 'glass' : 'white_concrete');
      }
      for (let x = 6; x <= 14; x++) for (let z = -10; z <= 1; z++) b.set(x, 6, z, 'white_concrete');
      b.door(10, 1, 0, 'iron_door', 'south');
      b.set(8, 1, 1, 'white_concrete'); b.set(8, 2, 1, 'light_blue_stained_glass');
      // price sign
      for (let y = 1; y <= 8; y++) b.set(-13, y, 8, 'gray_concrete');
      for (let y = 6; y <= 9; y++) for (let z = 6; z <= 10; z++) b.set(-13, y, z, y === 9 ? 'red_concrete' : (z % 2 ? 'white_concrete' : 'black_concrete'));
      streetLampModern(b, 13, 1, 9, 'west', 6);
    },
  },
  {
    id: 'road_straight', name: 'Road Segment', cat: 'City', seed: 97,
    desc: 'Straight two-lane road with dashed centre line, curbs, sidewalks, drains, street lights and a bench - tile it end to end.',
    build(b) {
      const L = 15;
      for (let x = 0; x < L; x++) for (let z = -8; z <= 8; z++) {
        const a = Math.abs(z);
        b.set(x, 0, z, a <= 5 ? 'black_concrete' : 'smooth_stone');
        if (a === 6) b.set(x, 1, z, 'smooth_stone_slab', { type: 'bottom' });
        if (a >= 7) b.slab(x, 1, z, (x + z) % 2 ? 'smooth_stone_slab' : 'polished_andesite_slab');
      }
      for (let x = 0; x < L; x++) if (x % 4 < 2) b.set(x, 1, 0, 'yellow_carpet');
      for (const x of [2, 12]) streetLampModern(b, x, 2, -8, 'south', 6);
      streetLampModern(b, 7, 2, 8, 'north', 6);
      b.set(9, 1, 5, 'iron_trapdoor', { facing: 'north', half: 'top', open: 'false' });
      b.stairs(4, 2, 8, 'dark_oak_stairs', 'south'); b.stairs(5, 2, 8, 'dark_oak_stairs', 'south');
      b.set(3, 2, 8, 'polished_blackstone_wall'); b.set(6, 2, 8, 'polished_blackstone_wall');
    },
  },
  {
    id: 'road_intersection', name: 'Road Intersection', cat: 'City', seed: 98,
    desc: 'Four-way crossing with zebra crossings, stop lines, traffic lights on every corner and curb ramps.',
    build(b) {
      const R = 13;
      for (let x = -R; x <= R; x++) for (let z = -R; z <= R; z++) {
        const road = Math.abs(x) <= 5 || Math.abs(z) <= 5;
        b.set(x, 0, z, road ? 'black_concrete' : 'smooth_stone');
        if (!road) b.slab(x, 1, z, (x + z) % 2 ? 'smooth_stone_slab' : 'polished_andesite_slab');
      }
      // zebra crossings
      for (let k = -5; k <= 5; k++) if (k % 2 === 0) for (let d = 6; d <= 8; d++) { b.set(k, 1, d, 'white_carpet'); b.set(k, 1, -d, 'white_carpet'); b.set(d, 1, k, 'white_carpet'); b.set(-d, 1, k, 'white_carpet'); }
      for (let d = 10; d <= R; d++) if (d % 4 < 2) { b.set(0, 1, d, 'yellow_carpet'); b.set(0, 1, -d, 'yellow_carpet'); b.set(d, 1, 0, 'yellow_carpet'); b.set(-d, 1, 0, 'yellow_carpet'); }
      // traffic lights
      for (const [x, z, f] of [[6, 6, 'north'], [-6, -6, 'south'], [6, -6, 'west'], [-6, 6, 'east']]) {
        for (let y = 2; y <= 6; y++) b.set(x, y, z, 'polished_blackstone_wall');
        b.set(x, 7, z, 'black_concrete'); b.set(x, 8, z, 'black_concrete'); b.set(x, 9, z, 'black_concrete');
        const [dx, dz] = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] }[f];
        b.set(x + dx, 9, z + dz, 'red_stained_glass'); b.set(x + dx, 8, z + dz, 'yellow_stained_glass'); b.set(x + dx, 7, z + dz, 'lime_stained_glass');
      }
    },
  },
  {
    id: 'bus_stop', name: 'Bus Stop Shelter', cat: 'City', seed: 99,
    desc: 'Glass bus shelter with a bench, lit advert panel, route sign pole and sidewalk.',
    build(b) {
      for (let x = -5; x <= 5; x++) for (let z = -2; z <= 3; z++) b.slab(x, 0, z, (x + z) % 2 ? 'smooth_stone_slab' : 'polished_andesite_slab', 'top');
      for (let x = -3; x <= 3; x++) for (let y = 1; y <= 3; y++) b.set(x, y, -2, 'glass_pane');
      for (const x of [-3, 3]) for (let y = 1; y <= 3; y++) { b.set(x, y, -1, 'glass_pane'); b.set(x, y, 0, 'glass_pane'); }
      for (let x = -4; x <= 4; x++) for (let z = -2; z <= 1; z++) b.slab(x, 4, z, 'polished_blackstone_slab');
      for (const [x, z] of [[-4, -2], [4, -2], [-4, 1], [4, 1]]) for (let y = 1; y <= 3; y++) b.set(x, y, z, 'iron_bars');
      for (let x = -2; x <= 2; x++) b.stairs(x, 1, -1, 'dark_oak_stairs', 'north');
      for (let y = 1; y <= 3; y++) b.set(-3, y, -2, 'sea_lantern');
      for (let y = 1; y <= 4; y++) b.set(5, y, 2, 'iron_bars'); b.set(5, 5, 2, 'blue_concrete');
    },
  },
];
