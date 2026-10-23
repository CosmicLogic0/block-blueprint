// Reusable detailed pieces built with modern techniques: depth, texture mixing, gradients, layered roofs.
import { DIRS, OPP, CW, CCW, noise3, hash3, mixer, grad } from './lib.mjs';
export const roundLot = (w, d, seed = 1) => (x, z) => (x / (w / 2)) ** 2 + (z / (d / 2)) ** 2 <= 1.12 - hash3(x, 0, z, seed) * 0.12 || (Math.abs(x) <= w / 2 - 1 && Math.abs(z) <= d / 2 - 1 && (x / (w / 2)) ** 4 + (z / (d / 2)) ** 4 <= 0.9);

// ------------------------------------------------------------------ terrain + plants
const FLOWERS = ['poppy', 'dandelion', 'oxeye_daisy', 'cornflower', 'azure_bluet', 'allium', 'red_tulip', 'white_tulip', 'pink_tulip', 'orange_tulip', 'lily_of_the_valley'];
/** Grass island with soft edges, noisy dirt/moss/coarse patches and plant tufts. shape(x,z) -> bool */
export function ground(b, x0, z0, x1, z1, y = 0, opts = {}) {
  const { depth = 2, plants = 0.18, flowers = 0.05, shape = null, seed = 3, rim = true } = opts;
  const r = b.r;
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (shape && !shape(x, z)) continue;
    const n = noise3(x, 0, z, 4, seed);
    const top = n < 0.12 ? 'coarse_dirt' : n > 0.9 ? 'moss_block' : 'grass_block';
    b.set(x, y, z, top);
    const edge = x === x0 || x === x1 || z === z0 || z === z1;
    for (let d = 1; d <= depth; d++) {
      if (rim && edge && d === depth && hash3(x, d, z, seed) < 0.5) continue;
      b.set(x, y - d, z, d === depth && hash3(x, y, z, seed + 1) < 0.35 ? 'stone' : hash3(x, d, z, seed + 2) < 0.15 ? 'rooted_dirt' : 'dirt');
    }
    if (!b.has(x, y + 1, z) && opts.decorate !== false) {
      const t = r();
      if (t < flowers) b.set(x, y + 1, z, r.pick(FLOWERS));
      else if (t < flowers + plants) b.set(x, y + 1, z, r() < 0.75 ? 'short_grass' : 'fern');
      else if (t < flowers + plants + 0.02) b.tall(x, y + 1, z, 'tall_grass');
    }
  }
}
/** remove plants sitting on top of a cell (when something gets built on it) */
export function clearPlant(b, x, y, z) { const n = b.get(x, y, z); if (n && /grass$|fern|tulip|poppy|dandelion|daisy|cornflower|bluet|allium|lily_of|tall_grass/.test(n)) { b.del(x, y, z); if (b.get(x, y + 1, z) === n) b.del(x, y + 1, z); } }

/** organic leaf blob */
function blob(b, cx, cy, cz, rx, ry, rz, leaf, seed) {
  b.ellipsoid(cx, cy, cz, rx, ry, rz, leaf, {}, (x, y, z, d) => d < 0.62 || hash3(x, y, z, seed) > (d - 0.62) * 2.2);
}
export function oakTree(b, x, y, z, h = 7, opts = {}) {
  const { log = 'oak_log', leaf = 'oak_leaves', seed = 11, wide = 1 } = opts;
  const r = b.r;
  for (let i = 0; i < h; i++) b.log(x, y + i, z, log);
  // roots
  for (const [dx, dz] of Object.values(DIRS)) if (r() < 0.6) b.log(x + dx, y, z + dz, log, dx ? 'x' : 'z');
  const top = y + h;
  // branches
  const branches = 3 + r.int(0, 2);
  for (let i = 0; i < branches; i++) {
    const a = (i / branches) * Math.PI * 2 + r() * 0.8, len = 2 + r.int(0, 2) + (wide > 1 ? 1 : 0);
    const by = y + Math.floor(h * 0.55) + r.int(0, 2);
    let bx = x, bz = z;
    for (let k = 1; k <= len; k++) {
      const nx = x + Math.round(Math.cos(a) * k), nz = z + Math.round(Math.sin(a) * k);
      b.log(nx, by + Math.floor(k / 2), nz, log, Math.abs(Math.cos(a)) > 0.7 ? 'x' : Math.abs(Math.sin(a)) > 0.7 ? 'z' : 'y');
      bx = nx; bz = nz;
    }
    blob(b, bx, by + Math.floor(len / 2) + 1, bz, 2.6 * wide, 1.8, 2.6 * wide, leaf, seed + i);
  }
  blob(b, x, top, z, 3.2 * wide, 2.4, 3.2 * wide, leaf, seed + 9);
  // keep trunk visible inside the canopy
  for (let i = 0; i < h; i++) b.log(x, y + i, z, log);
}
export function birchTree(b, x, y, z, h = 8, opts = {}) {
  const { seed = 21 } = opts;
  for (let i = 0; i < h; i++) b.log(x, y + i, z, 'birch_log');
  blob(b, x, y + h - 1, z, 2.2, 3, 2.2, 'birch_leaves', seed);
  for (let i = h - 4; i < h + 1; i++) b.log(x, y + i, z, 'birch_log');
}
export function spruceTree(b, x, y, z, h = 12, opts = {}) {
  const { seed = 31 } = opts;
  for (let i = 0; i < h; i++) b.log(x, y + i, z, 'spruce_log');
  for (let i = 3; i <= h + 1; i++) {
    const t = (h + 1 - i) / (h - 2);
    const rad = (i % 2 ? 1.2 : 0.4) + t * 3.2;
    b.disc(x, y + i, z, rad, 'spruce_leaves');
    b.log(x, y + i, z, 'spruce_log');
  }
  b.set(x, y + h + 1, z, 'spruce_leaves'); b.set(x, y + h + 2, z, 'spruce_leaves');
  for (let d = 0; d < 0; d++) hash3(d, 0, 0, seed);
}
export function cherryTree(b, x, y, z, h = 6, opts = {}) {
  const { seed = 41 } = opts;
  const r = b.r;
  for (let i = 0; i < h; i++) b.log(x, y + i, z, 'cherry_log');
  const arms = [[1, 0], [-1, 0], [0, 1], [0, -1]].filter(() => r() < 0.8);
  for (const [dx, dz] of arms) {
    let px = x, py = y + h - 2, pz = z;
    for (let k = 0; k < 3; k++) { px += dx; pz += dz; if (k) py++; b.log(px, py, pz, 'cherry_log', dx ? 'x' : 'z'); }
    blob(b, px, py + 2, pz, 3, 1.8, 3, 'cherry_leaves', seed + px * 7 + pz);
    // hanging petals
    for (let i = 0; i < 6; i++) { const hx = px + r.int(-2, 2), hz = pz + r.int(-2, 2); if (b.has(hx, py + 1, hz) && !b.has(hx, py, hz)) b.set(hx, py, hz, 'cherry_leaves'); }
  }
  blob(b, x, y + h + 1, z, 2.6, 2, 2.6, 'cherry_leaves', seed);
}
export function darkOakTree(b, x, y, z, h = 6, opts = {}) {
  const { seed = 51 } = opts;
  for (let i = 0; i < h; i++) for (const [dx, dz] of [[0, 0], [1, 0], [0, 1], [1, 1]]) b.log(x + dx, y + i, z + dz, 'dark_oak_log');
  blob(b, x + 0.5, y + h, z + 0.5, 4.5, 2.2, 4.5, 'dark_oak_leaves', seed);
  blob(b, x + 0.5, y + h + 2, z + 0.5, 2.8, 1.6, 2.8, 'dark_oak_leaves', seed + 1);
}
export function bush(b, x, y, z, size = 1, kind = 'mix') {
  const r = b.r;
  const leaves = kind === 'azalea' ? ['azalea_leaves', 'flowering_azalea_leaves'] : kind === 'oak' ? ['oak_leaves'] : kind === 'spruce' ? ['spruce_leaves'] : ['oak_leaves', 'azalea_leaves', 'oak_leaves', 'flowering_azalea_leaves'];
  const rx = 0.8 + size * 0.7, ry = 0.6 + size * 0.5;
  b.ellipsoid(x, y + ry - 0.4, z, rx, ry, rx, () => r.pick(leaves), {}, (bx, by, bz, d) => by >= y && (d < 0.55 || hash3(bx, by, bz, 5) > d - 0.4));
}
export function flowerBed(b, x0, z0, x1, z1, y, border = 'spruce_trapdoor') {
  const r = b.r;
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1;
    b.set(x, y, z, edge ? 'coarse_dirt' : 'rooted_dirt');
    if (edge) {
      const f = x === x0 ? 'east' : x === x1 ? 'west' : z === z0 ? 'south' : 'north';
      b.trap(x, y + 1, z, border, f, 'bottom', true);
    } else b.set(x, y + 1, z, r() < 0.2 ? 'short_grass' : r.pick(FLOWERS));
  }
}

// ------------------------------------------------------------------ paths
/** Gradient path along a polyline: centre worn (dirt path / gravel), edges mossy cobble / grass blend. */
export function path(b, pts, y, width = 3, style = 'stone') {
  const pal = style === 'stone' ? ['dirt_path', 'gravel', 'cobblestone', 'andesite', 'stone', 'mossy_cobblestone']
    : style === 'brick' ? ['bricks', 'mud_bricks', 'granite', 'polished_granite', 'packed_mud', 'terracotta']
    : style === 'modern' ? ['smooth_stone', 'polished_andesite', 'light_gray_concrete', 'polished_diorite', 'stone', 'andesite']
    : ['dirt_path', 'coarse_dirt', 'gravel', 'dirt_path', 'packed_mud', 'rooted_dirt'];
  const cells = new Map();
  for (let i = 0; i < pts.length - 1; i++) {
    const [ax, az] = pts[i], [bx, bz] = pts[i + 1];
    const n = Math.max(Math.abs(bx - ax), Math.abs(bz - az)) * 2 || 1;
    for (let k = 0; k <= n; k++) {
      const px = ax + (bx - ax) * k / n, pz = az + (bz - az) * k / n;
      for (let dx = -width; dx <= width; dx++) for (let dz = -width; dz <= width; dz++) {
        const d = Math.hypot(dx, dz) / (width / 2 + 0.5);
        const cx = Math.round(px + dx), cz = Math.round(pz + dz), kk = cx + ',' + cz;
        if (d <= 1.05 && (!cells.has(kk) || cells.get(kk) > d)) cells.set(kk, d);
      }
    }
  }
  for (const [kk, d] of cells) {
    const [x, z] = kk.split(',').map(Number);
    if (d > 0.85 && hash3(x, y, z, 9) < 0.45) continue; // ragged edge
    const t = Math.min(0.999, d * 0.75 + noise3(x, y, z, 2, 4) * 0.35);
    clearPlant(b, x, y + 1, z);
    b.set(x, y, z, style === 'modern' ? (d < 0.6 ? 'polished_andesite' : 'smooth_stone') : grad(pal, t));
  }
}
export function steppingStones(b, pts, y, block = 'stone_brick_slab') {
  for (let i = 0; i < pts.length - 1; i++) {
    const [ax, az] = pts[i], [bx, bz] = pts[i + 1];
    const n = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
    for (let k = 0; k <= n; k += 2) { const x = Math.round(ax + (bx - ax) * k / n), z = Math.round(az + (bz - az) * k / n); clearPlant(b, x, y + 1, z); b.slab(x, y + 1, z, block); }
  }
}

// ------------------------------------------------------------------ lights
export function streetLampModern(b, x, y, z, facing = 'south', h = 5) {
  b.set(x, y, z, 'polished_blackstone_wall');
  for (let i = 1; i < h; i++) b.set(x, y + i, z, 'iron_bars');
  b.set(x, y + h, z, 'polished_blackstone_slab', { type: 'bottom' });
  const [dx, dz] = DIRS[facing];
  b.trap(x + dx, y + h, z + dz, 'iron_trapdoor', facing, 'top');
  b.set(x + dx, y + h - 1, z + dz, 'end_rod', { facing: 'down' });
}
export function streetLampClassic(b, x, y, z, h = 4) {
  b.set(x, y, z, 'stone_brick_wall'); b.set(x, y + 1, z, 'stone_brick_wall');
  for (let i = 2; i < h; i++) b.set(x, y + i, z, 'dark_oak_fence');
  b.set(x, y + h, z, 'lantern');
  b.set(x, y + h + 1, z, 'dark_oak_trapdoor', { facing: 'north', half: 'bottom', open: 'false' });
}
export function hangingLantern(b, x, y, z, len = 1) { for (let i = 0; i < len; i++) b.chain(x, y - i, z); b.lantern(x, y - len, z, true); }

// ------------------------------------------------------------------ roofs
/**
 * Gable roof over x0..x1, z0..z1 at height y (first roof layer). axis = ridge direction ('x' or 'z').
 * mat = { stairs, slab, full, trim (optional stairs for edge), gable (wall block for the ends) }
 * Layered: an outer trim row of a second stair type sits one step lower for depth.
 */
export function gableRoof(b, x0, z0, x1, z1, y, axis, mat, opts = {}) {
  const { overhang = 1, fillGable = true, inner = null } = opts;
  const X0 = x0 - (axis === 'x' ? overhang : overhang), X1 = x1 + overhang, Z0 = z0 - overhang, Z1 = z1 + overhang;
  const span = axis === 'x' ? Z1 - Z0 + 1 : X1 - X0 + 1;
  const half = Math.floor(span / 2);
  const odd = span % 2 === 1;
  // gable wall: framing posts, a beam row and a small window in the middle
  const gableAt = (gx, gy, gz, mid, i) => {
    const off = axis === 'x' ? gz - mid : gx - mid;
    if (mat.gableWin !== false && i >= 1 && i <= 2 && Math.abs(off) <= 0.5) return 'glass_pane';
    if (mat.gablePost && Math.abs(Math.round(off * 2)) % 6 === 3) return [mat.gablePost, { axis: 'y' }];
    if (mat.gableBeam && i === 0) return [mat.gableBeam, { axis: axis === 'x' ? 'z' : 'x' }];
    return mat.gable;
  };
  const course = (i) => (mat.trim && i === 0 ? mat.trim : mat.stairs);
  for (let i = 0; i < half + (odd ? 1 : 0); i++) {
    const yy = y + i;
    const ridge = odd && i === half;
    const st = course(i);
    const mixAt = mat.mix ? (xx, zz) => (hash3(xx, yy, zz, 77) < (i === 0 ? 0.5 : i === 1 ? 0.2 : 0) ? mat.mix : st) : () => st;
    if (axis === 'x') {
      for (let x = X0; x <= X1; x++) {
        if (ridge) { b.set(x, yy, Z0 + i, mat.slab, { type: 'bottom' }); continue; }
        b.stairs(x, yy, Z0 + i, mixAt(x, Z0 + i), 'south'); b.stairs(x, yy, Z1 - i, mixAt(x, Z1 - i), 'north');
        if (i > 0) { b.stairs(x, yy - 1, Z0 + i, mat.under || st, 'north', 'top'); b.stairs(x, yy - 1, Z1 - i, mat.under || st, 'south', 'top'); }
      }
      if (fillGable) for (let z = Z0 + i + 1; z <= Z1 - i - 1; z++) for (const x of [x0, x1]) if (z >= z0 && z <= z1) b.set(x, yy, z, gableAt(x, yy, z, (z0 + z1) / 2, i));
    } else {
      for (let z = Z0; z <= Z1; z++) {
        if (ridge) { b.set(X0 + i, yy, z, mat.slab, { type: 'bottom' }); continue; }
        b.stairs(X0 + i, yy, z, mixAt(X0 + i, z), 'east'); b.stairs(X1 - i, yy, z, mixAt(X1 - i, z), 'west');
        if (i > 0) { b.stairs(X0 + i, yy - 1, z, mat.under || st, 'west', 'top'); b.stairs(X1 - i, yy - 1, z, mat.under || st, 'east', 'top'); }
      }
      if (fillGable) for (let x = X0 + i + 1; x <= X1 - i - 1; x++) for (const z of [z0, z1]) if (x >= x0 && x <= x1) b.set(x, yy, z, gableAt(x, yy, z, (x0 + x1) / 2, i));
    }
  }
  // gable-end trim: upside-down stairs under the roof edge at both ends give the roof a thick outline
  if (mat.trim) {
    for (let i = 0; i < half; i++) {
      if (axis === 'x') for (const x of [X0, X1]) { b.stairs(x, y + i, Z0 + i, mat.trim, 'south'); b.stairs(x, y + i, Z1 - i, mat.trim, 'north'); }
      else for (const z of [Z0, Z1]) { b.stairs(X0 + i, y + i, z, mat.trim, 'east'); b.stairs(X1 - i, y + i, z, mat.trim, 'west'); }
    }
  }
  if (!odd && mat.full) { // even span: ridge cap
    const i = half;
    if (axis === 'x') for (let x = X0; x <= X1; x++) { b.set(x, y + i - 1, Z0 + i - 1, mat.full); b.set(x, y + i - 1, Z0 + i, mat.full); b.slab(x, y + i, Z0 + i - 1, mat.slab); b.slab(x, y + i, Z0 + i, mat.slab); }
    else for (let z = Z0; z <= Z1; z++) { b.set(X0 + i - 1, y + i - 1, z, mat.full); b.set(X0 + i, y + i - 1, z, mat.full); b.slab(X0 + i - 1, y + i, z, mat.slab); b.slab(X0 + i, y + i, z, mat.slab); }
  }
  return half;
}
/** Flat modern roof with a slab parapet lip and a recessed inner deck. */
export function flatRoof(b, x0, z0, x1, z1, y, deck = 'smooth_quartz', lip = 'smooth_quartz_slab', over = 1) {
  for (let x = x0 - over; x <= x1 + over; x++) for (let z = z0 - over; z <= z1 + over; z++) {
    const edge = x === x0 - over || x === x1 + over || z === z0 - over || z === z1 + over;
    b.set(x, y, z, deck);
    if (edge) b.slab(x, y + 1, z, lip);
  }
}

// ------------------------------------------------------------------ walls / windows
/**
 * Window cut into a wall that runs along `axis` at fixed coordinate. Adds a recessed pane, a sill and shutters/frame.
 * dir = outward facing of the wall.
 */
export function windowIn(b, x, y, z, w, h, dir, opts = {}) {
  const { glass = 'glass_pane', sill = 'spruce_trapdoor', frame = 'spruce_stairs', shutters = null, box = null } = opts;
  const [ox, oz] = DIRS[dir];
  const along = dir === 'north' || dir === 'south' ? [1, 0] : [0, 1];
  for (let i = 0; i < w; i++) for (let j = 0; j < h; j++) b.set(x + along[0] * i, y + j, z + along[1] * i, glass);
  for (let i = 0; i < w; i++) {
    const sx = x + along[0] * i + ox, sz = z + along[1] * i + oz;
    if (sill) b.trap(sx, y - 1, sz, sill, dir, 'top');
    if (frame) b.stairs(sx, y + h, sz, frame, OPP[dir], 'top');
    if (box) { b.set(sx, y - 1, sz, box); b.set(sx, y, sz, i % 2 ? 'azalea' : 'potted_red_tulip'); }
  }
  if (shutters) {
    const lx = x - along[0] + ox, lz = z - along[1] + oz, rx = x + along[0] * w + ox, rz = z + along[1] * w + oz;
    // open trapdoors lie flat against the wall beside the window (panel on the side opposite `facing`)
    for (let j = 0; j < h; j++) { b.trap(lx, y + j, lz, shutters, dir, 'bottom', true); b.trap(rx, y + j, rz, shutters, dir, 'bottom', true); }
  }
}

// ------------------------------------------------------------------ furniture
export function chair(b, x, y, z, facing, wood = 'spruce') { // seat faces `facing`
  b.stairs(x, y, z, `${wood}_stairs`, OPP[facing]);
  const s = CW[facing], t = CCW[facing];
  const [ax, az] = DIRS[s], [cx, cz] = DIRS[t];
  if (!b.has(x + ax, y, z + az)) b.trap(x + ax, y, z + az, `${wood}_trapdoor`, s, 'bottom', true);
  if (!b.has(x + cx, y, z + cz)) b.trap(x + cx, y, z + cz, `${wood}_trapdoor`, t, 'bottom', true);
}
export function table(b, x, y, z, top = 'spruce_pressure_plate', leg = 'spruce_fence') { b.set(x, y, z, leg); b.set(x, y + 1, z, top); }
export function sofa(b, x, y, z, len, facing, color = 'gray', wood = 'dark_oak') {
  const along = facing === 'north' || facing === 'south' ? [1, 0] : [0, 1];
  for (let i = 0; i < len; i++) {
    const px = x + along[0] * i, pz = z + along[1] * i;
    b.stairs(px, y, pz, `${wood}_stairs`, OPP[facing]);
    b.set(px, y + 1, pz, `${color}_carpet`);
  }
  const f0 = facing === 'north' || facing === 'south' ? 'west' : 'north';
  b.trap(x - along[0], y, z - along[1], `${wood}_trapdoor`, f0, 'bottom', true);
  b.trap(x + along[0] * len, y, z + along[1] * len, `${wood}_trapdoor`, OPP[f0], 'bottom', true);
}
export function bookshelfWall(b, x, y, z, len, h, axis) {
  for (let i = 0; i < len; i++) for (let j = 0; j < h; j++) {
    const px = axis === 'x' ? x + i : x, pz = axis === 'z' ? z + i : z;
    b.set(px, y + j, pz, (i + j) % 3 === 2 ? 'spruce_planks' : 'bookshelf');
  }
}
export function plant(b, x, y, z, r) { b.set(x, y, z, r ? r.pick(['potted_fern', 'potted_azalea_bush', 'potted_flowering_azalea_bush', 'potted_red_tulip', 'potted_cactus', 'potted_bamboo']) : 'potted_fern'); }
