// Timber-frame house generator (medieval / fantasy, built with modern detailing techniques):
// gradient stone foundation, framed walls with weathered infill, jettied upper floors, layered roof with trim,
// recessed windows with sills + shutters, chimney, furnished interior.
import { DIRS, OPP, noise3, hash3, grad } from './lib.mjs';
import { gableRoof, windowIn, chair, table, hangingLantern, plant, bookshelfWall, clearPlant } from './pieces.mjs';

export const PAL = {
  oak: { frame: 'dark_oak_log', beam: 'stripped_dark_oak_log', infill: ['white_terracotta', 'calcite', 'smooth_quartz', 'white_terracotta', 'diorite'], wood: 'spruce', floor: 'spruce_planks',
    roof: { stairs: 'dark_oak_stairs', slab: 'dark_oak_slab', full: 'dark_oak_planks', trim: 'spruce_stairs', gable: 'stripped_spruce_log' } },
  red: { frame: 'stripped_spruce_log', beam: 'spruce_log', infill: ['calcite', 'white_terracotta', 'smooth_sandstone', 'calcite', 'diorite'], wood: 'spruce', floor: 'spruce_planks',
    roof: { stairs: 'mangrove_stairs', slab: 'mangrove_slab', full: 'mangrove_planks', trim: 'red_nether_brick_stairs', gable: 'spruce_planks' } },
  stone: { frame: 'spruce_log', beam: 'stripped_spruce_log', infill: ['stone_bricks', 'andesite', 'cobblestone', 'stone', 'mossy_cobblestone'], wood: 'spruce', floor: 'spruce_planks',
    roof: { stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab', full: 'deepslate_tiles', trim: 'cobbled_deepslate_stairs', gable: 'spruce_planks' } },
  birch: { frame: 'stripped_birch_log', beam: 'birch_log', infill: ['white_concrete', 'calcite', 'white_terracotta', 'smooth_quartz', 'calcite'], wood: 'birch', floor: 'birch_planks',
    roof: { stairs: 'prismarine_brick_stairs', slab: 'prismarine_brick_slab', full: 'prismarine_bricks', trim: 'dark_prismarine_stairs', gable: 'birch_planks' } },
  dark: { frame: 'stripped_dark_oak_log', beam: 'dark_oak_log', infill: ['calcite', 'white_terracotta', 'packed_mud', 'mud_bricks', 'bricks'], wood: 'dark_oak', floor: 'dark_oak_planks',
    roof: { stairs: 'spruce_stairs', slab: 'spruce_slab', full: 'spruce_planks', trim: 'dark_oak_stairs', gable: 'stripped_dark_oak_log' } },
};
const FOUND = ['stone_bricks', 'andesite', 'cobblestone', 'cobblestone', 'mossy_cobblestone', 'mossy_stone_bricks'];

/**
 * o: { x0, z0, w, d, floors=2, pal, jetty=true, chimney='east'|'west'|null, door='south', seed, interior=true, roofExtra=0 }
 * returns { top, rect } where rect is the top-floor footprint.
 */
export function timberHouse(b, o) {
  const P = typeof o.pal === 'string' ? PAL[o.pal] : o.pal;
  const { x0, z0, w, d, floors = 2, jetty = true, seed = 1, door = 'south', interior = true } = o;
  const x1 = x0 + w - 1, z1 = z0 + d - 1;
  const r = b.r;
  // foundation: 1 high, a little wider, gradient (mossy toward the ground and corners)
  for (let x = x0 - 1; x <= x1 + 1; x++) for (let z = z0 - 1; z <= z1 + 1; z++) {
    const edge = x < x0 || x > x1 || z < z0 || z > z1;
    clearPlant(b, x, 1, z);
    if (edge) {
      const t = noise3(x, 0, z, 2, seed) * 0.8 + (x === x0 - 1 || x === x1 + 1) * 0.1;
      b.set(x, 0, z, grad(FOUND, t));
      if (hash3(x, 1, z, seed) < 0.18) b.slab(x, 1, z, hash3(x, 2, z, seed) < 0.5 ? 'cobblestone_slab' : 'mossy_cobblestone_slab');
    } else b.set(x, 0, z, P.floor);
  }
  let rect = [x0, z0, x1, z1];
  let yb = 1;
  for (let f = 0; f < floors; f++) {
    const grow = jetty && f > 0 ? 1 : 0;
    const [a0, c0, a1, c1] = [x0 - 0, z0 - grow * f, x1 + 0, z1 + grow * f];
    rect = [a0, c0, a1, c1];
    const H = f === 0 ? 4 : 4;
    // floor slab of this level (upper floors)
    if (f > 0) for (let x = a0; x <= a1; x++) for (let z = c0; z <= c1; z++) {
      const edge = x === a0 || x === a1 || z === c0 || z === c1;
      b.set(x, yb - 1, z, edge ? P.beam : P.floor, edge ? { axis: x === a0 || x === a1 ? 'z' : 'x' } : undefined);
      if (edge && (x === a0 || x === a1) && (z === c0 || z === c1)) b.log(x, yb - 1, z, P.frame);
    }
    // jetty brackets under the overhang
    if (grow) for (let x = a0; x <= a1; x += 3) for (const [z, fz] of [[c0, 'south'], [c1, 'north']]) b.stairs(x, yb - 2, z, `${P.wood}_stairs`, fz, 'top');
    for (let y = yb; y < yb + H - 1; y++) {
      for (let x = a0; x <= a1; x++) for (let z = c0; z <= c1; z++) {
        const edge = x === a0 || x === a1 || z === c0 || z === c1;
        if (!edge) { b.del(x, y, z); continue; }
        const corner = (x === a0 || x === a1) && (z === c0 || z === c1);
        const along = z === c0 || z === c1 ? x - a0 : z - c0;
        const post = corner || along % 4 === 0;
        if (post) b.log(x, y, z, P.frame);
        else {
          // weathered infill: darker low, lighter high, noisy clusters
          const t = Math.min(0.99, Math.max(0, (1 - (y - yb) / (H - 1)) * 0.55 + noise3(x, y, z, 2, seed + f) * 0.55 - 0.15));
          b.set(x, y, z, grad(P.infill, t));
        }
      }
    }
    // top beam
    const yt = yb + H - 1;
    for (let x = a0; x <= a1; x++) for (const z of [c0, c1]) b.log(x, yt, z, P.beam, 'x');
    for (let z = c0; z <= c1; z++) for (const x of [a0, a1]) b.log(x, yt, z, P.beam, 'z');
    for (const [x, z] of [[a0, c0], [a1, c0], [a0, c1], [a1, c1]]) b.log(x, yt, z, P.frame);
    // diagonal braces on upper floors (stairs) next to corners
    if (f > 0) for (const z of [c0, c1]) { const fz = z === c0 ? 'north' : 'south'; b.stairs(a0 + 1, yb, z, `${P.wood}_stairs`, 'west', 'top'); b.stairs(a1 - 1, yb, z, `${P.wood}_stairs`, 'east', 'top'); void fz; }
    // windows between posts
    for (const [side, z] of [['north', c0], ['south', c1]]) {
      for (let x = a0 + 1, bay = 0; x + 1 < a1; x += 4, bay++) {
        if (f === 0 && side === door && Math.abs(x + 1 - (a0 + a1) / 2) < 2) continue;
        if (w > 10 && (bay + f) % 2 === 1) continue; // big walls: alternate bays so the framing shows
        windowIn(b, x + 1, yb + 1, z, 2, 1, side, { sill: `${P.wood}_trapdoor`, frame: `${P.wood}_stairs`, shutters: f === 0 ? `${P.wood}_trapdoor` : null });
      }
    }
    for (const [side, x] of [['west', a0], ['east', a1]]) {
      for (let z = c0 + 1; z + 1 < c1; z += 4) windowIn(b, x, yb + 1, z + 1, 2, 1, side, { sill: `${P.wood}_trapdoor`, frame: `${P.wood}_stairs` });
    }
    if (f === 0) {
      // front door with awning and lanterns
      const dx = Math.floor((a0 + a1) / 2), dz = door === 'south' ? c1 : c0;
      b.door(dx, yb, dz, `${P.wood}_door`, door, 'left');
      b.set(dx, yb + 2, dz, P.beam, { axis: 'x' });
      const [ox, oz] = DIRS[door];
      for (const ax of [dx - 1, dx, dx + 1]) b.stairs(ax, yb + 2, dz + oz, P.roof.stairs, OPP[door]);
      b.stairs(dx - 1, yb + 1, dz + oz, `${P.wood}_stairs`, OPP[door], 'top');
      b.stairs(dx + 1, yb + 1, dz + oz, `${P.wood}_stairs`, OPP[door], 'top');
      b.set(dx - 2, yb + 1, dz + oz, 'wall_torch', { facing: door });
      b.set(dx, yb - 1, dz + oz, 'spruce_slab', { type: 'top' });
      b.del(dx, yb, dz + oz);
      for (let k = 2; k < 6; k++) clearPlant(b, dx, yb, dz + oz * k);
      void ox;
    }
    yb += H;
  }
  // roof on the top floor footprint
  const [a0, c0, a1, c1] = rect;
  const top = yb - 1;
  const mat = { ...P.roof, mix: o.roofMix || null, gablePost: P.frame, gableBeam: P.beam };
  const half = gableRoof(b, a0, c0, a1, c1, top, 'x', mat, { overhang: 1 });
  // attic floor already = top beams; fill attic floor
  for (let x = a0 + 1; x < a1; x++) for (let z = c0 + 1; z < c1; z++) b.set(x, top - 0, z, P.floor);
  // ridge decoration: end caps
  // chimney
  if (o.chimney) {
    const cx = o.chimney === 'east' ? a1 - 2 : a0 + 2, cz = c0 + 1;
    for (let y = 1; y <= top + half + 2; y++) for (const [ex, ez] of [[0, 0], [1, 0]]) {
      const n = noise3(cx + ex, y, cz + ez, 2, seed + 5);
      b.set(cx + ex, y, cz + ez, y > top ? (n < 0.4 ? 'bricks' : n < 0.75 ? 'mud_bricks' : 'stone_bricks') : 'bricks');
    }
    b.set(cx, top + half + 3, cz, 'cobblestone_wall'); b.set(cx + 1, top + half + 3, cz, 'cobblestone_wall');
    b.set(cx, 1, cz + 1, 'campfire', { facing: 'south', lit: 'true' });
  }
  // moss / vines on the lower roof edge
  if (o.vines !== false) for (let x = a0 - 1; x <= a1 + 1; x++) for (const z of [c0 - 1, c1 + 1]) if (hash3(x, 9, z, seed) < 0.25) b.soft(x, top - 1, z, 'oak_leaves');

  if (interior) {
    const mx = Math.floor((x0 + x1) / 2);
    // ground floor: kitchen + table
    table(b, mx, 1, z0 + 2);
    chair(b, mx - 1, 1, z0 + 2, 'east', P.wood); chair(b, mx + 1, 1, z0 + 2, 'west', P.wood);
    b.set(x0 + 1, 1, z0 + 1, 'smoker', { facing: 'south' }); b.set(x0 + 2, 1, z0 + 1, 'crafting_table');
    b.set(x0 + 1, 1, z0 + 2, 'barrel', { facing: 'up' });
    b.set(x1 - 1, 1, z0 + 1, 'chest', { facing: 'south', type: 'single' });
    hangingLantern(b, mx, 3, z0 + 2, 1);
    // ladder up
    if (floors > 1) for (let y = 1; y <= 4; y++) { b.set(x1 - 1, y, z1 - 1, 'ladder', { facing: 'west' }); }
    if (floors > 1) b.del(x1 - 1, 4, z1 - 1);
    if (floors > 1) b.set(x1 - 1, 4, z1 - 1, 'ladder', { facing: 'west' });
    // upper floor: bedroom
    if (floors > 1) {
      b.bed(x0 + 1, 5, z0 + 1, o.bed || 'red', 'south');
      b.set(x0 + 2, 5, z0 + 1, `${P.wood}_trapdoor`, { facing: 'north', half: 'top', open: 'false' });
      plant(b, x0 + 2, 6, z0 + 1, r);
      bookshelfWall(b, x0 + 3, 5, z0 + 1, Math.min(3, w - 6), 2, 'x');
      b.set(x0 + 1, 5, z1 - 1, 'brown_carpet');
      hangingLantern(b, mx, 7, Math.floor((z0 + z1) / 2), 0);
    }
  }
  return { top, half, rect };
}
