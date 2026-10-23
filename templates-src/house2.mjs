// Detailed house kit: framed walls with recessed infill, steep layered roofs, dormers, wings, towers, porches.
// Interiors are left empty (floors only) for the player to decorate.
import { DIRS, OPP, CW, CCW, noise3, hash3, grad } from './lib.mjs';

// Palettes follow the 3-5 block rule: one main block per surface, a few same-colour-family variants used sparingly,
// a slightly darker variant low down (weathering), and a roof that contrasts with the walls.
// wall/found/tower = { main, mix: [[block, share]], low: [[block, share]] }   roof = one wood/stone family + bargeboard trim
// Colour-matched families (blocks of near-identical colour, so mixing them adds texture, not noise):
export const FAM = {
  stone: { main: 'stone', mix: [['andesite', 0.18], ['dead_fire_coral_block', 0.06]], low: [['cobblestone', 0.4], ['tuff', 0.2]] },
  stonebrick: { main: 'stone_bricks', mix: [['cracked_stone_bricks', 0.1], ['andesite', 0.08]], low: [['mossy_stone_bricks', 0.35], ['tuff', 0.15]] },
  cobble: { main: 'cobblestone', mix: [['andesite', 0.15], ['stone', 0.08]], low: [['mossy_cobblestone', 0.45], ['tuff', 0.2]] },
  deepslate: { main: 'cobbled_deepslate', mix: [['deepslate', 0.15], ['deepslate_bricks', 0.1]], low: [['basalt', 0.15], ['deepslate_tiles', 0.2]] },
  white: { main: 'calcite', mix: [['white_concrete_powder', 0.12], ['diorite', 0.05]], low: [['diorite', 0.3]] },
  plaster: { main: 'white_terracotta', mix: [['calcite', 0.1]], low: [['light_gray_terracotta', 0.25], ['diorite', 0.1]] },
  mud: { main: 'packed_mud', mix: [['granite', 0.08], ['dripstone_block', 0.06]], low: [['mud_bricks', 0.3], ['light_gray_terracotta', 0.1]] },
  brick: { main: 'bricks', mix: [['granite', 0.07]], low: [['packed_mud', 0.2], ['rooted_dirt', 0.06]] },
  sand: { main: 'smooth_sandstone', mix: [['sandstone', 0.15], ['end_stone', 0.05]], low: [['sandstone', 0.4], ['birch_planks', 0.04]] },
  redsand: { main: 'smooth_red_sandstone', mix: [['red_sandstone', 0.15], ['orange_terracotta', 0.06]], low: [['red_sandstone', 0.4]] },
  mossy: { main: 'tuff', mix: [['mossy_cobblestone', 0.12], ['dead_horn_coral_block', 0.06]], low: [['mossy_cobblestone', 0.4]] },
};
// Styles: wall + foundation family, frame woods, one roof family (contrasting the walls) with a darker bargeboard trim.
export const STYLE = {
  red: { frame: 'stripped_spruce_log', post: 'spruce_log', wood: 'spruce', floor: 'spruce_planks', wall: FAM.white, found: FAM.stonebrick, tower: FAM.stonebrick,
    roof: 'mangrove', trim: 'dark_oak', ridge: 'stripped_dark_oak_log' },
  oak: { frame: 'stripped_dark_oak_log', post: 'dark_oak_log', wood: 'dark_oak', floor: 'spruce_planks', wall: FAM.plaster, found: FAM.cobble, tower: FAM.cobble,
    roof: 'spruce', trim: 'dark_oak', ridge: 'stripped_dark_oak_log' },
  slate: { frame: 'stripped_spruce_log', post: 'spruce_log', wood: 'spruce', floor: 'spruce_planks', wall: FAM.stonebrick, found: FAM.cobble, tower: FAM.stone,
    roof: 'deepslate_tile', trim: 'spruce', ridge: 'stripped_spruce_log' },
  birch: { frame: 'stripped_birch_log', post: 'birch_log', wood: 'birch', floor: 'birch_planks', wall: FAM.white, found: FAM.stone, tower: FAM.white,
    roof: 'dark_prismarine', trim: 'spruce', ridge: 'stripped_spruce_log' },
  cherry: { frame: 'stripped_cherry_log', post: 'cherry_log', wood: 'cherry', floor: 'cherry_planks', wall: FAM.white, found: FAM.stonebrick, tower: FAM.white,
    roof: 'cherry', trim: 'mangrove', ridge: 'stripped_mangrove_log' },
  desert: { frame: 'stripped_jungle_log', post: 'jungle_log', wood: 'jungle', floor: 'jungle_planks', wall: FAM.sand, found: FAM.redsand, tower: FAM.sand,
    roof: 'acacia', trim: 'jungle', ridge: 'stripped_jungle_log' },
  dark: { frame: 'stripped_dark_oak_log', post: 'dark_oak_log', wood: 'dark_oak', floor: 'dark_oak_planks', wall: FAM.mud, found: FAM.cobble, tower: FAM.brick,
    roof: 'spruce', trim: 'dark_oak', ridge: 'stripped_dark_oak_log' },
  stone: { frame: 'stripped_spruce_log', post: 'spruce_log', wood: 'spruce', floor: 'spruce_planks', wall: FAM.stone, found: FAM.deepslate, tower: FAM.stone,
    roof: 'dark_oak', trim: 'spruce', ridge: 'stripped_spruce_log' },
};
/** stair / slab version of a full block (for chamfers and trims) */
export const STAIR_OF = (blk) => ({ stone: 'stone_stairs', andesite: 'andesite_stairs', cobblestone: 'cobblestone_stairs', mossy_cobblestone: 'mossy_cobblestone_stairs', stone_bricks: 'stone_brick_stairs',
  mossy_stone_bricks: 'mossy_stone_brick_stairs', cracked_stone_bricks: 'stone_brick_stairs', tuff: 'tuff_stairs', cobbled_deepslate: 'cobbled_deepslate_stairs', deepslate_bricks: 'deepslate_brick_stairs',
  deepslate_tiles: 'deepslate_tile_stairs', deepslate: 'cobbled_deepslate_stairs', basalt: 'cobbled_deepslate_stairs', calcite: 'diorite_stairs', diorite: 'diorite_stairs', bricks: 'brick_stairs', granite: 'granite_stairs',
  packed_mud: 'mud_brick_stairs', mud_bricks: 'mud_brick_stairs', smooth_sandstone: 'smooth_sandstone_stairs', sandstone: 'sandstone_stairs', smooth_red_sandstone: 'smooth_red_sandstone_stairs', red_sandstone: 'red_sandstone_stairs',
  white_terracotta: 'diorite_stairs', dead_fire_coral_block: 'andesite_stairs', dead_horn_coral_block: 'tuff_stairs', rooted_dirt: 'mud_brick_stairs', end_stone: 'end_stone_brick_stairs', polished_andesite: 'polished_andesite_stairs' }[blk] || 'stone_brick_stairs');
/** pick from a palette spec: clustered noise so variants form small patches, not salt-and-pepper */
export function tex(spec, x, y, z, seed = 1, low = 0) {
  const n = noise3(x, y, z, 1.6, seed), m = hash3(x, y, z, seed + 9);
  if (low > 0) { let acc = 0; for (const [blk, sh] of spec.low || []) { acc += sh * low; if (m < acc && n > 0.35) return blk; } }
  let acc = 0;
  for (const [blk, sh] of spec.mix || []) { acc += sh; if (n > 1 - acc * 1.6 && m < 0.7) return blk; }
  return spec.main;
}
// stairs/slab/full names for a roof material word
const STAIR = (m) => (m === 'nether_brick' || m === 'red_nether_brick' || m === 'mud_brick' || m === 'deepslate_brick' || m === 'deepslate_tile' || m === 'prismarine_brick' || m === 'stone_brick' || m === 'polished_deepslate' || m === 'cobbled_deepslate' || m === 'dark_prismarine' || m === 'granite' || m === 'brick' ? `${m}_stairs` : `${m}_stairs`);
const SLAB = (m) => `${m}_slab`;
const FULL = (m) => ({ purpur: 'purpur_block', nether_brick: 'nether_bricks', red_nether_brick: 'red_nether_bricks', mud_brick: 'mud_bricks', deepslate_brick: 'deepslate_bricks', deepslate_tile: 'deepslate_tiles', prismarine_brick: 'prismarine_bricks', stone_brick: 'stone_bricks', brick: 'bricks', granite: 'granite', polished_deepslate: 'polished_deepslate', cobbled_deepslate: 'cobbled_deepslate', dark_prismarine: 'dark_prismarine' }[m] || `${m}_planks`);

/** roof material: one family; bargeboard ends use the trim wood */
function roofMat(S) { return S.roof; }

/**
 * One or more gable roofs merged as a height field, so wings meet the main roof in clean valleys.
 * Each roof: { x0, z0, x1, z1 (wall footprint), y (first course), axis ('x' ridge along x), pitch=1|2, ov=1, endOv=1, gable=true }
 */
export function roofs(b, S, list, seed = 1) {
  const R = list.map((r) => {
    const ov = r.ov ?? 1, endOv = r.endOv ?? 1, pitch = r.pitch || 1;
    const X0 = r.x0 - (r.axis === 'x' ? endOv : ov), X1 = r.x1 + (r.axis === 'x' ? endOv : ov);
    const Z0 = r.z0 - (r.axis === 'z' ? endOv : ov), Z1 = r.z1 + (r.axis === 'z' ? endOv : ov);
    const lo = r.axis === 'x' ? Z0 : X0, hi = r.axis === 'x' ? Z1 : X1;
    return { ...r, ov, endOv, pitch, X0, X1, Z0, Z1, lo, hi, rows: Math.ceil((hi - lo + 1) / 2) };
  });
  const sample = (r, x, z) => {
    if (x < r.X0 || x > r.X1 || z < r.Z0 || z > r.Z1) return null;
    const v = r.axis === 'x' ? z : x;
    const i = Math.min(v - r.lo, r.hi - v);
    const ridge = v - r.lo === r.hi - v;
    const facing = r.axis === 'x' ? (v - r.lo < r.hi - v ? 'south' : 'north') : (v - r.lo < r.hi - v ? 'east' : 'west');
    const u = r.axis === 'x' ? x : z, endRow = u === (r.axis === 'x' ? r.X0 : r.Z0) || u === (r.axis === 'x' ? r.X1 : r.Z1);
    const rise = Math.max(1, Math.floor((i + 1) * r.pitch) - Math.floor(i * r.pitch));
    return { h: r.y + Math.floor(i * r.pitch), rise, i, ridge, facing, endRow, r };
  };
  const inWalls = (x, z) => R.some((r) => x >= r.x0 && x <= r.x1 && z >= r.z0 && z <= r.z1);
  const xs = [Math.min(...R.map((r) => r.X0)), Math.max(...R.map((r) => r.X1))], zs = [Math.min(...R.map((r) => r.Z0)), Math.max(...R.map((r) => r.Z1))];
  const H = new Map();
  for (let x = xs[0]; x <= xs[1]; x++) for (let z = zs[0]; z <= zs[1]; z++) {
    let best = null;
    for (const r of R) { const s = sample(r, x, z); if (s && (!best || s.h > best.h)) best = s; }
    if (!best) continue;
    H.set(x + ',' + z, best.h);
    const { h, ridge, facing, endRow, r, i, rise } = best;
    const t = i / Math.max(1, r.rows - 1);
    const m = endRow ? S.trim : S.roof; void t;
    if (ridge) { for (let k = 0; k < rise; k++) b.set(x, h + k, z, FULL(m)); b.log(x, h + rise, z, S.ridge, r.axis); }
    else {
      for (let k = 0; k < rise - 1; k++) b.set(x, h + k, z, FULL(m));
      b.stairs(x, h + rise - 1, z, STAIR(m), facing);
    }
    if (!inWalls(x, z)) { // visible underside of the overhang
      if (i === 0) b.slab(x, h - 1, z, SLAB(S.trim), 'top');
      else b.stairs(x, h - 1, z, STAIR(endRow ? S.trim : S.roof), OPP[facing], 'top');
    }
  }
  // gable walls under each roof's ends
  for (const r of R) {
    if (r.gable === false) continue;
    const ends = r.axis === 'x' ? [r.x0, r.x1] : [r.z0, r.z1];
    const vlo = r.axis === 'x' ? r.z0 : r.x0, vhi = r.axis === 'x' ? r.z1 : r.x1, mid = (vlo + vhi) / 2;
    for (const u of ends) for (let v = vlo; v <= vhi; v++) {
      const x = r.axis === 'x' ? u : v, z = r.axis === 'x' ? v : u;
      if (r.skipEnd && r.skipEnd === (u === ends[0] ? 0 : 1)) continue;
      const own = sample(r, x, z); if (!own) continue;
      const top = own.h + own.rise - 2;
      for (let yy = r.y; yy <= top; yy++) {
        if (b.has(x, yy, z)) continue;
        const off = v - mid;
        if (r.gableWindow !== false && Math.abs(off) <= 0.5 && yy >= r.y + 1 && yy <= r.y + 2 + (r.pitch > 1.4 ? 1 : 0)) { b.set(x, yy, z, 'glass_pane'); continue; }
        if (yy === r.y) { b.log(x, yy, z, S.frame, r.axis === 'x' ? 'z' : 'x'); continue; }
        if (Math.abs(Math.round(off)) % 3 === 2) { b.log(x, yy, z, S.frame, 'y'); continue; }
        b.set(x, yy, z, tex(S.wall, x, yy, z, seed));
      }
    }
  }
  return (x, z) => H.get(x + ',' + z);
}
const gableInfill = (S, x, y, z, seed) => `${S.wood}_planks`;

/**
 * Framed walls for one storey. Outer plane = frame (posts + beams), infill set one block inward.
 * rect is the outer plane. windows: list of {side, at, w, h} or 'auto'.
 */
export function storey(b, S, x0, z0, x1, z1, y, h, opts = {}) {
  const { seed = 1, bay = 4, windows = 'auto', doors = [], sillBeam = false, skipSides = [], every = 2, winOff = 0 } = opts;
  const inner = [x0 + 1, z0 + 1, x1 - 1, z1 - 1];
  for (let yy = y; yy < y + h; yy++) {
    // infill ring (inner plane)
    for (let x = inner[0]; x <= inner[2]; x++) for (let z = inner[1]; z <= inner[3]; z++) {
      const edge = x === inner[0] || x === inner[2] || z === inner[1] || z === inner[3];
      if (!edge) { if (yy !== y || !opts.floor) b.del(x, yy, z); continue; }
      b.set(x, yy, z, tex(S.wall, x, yy, z, seed, yy - y === 0 ? 1 : yy - y === 1 ? 0.4 : 0));
    }
    // outer plane: posts at corners + every `bay`, beams top and bottom
    const posts = [];
    for (let x = x0; x <= x1; x++) for (const z of [z0, z1]) posts.push([x, z, (x - x0) % bay === 0 || x === x1, 'x']);
    for (let z = z0 + 1; z < z1; z++) for (const x of [x0, x1]) posts.push([x, z, (z - z0) % bay === 0 || z === z1, 'z']);
    for (const [x, z, isPost, ax] of posts) {
      const side = z === z0 ? 'north' : z === z1 ? 'south' : x === x0 ? 'west' : 'east';
      if (skipSides.includes(side)) continue;
      const corner = (x === x0 || x === x1) && (z === z0 || z === z1);
      if (yy === y + h - 1) b.log(x, yy, z, corner ? S.post : S.frame, corner ? 'y' : ax);
      else if (yy === y && sillBeam) b.log(x, yy, z, corner ? S.post : S.frame, corner ? 'y' : ax);
      else if (isPost) b.log(x, yy, z, corner ? S.post : S.frame, 'y');
      else b.del(x, yy, z);
    }
  }
  // floor of this storey (inside inner ring)
  for (let x = inner[0] + 1; x < inner[2]; x++) for (let z = inner[1] + 1; z < inner[3]; z++) b.set(x, y - 1, z, S.floor);
  // windows centred in bays, in the infill plane, with sills on the frame plane
  if (windows === 'auto') {
    const winH = h >= 5 ? 3 : 2, wy = y + 1;
    for (const side of ['north', 'south', 'west', 'east']) {
      if (skipSides.includes(side)) continue;
      const len = side === 'north' || side === 'south' ? x1 - x0 : z1 - z0;
      for (let p = 0, k2 = 0; p + bay <= len; p += bay, k2++) {
        if ((k2 + winOff) % every !== 0 && len > bay * 2) continue; // leave plaster bays between windows
        const c = p + bay / 2; // centre of the bay (between posts)
        if (doors.some((d) => d.side === side && Math.abs(d.at - c) < bay / 2)) continue;
        const wW = bay >= 4 ? 2 : 1, s0 = Math.round(c - wW / 2 + 0.01);
        for (let k = 0; k < wW; k++) for (let j = 0; j < winH; j++) {
          const [ix, iz, ox, oz] = side === 'north' ? [x0 + s0 + k, z0 + 1, x0 + s0 + k, z0] : side === 'south' ? [x0 + s0 + k, z1 - 1, x0 + s0 + k, z1]
            : side === 'west' ? [x0 + 1, z0 + s0 + k, x0, z0 + s0 + k] : [x1 - 1, z0 + s0 + k, x1, z0 + s0 + k];
          b.set(ix, wy + j, iz, 'glass_pane');
          if (j === 0) b.trap(ox, wy - 1, oz, `${S.wood}_trapdoor`, side, 'top');
          if (j === 0 && opts.boxes && hash3(ox, wy, oz, seed) < 0.6) { b.trap(ox, wy - 1, oz, `${S.wood}_trapdoor`, side, 'top'); b.set(ox, wy, oz, hash3(ox, 1, oz, seed) < 0.5 ? 'azalea' : 'flowering_azalea'); }
        }
      }
    }
  }
  for (const d of doors) {
    const [ix, iz, ox, oz] = d.side === 'south' ? [x0 + d.at, z1 - 1, x0 + d.at, z1] : d.side === 'north' ? [x0 + d.at, z0 + 1, x0 + d.at, z0]
      : d.side === 'west' ? [x0 + 1, z0 + d.at, x0, z0 + d.at] : [x1 - 1, z0 + d.at, x1, z0 + d.at];
    b.door(ix, y, iz, `${S.wood}_door`, d.side, 'left');
    b.del(ox, y, oz); b.del(ox, y + 1, oz);
    b.log(ix, y + 2, iz, S.frame, d.side === 'north' || d.side === 'south' ? 'x' : 'z');
  }
}

/** Stone plinth under a footprint, stepping out one block with stairs as a chamfer. */
export function plinth(b, S, x0, z0, x1, z1, y, h = 1, seed = 1) {
  for (let x = x0 - 1; x <= x1 + 1; x++) for (let z = z0 - 1; z <= z1 + 1; z++) {
    const out = x < x0 || x > x1 || z < z0 || z > z1;
    for (let yy = y; yy < y + h; yy++) {
      const m = tex(S.found, x, yy, z, seed, yy === y ? 1 : 0.5);
      if (out && yy === y + h - 1) {
        const f = x < x0 ? 'east' : x > x1 ? 'west' : z < z0 ? 'south' : 'north';
        const corner = (x < x0 || x > x1) && (z < z0 || z > z1);
        if (corner) { b.set(x, yy, z, m); continue; }
        b.stairs(x, yy, z, STAIR_OF(m), f);
      } else b.set(x, yy, z, m);
    }
  }
}

/** Shed dormer on a roof side: a small framed box with its own mini gable. side = facing outward. */
export function dormer(b, S, cx, y, cz, side, seed = 1) {
  const along = side === 'north' || side === 'south' ? 'x' : 'z';
  const [ox, oz] = DIRS[side];
  // front face 3 wide, 2 tall window between posts
  for (let k = -1; k <= 1; k++) for (let j = 0; j < 3; j++) {
    const x = along === 'x' ? cx + k : cx, z = along === 'z' ? cz + k : cz;
    if (k === 0 && j < 2) b.set(x, y + j, z, 'glass_pane');
    else b.log(x, y + j, z, S.frame, k === 0 || j === 2 ? (along === 'x' ? 'x' : 'z') : 'y');
    // cheeks back into the roof
    for (let d = 1; d <= 2; d++) if (k !== 0) b.set(x - ox * d, y + j, z - oz * d, `${S.wood}_planks`);
    if (k === 0) for (let d = 1; d <= 2; d++) { b.del(x - ox * d, y + j, z - oz * d); }
  }
  b.trap(cx + ox, y - 1, cz + oz, `${S.wood}_trapdoor`, side, 'top');
  // mini gable roof on the dormer
  for (let d = -1; d <= 3; d++) {
    const bx = cx - ox * d + 0, bz = cz - oz * d;
    for (const [k, f] of [[-2, along === 'x' ? 'east' : 'south'], [2, along === 'x' ? 'west' : 'north']]) {
      const x = along === 'x' ? bx + k : bx, z = along === 'z' ? bz + k : bz;
      b.stairs(x, y + 2, z, STAIR(S.roof), f);
    }
    for (const [k, f] of [[-1, along === 'x' ? 'east' : 'south'], [1, along === 'x' ? 'west' : 'north']]) {
      const x = along === 'x' ? bx + k : bx, z = along === 'z' ? bz + k : bz;
      b.stairs(x, y + 3, z, STAIR(S.roof), f);
    }
    b.slab(bx, y + 4, bz, SLAB(S.roof)); b.set(bx, y + 3, bz, FULL(S.roof));
  }
}

/** Stone chimney with stepped base and stair cap. */
export function chimney(b, S, x, z, y0, y1, seed = 1) {
  for (let y = y0; y <= y1; y++) for (const [dx, dz] of [[0, 0], [1, 0], [0, 1], [1, 1]]) {
    b.set(x + dx, y, z + dz, tex(S.found, x + dx, y, z + dz, seed + 2, y < y0 + 3 ? 1 : 0));
  }
  for (const [dx, dz, f] of [[-1, 0, 'east'], [-1, 1, 'east'], [2, 0, 'west'], [2, 1, 'west'], [0, -1, 'south'], [1, -1, 'south'], [0, 2, 'north'], [1, 2, 'north']]) b.stairs(x + dx, y1, z + dz, 'stone_brick_stairs', f, 'top');
  for (const [dx, dz] of [[0, 0], [1, 0], [0, 1], [1, 1]]) b.set(x + dx, y1 + 1, z + dz, 'cobblestone_wall');
  b.set(x, y1 + 1, z, 'campfire', { lit: 'true', facing: 'north' });
}

/** Round stone tower with a conical shingle spire. */
export function tower(b, S, cx, cz, y0, h, r = 3, seed = 1) {
  for (let y = y0; y < y0 + h; y++) {
    for (let x = -r; x <= r; x++) for (let z = -r; z <= r; z++) {
      const d = Math.hypot(x, z);
      if (d > r + 0.4) continue;
      if (d > r - 0.6) {
        b.set(cx + x, y, cz + z, tex(S.tower, cx + x, y, cz + z, seed, y - y0 < 3 ? 1 - (y - y0) / 3 : 0));
      } else b.del(cx + x, y, cz + z);
    }
    if ((y - y0) % 4 === 0) b.disc(cx, y - 1, cz, r - 1, S.floor);
  }
  // windows
  for (let y = y0 + 2; y < y0 + h - 1; y += 4) for (const [dx, dz, f] of [[r, 0, 'east'], [-r, 0, 'west'], [0, r, 'south'], [0, -r, 'north']]) {
    b.set(cx + dx, y, cz + dz, 'glass_pane'); b.set(cx + dx, y + 1, cz + dz, 'glass_pane');
    const [ox, oz] = DIRS[f]; b.trap(cx + dx + ox, y - 1, cz + dz + oz, `${S.wood}_trapdoor`, f, 'top');
  }
  // corbel ring + conical spire made of stair rings (radius shrinks half a block per layer)
  const yt = y0 + h;
  b.disc(cx, yt, cz, r + 0.6, FULL(S.trim), {}, true);
  let y = yt + 1;
  for (let rr = r + 1.4; rr > 0.4; rr -= 0.5, y++) {
    for (let x = -Math.ceil(rr); x <= Math.ceil(rr); x++) for (let z = -Math.ceil(rr); z <= Math.ceil(rr); z++) {
      const d = Math.hypot(x, z);
      if (d > rr + 0.3) continue;
      const m = S.roof;
      if (d > rr - 0.75) {
        const f = Math.abs(x) >= Math.abs(z) ? (x > 0 ? 'west' : 'east') : (z > 0 ? 'north' : 'south');
        b.stairs(cx + x, y, cz + z, STAIR(m), f);
      } else b.set(cx + x, y, cz + z, FULL(S.roof));
    }
  }
  b.set(cx, y, cz, FULL(S.roof)); b.set(cx, y + 1, cz, 'lightning_rod', { facing: 'up' });
}

/** Covered porch: posts with fence brackets and a slab roof. */
export function porch(b, S, x0, z0, x1, z1, y, side) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) b.set(x, y - 1, z, `${S.wood}_planks`);
  const posts = [[x0, z0], [x1, z0], [x0, z1], [x1, z1]];
  for (const [x, z] of posts) { for (let k = 0; k < 3; k++) b.log(x, y + k, z, S.post); }
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) b.slab(x, y + 3, z, SLAB(S.roof));
  for (let x = x0 + 1; x < x1; x++) for (const z of [z0, z1]) b.stairs(x, y + 2, z, `${S.wood}_stairs`, z === z0 ? 'south' : 'north', 'top');
  for (let z = z0 + 1; z < z1; z++) for (const x of [x0, x1]) b.stairs(x, y + 2, z, `${S.wood}_stairs`, x === x0 ? 'east' : 'west', 'top');
  b.lantern(Math.floor((x0 + x1) / 2), y + 2, Math.floor((z0 + z1) / 2), true);
  void side;
}
export { STAIR, SLAB, FULL };

/** Rubble detail on a stone surface: small pebbles (buttons) stuck to exposed faces. */
export function rubble(b, test, x0, y0, z0, x1, y1, z1, share = 0.05, seed = 1, button = 'stone_button') {
  for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) for (let z = z0; z <= z1; z++) {
    const n = b.get(x, y, z); if (!n || !test(n)) continue;
    for (const [f, dx, dz] of [['north', 0, -1], ['south', 0, 1], ['east', 1, 0], ['west', -1, 0]]) {
      if (b.has(x + dx, y, z + dz) || hash3(x * 3 + dx, y, z * 3 + dz, seed) > share) continue;
      b.set(x + dx, y, z + dz, button, { face: 'wall', facing: f });
    }
  }
}
