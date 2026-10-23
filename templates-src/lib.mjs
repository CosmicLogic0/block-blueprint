// Template toolkit: place blocks by name + properties, checked against the real Minecraft catalog.
// Coordinates are Minecraft's: +x east, +y up, +z south.
import fs from 'node:fs';

const CATALOG = process.env.BB_CATALOG || '/home/claude/realmc/catalog.json';
const cat = JSON.parse(fs.readFileSync(CATALOG, 'utf8')).blocks;
const parsed = new Map();
function variantsOf(name) {
  let v = parsed.get(name);
  if (!v) {
    const c = cat[name];
    if (!c) throw new Error(`Unknown block: ${name}`);
    v = c.variants.map((x) => ({ label: x.label, p: x.label === 'default' ? {} : Object.fromEntries(x.label.split(',').map((q) => q.split('='))) }));
    parsed.set(name, v);
  }
  return v;
}
/** Best variant label for the wanted properties. Unknown property names are an error (catches typos).
 *  Identical-looking variants were merged in the catalog, so the closest match is used when there is no exact one. */
export function label(name, props = {}) {
  const vs = variantsOf(name);
  if (cat[name].mp) return 'default';
  const keys = Object.keys(props);
  const known = new Set(vs.flatMap((v) => Object.keys(v.p)));
  const bad = keys.filter((k) => !known.has(k) && !['waterlogged', 'powered', 'persistent', 'distance', 'lit', 'open', 'occupied'].includes(k));
  if (bad.length) throw new Error(`${name} has no property ${bad.join(',')} (has ${[...known].join(',')})`);
  let best = null, bestScore = -1e9;
  for (const v of vs) {
    let sc = 0;
    for (const k of keys) { if (v.p[k] === undefined) continue; sc += v.p[k] === String(props[k]) ? 1 : -2; }
    if (sc > bestScore) { bestScore = sc; best = v; }
  }
  return best.label;
}
export const has = (name) => !!cat[name];

// ---------- random
export function rng(seed) {
  let s = seed >>> 0 || 1;
  const f = () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; };
  f.int = (a, b) => a + Math.floor(f() * (b - a + 1));
  f.pick = (arr) => arr[Math.floor(f() * arr.length)];
  f.chance = (p) => f() < p;
  return f;
}
/** weighted mix: [['stone_bricks',6],['cracked_stone_bricks',1]] -> () => name */
export function mixer(r, list) {
  const tot = list.reduce((a, [, w]) => a + w, 0);
  return () => { let t = r() * tot; for (const [n, w] of list) { if ((t -= w) < 0) return n; } return list[0][0]; };
}
/** 3D value-noise-ish hash in [0,1) for smooth-ish texture clusters */
export function hash3(x, y, z, seed = 1) {
  let h = (x * 374761393 + y * 668265263 + z * 2147483647 + seed * 1442695041) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16;
  return ((h >>> 0) % 100000) / 100000;
}
export function noise3(x, y, z, scale = 3, seed = 1) {
  const fx = Math.floor(x / scale), fy = Math.floor(y / scale), fz = Math.floor(z / scale);
  const tx = x / scale - fx, ty = y / scale - fy, tz = z / scale - fz;
  const L = (a, b, t) => a + (b - a) * (t * t * (3 - 2 * t));
  const c = (i, j, k) => hash3(fx + i, fy + j, fz + k, seed);
  return L(L(L(c(0, 0, 0), c(1, 0, 0), tx), L(c(0, 1, 0), c(1, 1, 0), tx), ty), L(L(c(0, 0, 1), c(1, 0, 1), tx), L(c(0, 1, 1), c(1, 1, 1), tx), ty), tz);
}
/** palette gradient: list ordered light->dark, t in [0,1] */
export const grad = (list, t) => list[Math.max(0, Math.min(list.length - 1, Math.floor(t * list.length)))];

export const DIRS = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] };
export const OPP = { north: 'south', south: 'north', east: 'west', west: 'east' };
export const CW = { north: 'east', east: 'south', south: 'west', west: 'north' };
export const CCW = { north: 'west', west: 'south', south: 'east', east: 'north' };

// ---------- the build
export class Build {
  constructor(meta) { this.meta = meta; this.m = new Map(); this.r = rng(meta.seed || 7); }
  key(x, y, z) { return `${Math.round(x)},${Math.round(y)},${Math.round(z)}`; }
  /** spec: 'name' or ['name', {props}] or null (air) */
  set(x, y, z, name, props) {
    x = Math.round(x); y = Math.round(y); z = Math.round(z);
    if (Array.isArray(name)) { props = name[1]; name = name[0]; }
    if (typeof name === 'function') name = name(x, y, z);
    if (Array.isArray(name)) { props = name[1]; name = name[0]; }
    const k = this.key(x, y, z);
    if (!name || name === 'air') { this.m.delete(k); return this; }
    this.m.set(k, [x, y, z, name, label(name, props || {})]);
    return this;
  }
  get(x, y, z) { const v = this.m.get(this.key(x, y, z)); return v ? v[3] : null; }
  has(x, y, z) { return this.m.has(this.key(x, y, z)); }
  del(x, y, z) { this.m.delete(this.key(x, y, z)); return this; }
  /** set only if empty */
  soft(x, y, z, name, props) { if (!this.has(x, y, z)) this.set(x, y, z, name, props); return this; }
  fill(x0, y0, z0, x1, y1, z1, name, props) {
    for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
      for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
        for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) this.set(x, y, z, name, props);
    return this;
  }
  clear(x0, y0, z0, x1, y1, z1) { return this.fill(x0, y0, z0, x1, y1, z1, null); }
  /** walls only (no floor/ceiling) */
  walls(x0, y0, z0, x1, y1, z1, name, props) {
    for (let y = y0; y <= y1; y++) {
      for (let x = x0; x <= x1; x++) { this.set(x, y, z0, name, props); this.set(x, y, z1, name, props); }
      for (let z = z0; z <= z1; z++) { this.set(x0, y, z, name, props); this.set(x1, y, z, name, props); }
    }
    return this;
  }
  each(fn) { for (const v of [...this.m.values()]) fn(v[0], v[1], v[2], v[3]); }
  replace(from, to, props) { this.each((x, y, z, n) => { if (n === from || (from instanceof RegExp && from.test(n))) this.set(x, y, z, typeof to === 'function' ? to(x, y, z, n) : to, props); }); return this; }

  // ---- common pieces
  stairs(x, y, z, name, facing, half = 'bottom', shape = 'straight') { return this.set(x, y, z, name, { facing, half, shape }); }
  slab(x, y, z, name, type = 'bottom') { return this.set(x, y, z, name, { type }); }
  log(x, y, z, name, axis = 'y') { return this.set(x, y, z, name, { axis }); }
  trap(x, y, z, name, facing, half = 'bottom', open = false) { return this.set(x, y, z, name, { facing, half, open: String(open) }); }
  door(x, y, z, name, facing, hinge = 'left') {
    this.set(x, y, z, name, { facing, half: 'lower', hinge, open: 'false' });
    return this.set(x, y + 1, z, name, { facing, half: 'upper', hinge, open: 'false' });
  }
  bed(x, y, z, color, facing) { // foot at (x,y,z), head one step toward facing
    const [dx, dz] = DIRS[facing];
    this.set(x, y, z, `${color}_bed`, { facing, part: 'foot' });
    return this.set(x + dx, y, z + dz, `${color}_bed`, { facing, part: 'head' });
  }
  lantern(x, y, z, hanging = false, soul = false) { return this.set(x, y, z, soul ? 'soul_lantern' : 'lantern', { hanging: String(hanging) }); }
  chain(x, y, z, axis = 'y') { return this.set(x, y, z, has('iron_chain') ? 'iron_chain' : 'chain', { axis }); }
  wallTorch(x, y, z, facing) { return this.set(x, y, z, 'wall_torch', { facing }); }
  tall(x, y, z, name) { this.set(x, y, z, name, { half: 'lower' }); return this.set(x, y + 1, z, name, { half: 'upper' }); }

  /** cylinder (solid or ring) on the y axis */
  disc(cx, y, cz, r, name, props, ring = false) {
    for (let x = -Math.ceil(r); x <= Math.ceil(r); x++) for (let z = -Math.ceil(r); z <= Math.ceil(r); z++) {
      const d = Math.hypot(x, z);
      if (d <= r + 0.3 && (!ring || d > r - 0.9)) this.set(cx + x, y, cz + z, name, props);
    }
    return this;
  }
  ellipsoid(cx, cy, cz, rx, ry, rz, name, props, fn) {
    for (let x = -Math.ceil(rx); x <= Math.ceil(rx); x++) for (let y = -Math.ceil(ry); y <= Math.ceil(ry); y++) for (let z = -Math.ceil(rz); z <= Math.ceil(rz); z++) {
      const d = (x / rx) ** 2 + (y / ry) ** 2 + (z / rz) ** 2;
      if (d <= 1 && (!fn || fn(cx + x, cy + y, cz + z, d))) this.set(cx + x, cy + y, cz + z, name, props);
    }
    return this;
  }
  line(x0, y0, z0, x1, y1, z1, name, props) {
    const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0), 1);
    for (let i = 0; i <= n; i++) this.set(Math.round(x0 + (x1 - x0) * i / n), Math.round(y0 + (y1 - y0) * i / n), Math.round(z0 + (z1 - z0) * i / n), name, props);
    return this;
  }

  toJSON() {
    // shift so the lowest corner is at (0,0,0) .. centred on x/z
    let mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
    for (const [x, y, z] of this.m.values()) { mn = [Math.min(mn[0], x), Math.min(mn[1], y), Math.min(mn[2], z)]; mx = [Math.max(mx[0], x), Math.max(mx[1], y), Math.max(mx[2], z)]; }
    const ox = Math.floor((mn[0] + mx[0]) / 2), oz = Math.floor((mn[2] + mx[2]) / 2), oy = mn[1];
    const blocks = [...this.m.values()].map(([x, y, z, n, l]) => [x - ox, y - oy, z - oz, n, l, 0]);
    blocks.sort((a, b) => a[1] - b[1] || a[2] - b[2] || a[0] - b[0]);
    return { format: 'block-blueprint', version: 1, mc: '26.3', name: this.meta.name, blocks,
      size: [mx[0] - mn[0] + 1, mx[1] - mn[1] + 1, mx[2] - mn[2] + 1] };
  }
}
