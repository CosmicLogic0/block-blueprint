// Steady-state redstone preview: works out which dust is powered (and how strongly), which lamps are lit,
// which torches burn out, which repeaters/comparators pass a signal, and extends powered pistons (pushing blocks).
// Returns a display copy of the world; the real world (and the export) are never changed.
import { encode, decode } from './bake.js';

const ROT = ['north', 'east', 'south', 'west'];
const V = { north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };
const OPP = { north: 'south', south: 'north', east: 'west', west: 'east', up: 'down', down: 'up' };
const SIDES = ['north', 'east', 'south', 'west'];
const ALL = ['north', 'east', 'south', 'west', 'up', 'down'];
const IMMOVABLE = /^(obsidian|crying_obsidian|bedrock|barrier|end_portal_frame|reinforced_deepslate|enchanting_table|ender_chest|respawn_anchor|beacon|spawner|trial_spawner|vault|.*_chest|chest|furnace|blast_furnace|smoker|barrel|hopper|dropper|dispenser|jukebox|lectern|.*_sign|.*_banner|.*_bed|.*_shulker_box|shulker_box|piston_head|moving_piston|command_block)$/;
const REDSTONE = /redstone|piston|repeater|comparator|lever|_button|lamp|daylight|observer|target|torch/;

const parse = (label) => (!label || label === 'default' ? {} : Object.fromEntries(label.split(',').map((q) => q.split('='))));
const rotDir = (d, r) => (ROT.includes(d) ? ROT[(ROT.indexOf(d) + (r || 0)) % 4] : d);

export function hasRedstone(world) { for (const b of world.values()) if (REDSTONE.test(b.b)) return true; return false; }

export function simulate(world, baker) {
  if (!hasRedstone(world)) return world;
  const cat = baker.catalog.blocks;
  const cells = new Map(); // key -> { name, p (world-space props), x,y,z, blk }
  for (const [k, blk] of world) {
    const { x, y, z } = decode(k);
    const p = parse(baker.variantLabel(blk.b, blk.v));
    if (p.facing) p.facing = rotDir(p.facing, blk.r);
    cells.set(k, { name: blk.b, p, x, y, z, blk });
  }
  const get = (x, y, z) => cells.get(encode(x, y, z));
  const step = (c, d, n = 1) => [c.x + V[d][0] * n, c.y + V[d][1] * n, c.z + V[d][2] * n];
  const solidCache = new Map();
  const solid = (c) => {
    if (!c) return false;
    if (!solidCache.has(c.name)) { const bk = cat[c.name] ? baker.bake(c.name, 0, 0) : null; solidCache.set(c.name, !!(bk && bk.opaque && bk.cube) && !REDSTONE.test(c.name)); }
    return solidCache.get(c.name);
  };
  const isDust = (c) => c && c.name === 'redstone_wire';
  const isTorch = (c) => c && (c.name === 'redstone_torch' || c.name === 'redstone_wall_torch');
  const isDiode = (c) => c && (c.name === 'repeater' || c.name === 'comparator');
  const attachedDir = (c) => {
    if (c.name === 'redstone_wall_torch') return OPP[c.p.facing];
    if (c.name === 'redstone_torch') return 'down';
    if (c.name === 'lever' || /_button$/.test(c.name)) return c.p.face === 'floor' ? 'down' : c.p.face === 'ceiling' ? 'up' : OPP[c.p.facing];
    return null;
  };
  // which sides a dust cell points into (for powering blocks / components)
  const dustSides = (c) => {
    const on = [];
    for (const d of SIDES) {
      const [x, y, z] = step(c, d); const n = get(x, y, z);
      const joins = n && (isDust(n) || isTorch(n) || n.name === 'lever' || /_button$|_pressure_plate$/.test(n.name) || n.name === 'redstone_block' || n.name === 'daylight_detector' || n.name === 'target' || n.name === 'observer' ||
        (isDiode(n) && (n.p.facing === d || n.p.facing === OPP[d])));
      const stepUp = !joins && isDust(get(x, y + 1, z)) && !solid(get(c.x, c.y + 1, c.z));
      const stepDn = !joins && !solid(n) && isDust(get(x, y - 1, z));
      if (joins || stepUp || stepDn) on.push(d);
    }
    if (on.length === 0) return SIDES.slice();         // lone dust: a cross, powers all four sides
    if (on.length === 1) on.push(OPP[on[0]]);          // a line points through
    return on;
  };

  const torchOn = new Map(), diodeOn = new Map();
  for (const c of cells.values()) { if (isTorch(c)) torchOn.set(c, true); if (isDiode(c)) diodeOn.set(c, false); }
  let dust = new Map();
  let strong = new Set(), weak = new Set(), adj = new Map(); // adj: key -> level of direct power into a component/dust cell

  for (let iter = 0; iter < 24; iter++) {
    strong = new Set(); weak = new Set(); adj = new Map();
    const feed = (x, y, z, lvl = 15) => { const k = encode(x, y, z); if ((adj.get(k) || 0) < lvl) adj.set(k, lvl); };
    for (const c of cells.values()) {
      const n = c.name;
      const src = n === 'redstone_block' || (n === 'daylight_detector' && c.p.inverted !== 'true') || ((n === 'lever' || /_button$/.test(n)) && c.p.powered === 'true') || (isTorch(c) && torchOn.get(c));
      if (src) {
        const att = attachedDir(c);
        for (const d of ALL) if (d !== att || n === 'lever' || /_button$/.test(n)) feed(...step(c, d));
        if (n === 'lever' || /_button$/.test(n)) strong.add(encode(...step(c, att)));
        if (isTorch(c)) strong.add(encode(c.x, c.y + 1, c.z));
      }
      if (isDiode(c) && diodeOn.get(c)) { const out = step(c, OPP[c.p.facing]); feed(...out); strong.add(encode(...out)); }
    }
    for (const k of strong) { const c = cells.get(k); if (!c || !solid(c)) { strong.delete(k); continue; } for (const d of ALL) feed(...step(c, d)); }
    // dust levels: 15 next to a source / strongly powered block, then -1 per block along connections
    const nd = new Map(); const queue = [];
    for (const c of cells.values()) if (isDust(c)) { const l = adj.get(encode(c.x, c.y, c.z)) || 0; if (l > 0) { nd.set(c, l); queue.push(c); } }
    while (queue.length) {
      const c = queue.shift(), l = nd.get(c);
      if (l <= 1) continue;
      for (const d of SIDES) {
        const [x, y, z] = step(c, d);
        for (const n of [get(x, y, z), !solid(get(c.x, c.y + 1, c.z)) ? get(x, y + 1, z) : null, !solid(get(x, y, z)) ? get(x, y - 1, z) : null]) {
          if (isDust(n) && (nd.get(n) || 0) < l - 1) { nd.set(n, l - 1); queue.push(n); }
        }
      }
    }
    dust = nd;
    // dust powers the block below it and the blocks / components it points into
    for (const [c, l] of dust) {
      const below = get(c.x, c.y - 1, c.z); if (solid(below)) weak.add(encode(c.x, c.y - 1, c.z)); else feed(c.x, c.y - 1, c.z, l);
      for (const d of dustSides(c)) { const [x, y, z] = step(c, d); const n = get(x, y, z); if (solid(n)) weak.add(encode(x, y, z)); else if (n && !isDust(n)) feed(x, y, z, l); }
    }
    for (const k of weak) if (strong.has(k)) weak.delete(k);
    const poweredBlock = (x, y, z) => { const k = encode(x, y, z); return strong.has(k) || weak.has(k); };
    // next torch / diode states
    let changed = false;
    for (const c of cells.values()) {
      if (isTorch(c)) {
        const [x, y, z] = step(c, attachedDir(c));
        const on = !poweredBlock(x, y, z);
        if (on !== torchOn.get(c)) { torchOn.set(c, on); changed = true; }
      } else if (isDiode(c)) {
        const back = step(c, c.p.facing), bk = encode(...back), bc = cells.get(bk);
        let on = poweredBlock(...back) || (bc && bc.name === 'redstone_block') || (isDust(bc) && (dust.get(bc) || 0) > 0)
          || (bc && isTorch(bc) && torchOn.get(bc)) || (bc && (bc.name === 'lever' || /_button$/.test(bc.name)) && bc.p.powered === 'true')
          || (isDiode(bc) && diodeOn.get(bc) && bc.p.facing === c.p.facing) || (bc && bc.name === 'daylight_detector' && bc.p.inverted !== 'true');
        on = !!on;
        if (on !== diodeOn.get(c)) { diodeOn.set(c, on); changed = true; }
      }
    }
    if (!changed && iter > 0) break;
  }
  const poweredBlock = (x, y, z) => { const k = encode(x, y, z); return strong.has(k) || weak.has(k); };
  const componentPowered = (c, except) => {
    if ((adj.get(encode(c.x, c.y, c.z)) || 0) > 0) return true;
    for (const d of ALL) { if (d === except) continue; const [x, y, z] = step(c, d); if (poweredBlock(x, y, z)) return true; const n = get(x, y, z); if (n && n.name === 'redstone_block') return true; }
    return false;
  };

  // ---- build the display world
  const out = new Map(world);
  const withProps = (blk, change) => {
    const vs = cat[blk.b] && cat[blk.b].variants; if (!vs) return blk;
    const cur = parse(baker.variantLabel(blk.b, blk.v)), want = { ...cur, ...change };
    const i = vs.findIndex((va) => { const q = parse(va.label); return Object.keys(want).every((k) => q[k] === undefined || q[k] === want[k]) && Object.keys(change).every((k) => q[k] === change[k]); });
    return i < 0 ? blk : { ...blk, v: i };
  };
  const pistons = [];
  for (const c of cells.values()) {
    const k = encode(c.x, c.y, c.z);
    if (isDust(c)) out.set(k, { ...c.blk, pw: dust.get(c) || 0 });
    else if (c.name === 'redstone_lamp') out.set(k, withProps(c.blk, { lit: String(componentPowered(c)) }));
    else if (isTorch(c)) out.set(k, withProps(c.blk, { lit: String(!!torchOn.get(c)) }));
    else if (isDiode(c)) out.set(k, withProps(c.blk, { powered: String(!!diodeOn.get(c)) }));
    else if ((c.name === 'piston' || c.name === 'sticky_piston') && componentPowered(c, c.p.facing)) pistons.push(c);
  }
  // pistons: push up to 12 movable blocks one step, then put the head in front
  for (const c of pistons) {
    const f = c.p.facing, chain = [];
    let [x, y, z] = step(c, f), ok = true;
    while (out.has(encode(x, y, z))) {
      const b = out.get(encode(x, y, z));
      if (IMMOVABLE.test(b.b) || chain.length >= 12 || (b.b === 'piston' || b.b === 'sticky_piston') && /extended=true/.test(baker.variantLabel(b.b, b.v))) { ok = false; break; }
      chain.push([x, y, z, b]); x += V[f][0]; y += V[f][1]; z += V[f][2];
    }
    if (!ok || y < 0) continue;
    for (let i = chain.length - 1; i >= 0; i--) { const [a, b2, c2, blk] = chain[i]; out.delete(encode(a, b2, c2)); out.set(encode(a + V[f][0], b2 + V[f][1], c2 + V[f][2]), blk); }
    const k = encode(c.x, c.y, c.z);
    out.set(k, withProps(c.blk, { extended: 'true' }));
    const head = { b: 'piston_head', v: 0, r: c.blk.r };
    const label = parse(baker.variantLabel(c.blk.b, c.blk.v)).facing;
    if (cat.piston_head) out.set(encode(...step(c, f)), withProps(head, { facing: label, type: c.name === 'sticky_piston' ? 'sticky' : 'normal', short: 'false' }));
  }
  return out;
}

/** Use tool: flip a lever, press a button, open a door / trapdoor / gate, change a repeater delay, flip a comparator or daylight sensor. */
export function useBlock(name, label) {
  const p = parse(label);
  if (name === 'lever' || /_button$/.test(name)) return { powered: p.powered === 'true' ? 'false' : 'true' };
  if (/_door$|_trapdoor$|_fence_gate$/.test(name) && 'open' in p) return { open: p.open === 'true' ? 'false' : 'true' };
  if (name === 'repeater') return { delay: String((+(p.delay || 1)) % 4 + 1) };
  if (name === 'comparator') return { mode: p.mode === 'compare' ? 'subtract' : 'compare' };
  if (name === 'daylight_detector') return { inverted: p.inverted === 'true' ? 'false' : 'true' };
  return null;
}
