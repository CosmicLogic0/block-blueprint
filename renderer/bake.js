import * as THREE from 'three';
import { COLS } from './atlas.js';
import { autoProps, isGate, isStairs } from './connect.js';

export const DIR_VEC = {
  down: [0, -1, 0], up: [0, 1, 0], north: [0, 0, -1], south: [0, 0, 1], west: [-1, 0, 0], east: [1, 0, 0],
};

// World cell <-> numeric key. x,z in [-512,511], y in [0,511].
export const encode = (x, y, z) => (x + 512) + 1024 * (z + 512) + 1048576 * y;
export function decode(k) {
  const x = (k % 1024) - 512;
  const z = (Math.floor(k / 1024) % 1024) - 512;
  const y = Math.floor(k / 1048576);
  return { x, y, z };
}
export const inRange = (x, y, z) => x >= -512 && x < 512 && z >= -512 && z < 512 && y >= 0 && y < 512;

// Corners TL,TR,BR,BL as seen from outside each face (Minecraft axes: +x east, +y up, +z south).
const FACE_CORNERS = {
  north: (a, b) => [[b[0], b[1], a[2]], [a[0], b[1], a[2]], [a[0], a[1], a[2]], [b[0], a[1], a[2]]],
  south: (a, b) => [[a[0], b[1], b[2]], [b[0], b[1], b[2]], [b[0], a[1], b[2]], [a[0], a[1], b[2]]],
  west: (a, b) => [[a[0], b[1], a[2]], [a[0], b[1], b[2]], [a[0], a[1], b[2]], [a[0], a[1], a[2]]],
  east: (a, b) => [[b[0], b[1], b[2]], [b[0], b[1], a[2]], [b[0], a[1], a[2]], [b[0], a[1], b[2]]],
  up: (a, b) => [[a[0], b[1], a[2]], [b[0], b[1], a[2]], [b[0], b[1], b[2]], [a[0], b[1], b[2]]],
  down: (a, b) => [[a[0], a[1], b[2]], [b[0], a[1], b[2]], [b[0], a[1], a[2]], [a[0], a[1], a[2]]],
};

function defaultUV(dir, a, b) {
  const [x1, y1, z1] = a, [x2, y2, z2] = b;
  switch (dir) {
    case 'down': return [x1, 16 - z2, x2, 16 - z1];
    case 'up': return [x1, z1, x2, z2];
    case 'north': return [16 - x2, 16 - y2, 16 - x1, 16 - y1];
    case 'south': return [x1, 16 - y2, x2, 16 - y1];
    case 'west': return [z1, 16 - y2, z2, 16 - y1];
    default: return [16 - z2, 16 - y2, 16 - z1, 16 - y1]; // east
  }
}

const rad = (d) => (d * Math.PI) / 180;
function rot(v, axis, deg) {
  const c = Math.cos(rad(deg)), s = Math.sin(rad(deg));
  const [x, y, z] = v;
  if (axis === 'x') return [x, y * c - z * s, y * s + z * c];
  if (axis === 'y') return [x * c + z * s, y, -x * s + z * c];
  return [x * c - y * s, x * s + y * c, z];
}
function vecToDir(v) {
  const ax = Math.abs(v[0]), ay = Math.abs(v[1]), az = Math.abs(v[2]);
  if (ay >= ax && ay >= az) return v[1] > 0 ? 'up' : 'down';
  if (ax >= az) return v[0] > 0 ? 'east' : 'west';
  return v[2] > 0 ? 'south' : 'north';
}

// Biome-style tints (plains defaults) for tintindex faces.
function matchWhen(when, props) {
  if (when.OR) return when.OR.some((w) => matchWhen(w, props));
  if (when.AND) return when.AND.every((w) => matchWhen(w, props));
  return Object.entries(when).every(([k, val]) => String(val).split('|').includes(String(props[k])));
}

const DYE = { white: 0xf9fffe, orange: 0xf9801d, magenta: 0xc74ebd, light_blue: 0x3ab3da, yellow: 0xfed83d, lime: 0x80c71f, pink: 0xf38baa, gray: 0x474f52, light_gray: 0x9d9d97, cyan: 0x169c9c, purple: 0x8932b8, blue: 0x3c44aa, brown: 0x835432, green: 0x5e7c16, red: 0xb02e26, black: 0x1d1d21 };

// redstone dust colour for power 0..15 (Minecraft's RedStoneWireBlock colour curve)
export function dustColor(p) {
  const f = p / 15, r = f * 0.6 + (f > 0 ? 0.4 : 0.3), g = Math.max(0, Math.min(1, f * f * 0.7 - 0.5)), b = Math.max(0, Math.min(1, f * f * 0.6 - 0.7));
  return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
}
export function tintFor(blockName, tex, power) {
  const bm = /^(.+?)_(?:wall_)?banner$/.exec(blockName);
  if (bm && DYE[bm[1]] !== undefined) return DYE[bm[1]];
  if (blockName === 'water') return 0x3f76e4;
  if (/cherry|azalea|pale_oak/.test(blockName)) return 0xffffff; // these leaves are never tinted
  if (blockName.includes('leaves')) {
    if (blockName.startsWith('birch')) return 0x80a755;
    if (blockName.startsWith('spruce')) return 0x619961;
    if (blockName.startsWith('mangrove')) return 0x8db127;
    return 0x77ab2f;
  }
  if (blockName.includes('vine')) return 0x77ab2f;
  if (blockName.includes('lily_pad')) return 0x208030;
  if (blockName.includes('redstone_wire')) return dustColor(power || 0);
  if (blockName.includes('stem')) return 0x7fbf3f;
  return 0x91bd59; // grass family
}

const hexToRgb = (h) => [((h >> 16) & 255) / 255, ((h >> 8) & 255) / 255, (h & 255) / 255];

export class Baker {
  constructor(catalog, atlas, shading = true) {
    this.catalog = catalog; this.atlas = atlas; this.shading = shading; this.cache = new Map(); this._kind = new Map();
  }
  setShading(v) { this.shading = v; this.cache.clear(); }

  variantCount(name) { return this.catalog.blocks[name].variants.length; }
  variantLabel(name, v) { return this.catalog.blocks[name].variants[v].label; }

  bake(name, v = 0, r = 0) {
    const key = `${name}|${v}|${r}|${this.shading ? 1 : 0}`;
    let b = this.cache.get(key);
    if (b) return b;
    const blk = this.catalog.blocks[name];
    const variant = blk && blk.variants[v % blk.variants.length];
    b = this._bakeParts(name, variant ? variant.parts : [], r);
    this.cache.set(key, b);
    return b;
  }

  _bakeParts(name, parts, r) {
    const faces = [];
    let opaque = false, cube = false;
    for (const part of parts) {
      const model = this.catalog.models[part.m];
      if (!model) continue;
      const px = part.x || 0, py = ((part.y || 0) + r * 90) % 360;
      for (const el of model.elements) {
        const full = el.from.every((n) => n === 0) && el.to.every((n) => n === 16) && !el.rotation;
        if (full && Object.keys(el.faces).length === 6) {
          cube = true;
          if (Object.values(el.faces).every((f) => this.atlas.opaque[f.tex])) opaque = true;
        }
        for (const [dir, f] of Object.entries(el.faces)) faces.push(this._face(name, el, dir, f, px, py));
      }
    }
    return { faces, opaque, cube };
  }

  // Bake the block at a world cell: fences/walls/panes join their neighbours, gates sink into walls.
  bakeAt(blk, x, y, z, at, solid) {
    const name = blk.b, cat = this.catalog.blocks[name];
    if (!cat) return this.bake(name, blk.v, blk.r);
    let kind = this._kind.get(name);
    if (kind === undefined) { kind = cat.mp ? 'mp' : isGate(name) || isStairs(name) ? 'gate' : ''; this._kind.set(name, kind); }
    if (!kind) return this.bake(name, blk.v, blk.r);
    const props = autoProps(name, x, y, z, at, solid);
    if (!props) return this.bake(name, blk.v, blk.r);
    if (kind === 'mp') {
      if (name === 'redstone_wire') props.power = String(blk.pw || 0);
      const key = `${name}|mp|${JSON.stringify(props)}|${this.shading ? 1 : 0}`;
      let b = this.cache.get(key);
      if (!b) {
        const parts = cat.mp.base.concat(cat.mp.rules.filter((r) => matchWhen(r.when, props)).map((r) => r.part));
        this._pw = +(props.power || 0);
        b = this._bakeParts(name, parts, 0); this.cache.set(key, b);
        this._pw = 0;
      }
      return b;
    }
    // gate / stairs: pick the variant with the same properties plus the computed in_wall / shape
    const label = cat.variants[blk.v % cat.variants.length].label;
    const want = Object.assign(Object.fromEntries(label === 'default' ? [] : label.split(',').map((q) => q.split('='))), props);
    const idx = cat.variants.findIndex((va) => va.label !== 'default' && va.label.split(',').every((q) => { const [k, val] = q.split('='); return want[k] === val; }));
    return this.bake(name, idx < 0 ? blk.v : idx, blk.r);
  }

  _face(blockName, el, dir, f, px, py) {
    const corners = FACE_CORNERS[dir](el.from, el.to).map((p) => p.slice());
    let normal = DIR_VEC[dir].slice();
    if (el.rotation) {
      const { origin: o, axis, angle, rescale } = el.rotation;
      const sc = rescale ? 1 / Math.cos(rad(Math.abs(angle))) : 1;
      for (const p of corners) {
        let q = rot([p[0] - o[0], p[1] - o[1], p[2] - o[2]], axis, angle);
        if (rescale) { if (axis !== 'x') q[0] *= sc; if (axis !== 'y') q[1] *= sc; if (axis !== 'z') q[2] *= sc; }
        p[0] = q[0] + o[0]; p[1] = q[1] + o[1]; p[2] = q[2] + o[2];
      }
      normal = rot(normal, axis, angle);
    }
    const spin = (v) => { let q = v; if (px) q = rot(q, 'x', -px); if (py) q = rot(q, 'y', -py); return q; };
    const pos = [];
    for (const p of corners) { const q = spin([p[0] - 8, p[1] - 8, p[2] - 8]); pos.push((q[0] + 8) / 16, (q[1] + 8) / 16, (q[2] + 8) / 16); }
    normal = spin(normal);

    // UVs
    const [u1, v1, u2, v2] = f.uv || defaultUV(dir, el.from, el.to);
    const baseUV = [[u1, v1], [u2, v1], [u2, v2], [u1, v2]];
    const k = (((f.rotation || 0) / 90) % 4 + 4) % 4;
    const idx = this.atlas.index[f.tex] || 0;
    const col = idx % COLS, row = Math.floor(idx / COLS);
    const uLo = Math.min(u1, u2), vLo = Math.min(v1, v2), eps = 0.03;
    const uv = [];
    const { tile, size } = this.atlas, sc = tile / 16; // model UVs are in 0..16 texture units
    for (let i = 0; i < 4; i++) {
      const [pu, pv] = baseUV[(i - k + 4) % 4];
      const iu = (pu === uLo ? pu + eps : pu - eps) * sc, iv = (pv === vLo ? pv + eps : pv - eps) * sc;
      uv.push((col * tile + iu) / size, 1 - (row * tile + iv) / size);
    }

    // Vertex color = tint * vanilla-style directional shade
    let shade = 1;
    if (this.shading) shade = 0.8 * normal[2] ** 2 + 0.6 * normal[0] ** 2 + (normal[1] > 0 ? 1 : 0.5) * normal[1] ** 2;
    const base = f.tintindex !== undefined && f.tintindex >= 0 ? hexToRgb(tintFor(blockName, f.tex, this._pw)) : [1, 1, 1];
    const color = [base[0] * shade, base[1] * shade, base[2] * shade];

    let cull = null;
    if (f.cullface) cull = vecToDir(spin(DIR_VEC[f.cullface].slice()));
    return { pos, uv, color, cull, trans: !!(this.atlas.translucent && this.atlas.translucent[f.tex]) };
  }
}

function pushFace(out, f, ox, oy, oz) {
  const base = out.pos.length / 3;
  for (let i = 0; i < 4; i++) {
    out.pos.push(f.pos[i * 3] + ox, f.pos[i * 3 + 1] + oy, f.pos[i * 3 + 2] + oz);
    out.uv.push(f.uv[i * 2], f.uv[i * 2 + 1]);
    out.col.push(f.color[0], f.color[1], f.color[2]);
  }
  out.idx.push(base, base + 3, base + 2, base, base + 2, base + 1);
}

function toGeometry(out) {
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(out.pos, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(out.uv, 2));
  g.setAttribute('color', new THREE.Float32BufferAttribute(out.col, 3));
  g.setIndex(out.idx);
  g.computeBoundingSphere();
  return g;
}

// map: Map<key,{b,v,r}>; hides cells above maxY and culls faces hidden by opaque neighbours.
export function buildWorldGeometry(map, maxY, baker) {
  const solidOut = { pos: [], uv: [], col: [], idx: [] }, transOut = { pos: [], uv: [], col: [], idx: [] };
  const at = (x, y, z) => { const n = map.get(encode(x, y, z)); return n && { name: n.b, r: n.r, label: baker.variantLabel(n.b, n.v) }; };
  const solidMemo = new Map();
  const solid = (n) => { let v = solidMemo.get(n); if (v === undefined) { const bk = baker.catalog.blocks[n] ? baker.bake(n, 0, 0) : null; v = !!(bk && bk.opaque && bk.cube); solidMemo.set(n, v); } return v; };
  for (const [k, blk] of map) {
    const { x, y, z } = decode(k);
    if (y > maxY) continue;
    const baked = baker.bakeAt(blk, x, y, z, at, solid);
    for (const f of baked.faces) {
      if (f.cull) {
        const d = DIR_VEC[f.cull];
        const ny = y + d[1];
        if (ny <= maxY) {
          const nb = map.get(encode(x + d[0], ny, z + d[2]));
          if (nb) {
            const nbk = baker.bake(nb.b, nb.v, nb.r);
            if (nbk.opaque || (nb.b === blk.b && baked.cube && nbk.cube)) continue;
          }
        }
      }
      pushFace(f.trans ? transOut : solidOut, f, x, y, z);
    }
  }
  // one geometry, two groups: 0 = solid / cut-out faces, 1 = translucent faces (glass, ice, water)
  const n = solidOut.pos.length / 3, nIdx = solidOut.idx.length;
  const out = { pos: solidOut.pos.concat(transOut.pos), uv: solidOut.uv.concat(transOut.uv), col: solidOut.col.concat(transOut.col), idx: solidOut.idx.concat(transOut.idx.map((i) => i + n)) };
  const g = toGeometry(out);
  g.addGroup(0, nIdx, 0); g.addGroup(nIdx, transOut.idx.length, 1);
  return g;
}

// The same block repeated at several cells (ghost preview of a line).
export function buildMultiGeometry(baked, cells) {
  const out = { pos: [], uv: [], col: [], idx: [] };
  for (const c of cells) for (const f of baked.faces) pushFace(out, f, c.x, c.y, c.z);
  return toGeometry(out);
}

// Single block at the origin cell (for hover ghost and palette icons).
export function buildBlockGeometry(baked) {
  const out = { pos: [], uv: [], col: [], idx: [] };
  for (const f of baked.faces) pushFace(out, f, 0, 0, 0);
  return toGeometry(out);
}
