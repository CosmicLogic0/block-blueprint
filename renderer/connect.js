// Neighbour-dependent block states the game normally works out itself:
// fences / walls / panes / bars connect sideways, fence gates drop into walls, wall posts appear.
// at(x,y,z) -> { name, label, r } | undefined      solid(name) -> boolean
const SIDES = [['north', 0, -1], ['east', 1, 0], ['south', 0, 1], ['west', -1, 0]];
const ROT = ['north', 'east', 'south', 'west'];

export const isConnecting = (n) => /(_fence|_wall|_pane)$|^iron_bars$|^redstone_wire$/.test(n);
export const isGate = (n) => /_fence_gate$/.test(n);

// facing of a placed block after its quarter-turn rotation
function facingOf(b) {
  const m = /facing=(north|east|south|west)/.exec(b.label || '');
  if (!m) return null;
  return ROT[(ROT.indexOf(m[1]) + (b.r || 0)) % 4];
}
// a gate placed facing north/south runs east-west, so it only joins things on its east/west sides
function gateJoins(b, dir) {
  const f = facingOf(b);
  if (!f) return true;
  return (f === 'north' || f === 'south') === (dir === 'east' || dir === 'west');
}

// things redstone dust visibly connects to (repeaters / comparators / observers only along their facing line)
const DUST = /^(redstone_wire|redstone_torch|redstone_wall_torch|lever|redstone_block|daylight_detector|target|trapped_chest|lectern|tripwire_hook|sculk_sensor|calibrated_sculk_sensor|detector_rail)$|_button$|_pressure_plate$/;
function dustJoins(nb, d) {
  if (DUST.test(nb.name)) return true;
  if (/^(repeater|comparator|observer)$/.test(nb.name)) {
    const f = facingOf(nb); if (!f) return false;
    return (f === 'north' || f === 'south') === (d === 'north' || d === 'south');
  }
  return false;
}
function wireProps(x, y, z, at, solid) {
  const on = {};
  for (const [d, dx, dz] of SIDES) {
    const nb = at(x + dx, y, z + dz);
    let c = !!nb && dustJoins(nb, d);
    // dust running up or down a block step
    if (!c) { const up = at(x + dx, y + 1, z + dz); if (up && up.name === 'redstone_wire' && !(at(x, y + 1, z) && solid(at(x, y + 1, z).name))) c = 'up'; }
    if (!c && !(nb && solid(nb.name))) { const dn = at(x + dx, y - 1, z + dz); if (dn && dn.name === 'redstone_wire') c = true; }
    on[d] = c;
  }
  const n = SIDES.filter(([d]) => on[d]).length;
  if (n === 0) { for (const [d] of SIDES) on[d] = true; } // lone dust shows as a cross, like in game
  if (n === 1) { // a single connection draws a straight line through the block
    const opp = { north: 'south', south: 'north', east: 'west', west: 'east' };
    for (const [d] of SIDES) if (on[d] && !on[opp[d]]) on[opp[d]] = true;
  }
  const p = {};
  for (const [d] of SIDES) p[d] = on[d] === 'up' ? 'up' : on[d] ? 'side' : 'none';
  p.power = '0';
  return p;
}

// ---- stairs: Minecraft's own corner rules (StairBlock.getStairsShape)
export const isStairs = (n) => /_stairs$/.test(n);
const VEC = { north: [0, -1], east: [1, 0], south: [0, 1], west: [-1, 0] };
const CCW = { north: 'west', west: 'south', south: 'east', east: 'north' };
const halfOf = (b) => (/half=top/.test(b.label || '') ? 'top' : 'bottom');
function stairShape(x, y, z, at) {
  const me = at(x, y, z); if (!me) return null;
  const F = facingOf(me); if (!F) return null;
  const H = halfOf(me);
  const st = (dx, dz) => { const n = at(x + dx, y, z + dz); return n && isStairs(n.name) && halfOf(n) === H ? n : null; };
  const axis = (d) => (d === 'north' || d === 'south' ? 'z' : 'x');
  const canTake = (dir) => { const n = st(VEC[dir][0], VEC[dir][1]); return !n || facingOf(n) !== F; };
  const front = st(VEC[F][0], VEC[F][1]);
  if (front) { const d1 = facingOf(front); if (d1 && axis(d1) !== axis(F) && canTake({ north: 'south', south: 'north', east: 'west', west: 'east' }[d1])) return d1 === CCW[F] ? 'outer_left' : 'outer_right'; }
  const back = st(-VEC[F][0], -VEC[F][1]);
  if (back) { const d2 = facingOf(back); if (d2 && axis(d2) !== axis(F) && canTake(d2)) return d2 === CCW[F] ? 'inner_left' : 'inner_right'; }
  return 'straight';
}

export function autoProps(name, x, y, z, at, solid) {
  if (name === 'redstone_wire') return wireProps(x, y, z, at, solid);
  if (isStairs(name)) { const sh = stairShape(x, y, z, at); return sh ? { shape: sh } : null; }
  if (isGate(name)) {
    const me = at(x, y, z), f = me && facingOf(me);
    if (!f) return null;
    const side = f === 'north' || f === 'south' ? [[1, 0], [-1, 0]] : [[0, 1], [0, -1]];
    const inWall = side.some(([dx, dz]) => { const nb = at(x + dx, y, z + dz); return nb && /_wall$/.test(nb.name); });
    return { in_wall: String(inWall) };
  }
  const fence = /_fence$/.test(name), pane = /_pane$|^iron_bars$/.test(name), wall = /_wall$/.test(name);
  if (!fence && !pane && !wall) return null;
  const p = {}, on = {};
  for (const [d, dx, dz] of SIDES) {
    const nb = at(x + dx, y, z + dz);
    let c = false;
    if (nb) {
      const n = nb.name;
      if (fence) c = (/_fence$/.test(n) && n.includes('nether_brick') === name.includes('nether_brick')) || (isGate(n) && gateJoins(nb, d)) || solid(n);
      else if (pane) c = /_pane$|^iron_bars$|_wall$|glass$/.test(n) || solid(n);
      else c = /_wall$|_pane$|^iron_bars$/.test(n) || (isGate(n) && gateJoins(nb, d)) || solid(n);
    }
    on[d] = c;
    p[d] = wall ? (c ? 'low' : 'none') : String(c);
  }
  if (wall) {
    const straight = (on.north && on.south && !on.east && !on.west) || (on.east && on.west && !on.north && !on.south);
    p.up = String(!straight || !!at(x, y + 1, z));
  }
  return p;
}
export const propsToLabel = (p) => Object.entries(p).map(([k, v]) => `${k}=${v}`).join(',');
