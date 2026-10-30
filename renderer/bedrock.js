// "Save for Bedrock": a build as a .mcstructure file. In Bedrock, put it in the world's (or a behavior pack's)
// structures folder and load it with a structure block or /structure load.
// Block names come from bedrock-blocks.json (made by tools/bedrock/make_map.py from Minecraft's own block list and
// GeyserMC's open Java <-> Bedrock table, MIT).

let MAP = null;
export async function bedrockMap() {
  if (!MAP) MAP = await (await fetch('bedrock-blocks.json')).json();
  return MAP;
}

// Java blocks that got a new name
const RENAMED = { 'minecraft:chain': 'minecraft:iron_chain', 'minecraft:grass': 'minecraft:short_grass', 'minecraft:grass_path': 'minecraft:dirt_path' };
const BLOCK_VERSION = 18168832;   // 1.21.60 - newer games update older entries by themselves

/** Java state (id + "k=v,k=v" or "default") -> { name, states, water } or null when Bedrock has nothing for it. */
export function toBedrock(map, id, label) {
  let jid = String(id).includes(':') ? String(id) : 'minecraft:' + id;
  if (!map[jid] && RENAMED[jid]) jid = RENAMED[jid];   // builds made in an older Minecraft
  const e = map[jid];
  if (!e) return null;
  const vals = e.d.split(',');
  const given = {};
  if (label && label !== 'default') for (const kv of String(label).split(',')) { const i = kv.indexOf('='); if (i > 0) given[kv.slice(0, i).trim()] = kv.slice(i + 1).trim(); }
  e.p.forEach((k, i) => { if (given[k] !== undefined) vals[i] = given[k]; });
  let m = e.m[vals.join(',')];
  if (!m) m = e.m[e.d];   // a value this Minecraft version doesn't have: use the block's normal state
  if (!m) return null;
  const wl = e.p.indexOf('waterlogged');
  return { name: 'minecraft:' + (m[0] || jid.slice(10)), states: m[1] || {}, water: wl >= 0 && vals[wl] === 'true' };
}

// ---- little-endian NBT (Bedrock's format)
class W {
  constructor() { this.b = new Uint8Array(1 << 16); this.n = 0; }
  need(k) { if (this.n + k > this.b.length) { const nb = new Uint8Array(Math.max(this.b.length * 2, this.n + k)); nb.set(this.b); this.b = nb; } }
  u8(v) { this.need(1); this.b[this.n++] = v & 0xff; }
  i16(v) { this.need(2); this.b[this.n++] = v & 0xff; this.b[this.n++] = (v >> 8) & 0xff; }
  i32(v) { this.need(4); for (let i = 0; i < 4; i++) this.b[this.n++] = (v >> (8 * i)) & 0xff; }
  str(s) { const t = new TextEncoder().encode(s); this.i16(t.length); this.need(t.length); this.b.set(t, this.n); this.n += t.length; }
  tag(type, name) { this.u8(type); this.str(name); }
  out() { return this.b.slice(0, this.n); }
}
const T = { BYTE: 1, INT: 3, STRING: 8, LIST: 9, COMPOUND: 10, END: 0 };

function writeStates(w, states) {
  for (const [k, v] of Object.entries(states)) {
    if (typeof v === 'boolean') { w.tag(T.BYTE, k); w.u8(v ? 1 : 0); }
    else if (typeof v === 'number') { w.tag(T.INT, k); w.i32(v); }
    else { w.tag(T.STRING, k); w.str(String(v)); }
  }
  w.u8(T.END);
}

/** build = { blocks: [[x, y, z, id, label], ...] } -> { bytes, missing: [java ids Bedrock doesn't have], size } */
export function buildToMcstructure(build, map) {
  const bs = build.blocks || [];
  if (!bs.length) throw new Error('This build is empty');
  const mn = [Infinity, Infinity, Infinity], mx = [-Infinity, -Infinity, -Infinity];
  for (const [x, y, z] of bs) { const p = [Math.round(x), Math.round(y), Math.round(z)]; for (let a = 0; a < 3; a++) { mn[a] = Math.min(mn[a], p[a]); mx[a] = Math.max(mx[a], p[a]); } }
  const sx = mx[0] - mn[0] + 1, sy = mx[1] - mn[1] + 1, sz = mx[2] - mn[2] + 1;
  if (sx * sy * sz > 64 * 1024 * 1024) throw new Error('That build is too big');
  const palette = [], key = new Map();
  const add = (name, states) => { const k = name + JSON.stringify(states); let i = key.get(k); if (i === undefined) { i = palette.length; key.set(k, i); palette.push({ name, states }); } return i; };
  const AIR = add('minecraft:air', {});
  let WATER = -1;
  const l0 = new Int32Array(sx * sy * sz).fill(AIR), l1 = new Int32Array(sx * sy * sz).fill(-1);
  const missing = new Set(), cache = new Map();
  for (const [x, y, z, id, label] of bs) {
    const ck = id + '|' + (label || '');
    let r = cache.get(ck);
    if (r === undefined) { r = toBedrock(map, id, label); cache.set(ck, r); if (!r) missing.add(String(id)); }
    if (!r) continue;
    const i = ((Math.round(x) - mn[0]) * sy + (Math.round(y) - mn[1])) * sz + (Math.round(z) - mn[2]);   // x, then y, then z
    l0[i] = add(r.name, r.states);
    if (r.water) { if (WATER < 0) WATER = add('minecraft:water', { liquid_depth: 0 }); l1[i] = WATER; }
  }
  const w = new W();
  w.tag(T.COMPOUND, '');
  w.tag(T.INT, 'format_version'); w.i32(1);
  w.tag(T.LIST, 'size'); w.u8(T.INT); w.i32(3); w.i32(sx); w.i32(sy); w.i32(sz);
  w.tag(T.COMPOUND, 'structure');
  w.tag(T.LIST, 'block_indices'); w.u8(T.LIST); w.i32(2);
  for (const layer of [l0, l1]) { w.u8(T.INT); w.i32(layer.length); for (let i = 0; i < layer.length; i++) w.i32(layer[i]); }
  w.tag(T.LIST, 'entities'); w.u8(T.COMPOUND); w.i32(0);
  w.tag(T.COMPOUND, 'palette'); w.tag(T.COMPOUND, 'default');
  w.tag(T.LIST, 'block_palette'); w.u8(T.COMPOUND); w.i32(palette.length);
  for (const p of palette) {
    w.tag(T.STRING, 'name'); w.str(p.name);
    w.tag(T.COMPOUND, 'states'); writeStates(w, p.states);
    w.tag(T.INT, 'version'); w.i32(BLOCK_VERSION);
    w.u8(T.END);
  }
  w.tag(T.COMPOUND, 'block_position_data'); w.u8(T.END);
  w.u8(T.END);   // default
  w.u8(T.END);   // palette
  w.u8(T.END);   // structure
  w.tag(T.LIST, 'structure_world_origin'); w.u8(T.INT); w.i32(3); w.i32(0); w.i32(0); w.i32(0);
  w.u8(T.END);
  return { bytes: w.out(), missing: [...missing], size: [sx, sy, sz] };
}

// ---- a tiny behavior pack (.mcpack = zip) holding the structure: double-click it and Bedrock imports it
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
function crc32(u8) { let c = 0xffffffff; for (let i = 0; i < u8.length; i++) c = CRC[(c ^ u8[i]) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
export function zip(files) {   // [{ name, data: Uint8Array }] -> stored (no compression) zip
  const parts = [], central = []; let off = 0;
  const le = (n, v) => { const b = new Uint8Array(n); for (let i = 0; i < n; i++) b[i] = (v >>> (8 * i)) & 0xff; return b; };
  for (const f of files) {
    const nm = new TextEncoder().encode(f.name), crc = crc32(f.data), sz = f.data.length;
    const head = [le(4, 0x04034b50), le(2, 20), le(2, 0x0800), le(2, 0), le(2, 0), le(2, 0x21), le(4, crc), le(4, sz), le(4, sz), le(2, nm.length), le(2, 0), nm];
    central.push([le(4, 0x02014b50), le(2, 20), le(2, 20), le(2, 0x0800), le(2, 0), le(2, 0), le(2, 0x21), le(4, crc), le(4, sz), le(4, sz), le(2, nm.length), le(2, 0), le(2, 0), le(2, 0), le(2, 0), le(4, 0), le(4, off), nm]);
    for (const h of head) { parts.push(h); off += h.length; }
    parts.push(f.data); off += sz;
  }
  const cstart = off; let clen = 0;
  for (const c of central) for (const h of c) { parts.push(h); clen += h.length; }
  parts.push(le(4, 0x06054b50), le(2, 0), le(2, 0), le(2, files.length), le(2, files.length), le(4, clen), le(4, cstart), le(2, 0));
  const out = new Uint8Array(parts.reduce((a, p) => a + p.length, 0)); let o = 0;
  for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
}
const uuid = () => (globalThis.crypto && crypto.randomUUID ? crypto.randomUUID() : 'xxxxxxxx-xxxx-4xxx-8xxx-xxxxxxxxxxxx'.replace(/x/g, () => (Math.random() * 16 | 0).toString(16)));

/** The build as a behavior pack. In the world it's "blueprint:<id>": /structure load blueprint:<id> ~ ~ ~ */
export function buildToMcpack(build, map, title, icon) {
  const r = buildToMcstructure(build, map);
  const id = String(title || 'build').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '').slice(0, 40) || 'build';
  const manifest = {
    format_version: 2,
    header: { name: 'Block Blueprint: ' + String(title || 'Build').slice(0, 40), description: `Load it with: /structure load blueprint:${id} ~ ~ ~`, uuid: uuid(), version: [1, 0, 0], min_engine_version: [1, 21, 60] },
    modules: [{ type: 'data', uuid: uuid(), version: [1, 0, 0] }],
  };
  const files = [{ name: 'manifest.json', data: new TextEncoder().encode(JSON.stringify(manifest, null, 2)) },
                 { name: `structures/blueprint/${id}.mcstructure`, data: r.bytes }];
  if (icon) files.push({ name: 'pack_icon.png', data: icon });
  return { ...r, id, bytes: zip(files) };
}
