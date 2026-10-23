// Modern / city kit: stacked volumes, framed glass curtain walls, wood cladding, flat roofs with lips, pools, planters.
import { DIRS, OPP, hash3, noise3 } from './lib.mjs';

export const MOD = {
  white: { wall: 'white_concrete', wall2: 'smooth_quartz', frame: 'gray_concrete', dark: 'black_concrete', wood: 'stripped_dark_oak_log', woodp: 'dark_oak', lip: 'smooth_quartz_slab', floor: 'smooth_quartz', glass: 'glass', pane: 'glass_pane', light: 'sea_lantern' },
  concrete: { wall: 'light_gray_concrete', wall2: 'polished_andesite', frame: 'black_concrete', dark: 'gray_concrete', wood: 'stripped_spruce_log', woodp: 'spruce', lip: 'polished_andesite_slab', floor: 'polished_andesite', glass: 'gray_stained_glass', pane: 'gray_stained_glass_pane', light: 'sea_lantern' },
  dark: { wall: 'black_concrete', wall2: 'gray_concrete', frame: 'black_concrete', dark: 'blackstone', wood: 'stripped_oak_log', woodp: 'oak', lip: 'polished_blackstone_slab', floor: 'polished_deepslate', glass: 'light_gray_stained_glass', pane: 'light_gray_stained_glass_pane', light: 'pearlescent_froglight' },
  warm: { wall: 'white_terracotta', wall2: 'smooth_sandstone', frame: 'stripped_spruce_log', dark: 'brown_terracotta', wood: 'stripped_spruce_log', woodp: 'spruce', lip: 'smooth_sandstone_slab', floor: 'smooth_sandstone', glass: 'glass', pane: 'glass_pane', light: 'sea_lantern' },
};

/** Solid volume shell: walls + floor slab + flat roof plate. faces = { north: 'solid'|'glass'|'wood'|'open', ... } */
export function volume(b, M, x0, y0, z0, x1, y1, z1, faces = {}, opts = {}) {
  const { mull = 3, roof = true, floor = true, lipOver = 1, band = true } = opts;
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edgeX = x === x0 || x === x1, edgeZ = z === z0 || z === z1;
    if (!edgeX && !edgeZ) { b.del(x, y, z); continue; }
    const side = z === z0 ? 'north' : z === z1 ? 'south' : x === x0 ? 'west' : 'east';
    const kind = faces[side] || 'solid';
    const corner = edgeX && edgeZ;
    const along = side === 'north' || side === 'south' ? x - x0 : z - z0;
    if (corner) { b.set(x, y, z, kind === 'glass' ? M.frame : M.wall); continue; }
    if (kind === 'open') { b.del(x, y, z); continue; }
    if (kind === 'glass') b.set(x, y, z, along % mull === 0 ? M.frame : M.glass);
    else if (kind === 'wood') b.log(x, y, z, M.wood, 'y');
    else if (kind === 'band') b.set(x, y, z, (y - y0) % 2 === 1 ? M.glass : M.wall);
    else b.set(x, y, z, M.wall);
  }
  if (floor) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) b.set(x, y0 - 1, z, M.floor);
  if (roof) {
    for (let x = x0 - lipOver; x <= x1 + lipOver; x++) for (let z = z0 - lipOver; z <= z1 + lipOver; z++) {
      b.set(x, y1 + 1, z, M.wall2);
      const edge = x === x0 - lipOver || x === x1 + lipOver || z === z0 - lipOver || z === z1 + lipOver;
      if (edge && band) b.set(x, y1 + 1, z, M.wall);
    }
  }
}
/** Glass curtain wall on one face with thin mullions (iron bars) and slab floor bands every storey. */
export function curtain(b, M, side, a0, a1, c, y0, y1, storey = 4) {
  for (let a = a0; a <= a1; a++) for (let y = y0; y <= y1; y++) {
    const [x, z] = side === 'north' || side === 'south' ? [a, c] : [c, a];
    const floorBand = (y - y0) % storey === storey - 1;
    b.set(x, y, z, floorBand ? M.frame : (a - a0) % 4 === 0 ? M.frame : M.glass);
  }
}
/** Pool with a quartz rim, water and lights in the floor. */
export function pool(b, M, x0, z0, x1, z1, y, depth = 2) {
  for (let x = x0 - 1; x <= x1 + 1; x++) for (let z = z0 - 1; z <= z1 + 1; z++) {
    const rim = x < x0 || x > x1 || z < z0 || z > z1;
    if (rim) { b.set(x, y, z, M.wall2); continue; }
    for (let d = 1; d <= depth; d++) b.set(x, y - d + 1, z, 'water');
    b.set(x, y - depth, z, (x + z) % 4 === 0 ? 'sea_lantern' : 'light_blue_concrete');
  }
  for (let x = x0 - 1; x <= x1 + 1; x++) for (let z = z0 - 1; z <= z1 + 1; z++) for (let d = 1; d <= depth; d++) {
    const wall = x === x0 - 1 || x === x1 + 1 || z === z0 - 1 || z === z1 + 1;
    if (wall) b.set(x, y - d, z, 'light_blue_concrete');
  }
}
/** Hedge planter: dark box with trimmed leaves on top. */
export function planter(b, x0, z0, x1, z1, y, box = 'polished_deepslate', leaf = 'oak_leaves', h = 1) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) { b.set(x, y, z, box); for (let k = 1; k <= h; k++) b.set(x, y + k, z, leaf); }
}
/** Glass balcony railing along a line. */
export function railing(b, x0, z0, x1, z1, y, pane = 'glass_pane') { b.line(x0, y, z0, x1, y, z1, pane); }
/** Recessed down-lights in a ceiling plane. */
export function downlights(b, x0, z0, x1, z1, y, M, every = 3) {
  for (let x = x0; x <= x1; x += every) for (let z = z0; z <= z1; z += every) b.set(x, y, z, M.light);
}
/** Vertical wood slat cladding on a wall plane (logs alternating with recessed trapdoors). */
export function slats(b, M, side, a0, a1, c, y0, y1) {
  for (let a = a0; a <= a1; a++) for (let y = y0; y <= y1; y++) {
    const [x, z] = side === 'north' || side === 'south' ? [a, c] : [c, a];
    b.log(x, y, z, M.wood, 'y');
    if ((a - a0) % 2 === 1) { const [ox, oz] = DIRS[side]; b.trap(x + ox, y, z + oz, `${M.woodp}_trapdoor`, side, 'bottom', true); }
  }
}
export function tree(b, x, y, z, h = 4, leaf = 'azalea_leaves', log = 'stripped_birch_log') {
  for (let i = 0; i < h; i++) b.log(x, y + i, z, log);
  b.ellipsoid(x, y + h, z, 2, 1.5, 2, leaf, {}, (bx, by, bz, d) => d < 0.6 || hash3(bx, by, bz, 3) > d - 0.35);
}
export { noise3 };
