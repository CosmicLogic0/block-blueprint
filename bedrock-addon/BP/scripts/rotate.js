// Turning a build: where each block goes, and which way it then faces. One turn = 90 degrees clockwise seen from
// above (north -> east -> south -> west).

/** Position on the ground (x, z) of a block after `turns`, for a build sx wide and sz deep. */
export function rotatePos(x, z, sx, sz, turns) {
  switch (turns & 3) {
    case 1: return { x: sz - 1 - z, z: x };
    case 2: return { x: sx - 1 - x, z: sz - 1 - z };
    case 3: return { x: z, z: sx - 1 - x };
    default: return { x, z };
  }
}

const CW = ['north', 'east', 'south', 'west'];
const turnName = (v, t) => { const i = CW.indexOf(v); return i < 0 ? v : CW[(i + t) % 4]; };
// number -> direction name, per state
const FACING = ['down', 'up', 'north', 'south', 'west', 'east'];          // facing_direction
const WEIRDO = ['east', 'west', 'south', 'north'];                        // weirdo_direction (stairs), trapdoor direction
const DIRECTION = ['south', 'west', 'north', 'east'];                     // direction (beds, bells, looms...)
const turnIdx = (v, table, t) => { const n = table.indexOf(turnName(table[v], t)); return n < 0 ? v : n; };
const LEVER = { north: 'east', east: 'south', south: 'west', west: 'north', up_north_south: 'up_east_west', up_east_west: 'up_north_south', down_north_south: 'down_east_west', down_east_west: 'down_north_south' };
// bit sets: vine_direction_bits (south 1, west 2, north 4, east 8), multi_face_direction_bits (down 1, up 2, south 4, west 8, north 16, east 32)
function turnBits(v, bits, t) {
  let out = 0;
  for (const [name, bit] of Object.entries(bits)) if (v & bit) out |= bits[turnName(name, t)] ?? bit;
  return out;
}
const VINE = { south: 1, west: 2, north: 4, east: 8 };
const MULTI = { down: 1, up: 2, south: 4, west: 8, north: 16, east: 32 };

/** The states of a block after `turns` (blockName decides what "direction" means). */
export function rotateStates(states, turns, blockName = '') {
  const t = turns & 3;
  if (!t) return states;
  const s = { ...states };
  for (const [k, v] of Object.entries(states)) {
    switch (k) {
      case 'minecraft:cardinal_direction': case 'minecraft:facing_direction': case 'minecraft:block_face': case 'torch_facing_direction':
        s[k] = turnName(v, t); break;
      case 'facing_direction': s[k] = turnIdx(v, FACING, t); break;
      case 'weirdo_direction': s[k] = turnIdx(v, WEIRDO, t); break;
      case 'direction': s[k] = blockName.includes('trapdoor') ? turnIdx(v, WEIRDO, t) : turnIdx(v, DIRECTION, t); break;
      case 'ground_sign_direction': s[k] = (v + 4 * t) % 16; break;
      case 'pillar_axis': case 'portal_axis': if (t % 2) s[k] = v === 'x' ? 'z' : v === 'z' ? 'x' : v; break;
      case 'lever_direction': { let x = v; for (let i = 0; i < t; i++) x = LEVER[x] || x; s[k] = x; break; }
      case 'vine_direction_bits': s[k] = turnBits(v, VINE, t); break;
      case 'multi_face_direction_bits': s[k] = turnBits(v, MULTI, t); break;
    }
  }
  return s;
}
