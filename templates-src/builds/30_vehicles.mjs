// Vehicles: cars, planes, helicopter, ships. Front / bow points east (+x).
import { hull, squareSail, triSail, wheel, tube } from '../vehicles.mjs';

/** Detailed car. len along x (front = +x), width 3 (z -1..1). Low body, tinted glass cabin, carpet hood/trunk, wheels in arches. */
function car(b, o) {
  const { len = 7, color = 'red', roofFrom = 2, roofTo = len - 3, low = false, ox = 0 } = o;
  const C = `${color}_concrete`, X = (x) => x + ox;
  const W = [1, len - 2];
  for (let x = 0; x < len; x++) for (let z = -1; z <= 1; z++) {
    const isWheel = W.includes(x) && Math.abs(z) === 1;
    b.set(X(x), 0, z, isWheel ? 'black_concrete' : z === 0 ? 'gray_concrete' : (x === 0 || x === len - 1 ? C : 'gray_concrete'));
    b.set(X(x), 1, z, C);
  }
  for (const x of W) for (const z of [-1, 1]) b.set(X(x), 0, z * 2, 'polished_blackstone_button', { face: 'wall', facing: z > 0 ? 'south' : 'north' });
  // lights: white headlights, red tail lights, black bumper strip
  for (const z of [-1, 1]) { b.set(X(len - 1), 1, z, 'white_stained_glass'); b.set(X(0), 1, z, 'red_stained_glass'); }
  b.set(X(len - 1), 1, 0, 'black_concrete'); b.set(X(0), 1, 0, C);
  // cabin
  for (let x = 0; x < len; x++) for (let z = -1; z <= 1; z++) {
    if (x < roofFrom || x > roofTo) { b.set(X(x), 2, z, `${color}_carpet`); continue; }
    b.set(X(x), 2, z, 'black_stained_glass');
    if (!low) b.set(X(x), 3, z, x === roofFrom || x === roofTo ? `${color}_carpet` : C);
  }
  if (low) for (let x = roofFrom; x <= roofTo; x++) for (let z = -1; z <= 1; z++) b.set(X(x), 3, z, x === roofFrom ? 'air' : `${color}_carpet`);
  // mirrors
  b.set(X(roofTo), 2, -2, 'stone_button', { face: 'wall', facing: 'north' }); b.set(X(roofTo), 2, 2, 'stone_button', { face: 'wall', facing: 'south' });
}

export default [
  {
    id: 'car_sedan', name: 'Red Family Sedan', cat: 'Vehicles', seed: 61,
    desc: 'Three-wide sedan with hubcaps, headlights, tail lights, grille, tinted windows and wing mirrors.',
    build(b) { car(b, { len: 8, color: 'red', roofFrom: 2, roofTo: 5 }); },
  },
  {
    id: 'car_sports', name: 'Sports Car', cat: 'Vehicles', seed: 62,
    desc: 'Low yellow sports car with a glass canopy, rear spoiler, side vents and twin exhausts.',
    build(b) {
      car(b, { len: 8, color: 'yellow', roofFrom: 3, roofTo: 5, low: true });
      for (const z of [-1, 0, 1]) b.slab(0, 3, z, 'blackstone_slab'); for (const z of [-1, 1]) b.set(0, 2, z, 'polished_blackstone_wall');
      for (const x of [3, 4]) { b.set(x, 1, -2, 'black_carpet'); }
    },
  },
  {
    id: 'car_pickup', name: 'Pickup Truck', cat: 'Vehicles', seed: 63,
    desc: 'Blue pickup with a tall cab, open load bed with trapdoor tailgate, roof lights and a big front grille.',
    build(b) {
      car(b, { len: 10, color: 'blue', roofFrom: 5, roofTo: 7 });
      for (let x = 0; x <= 4; x++) for (let z = -1; z <= 1; z++) { b.del(x, 3, z); b.set(x, 2, z, Math.abs(z) === 1 || x === 0 ? 'blue_concrete' : 'spruce_slab'); }
      for (let z = -1; z <= 1; z++) b.set(5, 2, z, 'blue_concrete');
      b.trap(-1, 2, 0, 'spruce_trapdoor', 'west', 'bottom', true);
      for (const z of [-1, 0, 1]) b.set(7, 4, z, 'stone_button', { face: 'floor', facing: 'east' });
      b.set(2, 3, 0, 'barrel', { facing: 'up' });
    },
  },
  {
    id: 'city_bus', name: 'City Bus', cat: 'Vehicles', seed: 64,
    desc: 'Long white-and-blue city bus with a window band, doors, destination sign and roof vents.',
    build(b) {
      const L = 15;
      for (const x of [2, L - 3]) for (const z of [-1, 1]) wheel(b, x, 0, z, z);
      for (let x = 0; x < L; x++) for (let z = -1; z <= 1; z++) {
        if (!b.has(x, 0, z)) b.set(x, 0, z, 'gray_concrete');
        b.set(x, 1, z, 'blue_concrete');
        b.set(x, 2, z, Math.abs(z) === 1 || x === 0 || x === L - 1 ? (x % 3 === 0 && x > 0 && x < L - 1 ? 'white_concrete' : 'black_stained_glass') : 'air');
        b.set(x, 3, z, 'white_concrete');
        b.set(x, 4, z, x % 4 === 2 && z === 0 ? 'light_gray_concrete' : 'smooth_quartz_slab');
      }
      for (const x of [3, 10]) b.set(x, 2, 1, 'iron_door', { facing: 'south', half: 'lower' });
      b.set(L - 1, 3, 0, 'orange_stained_glass'); b.set(L - 1, 1, -1, 'ochre_froglight'); b.set(L - 1, 1, 1, 'ochre_froglight');
      b.set(0, 1, -1, 'red_stained_glass'); b.set(0, 1, 1, 'red_stained_glass');
    },
  },
  {
    id: 'police_car', name: 'Police Cruiser', cat: 'Vehicles', seed: 65,
    desc: 'Black-and-white police car with a red and blue light bar and push bumper.',
    build(b) {
      car(b, { len: 8, color: 'white', roofFrom: 2, roofTo: 5 });
      for (let x = 0; x < 8; x++) for (const z of [-1, 1]) if (x < 2 || x > 5) b.set(x, 2, z, 'black_carpet');
      for (let x = 0; x < 8; x++) for (const z of [-1, 1]) b.set(x, 1, z, x >= 2 && x <= 5 ? 'white_concrete' : 'black_concrete');
      b.set(3, 4, -1, 'red_stained_glass'); b.set(3, 4, 1, 'blue_stained_glass'); b.set(3, 4, 0, 'white_carpet');
      for (const z of [-1, 1]) b.set(8, 1, z, 'iron_bars');
    },
  },
  {
    id: 'airliner', name: 'Passenger Airliner', cat: 'Vehicles', seed: 71,
    desc: 'Big white twin-engine airliner: round fuselage with a window line and blue stripe, cockpit glass, swept wings, engines and tail.',
    build(b) {
      const L = 44, cy = 6, R = 2.6;
      const r = (x) => (x > L - 6 ? R * Math.sqrt(Math.max(0.05, (L - x) / 6)) : x < 9 ? R * (0.35 + 0.65 * (x / 9)) : R);
      tube(b, 0, L, cy, r, (x, y, z, d, rr) => {
        if (x > L - 5 && y >= cy && Math.abs(z) <= 1 && y <= cy + 1) return 'black_stained_glass';
        if (y === cy + 1 && Math.abs(z) >= 2 && x > 8 && x < L - 6 && x % 2 === 0) return 'black_stained_glass';
        if (y === cy - 1 && Math.abs(z) >= 2 && x > 6) return 'blue_concrete';
        if (y < cy - 1) return 'light_gray_concrete';
        return 'white_concrete';
      });
      // wings (swept back)
      for (let s = 1; s <= 16; s++) for (const sg of [-1, 1]) {
        const z = sg * (2 + s), x0 = 18 - Math.floor(s * 0.6), cw = Math.max(2, 7 - Math.floor(s / 3));
        for (let x = x0; x < x0 + cw; x++) b.set(x, cy - 2, z, s === 16 ? 'blue_concrete' : 'white_concrete');
        b.slab(x0 - 1, cy - 2, z, 'smooth_quartz_slab', 'top');
      }
      // engines under the wings
      for (const sg of [-1, 1]) {
        const z = sg * 7;
        for (let x = 15; x <= 21; x++) for (const [dy, dz] of [[0, 0], [-1, 0], [0, 1], [-1, 1], [0, -1], [-1, -1]]) b.set(x, cy - 3 + dy, z + dz, x === 21 ? 'gray_concrete' : 'light_gray_concrete');
        b.set(21, cy - 3, z, 'black_concrete'); b.set(21, cy - 4, z, 'black_concrete');
        for (let x = 17; x <= 19; x++) b.set(x, cy - 2 + 0, z, 'white_concrete');
      }
      // tail fin + stabilisers
      for (let y = 0; y < 8; y++) for (let x = 0; x < 6 - Math.floor(y * 0.5); x++) b.set(x + Math.floor(y * 0.6), cy + 2 + y, 0, y === 7 ? 'blue_concrete' : y > 4 ? 'blue_concrete' : 'white_concrete');
      for (let s = 1; s <= 6; s++) for (const sg of [-1, 1]) for (let x = Math.floor(s * 0.4); x < 4; x++) b.set(x, cy + 1, sg * (2 + s), 'white_concrete');
      // landing gear
      for (const [x, z] of [[L - 7, 0], [19, -3], [19, 3]]) { b.set(x, cy - 3, z, 'iron_bars'); b.set(x, 1, z, 'iron_bars'); for (let y = 1; y < cy - 2; y++) b.set(x, y, z, 'iron_bars'); b.set(x, 0, z, 'black_concrete'); }
    },
  },
  {
    id: 'prop_plane', name: 'Bush Plane', cat: 'Vehicles', seed: 72,
    desc: 'Small high-wing propeller plane in red and white with struts, tail-dragger wheels and a two-blade prop.',
    build(b) {
      const cy = 3;
      tube(b, 0, 13, cy, (x) => (x < 4 ? 0.8 + x * 0.25 : x > 11 ? 1.2 : 1.6), (x, y, z) => (y === cy + 1 && x >= 8 && x <= 10 ? 'black_stained_glass' : y === cy ? 'red_concrete' : 'white_concrete'));
      for (let z = -9; z <= 9; z++) for (let x = 7; x <= 10; x++) b.set(x, cy + 2, z, Math.abs(z) > 7 ? 'red_concrete' : 'white_concrete');
      for (const sg of [-1, 1]) b.line(9, cy - 1, sg * 1, 9, cy + 1, sg * 5, 'iron_bars');
      for (let y = cy + 1; y <= cy + 4; y++) for (let x = 0; x < 3; x++) b.set(x, y, 0, 'red_concrete');
      for (let z = -3; z <= 3; z++) b.set(1, cy, z, 'white_concrete');
      b.set(14, cy, 0, 'gray_concrete'); for (let y = cy - 2; y <= cy + 2; y++) b.set(15, y, 0, y === cy ? 'iron_block' : 'dark_oak_fence');
      for (const z of [-2, 2]) { b.set(10, cy - 2, z, 'iron_bars'); b.set(10, 0, z, 'black_concrete'); }
      b.set(1, 1, 0, 'black_concrete');
    },
  },
  {
    id: 'fighter_jet', name: 'Fighter Jet', cat: 'Vehicles', seed: 73,
    desc: 'Grey delta-wing fighter with a bubble canopy, twin tail fins, intakes and afterburner nozzle.',
    build(b) {
      const cy = 3;
      tube(b, 0, 22, cy, (x) => (x > 18 ? 1.4 * (22 - x) / 4 + 0.3 : 1.5), (x, y, z) => (y > cy && x >= 13 && x <= 17 && Math.abs(z) <= 1 ? 'light_blue_stained_glass' : y < cy ? 'light_gray_concrete' : 'gray_concrete'));
      for (let x = 2; x <= 14; x++) { const w = Math.round((14 - x) * 0.75) + 1; for (let z = 2; z <= 1 + w; z++) for (const sg of [-1, 1]) b.set(x, cy - 1, sg * z, z > w ? 'light_gray_concrete' : 'gray_concrete'); }
      for (const sg of [-1, 1]) for (let y = 1; y <= 4; y++) for (let x = 1 + Math.floor(y / 2); x <= 4; x++) b.set(x, cy + 1 + y, sg * 1, 'gray_concrete');
      for (let y = -1; y <= 1; y++) for (let z = -1; z <= 1; z++) b.set(-1, cy + y, z, y === 0 && z === 0 ? 'magma_block' : 'polished_blackstone');
      for (const sg of [-1, 1]) for (let x = 10; x <= 13; x++) b.set(x, cy, sg * 2, 'black_concrete');
      for (const [x, z] of [[17, 0], [8, -3], [8, 3]]) for (let y = 0; y < cy - 1; y++) b.set(x, y, z, y === 0 ? 'black_concrete' : 'iron_bars');
    },
  },
  {
    id: 'helicopter', name: 'Rescue Helicopter', cat: 'Vehicles', seed: 74,
    desc: 'Red rescue helicopter with a glass nose, tail boom and rotor, four-blade main rotor and landing skids.',
    build(b) {
      const cy = 3;
      b.ellipsoid(8, cy + 1, 0, 4, 2.4, 2.2, 'red_concrete', {}, (x, y, z, d) => d > 0.55);
      b.ellipsoid(8, cy + 1, 0, 4, 2.4, 2.2, 'light_blue_stained_glass', {}, (x, y, z, d) => d > 0.55 && x >= 10 && y >= cy);
      for (let x = -6; x <= 4; x++) b.set(x, cy + 2, 0, x < -4 ? 'red_concrete' : 'white_concrete');
      for (let y = cy + 3; y <= cy + 5; y++) b.set(-6, y, 0, 'red_concrete');
      for (const z of [-2, -1, 1, 2]) b.set(-6, cy + 4, z, 'iron_bars');
      b.set(8, cy + 4, 0, 'gray_concrete'); b.set(8, cy + 5, 0, 'polished_blackstone_wall');
      for (let k = 1; k <= 8; k++) { b.slab(8 + k, cy + 6, 0, 'smooth_quartz_slab'); b.slab(8 - k, cy + 6, 0, 'smooth_quartz_slab'); b.slab(8, cy + 6, k, 'smooth_quartz_slab'); b.slab(8, cy + 6, -k, 'smooth_quartz_slab'); }
      b.set(8, cy + 6, 0, 'polished_blackstone');
      for (const z of [-3, 3]) { for (let x = 4; x <= 12; x++) b.set(x, 0, z, 'iron_bars'); for (const x of [6, 10]) b.set(x, 1, z, 'iron_bars'); }
      for (let x = 6; x <= 10; x++) b.set(x, cy, 0, 'white_concrete');
      b.set(8, cy + 1, -3, 'white_concrete'); b.set(8, cy + 1, 3, 'white_concrete');
    },
  },
  {
    id: 'pirate_ship', name: 'Pirate Ship', cat: 'Ships', seed: 81,
    desc: 'Three-masted pirate ship: curved dark oak hull, gun ports with cannons, raised stern castle with windows, billowing square sails, crow\'s nest and black flag.',
    build(b) {
      const L = 40;
      const { deckAt } = hull(b, L, 11, 6, { hull: 'dark_oak_planks', stairs: 'dark_oak_stairs', stripe: 'stripped_dark_oak_log', keel: 'spruce_planks', deck: 'spruce_planks', rail: 'dark_oak_planks', fence: 'dark_oak_fence' }, { sheer: 3, bowSharp: 1.4 });
      // gun ports with cannons
      for (let x = 10; x <= 28; x += 4) for (const sg of [-1, 1]) { const z = sg * 5; b.set(x, 4, z, 'black_concrete'); b.set(x, 4, z + sg, 'polished_blackstone_wall'); }
      // stern castle
      for (let x = 0; x <= 8; x++) for (let z = -4; z <= 4; z++) for (let y = deckAt(x / (L - 1)) + 1; y <= 11; y++) {
        const edge = x === 0 || x === 8 || Math.abs(z) === 4;
        if (!edge) continue;
        b.set(x, y, z, x === 0 && y === 9 && Math.abs(z) <= 2 ? 'yellow_stained_glass' : (Math.abs(z) === 4 && y === 9 && x % 3 === 1) ? 'yellow_stained_glass' : 'dark_oak_planks');
      }
      for (let x = -1; x <= 9; x++) for (let z = -5; z <= 5; z++) b.set(x, 12, z, Math.abs(z) === 5 || x === -1 ? 'dark_oak_fence' : 'spruce_planks');
      b.door(8, deckAt(0.2) + 1, 0, 'dark_oak_door', 'east');
      for (const z of [-3, 3]) b.lantern(9, 11, z, true);
      // masts, sails, crow's nest
      for (const [mx, h, w] of [[13, 22, 5], [22, 26, 6], [31, 20, 4]]) {
        const d = deckAt(mx / (L - 1));
        for (let y = d + 1; y <= d + h; y++) b.log(mx, y, 0, 'stripped_spruce_log');
        squareSail(b, mx + 1, d + 5, Math.round(h * 0.35), w, 'white_wool', 1);
        squareSail(b, mx + 1, d + 6 + Math.round(h * 0.4), Math.round(h * 0.25), w - 1, 'white_wool', 1);
        if (mx === 22) { for (let x = -1; x <= 1; x++) for (let z = -1; z <= 1; z++) if (x || z) b.set(mx + x, d + h - 3, z, 'spruce_slab'); for (const [x, z] of [[-2, 0], [2, 0], [0, -2], [0, 2]]) b.set(mx + x, d + h - 2, z, 'spruce_fence'); }
        if (mx === 22) for (let k = 0; k < 3; k++) for (let y = 0; y < 2; y++) b.set(mx - 1 - k, d + h - y, 0, 'black_wool');
      }
      // bowsprit and figurehead
      for (let k = 0; k < 7; k++) b.log(L - 1 + k, deckAt(1) + 1 + Math.floor(k / 2), 0, 'stripped_spruce_log', 'x');
      b.set(L - 1, deckAt(1), 0, 'gold_block');
      b.set(5, 13, 0, 'spruce_fence'); b.set(5, 14, 0, 'spruce_trapdoor', { facing: 'north', half: 'bottom', open: 'false' });
    },
  },
  {
    id: 'sailboat', name: 'Sailing Sloop', cat: 'Ships', seed: 82,
    desc: 'White and navy sailing sloop with a tall mast, mainsail and jib, small cabin with portholes and a teak deck.',
    build(b) {
      const L = 18;
      const { deckAt } = hull(b, L, 6, 3, { hull: 'smooth_quartz', stairs: 'smooth_quartz_stairs', stripe: 'blue_concrete', keel: 'blue_concrete', deck: 'stripped_oak_wood', rail: 'smooth_quartz', fence: null }, { sheer: 1, bowSharp: 1.2 });
      for (let x = 4; x <= 9; x++) for (let z = -1; z <= 1; z++) { b.set(x, deckAt(0.4) + 1, z, Math.abs(z) === 1 && x % 2 === 0 ? 'light_blue_stained_glass' : 'white_concrete'); b.slab(x, deckAt(0.4) + 2, z, 'smooth_quartz_slab'); }
      const mx = 10, d = deckAt(mx / (L - 1));
      for (let y = d + 1; y <= d + 17; y++) b.set(mx, y, 0, 'iron_bars');
      triSail(b, mx, d + 3, 13, 8, 'white_wool');
      for (let y = 0; y < 11; y++) { const k = Math.round(6 * (1 - y / 11)); for (let i = 1; i <= k; i++) b.set(mx + i, d + 2 + y, 0, 'white_wool'); }
      b.line(mx, d + 17, 0, L, d + 1, 0, 'iron_bars');
      for (let y = 0; y > -4; y--) b.set(8, y, 0, 'blue_concrete');
      b.set(1, d + 1, 0, 'spruce_fence'); b.set(1, d + 2, 0, 'dark_oak_trapdoor', { facing: 'north', half: 'bottom', open: 'false' });
    },
  },
  {
    id: 'luxury_yacht', name: 'Luxury Yacht', cat: 'Ships', seed: 83,
    desc: 'Sleek white motor yacht with tiered decks, black wrap-around windows, a sun deck with hot tub, and a radar mast.',
    build(b) {
      const L = 34;
      const { deckAt } = hull(b, L, 10, 4, { hull: 'smooth_quartz', stairs: 'smooth_quartz_stairs', stripe: 'black_concrete', keel: 'light_gray_concrete', deck: 'stripped_oak_wood', rail: 'smooth_quartz', fence: 'glass_pane' }, { sheer: 1, bowSharp: 1.1 });
      const d = deckAt(0.5);
      // deck 1 superstructure
      for (let x = 4; x <= 24; x++) for (let z = -3; z <= 3; z++) for (let y = d + 1; y <= d + 3; y++) {
        const edge = Math.abs(z) === 3 || x === 4 || x === 24; if (!edge) continue;
        b.set(x, y, z, y === d + 2 ? 'black_stained_glass' : 'white_concrete');
      }
      for (let x = 3; x <= 26; x++) for (let z = -4; z <= 4; z++) b.set(x, d + 4, z, 'white_concrete');
      // deck 2
      for (let x = 9; x <= 21; x++) for (let z = -3; z <= 3; z++) for (let y = d + 5; y <= d + 6; y++) {
        const edge = Math.abs(z) === 3 || x === 9 || x === 21; if (!edge) continue;
        b.set(x, y, z, y === d + 5 ? 'black_stained_glass' : 'white_concrete');
      }
      for (let x = 8; x <= 22; x++) for (let z = -4; z <= 4; z++) b.set(x, d + 7, z, Math.abs(z) === 4 || x === 8 || x === 22 ? 'smooth_quartz_slab' : 'stripped_oak_wood');
      for (let x = 8; x <= 22; x++) for (const z of [-4, 4]) b.set(x, d + 8, z, 'glass_pane');
      // sun deck hot tub + loungers
      for (let x = 11; x <= 13; x++) for (let z = -1; z <= 1; z++) b.set(x, d + 7, z, 'water');
      for (const z of [-2, 2]) for (const x of [16, 18]) b.stairs(x, d + 8, z, 'quartz_stairs', 'west');
      // radar mast
      for (let y = d + 8; y <= d + 12; y++) b.set(20, y, 0, 'iron_bars');
      b.set(20, d + 13, 0, 'white_concrete'); b.slab(19, d + 13, 0, 'smooth_quartz_slab'); b.slab(21, d + 13, 0, 'smooth_quartz_slab');
      b.set(20, d + 14, 0, 'end_rod', { facing: 'up' });
      // swim platform
      for (let z = -3; z <= 3; z++) b.slab(-1, 2, z, 'stripped_oak_wood' === 'x' ? 'oak_slab' : 'oak_slab');
    },
  },
  {
    id: 'fishing_boat', name: 'Fishing Trawler', cat: 'Ships', seed: 84,
    desc: 'Weathered red-and-black trawler with a wheelhouse, net boom, crates, buoys and lanterns.',
    build(b) {
      const L = 22;
      const { deckAt } = hull(b, L, 8, 4, { hull: 'red_terracotta', stripe: 'white_terracotta', keel: 'black_terracotta', deck: 'spruce_planks', rail: 'red_terracotta', fence: 'spruce_fence' }, { sheer: 2 });
      const d = deckAt(0.6);
      for (let x = 12; x <= 16; x++) for (let z = -2; z <= 2; z++) for (let y = d + 1; y <= d + 4; y++) {
        const edge = Math.abs(z) === 2 || x === 12 || x === 16; if (!edge) continue;
        b.set(x, y, z, y === d + 3 ? 'glass_pane' : 'white_terracotta');
      }
      for (let x = 11; x <= 17; x++) for (let z = -3; z <= 3; z++) b.set(x, d + 5, z, 'blue_terracotta');
      b.set(14, d + 6, 0, 'iron_bars'); b.set(14, d + 7, 0, 'lantern');
      for (let y = d + 1; y <= d + 9; y++) b.log(6, y, 0, 'stripped_spruce_log');
      b.line(6, d + 9, 0, 0, d + 4, 0, 'stripped_spruce_log');
      for (let y = d + 2; y <= d + 4; y++) b.set(0, y, 0, 'cobweb');
      for (const [x, z] of [[3, -2], [4, -2], [3, 2]]) b.set(x, d + 1, z, 'barrel', { facing: 'up' });
      b.set(8, d + 1, 2, 'composter'); b.set(9, d + 1, -2, 'chest', { facing: 'south' });
    },
  },
  {
    id: 'viking_longship', name: 'Viking Longship', cat: 'Ships', seed: 85,
    desc: 'Long low spruce longship with a striped square sail, round shields along the sides, oars and a dragon-head prow.',
    build(b) {
      const L = 32;
      const { deckAt } = hull(b, L, 7, 3, { hull: 'spruce_planks', stairs: 'spruce_stairs', stripe: 'stripped_spruce_log', keel: 'dark_oak_planks', deck: 'spruce_slab', rail: 'spruce_planks' }, { sheer: 4, bowSharp: 1.0, sternFlat: 0.1 });
      for (let x = 5; x <= 26; x += 3) for (const sg of [-1, 1]) { b.set(x, deckAt(x / (L - 1)), sg * 4, x % 2 ? 'red_terracotta' : 'yellow_terracotta'); b.line(x, deckAt(x / (L - 1)) - 1, sg * 4, x, 0, sg * 7, 'stripped_spruce_log'); }
      const mx = 15, d = deckAt(0.5);
      for (let y = d + 1; y <= d + 14; y++) b.log(mx, y, 0, 'stripped_spruce_log');
      for (let y = d + 4; y <= d + 12; y++) for (let z = -5; z <= 5; z++) b.set(mx + 1 + (Math.abs(z) < 3 ? 1 : 0), y, z, Math.floor((z + 5) / 2) % 2 ? 'red_wool' : 'white_wool');
      for (let k = 0; k < 5; k++) b.set(L - 1 + Math.floor(k / 2), deckAt(1) + 1 + k, 0, k === 4 ? 'dark_oak_planks' : 'stripped_spruce_log', k === 4 ? undefined : { axis: 'y' });
      b.set(L, deckAt(1) + 5, 0, 'spruce_trapdoor', { facing: 'east', half: 'top', open: 'false' });
      for (let k = 0; k < 4; k++) b.set(-1 - Math.floor(k / 2), deckAt(0) + 1 + k, 0, 'stripped_spruce_log', { axis: 'y' });
    },
  },
  {
    id: 'speedboat', name: 'Speedboat', cat: 'Ships', seed: 86,
    desc: 'Fast orange-and-white speedboat with a windscreen, leather seats and twin outboard engines.',
    build(b) {
      const L = 13;
      const { deckAt } = hull(b, L, 5, 2, { hull: 'smooth_quartz', stairs: 'smooth_quartz_stairs', stripe: 'orange_concrete', keel: 'orange_concrete', deck: 'smooth_quartz_slab', rail: 'smooth_quartz' }, { sheer: 1, bowSharp: 1.3 });
      const d = deckAt(0.5);
      for (let z = -1; z <= 1; z++) b.set(7, d + 1, z, 'light_gray_stained_glass_pane');
      for (const x of [3, 5]) for (const z of [-1, 1]) b.stairs(x, d, z, 'brown_terracotta' === 'x' ? 'oak_stairs' : 'spruce_stairs', 'west');
      for (const z of [-1, 1]) { b.set(-1, d - 1, z, 'gray_concrete'); b.set(-1, d - 2, z, 'polished_blackstone_wall'); b.set(-1, d, z, 'black_concrete'); }
    },
  },
  {
    id: 'boat_house', name: 'Floating Boat House', cat: 'Ships', seed: 87,
    desc: 'Rustic dark oak house boat: stair-smoothed hull, timber cabin with a sweeping layered roof, stone chimney, lanterns and a front deck.',
    build(b) {
      const L = 26;
      const { deckAt } = hull(b, L, 9, 3, { hull: 'dark_oak_planks', stairs: 'dark_oak_stairs', stripe: 'stripped_dark_oak_log', keel: 'spruce_planks', deck: 'spruce_planks', rail: 'dark_oak_planks', fence: 'dark_oak_fence' }, { sheer: 3, bowSharp: 1.3 });
      const d = deckAt(0.4);
      for (let x = 2; x <= 13; x++) for (let z = -3; z <= 3; z++) for (let y = d + 1; y <= d + 4; y++) {
        const edge = x === 2 || x === 13 || Math.abs(z) === 3; if (!edge) continue;
        const post = (x === 2 || x === 13) && Math.abs(z) === 3 || (Math.abs(z) === 3 && x % 4 === 1);
        b.set(x, y, z, post ? 'dark_oak_log' : (y === d + 2 && x % 4 === 3) ? 'glass_pane' : y === d + 4 ? 'stripped_dark_oak_log' : 'spruce_planks', post ? { axis: 'y' } : (y === d + 4 ? { axis: 'x' } : undefined));
      }
      // layered roof with flared eaves
      for (let i = 0; i <= 4; i++) for (let x = 0 - (i === 0 ? 1 : 0); x <= 15 + (i === 0 ? 1 : 0); x++) {
        b.stairs(x, d + 5 + i, -5 + i, 'spruce_stairs', 'south'); b.stairs(x, d + 5 + i, 5 - i, 'spruce_stairs', 'north');
        if (i > 0) { b.stairs(x, d + 4 + i, -5 + i, 'dark_oak_stairs', 'north', 'top'); b.stairs(x, d + 4 + i, 5 - i, 'dark_oak_stairs', 'south', 'top'); }
      }
      for (let x = -1; x <= 16; x++) b.slab(x, d + 10, 0, 'spruce_slab');
      for (const x of [2, 13]) for (let i = 0; i < 4; i++) for (let z = -3 + i; z <= 3 - i; z++) b.set(x, d + 5 + i, z, 'stripped_dark_oak_log', { axis: 'y' });
      for (let y = d + 1; y <= d + 12; y++) b.set(4, y, -2, y > d + 9 ? 'stone_bricks' : 'cobblestone'); b.set(4, d + 13, -2, 'cobblestone_wall');
      b.door(13, d + 1, 0, 'dark_oak_door', 'east');
      b.lantern(14, d + 3, -2, true); b.lantern(L - 3, deckAt(0.9) + 2, 0, false);
      b.set(18, deckAt(0.7) + 1, -2, 'barrel', { facing: 'up' }); b.set(19, deckAt(0.7) + 1, 2, 'composter');
    },
  },
  {
    id: 'hot_air_balloon', name: 'Hot Air Balloon', cat: 'Vehicles', seed: 88,
    desc: 'Big striped hot air balloon: purple and white panels, wooden ribs, a burner ring, hanging lanterns on ropes and a wicker basket.',
    build(b) {
      const cy = 24, R = 10;
      b.ellipsoid(0, cy, 0, R, R * 1.1, R, (x, y, z) => {
        const a = Math.atan2(z, x), seg = Math.floor(((a + Math.PI) / (Math.PI * 2)) * 12);
        const rib = Math.abs(((a + Math.PI) / (Math.PI * 2)) * 12 - Math.round(((a + Math.PI) / (Math.PI * 2)) * 12)) < 0.07;
        if (rib || (y - cy) % 6 === 0) return 'spruce_planks';
        return seg % 2 ? 'purple_wool' : 'white_wool';
      }, {}, (x, y, z, d) => d > 0.78);
      // taper to the mouth
      for (let y = cy - R - 4; y < cy - R + 1; y++) b.disc(0, y, 0, 2.5 + (y - (cy - R - 4)) * 0.6, y % 2 ? 'purple_wool' : 'spruce_planks', {}, true);
      b.disc(0, cy - R - 5, 0, 2.5, 'iron_bars', {}, true); b.set(0, cy - R - 5, 0, 'campfire', { lit: 'true' });
      // ropes and basket
      for (const [x, z] of [[2, 2], [-2, 2], [2, -2], [-2, -2]]) b.line(x, cy - R - 5, z, x, 4, z, 'iron_chain');
      for (let x = -2; x <= 2; x++) for (let z = -2; z <= 2; z++) for (let y = 0; y <= 3; y++) {
        const edge = Math.abs(x) === 2 || Math.abs(z) === 2; if (!edge && y > 0) continue;
        b.set(x, y, z, y === 3 ? 'spruce_slab' : y === 0 ? 'stripped_spruce_wood' : 'bamboo_mosaic');
      }
      for (const [x, z] of [[R, 0], [-R, 0], [0, R], [0, -R]]) { b.line(x, cy - 3, z, x, cy - 7, z, 'iron_chain'); b.lantern(x, cy - 8, z, true); }
      b.set(0, cy + R * 1.1 + 1, 0, 'spruce_fence'); b.set(0, cy + R * 1.1 + 2, 0, 'lightning_rod', { facing: 'up' });
    },
  },
  {
    id: 'hot_air_balloon_red', name: 'Royal Red Balloon', cat: 'Vehicles', seed: 89,
    desc: 'Red and cream balloon with a crown band of lanterns, draped red tassels, a spire and a carved dark oak gondola.',
    build(b) {
      const cy = 22, R = 9;
      b.ellipsoid(0, cy, 0, R, R * 1.05, R, (x, y, z) => {
        if (y > cy + 2) return (x + z) % 5 === 0 && y === cy + 5 ? 'shroomlight' : (y - cy) % 3 === 0 ? 'crimson_planks' : 'red_wool';
        return y < cy - 4 ? 'smooth_quartz' : 'white_wool';
      }, {}, (x, y, z, d) => d > 0.8);
      for (let a = 0; a < 16; a++) { const x = Math.round(Math.cos(a / 16 * Math.PI * 2) * R), z = Math.round(Math.sin(a / 16 * Math.PI * 2) * R); for (let y = cy - 1; y >= cy - 4 - (a % 3); y--) b.set(x, y, z, 'red_wool'); b.lantern(x, cy - 5 - (a % 3), z, true); }
      for (let y = cy - R - 3; y < cy - R + 1; y++) b.disc(0, y, 0, 2 + (y - (cy - R - 3)) * 0.7, 'smooth_quartz', {}, true);
      for (const [x, z] of [[2, 2], [-2, 2], [2, -2], [-2, -2]]) b.line(x, cy - R - 3, z, x, 4, z, 'iron_chain');
      for (let x = -3; x <= 3; x++) for (let z = -2; z <= 2; z++) for (let y = 0; y <= 3; y++) {
        const edge = Math.abs(x) === 3 || Math.abs(z) === 2; if (!edge && y > 0) continue;
        b.set(x, y, z, y === 3 ? 'dark_oak_slab' : y === 0 ? 'dark_oak_planks' : 'stripped_dark_oak_wood');
      }
      for (const x of [-3, 3]) b.stairs(x + (x > 0 ? 1 : -1), 1, 0, 'dark_oak_stairs', x > 0 ? 'west' : 'east', 'top');
      for (let y = 0; y < 4; y++) b.set(0, Math.round(cy + R * 1.05) + 1 + y, 0, y === 3 ? 'lightning_rod' : 'crimson_planks', y === 3 ? { facing: 'up' } : undefined);
    },
  },
  {
    id: 'f1_race_car', name: 'Formula Race Car', cat: 'Vehicles', seed: 90,
    desc: 'Low white open-wheel race car with front and rear wings, big tyres, sidepods, air intake and a blue driver seat.',
    build(b) {
      for (let x = 0; x <= 11; x++) b.set(x, 0, 0, 'white_concrete');
      for (let x = 2; x <= 8; x++) for (const z of [-1, 1]) b.set(x, 0, z, 'white_concrete');
      for (let x = 3; x <= 7; x++) for (const z of [-2, 2]) b.slab(x, 0, z, 'smooth_quartz_slab');
      for (const [x, z] of [[1, -2], [1, 2], [9, -2], [9, 2]]) { b.set(x, 0, z, 'polished_blackstone'); b.set(x, 1, z, 'blackstone_slab'); }
      for (let z = -3; z <= 3; z++) { b.slab(11, 0, z, 'smooth_quartz_slab'); b.slab(-1, 2, z, 'smooth_quartz_slab'); }
      for (const z of [-3, 3]) { b.trap(11, 0, z + (z > 0 ? 1 : -1), 'birch_trapdoor', z > 0 ? 'north' : 'south', 'bottom', true); b.set(-1, 1, z, 'iron_bars'); }
      b.set(4, 1, 0, 'white_concrete'); b.set(5, 1, 0, 'blue_concrete'); b.stairs(6, 1, 0, 'smooth_quartz_stairs', 'west');
      b.set(3, 1, 0, 'white_concrete'); b.set(3, 2, 0, 'white_concrete'); b.set(2, 1, 0, 'smooth_quartz');
      b.set(7, 1, 0, 'stone_button', { face: 'floor', facing: 'east' });
      for (const z of [-1, 1]) b.set(4, 1, z, 'smooth_quartz_slab');
    },
  },
];
