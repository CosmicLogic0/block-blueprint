// Redstone: working Java designs. Use the 🔴 Use tool in the app to flip levers and watch them work.

function floor(b, x0, z0, x1, z1, m = 'smooth_stone') { b.fill(x0, 0, z0, x1, 0, z1, m); }

export default [
  {
    id: 'piston_door_2x2', name: '2x2 Piston Door', cat: 'Redstone', seed: 101,
    desc: 'Four sticky pistons close a 2x2 stone doorway. One lever behind the wall drives both sides through a dust loop. Lever ON = closed, OFF = open.',
    build(b) {
      floor(b, -8, -4, 9, 3);
      // wall with the doorway at x = 0..1, y = 1..2
      for (let x = -8; x <= 9; x++) for (let y = 1; y <= 4; y++) b.set(x, y, 0, (x + y) % 5 === 0 ? 'cracked_stone_bricks' : 'stone_bricks');
      for (let x = -7; x <= 8; x++) for (let y = 1; y <= 2; y++) b.del(x, y, 0);
      // pistons and the door blocks they hold
      for (const y of [1, 2]) {
        b.set(-2, y, 0, 'sticky_piston', { facing: 'east' }); b.set(-1, y, 0, 'polished_andesite');
        b.set(3, y, 0, 'sticky_piston', { facing: 'west' }); b.set(2, y, 0, 'polished_andesite');
      }
      // left + right power: dust on top of stone, pointing into the upper piston and weakly powering the block beside the lower one
      for (const [x0, x1] of [[-5, -3], [4, 6]]) for (let x = x0; x <= x1; x++) { b.set(x, 1, 0, 'stone'); b.set(x, 2, 0, 'redstone_wire'); }
      // loop behind the wall joins both sides to one lever
      for (const x of [-5, 6]) { b.set(x, 1, -1, 'stone'); b.set(x, 2, -1, 'redstone_wire'); }
      for (let x = -5; x <= 6; x++) { b.set(x, 1, -2, 'stone'); b.set(x, 2, -2, 'redstone_wire'); }
      b.set(0, 1, -3, 'stone'); b.set(0, 2, -3, 'lever', { face: 'floor', facing: 'north', powered: 'true' });
      for (const x of [-7, -6, 7, 8]) for (let y = 1; y <= 2; y++) b.set(x, y, 0, 'stone_bricks');
      b.set(-6, 3, 1, 'wall_torch', { facing: 'south' }); b.set(7, 3, 1, 'wall_torch', { facing: 'south' });
    },
  },
  {
    id: 'iron_door_entrance', name: 'Pressure Plate Iron Door', cat: 'Redstone', seed: 102,
    desc: 'Double iron doors in a stone frame that open when you step on the plates on either side.',
    build(b) {
      floor(b, -4, -3, 4, 3, 'polished_andesite');
      for (let x = -4; x <= 4; x++) for (let y = 1; y <= 4; y++) b.set(x, y, 0, 'stone_bricks');
      b.door(0, 1, 0, 'iron_door', 'south', 'left'); b.door(-1, 1, 0, 'iron_door', 'south', 'right');
      for (const x of [-1, 0]) { b.set(x, 1, 1, 'stone_pressure_plate'); b.set(x, 1, -1, 'stone_pressure_plate'); }
      for (const x of [-3, 2]) b.set(x, 3, 1, 'lantern', { hanging: 'false' });
      for (let x = -2; x <= 1; x++) b.stairs(x, 3, 1, 'stone_brick_stairs', 'north', 'top');
    },
  },
  {
    id: 'light_switch_room', name: 'Lamp Switch Ceiling', cat: 'Redstone', seed: 103,
    desc: 'A lever on the wall powers a dust line hidden above the ceiling that lights six redstone lamps at once.',
    build(b) {
      floor(b, -4, -3, 4, 3, 'spruce_planks');
      for (let x = -4; x <= 4; x++) for (let z = -3; z <= 3; z++) for (let y = 1; y <= 4; y++) if ((x === 4 || z === -3) || ((x === -4 || z === 3) && y === 4)) b.set(x, y, z, y === 4 ? 'stripped_spruce_log' : 'white_terracotta', y === 4 ? { axis: Math.abs(x) === 4 ? 'z' : 'x' } : undefined);
      for (let x = -3; x <= 3; x++) for (let z = -2; z <= 2; z++) b.set(x, 5, z, 'spruce_planks');
      for (const x of [-2, 0, 2]) for (const z of [-1, 1]) { b.set(x, 5, z, 'redstone_lamp'); b.set(x, 6, z, 'redstone_wire'); }
      for (let x = -3; x <= 3; x++) b.set(x, 6, 0, 'redstone_wire');
      for (const x of [-1, 1]) for (const z of [-1, 1]) b.set(x, 6, z, 'redstone_wire');
      for (let y = 5; y <= 6; y++) b.set(4, y, 0, 'stone');
      b.set(4, 7, 0, 'redstone_wire'); b.set(3, 7, 0, 'stone'); b.set(4, 6, 0, 'stone');
      b.set(3, 6, 0, 'redstone_wire');
      b.set(5, 6, 0, 'lever', { face: 'wall', facing: 'east', powered: 'false' });
    },
  },
  {
    id: 'daylight_lamp_post', name: 'Automatic Night Lamp', cat: 'Redstone', seed: 104,
    desc: 'Street lamp that switches itself on at night: an inverted daylight sensor on top powers the redstone lamp under it. Use the sensor to flip day/night.',
    build(b) {
      b.slab(0, 0, 0, 'smooth_stone_slab', 'top');
      for (let y = 1; y <= 4; y++) b.set(0, y, 0, y === 1 ? 'polished_blackstone_wall' : 'polished_blackstone_wall');
      b.set(0, 5, 0, 'redstone_lamp'); b.set(0, 6, 0, 'daylight_detector', { inverted: 'true' });
      for (const [x, z, f] of [[1, 0, 'east'], [-1, 0, 'west'], [0, 1, 'south'], [0, -1, 'north']]) b.trap(x, 5, z, 'dark_oak_trapdoor', f, 'bottom', true);
    },
  },
  {
    id: 'auto_smelter', name: 'Auto Smelter (3 Furnaces)', cat: 'Redstone', seed: 105,
    desc: 'Drop ores in the top chest and fuel in the side chest. Hoppers feed three furnaces and collect the smelted items in the bottom chest.',
    build(b) {
      // top input chest -> hopper line along x feeding furnace tops
      b.set(-1, 4, 0, 'chest', { facing: 'south' });
      for (let x = 0; x <= 2; x++) b.set(x, 3, 0, 'hopper', { facing: 'down' });
      b.set(-1, 3, 0, 'hopper', { facing: 'east' });
      for (let x = 0; x <= 2; x++) b.set(x, 2, 0, 'furnace', { facing: 'south' });
      // fuel: chest behind, hoppers into furnace backs
      b.set(-1, 3, -1, 'chest', { facing: 'south' });
      b.set(-1, 2, -1, 'hopper', { facing: 'east' });
      for (let x = 0; x <= 2; x++) b.set(x, 2, -1, 'hopper', { facing: 'south' });
      // output
      for (let x = 0; x <= 2; x++) b.set(x, 1, 0, 'hopper', { facing: 'west' });
      b.set(-1, 1, 0, 'hopper', { facing: 'down' }); b.set(-1, 0, 0, 'chest', { facing: 'south' });
      for (let x = -2; x <= 3; x++) for (let z = -2; z <= 1; z++) if (!b.has(x, 0, z)) b.set(x, 0, z, 'polished_andesite');
      for (const x of [-2, 3]) for (let y = 1; y <= 4; y++) b.set(x, y, -2, 'stripped_spruce_log');
      for (let x = -2; x <= 3; x++) b.slab(x, 5, -2, 'spruce_slab'); b.slab(-1, 5, 0, 'spruce_slab'); b.lantern(1, 4, 0, false);
    },
  },
  {
    id: 'bubble_elevator', name: 'Bubble Column Elevator', cat: 'Redstone', seed: 106,
    desc: 'Glass tube elevator: soul sand at the bottom pushes you up, magma at the top tube brings you back down. 16 blocks tall.',
    build(b) {
      const H = 16;
      for (const [cx, base] of [[0, 'soul_sand'], [3, 'magma_block']]) {
        b.set(cx, 0, 0, base);
        for (let y = 1; y <= H; y++) { b.set(cx, y, 0, 'water'); for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) if (!(dx === 1 && cx === 0) && !(dx === -1 && cx === 3)) b.set(cx + dx, y, dz, dz === 1 && y <= 2 ? 'air' : 'glass'); }
        for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) b.set(cx + dx, 0, dz, 'polished_andesite');
      }
      for (let y = 1; y <= H; y++) { b.set(1, y, 0, 'glass'); b.set(2, y, 0, 'glass'); }
      for (let x = -1; x <= 4; x++) for (let z = -1; z <= 1; z++) if (!b.has(x, H + 1, z)) b.set(x, H + 1, z, 'smooth_stone_slab', { type: 'bottom' });
      for (const cx of [0, 3]) { b.del(cx, H, 1); b.door(cx, 1, 1, 'oak_door', 'south'); }
      b.set(0, H + 1, 0, 'air'); b.set(3, H + 1, 0, 'air');
    },
  },
  {
    id: 'cobblestone_generator', name: 'Cobblestone Generator', cat: 'Redstone', seed: 107,
    desc: 'Classic safe cobble generator: lava and water meet beside a gap so cobblestone forms over and over - mine it endlessly.',
    build(b) {
      for (let x = -2; x <= 3; x++) for (let z = -1; z <= 1; z++) b.set(x, 0, z, 'stone_bricks');
      for (let x = -2; x <= 3; x++) for (const z of [-1, 1]) b.set(x, 1, z, 'glass');
      b.set(-2, 1, 0, 'glass'); b.set(3, 1, 0, 'glass');
      b.set(-1, 1, 0, 'lava'); b.set(0, 1, 0, 'air'); b.set(1, 1, 0, 'cobblestone'); b.del(2, 0, 0); b.set(2, -1, 0, 'stone_bricks'); b.set(2, 0, 0, 'air'); b.set(3, 1, 0, 'water');
      b.set(2, 1, 0, 'air');
      b.del(0, 1, 0);
    },
  },
  {
    id: 'note_doorbell', name: 'Note Block Doorbell', cat: 'Redstone', seed: 108,
    desc: 'Press the button by the door and three note blocks chime together, wired with a short dust line behind the wall.',
    build(b) {
      floor(b, -3, -3, 3, 2, 'spruce_planks');
      for (let x = -3; x <= 3; x++) for (let y = 1; y <= 3; y++) b.set(x, y, 0, 'stripped_spruce_wood');
      b.door(0, 1, 0, 'spruce_door', 'south');
      b.set(2, 2, 1, 'stone_button', { face: 'wall', facing: 'south' });
      for (let x = 1; x <= 3; x++) b.set(x, 1, -1, 'redstone_wire');
      for (let x = 1; x <= 3; x++) b.set(x, 1, -2, 'note_block');
      b.set(2, 2, -1, 'redstone_wire'); b.set(2, 1, -1, 'stone');
    },
  },
];
