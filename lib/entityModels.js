'use strict';
// Blocks the game draws with entity renderers (beds, bells, chests, shulker boxes, signs, banners) have no real
// block model, so the block models in the game files are empty or just a frame. These are hand-built stand-ins
// that cut the right pieces out of the entity texture sheets (textures/entity/...).
//
// A texture name of the form  "entity/bed/red@6,6,16,16/64"  means "the 16x16 box at (6,6) of a 64-wide sheet".
// The renderer's atlas cuts it out and stretches it over one face, so every face here simply uses uv 0..16.

const COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black'];
const WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'bamboo', 'crimson', 'warped', 'pale_oak'];
const FACINGS = [['north', 0], ['east', 90], ['south', 180], ['west', 270]];

const crop = (src, x, y, w, h, ref = 64) => `${src}@${x},${y},${w},${h}/${ref}`;

function buildEntityBlocks(io, models, blocks, usedTextures) {
  if (!io.entityExists) return { added: 0, missing: [] };
  const missing = [];
  const has = (src) => { const ok = io.entityExists(src); if (!ok) missing.push(src); return ok; };
  let seq = 0;
  const added = [];

  const face = (tex, rotation, tint) => { usedTextures.add(tex); return { tex, uv: [0, 0, 16, 16], ...(rotation ? { rotation } : {}), ...(tint ? { tintindex: 0 } : {}) }; };
  const model = (elements) => { const id = `__ent${seq++}`; models[id] = { elements }; return id; };
  const box = (from, to, faces) => ({ from, to, faces });
  const setBlock = (name, variants) => { blocks[name] = { variants }; added.push(name); };
  const label = (props) => Object.entries(props).map(([k, v]) => `${k}=${v}`).join(',');

  // ---------------------------------------------------------------- beds (head or foot half, facing = direction the head points)
  for (const color of COLORS) {
    const src = `entity/bed/${color}`;
    if (!has(src)) continue;
    const c = (x, y, w, h) => crop(src, x, y, w, h);
    const leg = (x, z) => box([x, 0, z], [x + 3, 3, z + 3], {
      north: face(c(53, 3, 3, 3)), south: face(c(59, 3, 3, 3)), west: face(c(50, 3, 3, 3)), east: face(c(56, 3, 3, 3)),
      up: face(c(53, 0, 3, 3)), down: face(c(56, 0, 3, 3)) });
    const head = model([
      box([0, 3, 0], [16, 9, 16], { up: face(c(6, 6, 16, 16)), down: face(c(28, 6, 16, 16)), north: face(c(6, 0, 16, 6)), west: face(c(0, 6, 6, 16), 270), east: face(c(22, 6, 6, 16), 90) }),
      leg(0, 0), leg(13, 0)]);
    const foot = model([
      box([0, 3, 0], [16, 9, 16], { up: face(c(6, 28, 16, 16)), down: face(c(28, 28, 16, 16)), south: face(c(22, 22, 16, 6)), west: face(c(0, 28, 6, 16), 270), east: face(c(22, 28, 6, 16), 90) }),
      leg(0, 13), leg(13, 13)]);
    const variants = [];
    for (const part of ['foot', 'head']) for (const [f, y] of FACINGS) variants.push({ label: label({ facing: f, occupied: 'false', part }), parts: [{ m: part === 'head' ? head : foot, x: 0, y }] });
    setBlock(`${color}_bed`, variants);
  }

  // ---------------------------------------------------------------- bell: the frame model exists; add the bell body
  if (blocks.bell && has('entity/bell/bell_body')) {
    const c = (x, y, w, h) => crop('entity/bell/bell_body', x, y, w, h, 32);
    const body = model([
      box([5, 6, 5], [11, 13, 11], { up: face(c(6, 0, 6, 6)), down: face(c(12, 0, 6, 6)), west: face(c(0, 6, 6, 7)), north: face(c(6, 6, 6, 7)), east: face(c(12, 6, 6, 7)), south: face(c(18, 6, 6, 7)) }),
      box([4, 4, 4], [12, 6, 12], { up: face(c(8, 13, 8, 8)), down: face(c(16, 13, 8, 8)), west: face(c(0, 21, 8, 2)), north: face(c(8, 21, 8, 2)), east: face(c(16, 21, 8, 2)), south: face(c(24, 21, 8, 2)) })]);
    for (const v of blocks.bell.variants) v.parts.push({ m: body, x: 0, y: 0 });
    added.push('bell');
  }

  // ---------------------------------------------------------------- chests (single-chest look; left/right halves look the same)
  for (const [name, tex, withType] of [['chest', 'normal', true], ['trapped_chest', 'trapped', true], ['ender_chest', 'ender', false]]) {
    const src = `entity/chest/${tex}`;
    if (!has(src)) continue;
    const c = (x, y, w, h) => crop(src, x, y, w, h);
    const m = model([
      // base 14 x 10 x 14
      box([1, 0, 1], [15, 10, 15], { down: face(c(14, 19, 14, 14)), west: face(c(0, 33, 14, 10)), north: face(c(14, 33, 14, 10)), east: face(c(28, 33, 14, 10)), south: face(c(42, 33, 14, 10)) }),
      // lid 14 x 5 x 14 (sits on top; its top is the plain wood square)
      box([1, 10, 1], [15, 15, 15], { up: face(c(28, 0, 14, 14)), west: face(c(0, 14, 14, 5)), north: face(c(14, 14, 14, 5)), east: face(c(28, 14, 14, 5)), south: face(c(42, 14, 14, 5)) }),
      // latch on the front (north)
      box([7, 7, 0], [9, 11, 1], { north: face(c(1, 1, 2, 4)), south: face(c(4, 1, 2, 4)), west: face(c(0, 1, 1, 4)), east: face(c(3, 1, 1, 4)), up: face(c(1, 0, 2, 1)), down: face(c(3, 0, 2, 1)) })]);
    const variants = [];
    for (const type of withType ? ['single', 'left', 'right'] : [null]) for (const [f, y] of FACINGS) {
      const p = { facing: f }; if (type) p.type = type; p.waterlogged = 'false';
      variants.push({ label: label(p), parts: [{ m, x: 0, y }] });
    }
    setBlock(name, variants);
  }

  // ---------------------------------------------------------------- shulker boxes (16 colours + the undyed one)
  for (const color of [null, ...COLORS]) {
    const src = color ? `entity/shulker/shulker_${color}` : 'entity/shulker/shulker';
    if (!has(src)) continue;
    const c = (x, y, w, h) => crop(src, x, y, w, h);
    const m = model([
      // base (its top 4 rows are the notched tabs the lid's lip fits over); inset a hair so it never fights the lid
      box([0.02, 0, 0.02], [15.98, 8, 15.98], { down: face(c(32, 28, 16, 16)), west: face(c(0, 44, 16, 8)), north: face(c(16, 44, 16, 8)), east: face(c(32, 44, 16, 8)), south: face(c(48, 44, 16, 8)) }),
      // lid: 12 tall, overlapping the base by 4
      box([0, 4, 0], [16, 16, 16], { up: face(c(16, 0, 16, 16)), west: face(c(0, 16, 16, 12)), north: face(c(16, 16, 16, 12)), east: face(c(32, 16, 16, 12)), south: face(c(48, 16, 16, 12)) })]);
    const dirs = { up: [0, 0], down: [180, 0], north: [90, 0], east: [90, 90], south: [90, 180], west: [90, 270] };
    const variants = Object.entries(dirs).map(([f, [x, y]]) => ({ label: `facing=${f}`, parts: [{ m, x, y }] }));
    setBlock(color ? `${color}_shulker_box` : 'shulker_box', variants);
  }

  // ---------------------------------------------------------------- signs (standing: rotation 0-15, wall: facing)
  const K = 16 / 24; // board is 24 wide in the sheet, 16 in the block
  for (const wood of WOODS) {
    const src = `entity/signs/${wood}`;
    if (!has(src)) continue;
    const c = (x, y, w, h) => crop(src, x, y, w, h, 64);
    const boardFaces = { south: face(c(2, 2, 24, 12)), north: face(c(28, 2, 24, 12)), up: face(c(2, 0, 24, 2)), down: face(c(26, 0, 24, 2)), west: face(c(0, 2, 2, 12)), east: face(c(26, 2, 2, 12)) };
    const board = (y0, z0, z1) => box([0, y0, z0], [16, y0 + 8, z1], boardFaces);
    const stick = box([7.33, 0, 7.33], [8.67, 9.33, 8.67], { south: face(c(2, 16, 2, 14)), north: face(c(6, 16, 2, 14)), west: face(c(0, 16, 2, 14)), east: face(c(4, 16, 2, 14)), up: face(c(2, 14, 2, 2)), down: face(c(4, 14, 2, 2)) });
    const standing = model([stick, board(9.33, 7.33, 8.67)]);
    const wall = model([board(4.5, 14.67, 16)]);
    const sv = [];
    for (let r = 0; r < 16; r++) sv.push({ label: `rotation=${r},waterlogged=false`, parts: [{ m: standing, x: 0, y: r * 22.5 }] });
    setBlock(`${wood}_sign`, sv);
    // wall sign faces `facing` (the board's front points that way, away from the wall behind it)
    const wv = FACINGS.map(([f, y]) => ({ label: `facing=${f},waterlogged=false`, parts: [{ m: wall, x: 0, y: (y + 180) % 360 }] }));
    setBlock(`${wood}_wall_sign`, wv);
  }

  // ---------------------------------------------------------------- banners (plain colour; patterns are not drawn)
  if (has('entity/banner_base')) {
    const c = (x, y, w, h) => crop('entity/banner_base', x, y, w, h, 64);
    const cloth = (y0, y1, z0, z1) => box([1.33, y0, z0], [14.67, y1, z1], {
      south: face(c(1, 1, 20, 40), 0, 1), north: face(c(22, 1, 20, 40), 0, 1), west: face(c(0, 1, 1, 40), 0, 1), east: face(c(21, 1, 1, 40), 0, 1), up: face(c(1, 0, 20, 1), 0, 1), down: face(c(21, 0, 20, 1), 0, 1) });
    const bar = (y0, y1, z0, z1) => box([1.33, y0, z0], [14.67, y1, z1], {
      south: face(c(2, 44, 20, 2)), north: face(c(24, 44, 20, 2)), west: face(c(0, 44, 2, 2)), east: face(c(22, 44, 2, 2)), up: face(c(2, 42, 20, 2)), down: face(c(22, 42, 20, 2)) });
    const pole = box([7.33, 0, 7.33], [8.67, 28, 8.67], { south: face(c(46, 2, 2, 42)), north: face(c(50, 2, 2, 42)), west: face(c(44, 2, 2, 42)), east: face(c(48, 2, 2, 42)), up: face(c(46, 0, 2, 2)), down: face(c(48, 0, 2, 2)) });
    const standing = model([pole, bar(28, 29.33, 7.33, 8.67), cloth(1.33, 28, 8.67, 9.33)]);
    const wall = model([bar(14.67, 16, 14, 15.33), cloth(-12, 14.67, 13.33, 14)]);
    for (const color of COLORS) {
      const sv = [];
      for (let r = 0; r < 16; r++) sv.push({ label: `rotation=${r}`, parts: [{ m: standing, x: 0, y: r * 22.5 }] });
      setBlock(`${color}_banner`, sv);
      setBlock(`${color}_wall_banner`, FACINGS.map(([f, y]) => ({ label: `facing=${f}`, parts: [{ m: wall, x: 0, y: (y + 180) % 360 }] })));
    }
  }
  return { added: added.length, missing };
}

module.exports = { buildEntityBlocks, COLORS };
