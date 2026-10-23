// Export a build as short, pasteable "/buildblueprint load <id>.<part>.<total> <data>" commands for the Build Blueprint Fabric mod.
//
// Wire format (must match the mod's BlueprintCodec.java):
//   text    = base64url( crc32(deflated) as 4 bytes big-endian  ||  deflated )   -- split into chunks
//   deflated= raw DEFLATE of:
//     u8 version(1) | varint sx sy sz | str name | varint paletteCount | str[] palette
//     | RLE cells in order y, z, x:  (varint runLength, varint value)  value 0 = air, n = palette[n-1]
//   str     = varint byteLength + UTF-8, varint = unsigned LEB128
import { decode } from './bake.js';
import { autoProps, propsToLabel, isConnecting } from './connect.js';

const DIRS = ['north', 'east', 'south', 'west'];

// ---- block-state rotation by quarter turns (clockwise seen from above) ----
function rotDir(v, r) { const i = DIRS.indexOf(v); return i < 0 ? v : DIRS[(i + r) % 4]; }
function railShape(v, r) {
  const m = /^(north_south|east_west|ascending_(north|east|south|west)|north_east|south_east|south_west|north_west)$/.exec(v);
  if (!m) return v;
  if (v.startsWith('ascending_')) return 'ascending_' + rotDir(v.slice(10), r);
  const words = v.split('_').map((w) => rotDir(w, r));
  const set = new Set(words);
  const has = (a, b) => set.has(a) && set.has(b);
  if (has('north', 'south')) return 'north_south';
  if (has('east', 'west')) return 'east_west';
  if (has('north', 'east')) return 'north_east';
  if (has('south', 'east')) return 'south_east';
  if (has('south', 'west')) return 'south_west';
  return 'north_west';
}
export function rotateProps(props, r) {
  r = ((r % 4) + 4) % 4;
  if (!props || !r) return props || '';
  const kv = props.split(',').filter(Boolean).map((p) => { const i = p.indexOf('='); return [p.slice(0, i), p.slice(i + 1)]; });
  const sides = {};
  for (const [k, v] of kv) if (DIRS.includes(k)) sides[k] = v;
  const out = kv.map(([k, v]) => {
    if (k === 'facing') return [k, rotDir(v, r)];
    if (k === 'axis') return [k, r % 2 && v !== 'y' ? (v === 'x' ? 'z' : 'x') : v];
    if (k === 'rotation') return [k, String((parseInt(v, 10) + 4 * r) % 16)];
    if (k === 'shape' && /north|east|south|west|ascending/.test(v)) return [k, railShape(v, r)];
    if (DIRS.includes(k)) return [k, sides[DIRS[(DIRS.indexOf(k) - r + 4) % 4]]];
    return [k, v];
  });
  return out.map(([k, v]) => `${k}=${v}`).join(',');
}

// ---- byte helpers ----
function varint(out, n) { while (n >= 0x80) { out.push((n & 0x7f) | 0x80); n = Math.floor(n / 128); } out.push(n); }
function str(out, s) { const b = new TextEncoder().encode(s); varint(out, b.length); for (const x of b) out.push(x); }
const CRC_T = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
export function crc32(u8) { let c = 0xffffffff; for (let i = 0; i < u8.length; i++) c = CRC_T[(c ^ u8[i]) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
async function deflateRaw(u8) {
  const cs = new CompressionStream('deflate-raw'); const w = cs.writable.getWriter(); w.write(u8); w.close();
  return new Uint8Array(await new Response(cs.readable).arrayBuffer());
}
function b64url(u8) { let s = ''; for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000)); return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); }

export const MAX_LINE = 250; // Minecraft's chat box holds 256 characters

// world: Map<key,{b,v,r}>.  Returns {lines, size:[x,y,z], blocks, bytes, id} or null when empty.
export async function encodeBlueprint(world, baker, name) {
  if (!world.size) return null;
  let min = [1e9, 1e9, 1e9], max = [-1e9, -1e9, -1e9];
  const cells = [];
  for (const [k, b] of world) { const p = decode(k); cells.push([p, b]); min = [Math.min(min[0], p.x), Math.min(min[1], p.y), Math.min(min[2], p.z)]; max = [Math.max(max[0], p.x), Math.max(max[1], p.y), Math.max(max[2], p.z)]; }
  const sx = max[0] - min[0] + 1, sy = max[1] - min[1] + 1, sz = max[2] - min[2] + 1;
  const cellMap = new Map(cells.map(([p, b]) => [`${p.x},${p.y},${p.z}`, b]));
  const at = (x, y, z) => { const n = cellMap.get(`${x},${y},${z}`); return n && { name: n.b, r: n.r, label: baker.variantLabel(n.b, n.v) }; };
  const solidCache = new Map();
  const solid = (n) => { if (!solidCache.has(n)) { const bk = baker.catalog.blocks[n] ? baker.bake(n, 0, 0) : null; solidCache.set(n, !!(bk && bk.opaque && bk.cube)); } return solidCache.get(n); };

  const palette = [], pidx = new Map();
  const grid = new Uint32Array(sx * sy * sz);
  for (const [p, b] of cells) {
    const label = baker.variantLabel(b.b, b.v);
    let props = rotateProps(label === 'default' ? '' : label, b.r);
    const auto = autoProps(b.b, p.x, p.y, p.z, at, solid);
    if (auto) {
      if (isConnecting(b.b)) props = propsToLabel(auto);
      else { const m = Object.fromEntries(props.split(',').filter(Boolean).map((q) => q.split('='))); props = propsToLabel(Object.assign(m, auto)); }
    }
    const state = `minecraft:${b.b}${props ? `[${props}]` : ''}`;
    let i = pidx.get(state);
    if (i === undefined) { i = palette.length; palette.push(state); pidx.set(state, i); }
    grid[((p.y - min[1]) * sz + (p.z - min[2])) * sx + (p.x - min[0])] = i + 1;
  }

  const out = [1];
  varint(out, sx); varint(out, sy); varint(out, sz);
  str(out, (name || 'Blueprint').slice(0, 40));
  varint(out, palette.length); palette.forEach((s) => str(out, s));
  for (let i = 0; i < grid.length;) { let j = i; while (j < grid.length && grid[j] === grid[i]) j++; varint(out, j - i); varint(out, grid[i]); i = j; }

  const deflated = await deflateRaw(Uint8Array.from(out));
  const crc = crc32(deflated);
  const packed = new Uint8Array(4 + deflated.length);
  packed.set([crc >>> 24, (crc >>> 16) & 255, (crc >>> 8) & 255, crc & 255]); packed.set(deflated, 4);
  const text = b64url(packed);

  const id = crc.toString(16).padStart(8, '0').slice(0, 5);
  let per = 210, lines;
  for (;;) {
    const n = Math.ceil(text.length / per);
    lines = [];
    for (let i = 0; i < n; i++) lines.push(`/buildblueprint load ${id}.${i + 1}.${n} ${text.slice(i * per, (i + 1) * per)}`);
    if (lines.every((l) => l.length <= MAX_LINE) || per < 50) break;
    per -= 5;
  }
  return { lines, text, size: [sx, sy, sz], blocks: cells.length, types: palette.length, bytes: packed.length, id };
}
