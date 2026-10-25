// Schematic files from other tools, as Block Blueprint builds (and back).
//   .schem      Sponge schematic v1, v2, v3 (WorldEdit, FAWE, Axiom, Amulet...)  read + write
//   .litematic  Litematica                                                     read
//   .nbt        Minecraft structure block files                                read
// Works on raw (already un-gzipped) NBT bytes so it runs in the app window and in the main process alike.
// Same rules as the mod's SchemCodec.java, so a file looks the same in the app and in Minecraft.

export const SCHEM_EXTENSIONS = ['.schem', '.litematic', '.nbt', '.schematic'];
export const isSchematicName = (f) => SCHEM_EXTENSIONS.some((e) => String(f).toLowerCase().endsWith(e));
export const isGzip = (u8) => u8.length > 2 && u8[0] === 0x1f && u8[1] === 0x8b;

const MAX_DIM = 512, MAX_CELLS = 8_000_000;
const AIR = new Set(['minecraft:air', 'minecraft:cave_air', 'minecraft:void_air', 'minecraft:structure_void']);

// ---------------------------------------------------------------- NBT
export function parseNbt(u8) {
  const dv = new DataView(u8.buffer, u8.byteOffset, u8.byteLength); let p = 0;
  const td = new TextDecoder();
  const need = (n) => { if (p + n > u8.length) throw new Error('That schematic file ended early'); };
  const str = () => { need(2); const n = dv.getUint16(p); p += 2; need(n); const s = td.decode(u8.subarray(p, p + n)); p += n; return s; };
  const len = () => { need(4); const n = dv.getInt32(p); p += 4; if (n < 0 || n > 64_000_000) throw new Error('That schematic file is broken'); return n; };
  function payload(t, depth) {
    if (depth > 64) throw new Error('That schematic file is nested too deeply');
    switch (t) {
      case 1: need(1); return dv.getInt8(p++);
      case 2: { need(2); const v = dv.getInt16(p); p += 2; return v; }
      case 3: { need(4); const v = dv.getInt32(p); p += 4; return v; }
      case 4: { need(8); const v = dv.getBigInt64(p); p += 8; return v; }
      case 5: { need(4); const v = dv.getFloat32(p); p += 4; return v; }
      case 6: { need(8); const v = dv.getFloat64(p); p += 8; return v; }
      case 7: { const n = len(); need(n); const a = u8.slice(p, p + n); p += n; return a; }
      case 8: return str();
      case 9: { need(5); const et = dv.getUint8(p); p += 1; const n = len(); const a = []; for (let i = 0; i < n; i++) a.push(payload(et, depth + 1)); return a; }
      case 10: { const o = {}; for (;;) { need(1); const tt = dv.getUint8(p++); if (tt === 0) return o; const k = str(); o[k] = payload(tt, depth + 1); } }
      case 11: { const n = len(); need(n * 4); const a = new Int32Array(n); for (let i = 0; i < n; i++) { a[i] = dv.getInt32(p); p += 4; } return a; }
      case 12: { const n = len(); need(n * 8); const a = new BigUint64Array(n); for (let i = 0; i < n; i++) { a[i] = dv.getBigUint64(p); p += 8; } return a; }
      default: throw new Error('That schematic file is broken (unknown tag ' + t + ')');
    }
  }
  need(1); if (dv.getUint8(p++) !== 10) throw new Error("That file isn't a schematic");
  str();
  return payload(10, 0);
}

// ---------------------------------------------------------------- reading
const num = (v) => (typeof v === 'bigint' ? Number(v) : Number(v));
const u16 = (v) => num(v) & 0xffff;
function checkSize(x, y, z) {
  if (x <= 0 || y <= 0 || z <= 0) throw new Error('That schematic is empty');
  if (x > MAX_DIM || y > MAX_DIM || z > MAX_DIM || x * y * z > MAX_CELLS) throw new Error(`That schematic is too big (${x} x ${y} x ${z}). Up to 512 blocks each way.`);
}
const stateOf = (e) => {   // {Name, Properties} (older files) or {id, properties} (Minecraft 26.x structure files)
  const name = e && (typeof e.Name === 'string' ? e.Name : typeof e.id === 'string' ? e.id : null); if (!name) return null;
  const P = e.Properties || e.properties;
  const props = P && typeof P === 'object' ? Object.keys(P).sort().map((k) => `${k}=${P[k]}`) : [];
  return props.length ? `${name}[${props.join(',')}]` : name;
};

/** Grid result: { name, sx, sy, sz, palette: [state...], cells: Int32Array (0 = air, n = palette[n-1]) }. Index = (y*sz+z)*sx+x. */
export function readSchematic(root, fallbackName = 'Imported build') {
  if (root.Schematic && typeof root.Schematic === 'object' && !Array.isArray(root.Schematic)) root = root.Schematic;
  if (root.Regions && typeof root.Regions === 'object') return litematic(root, fallbackName);
  if (Array.isArray(root.size) && (Array.isArray(root.palette) || Array.isArray(root.palettes))) return structure(root, fallbackName);
  if ((root.Blocks && !(root.Blocks instanceof Uint8Array)) || root.Palette || root.BlockData) return sponge(root, fallbackName);
  if (root.Blocks instanceof Uint8Array || 'Materials' in root) throw new Error('That is an old .schematic file (from before Minecraft 1.13). Open it in WorldEdit on a newer version, save it as .schem, then try again.');
  throw new Error("That file isn't a schematic Block Blueprint knows");
}

function makePal() {
  const idx = new Map(), list = [];
  return { list, of(state) {
    if (!state) return 0;
    if (!state.split('[')[0].includes(':')) state = 'minecraft:' + state;
    if (AIR.has(state.split('[')[0])) return 0;
    let k = idx.get(state); if (k === undefined) { list.push(state); k = list.length; idx.set(state, k); }
    return k;
  } };
}

function sponge(s, fallbackName) {
  const w = u16(s.Width), h = u16(s.Height), l = u16(s.Length); checkSize(w, h, l);
  const b = s.Blocks && !(s.Blocks instanceof Uint8Array) ? s.Blocks : null;
  const palette = b ? b.Palette : s.Palette, data = b ? b.Data : s.BlockData;
  if (!palette || !data) throw new Error('That .schem file has no blocks in it');
  const pal = makePal(); const map = [];
  for (const [state, id] of Object.entries(palette)) map[num(id)] = pal.of(state);
  const n = w * h * l, cells = new Int32Array(n); let p = 0;
  for (let i = 0; i < n; i++) {
    let v = 0, shift = 0, byte;
    do { if (p >= data.length) throw new Error('That .schem file ended early'); byte = data[p++]; v |= (byte & 0x7f) << shift; shift += 7; } while (byte & 0x80);
    cells[i] = map[v] || 0;
  }
  const name = s.Metadata && typeof s.Metadata.Name === 'string' && s.Metadata.Name.trim() ? s.Metadata.Name.trim() : fallbackName;
  return { name, sx: w, sy: h, sz: l, palette: pal.list, cells };
}

function litematic(root, fallbackName) {
  const regions = Object.values(root.Regions); if (!regions.length) throw new Error('That .litematic file has no regions in it');
  const xyz = (o) => [num(o.x), num(o.y), num(o.z)];
  const mn = [Infinity, Infinity, Infinity], mx = [-Infinity, -Infinity, -Infinity];
  for (const r of regions) { const pos = xyz(r.Position), size = xyz(r.Size);
    for (let a = 0; a < 3; a++) { const lo = size[a] < 0 ? pos[a] + size[a] + 1 : pos[a]; mn[a] = Math.min(mn[a], lo); mx[a] = Math.max(mx[a], lo + Math.abs(size[a]) - 1); } }
  const sx = mx[0] - mn[0] + 1, sy = mx[1] - mn[1] + 1, sz = mx[2] - mn[2] + 1; checkSize(sx, sy, sz);
  const pal = makePal(), cells = new Int32Array(sx * sy * sz);
  for (const r of regions) {
    const pos = xyz(r.Position), size = xyz(r.Size), [ax, ay, az] = size.map(Math.abs);
    const o = [0, 1, 2].map((a) => (size[a] < 0 ? pos[a] + size[a] + 1 : pos[a]) - mn[a]);
    const plist = r.BlockStatePalette, bits = r.BlockStates; if (!plist || !bits || !plist.length) continue;
    const map = plist.map((e) => pal.of(stateOf(e)));
    const nb = BigInt(Math.max(2, 32 - Math.clz32(plist.length - 1))), mask = (1n << nb) - 1n;
    for (let y = 0; y < ay; y++) for (let z = 0; z < az; z++) for (let x = 0; x < ax; x++) {
      const i = BigInt((y * az + z) * ax + x), start = i * nb, si = Number(start >> 6n), ei = Number(((i + 1n) * nb - 1n) >> 6n), off = start & 63n;
      if (ei >= bits.length) throw new Error('That .litematic file is broken (too little block data)');
      const v = Number(si === ei ? (bits[si] >> off) & mask : ((bits[si] >> off) | (bits[ei] << (64n - off))) & mask);
      const c = map[v] || 0; if (c) cells[((o[1] + y) * sz + (o[2] + z)) * sx + (o[0] + x)] = c;
    }
  }
  const name = root.Metadata && typeof root.Metadata.Name === 'string' && root.Metadata.Name.trim() ? root.Metadata.Name.trim() : fallbackName;
  return { name, sx, sy, sz, palette: pal.list, cells };
}

function structure(root, fallbackName) {
  const [sx, sy, sz] = root.size.map(num); checkSize(sx, sy, sz);
  const plist = Array.isArray(root.palette) ? root.palette : root.palettes[0];
  const pal = makePal(), map = plist.map((e) => pal.of(stateOf(e))), cells = new Int32Array(sx * sy * sz);
  for (const b of root.blocks || []) { const [x, y, z] = b.pos.map(num), st = num(b.state);
    if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz || !(st in map)) continue; cells[(y * sz + z) * sx + x] = map[st]; }
  return { name: fallbackName, sx, sy, sz, palette: pal.list, cells };
}

/** Grid -> the app's build format ({format, name, blocks: [[x, y, z, id, label, 0]...]}), centred like Minecraft builds. */
export function gridToBuild(g, desc = '') {
  const split = g.palette.map((s) => { const b = s.indexOf('['); const id = (b < 0 ? s : s.slice(0, b)).replace(/^minecraft:/, '');
    const label = b < 0 ? 'default' : s.slice(b + 1, -1).split(',').filter(Boolean).sort().join(',') || 'default'; return [id, label]; });
  const ox = Math.floor(g.sx / 2), oz = Math.floor(g.sz / 2), blocks = [];
  for (let y = 0; y < g.sy; y++) for (let z = 0; z < g.sz; z++) for (let x = 0; x < g.sx; x++) {
    const c = g.cells[(y * g.sz + z) * g.sx + x]; if (c) blocks.push([x - ox, y, z - oz, split[c - 1][0], split[c - 1][1], 0]);
  }
  return { format: 'block-blueprint', version: 1, name: g.name, desc, size: [g.sx, g.sy, g.sz], blocks };
}

/** Raw NBT bytes (already un-gzipped) -> app build. */
export function schematicToBuild(rawNbt, fallbackName, desc) { return gridToBuild(readSchematic(parseNbt(rawNbt), fallbackName), desc); }

// ---------------------------------------------------------------- writing (.schem, Sponge v2)
export const DATA_VERSIONS = { '1.16.5': 2586, '1.17.1': 2730, '1.18.1': 2865, '1.18.2': 2975, '1.19': 3105, '1.19.2': 3120, '1.19.3': 3218, '1.19.4': 3337,
  '1.20.1': 3465, '1.20.2': 3578, '1.20.4': 3700, '1.20.6': 3839, '1.21.1': 3955, '1.21.3': 4082, '1.21.4': 4189, '1.21.5': 4325, '1.21.8': 4440,
  '1.21.10': 4556, '1.21.11': 4671, '26.1': 4786, '26.1.2': 4790, '26.2': 4903, '26.3': 5023 };
export function dataVersionFor(mc) { const v = String(mc || '').replace(/\.jar$/, ''); return DATA_VERSIONS[v] || 5023; }

/** App build -> raw (not yet gzipped) Sponge v2 .schem NBT bytes. */
export function buildToSchem(build, dataVersion = 5023) {
  const bs = build.blocks || []; if (!bs.length) throw new Error('This build is empty');
  const mn = [Infinity, Infinity, Infinity], mx = [-Infinity, -Infinity, -Infinity];
  for (const [x, y, z] of bs) { const p = [Math.round(x), Math.round(y), Math.round(z)]; for (let a = 0; a < 3; a++) { mn[a] = Math.min(mn[a], p[a]); mx[a] = Math.max(mx[a], p[a]); } }
  const sx = mx[0] - mn[0] + 1, sy = mx[1] - mn[1] + 1, sz = mx[2] - mn[2] + 1; checkSize(sx, sy, sz);
  const palette = new Map([['minecraft:air', 0]]), cells = new Int32Array(sx * sy * sz);
  for (const [x, y, z, id, label] of bs) {
    const state = (String(id).includes(':') ? id : 'minecraft:' + id) + (!label || label === 'default' ? '' : `[${label}]`);
    let k = palette.get(state); if (k === undefined) { k = palette.size; palette.set(state, k); }
    cells[((Math.round(y) - mn[1]) * sz + (Math.round(z) - mn[2])) * sx + (Math.round(x) - mn[0])] = k;
  }
  const data = []; for (let v of cells) { while (v & ~0x7f) { data.push((v & 0x7f) | 0x80); v >>>= 7; } data.push(v); }
  const out = []; const te = new TextEncoder();
  const u8 = (v) => out.push(v & 0xff), u16w = (v) => { u8(v >> 8); u8(v); }, i32 = (v) => { u8(v >>> 24); u8(v >>> 16); u8(v >>> 8); u8(v); };
  const s = (t) => { const b = te.encode(t); u16w(b.length); for (const x of b) out.push(x); };
  const tag = (t, name) => { u8(t); s(name); };
  tag(10, 'Schematic');
  tag(3, 'Version'); i32(2);
  tag(3, 'DataVersion'); i32(dataVersion);
  tag(10, 'Metadata'); tag(8, 'Name'); s(String(build.name || 'Build').slice(0, 100)); tag(8, 'Author'); s('Block Blueprint'); u8(0);
  tag(2, 'Width'); u16w(sx); tag(2, 'Height'); u16w(sy); tag(2, 'Length'); u16w(sz);
  tag(11, 'Offset'); i32(3); i32(0); i32(0); i32(0);
  tag(3, 'PaletteMax'); i32(palette.size);
  tag(10, 'Palette'); for (const [st, k] of palette) { tag(3, st); i32(k); } u8(0);
  tag(7, 'BlockData'); i32(data.length); for (const x of data) out.push(x);
  tag(9, 'BlockEntities'); u8(10); i32(0);
  u8(0);
  return new Uint8Array(out);
}
