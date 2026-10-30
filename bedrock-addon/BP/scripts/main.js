// Build Blueprint for Minecraft Bedrock.
// Pick a build (any structure in a behavior pack or saved in the world), put it where you want it, then build it
// layer by layer: colored markers show where the blocks of the layer you're on go, a big yellow marker shows the
// next one, red shows a wrong block, and the line above the hotbar says what to place and where.
// Open the menu with the Blueprint item (paper + blue dye) or /bb:menu.
import { world, system, BlockPermutation, MolangVariableMap, GameMode, CommandPermissionLevel, CustomCommandStatus, ItemStack } from '@minecraft/server';
import { ActionFormData, ModalFormData } from '@minecraft/server-ui';
import { colorOf } from './colors.js';
import { rotateStates, rotatePos } from './rotate.js';

const TICKS = 10;          // markers and the hint line every half second
const RANGE = 32;          // markers this far from the player
const MAX_MARKERS = 300;   // per player, per half second
const CHECK = ['minecraft:cardinal_direction', 'minecraft:facing_direction', 'facing_direction', 'weirdo_direction', 'upside_down_bit',
  'pillar_axis', 'minecraft:vertical_half', 'direction', 'ground_sign_direction', 'door_hinge_bit', 'upper_block_bit',
  'head_piece_bit', 'minecraft:block_face', 'top_slot_bit', 'hanging', 'attached_bit', 'torch_facing_direction', 'lever_direction'];
const SKIP = new Set(['minecraft:air', 'minecraft:structure_void', 'minecraft:light_block_0']);

// ---------------------------------------------------------------- builds (read from structures, kept in memory)
const builds = new Map();    // id -> { id, name, sx, sy, sz, cells: [{x, y, z, name, states}] by layer }
const loading = new Map();   // id -> [callbacks]

function niceName(id) {
  const p = id.includes(':') ? id.slice(id.indexOf(':') + 1) : id;
  return p.split('/').pop().replace(/[_-]+/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()).trim() || id;
}
function buildIds() {
  const m = world.structureManager, ids = new Set();
  try { for (const id of m.getPackStructureIds()) ids.add(id); } catch {}
  try { for (const id of m.getWorldStructureIds()) ids.add(id); } catch {}
  // ours and the player's (namespace:name); Minecraft's own structure pieces have no namespace
  return [...ids].filter((id) => id.includes(':') && !id.startsWith('minecraft:')).sort((a, b) => niceName(a).localeCompare(niceName(b)));
}

/** Reads a structure into a build, a bit every tick so the game doesn't stall. */
function loadBuild(id, done) {
  if (builds.has(id)) { done(builds.get(id)); return; }
  if (loading.has(id)) { loading.get(id).push(done); return; }
  loading.set(id, [done]);
  system.runJob((function* () {
    let b = null;
    try {
      const s = world.structureManager.get(id);
      if (s) {
        const { x: sx, y: sy, z: sz } = s.size;
        const layers = [];
        let n = 0;
        for (let y = 0; y < sy; y++) {
          const layer = [];
          for (let x = 0; x < sx; x++) for (let z = 0; z < sz; z++) {
            const p = s.getBlockPermutation({ x, y, z });
            if (p && !SKIP.has(p.type.id)) layer.push({ x, y, z, name: p.type.id, states: p.getAllStates() });
            if (++n % 1500 === 0) yield;
          }
          layers.push(layer);
        }
        b = { id, name: niceName(id), sx, sy, sz, layers, total: layers.reduce((a, l) => a + l.length, 0) };
        builds.set(id, b);
      }
    } catch (e) { console.warn('[Build Blueprint] could not read ' + id + ': ' + e); }
    for (const cb of loading.get(id) || []) cb(b);
    loading.delete(id);
  })());
}

// ---------------------------------------------------------------- the player's session (saved on the player)
const sessions = new Map();  // player id -> { id, ox, oy, oz, turns, locked, layer }
function save(player) {
  const s = sessions.get(player.id);
  try { player.setDynamicProperty('bb:session', s ? JSON.stringify(s) : undefined); } catch {}
}
function restore(player) {
  try {
    const raw = player.getDynamicProperty('bb:session');
    if (typeof raw === 'string') { const s = JSON.parse(raw); sessions.set(player.id, s); loadBuild(s.id, () => {}); }
  } catch {}
}

/** Size on the ground after turning. */
function footprint(b, turns) { return turns % 2 ? { w: b.sz, d: b.sx } : { w: b.sx, d: b.sz }; }

/** A block of the build where it goes in the world (turned), and its turned states. */
function worldCell(s, b, c) {
  const p = rotatePos(c.x, c.z, b.sx, b.sz, s.turns);
  return { x: s.ox + p.x, y: s.oy + c.y, z: s.oz + p.z, name: c.name, states: s.turns ? rotateStates(c.states, s.turns, c.name) : c.states };
}

/** Puts the build in front of the player, turned to face them. */
function placeInFront(player, s, b) {
  const r = player.getRotation().y;   // 0 = south, 90 = west, 180 = north, -90 = east
  const face = Math.round((((r % 360) + 360) % 360) / 90) % 4;   // 0 S, 1 W, 2 N, 3 E
  const loc = player.location, px = Math.floor(loc.x), py = Math.floor(loc.y), pz = Math.floor(loc.z);
  s.turns = (face + 2) % 4;   // the build's front looks back at the player
  const { w, d } = footprint(b, s.turns);
  const gap = 2;
  if (face === 0) { s.ox = px - Math.floor(w / 2); s.oz = pz + gap; }
  else if (face === 2) { s.ox = px - Math.floor(w / 2); s.oz = pz - gap - d + 1; }
  else if (face === 1) { s.ox = px - gap - w + 1; s.oz = pz - Math.floor(d / 2); }
  else { s.ox = px + gap; s.oz = pz - Math.floor(d / 2); }
  s.oy = py;
}

const base = (n) => n.replace('minecraft:flowing_', 'minecraft:');   // flowing water / lava turn into still ones
function matches(block, cell) {
  if (!block || base(block.typeId) !== base(cell.name)) return false;
  const have = block.permutation.getAllStates();
  for (const k of CHECK) if (k in cell.states && k in have && have[k] !== cell.states[k]) return false;
  return true;
}
function isEmpty(block) { return !block || block.isAir || block.isLiquid; }

// ---------------------------------------------------------------- markers + hint line, every half second
const molang = new Map();
function colorVars(name, a) {
  const k = name + a;
  let m = molang.get(k);
  if (!m) { const c = colorOf(name); m = new MolangVariableMap(); m.setColorRGBA('variable.color', { red: c[0], green: c[1], blue: c[2], alpha: a }); molang.set(k, m); }
  return m;
}
let YELLOW, RED;   // made on first use (not allowed while the world is still starting)
function fixed() {
  if (!YELLOW) {
    YELLOW = new MolangVariableMap(); YELLOW.setColorRGBA('variable.color', { red: 1, green: 0.9, blue: 0.2, alpha: 0.9 });
    RED = new MolangVariableMap(); RED.setColorRGBA('variable.color', { red: 1, green: 0.15, blue: 0.15, alpha: 0.9 });
  }
}

function dirWords(player, x, y, z) {
  const l = player.location, dx = x + 0.5 - l.x, dz = z + 0.5 - l.z, dy = y - Math.floor(l.y);
  const yaw = (player.getRotation().y * Math.PI) / 180, fx = -Math.sin(yaw), fz = Math.cos(yaw);
  const ahead = Math.round(dx * fx + dz * fz), right = Math.round(dx * -fz + dz * fx);
  const out = [];
  if (ahead) out.push(Math.abs(ahead) + (ahead > 0 ? ' ahead' : ' behind'));
  if (right) out.push(Math.abs(right) + (right > 0 ? ' right' : ' left'));
  if (dy) out.push(Math.abs(dy) + (dy > 0 ? ' up' : ' down'));
  return out.length ? out.join(', ') : 'right here';
}
const label = (id) => niceName(id);

function tick(player) {
  fixed();
  const s = sessions.get(player.id);
  if (!s) return;
  const b = builds.get(s.id);
  if (!b) { loadBuild(s.id, () => {}); return; }
  const dim = player.dimension, l = player.location;
  const near = (c) => Math.abs(c.x - l.x) <= RANGE && Math.abs(c.y - l.y) <= RANGE && Math.abs(c.z - l.z) <= RANGE;
  if (!s.locked) {   // not placed yet: outline of the box + the bottom layer
    const { w, d } = footprint(b, s.turns);
    let n = 0;
    for (let i = 0; i <= w; i += 2) for (const z of [0, d]) for (const y of [0, b.sy]) { if (n++ > MAX_MARKERS) break; player.spawnParticle('bb:ghost', { x: s.ox + i, y: s.oy + y, z: s.oz + z }, YELLOW); }
    for (let i = 0; i <= d; i += 2) for (const x of [0, w]) for (const y of [0, b.sy]) { if (n++ > MAX_MARKERS) break; player.spawnParticle('bb:ghost', { x: s.ox + x, y: s.oy + y, z: s.oz + i }, YELLOW); }
    for (let i = 0; i <= b.sy; i += 2) for (const x of [0, w]) for (const z of [0, d]) { if (n++ > MAX_MARKERS) break; player.spawnParticle('bb:ghost', { x: s.ox + x, y: s.oy + i, z: s.oz + z }, YELLOW); }
    for (const c of b.layers[0] || []) { if (n++ > MAX_MARKERS) break; const wc = worldCell(s, b, c); player.spawnParticle('bb:ghost', { x: wc.x + 0.5, y: wc.y + 0.5, z: wc.z + 0.5 }, colorVars(c.name, 0.75)); }
    player.onScreenDisplay.setActionBar(`§e${b.name}§r  ·  not placed yet  ·  use the Blueprint to move, turn or place it`);
    return;
  }
  // building: the layer you're on
  const layer = b.layers[s.layer] || [];
  let left = 0, wrong = 0, best = null, bestD = 1e9, shown = 0;
  for (const c of layer) {
    const wc = worldCell(s, b, c);
    let blk; try { blk = dim.getBlock(wc); } catch { blk = undefined; }
    if (blk && matches(blk, wc)) continue;
    left++;
    const bad = blk && !isEmpty(blk);
    if (bad) wrong++;
    const dd = (wc.x + 0.5 - l.x) ** 2 + (wc.y - l.y) ** 2 + (wc.z + 0.5 - l.z) ** 2;
    if (dd < bestD) { bestD = dd; best = wc; }
    if (shown < MAX_MARKERS && near(wc)) { shown++; player.spawnParticle(bad ? 'bb:wrong' : 'bb:ghost', { x: wc.x + 0.5, y: wc.y + 0.5, z: wc.z + 0.5 }, bad ? RED : colorVars(c.name, 0.8)); }
  }
  if (left === 0) {   // layer done: next one, or the whole build is done
    if (s.layer < b.sy - 1) {
      s.layer++; save(player);
      while (s.layer < b.sy - 1 && !(b.layers[s.layer] || []).length) s.layer++;
      player.playSound('random.orb');
      player.onScreenDisplay.setActionBar(`§aLayer done!§r Now layer ${s.layer + 1} of ${b.sy}`);
    } else if (!s.done) {
      s.done = true; save(player);
      player.playSound('random.levelup');
      player.onScreenDisplay.setTitle('§aBuild complete!', { subtitle: `${b.total} blocks`, fadeInDuration: 10, stayDuration: 60, fadeOutDuration: 20 });
    }
    return;
  }
  if (best) player.spawnParticle('bb:next', { x: best.x + 0.5, y: best.y + 0.5, z: best.z + 0.5 }, YELLOW);
  const placed = layer.length - left;
  player.onScreenDisplay.setActionBar(`Layer ${s.layer + 1}/${b.sy}  ·  ${placed}/${layer.length}` + (best ? `  ·  Next: §e${label(best.name)}§r ${dirWords(player, best.x, best.y, best.z)}` : '') + (wrong ? `  ·  §c${wrong} wrong (red)§r` : ''));
}

system.runInterval(() => {
  for (const p of world.getAllPlayers()) { try { tick(p); } catch (e) { /* one bad tick shouldn't stop the markers */ } }
}, TICKS);

// ---------------------------------------------------------------- what the build needs
function needs(player, s, b, onlyLayer) {
  const want = new Map();
  const dim = player.dimension;
  const layers = onlyLayer ? [b.layers[s.layer] || []] : b.layers;
  for (const layer of layers) for (const c of layer) {
    const wc = worldCell(s, b, c);
    let blk; try { blk = dim.getBlock(wc); } catch {}
    if (s.locked && blk && matches(blk, wc)) continue;
    let item = c.name;
    try { const st = BlockPermutation.resolve(c.name, c.states).getItemStack(1); if (st) item = st.typeId; } catch {}
    want.set(item, (want.get(item) || 0) + 1);
  }
  const have = new Map();
  try {
    const inv = player.getComponent('minecraft:inventory').container;
    for (let i = 0; i < inv.size; i++) { const it = inv.getItem(i); if (it) have.set(it.typeId, (have.get(it.typeId) || 0) + it.amount); }
  } catch {}
  return [...want.entries()].sort((a, b) => b[1] - a[1]).map(([id, n]) => ({ id, need: n, have: have.get(id) || 0 }));
}

// ---------------------------------------------------------------- auto-build (Creative)
const undo = new Map();   // player id -> [{x, y, z, perm}]

/** Places the cells (bottom first), then once more for any that fell off (a door's bottom half, a hanging lantern
 *  placed before the block it hangs from...). Calls back with how many were placed. Yields so the game keeps running. */
function* placeCells(dim, cells, before, keepChests) {
  let count = 0, n = 0;
  for (let pass = 0; pass < 2; pass++) {
    for (const wc of cells) {
      try {
        const blk = dim.getBlock(wc);
        if (!blk || matches(blk, wc)) continue;
        if (keepChests && blk.getComponent('minecraft:inventory')) continue;   // don't wipe out chests
        if (pass === 0 && before) before.push({ x: wc.x, y: wc.y, z: wc.z, perm: blk.permutation });
        let perm; try { perm = BlockPermutation.resolve(wc.name, wc.states); } catch { perm = BlockPermutation.resolve(wc.name); }
        blk.setPermutation(perm); if (pass === 0) count++;
      } catch {}
      if (++n % 150 === 0) yield;
    }
  }
  return count;
}
function autoBuild(player, s, b, onlyLayer) {
  if (player.getGameMode() !== GameMode.Creative) { player.sendMessage('§cAuto-build only works in Creative mode.'); return; }
  const dim = player.dimension, before = [];
  const layers = onlyLayer ? [b.layers[s.layer] || []] : b.layers;
  undo.set(player.id, before);
  const cells = [];
  for (const layer of layers) for (const c of layer) cells.push(worldCell(s, b, c));
  system.runJob((function* () {
    const count = yield* placeCells(dim, cells, before, true);
    player.sendMessage(`§aAuto-build finished: ${count} blocks placed.§r Blueprint > Undo auto-build takes it back.`);
  })());
}
function undoAuto(player) {
  const list = undo.get(player.id);
  if (!list || !list.length) { player.sendMessage('Nothing to undo.'); return; }
  undo.delete(player.id);
  const dim = player.dimension;
  system.runJob((function* () {
    let n = 0;
    for (const u of list.reverse()) { try { dim.getBlock(u)?.setPermutation(u.perm); } catch {} if (++n % 150 === 0) yield; }
    player.sendMessage('Undone.');
  })());
}

// ---------------------------------------------------------------- the menu
async function openMenu(player) {
  const s = sessions.get(player.id), b = s && builds.get(s.id);
  const f = new ActionFormData().title('Build Blueprint');
  const acts = [];
  const add = (text, fn) => { f.button(text); acts.push(fn); };
  if (!s) {
    f.body('Pick a build to make. It shows up in front of you as markers, then you build it layer by layer.');
    add('Pick a build', () => pickBuild(player));
  } else if (!b) {
    f.body('Loading the build...');
    add('OK', () => {});
  } else {
    const where = s.locked ? `Building layer ${s.layer + 1} of ${b.sy}` : 'Not placed yet: move or turn it, then Place it';
    f.body(`§e${b.name}§r  (${b.sx} x ${b.sy} x ${b.sz}, ${b.total} blocks)\n${where}`);
    if (!s.locked) {
      add('Place it (start building)', () => { s.locked = true; s.layer = 0; s.done = false; while (s.layer < b.sy - 1 && !(b.layers[s.layer] || []).length) s.layer++; save(player); player.sendMessage('Placed! Build the layer the markers show; the next layer comes by itself.'); });
      add('Move it in front of me', () => { placeInFront(player, s, b); save(player); });
      add('Turn it', () => { const { w, d } = footprint(b, s.turns); s.ox += Math.floor((w - d) / 2); s.oz += Math.floor((d - w) / 2); s.turns = (s.turns + 1) % 4; save(player); reopen(player); });
      add('Up 1', () => { s.oy++; save(player); reopen(player); });
      add('Down 1', () => { s.oy--; save(player); reopen(player); });
    } else {
      add('Blocks this layer needs', () => showNeeds(player, s, b, true));
      add('Blocks the whole build needs', () => showNeeds(player, s, b, false));
      add('Layer up', () => { s.layer = Math.min(b.sy - 1, s.layer + 1); s.done = false; save(player); });
      add('Layer down', () => { s.layer = Math.max(0, s.layer - 1); s.done = false; save(player); });
      add('Unlock (move it again)', () => { s.locked = false; save(player); });
      if (player.getGameMode() === GameMode.Creative) {
        add('Auto-build this layer (Creative)', () => autoBuild(player, s, b, true));
        add('Auto-build everything (Creative)', () => autoBuild(player, s, b, false));
        if ((undo.get(player.id) || []).length) add('Undo auto-build', () => undoAuto(player));
      }
    }
    add('Pick another build', () => pickBuild(player));
    add('Remove this build', () => { sessions.delete(player.id); save(player); player.sendMessage('Build removed. Pick one again any time.'); });
  }
  const r = await f.show(player);
  if (r.canceled || r.selection === undefined) return;
  acts[r.selection]?.();
}
function reopen(player) { system.runTimeout(() => openMenu(player), 2); }

async function pickBuild(player) {
  const ids = buildIds();
  if (!ids.length) {
    const f = new ActionFormData().title('No builds yet').body('Builds come from:\n- the Block Blueprint app on PC: open a build, press "Save for Bedrock" and open the file\n- structures you save in this world with a structure block\nThen use the Blueprint again.').button('OK');
    await f.show(player); return;
  }
  let list = ids;
  if (ids.length > 20) {
    const q = await new ModalFormData().title('Find a build').textField(`${ids.length} builds. Type part of a name (or leave empty for all):`, 'house, tower, ship...').show(player);
    if (q.canceled) return;
    const t = String(q.formValues?.[0] || '').toLowerCase().trim();
    if (t) list = ids.filter((id) => niceName(id).toLowerCase().includes(t) || id.toLowerCase().includes(t));
    if (!list.length) { player.sendMessage('No build matches "' + t + '".'); return; }
  }
  list = list.slice(0, 100);
  const f = new ActionFormData().title('Pick a build');
  for (const id of list) f.button(niceName(id) + '\n§8' + id);
  const r = await f.show(player);
  if (r.canceled || r.selection === undefined) return;
  const id = list[r.selection];
  player.onScreenDisplay.setActionBar('Loading ' + niceName(id) + '...');
  loadBuild(id, (b) => {
    if (!b) { player.sendMessage('§cCould not read that build.'); return; }
    const s = { id, ox: 0, oy: 0, oz: 0, turns: 0, locked: false, layer: 0 };
    placeInFront(player, s, b);
    sessions.set(player.id, s); save(player);
    player.sendMessage(`§e${b.name}§r is in front of you (the yellow box). Use the Blueprint to move or turn it, then Place it.`);
  });
}

async function showNeeds(player, s, b, onlyLayer) {
  const rows = needs(player, s, b, onlyLayer);
  const lines = rows.length ? rows.map((r) => `${r.have >= r.need ? '§a' : '§c'}${label(r.id)}§r  ${r.have}/${r.need}`) : ['§aNothing left - you have placed it all!'];
  await new ActionFormData().title(onlyLayer ? `Layer ${s.layer + 1} needs` : 'The whole build needs').body('have / need\n\n' + lines.join('\n')).button('OK').show(player);
}

// ---------------------------------------------------------------- ways in: the Blueprint item, /bb:menu
world.afterEvents.itemUse.subscribe((e) => { if (e.itemStack?.typeId === 'bb:blueprint') openMenu(e.source); });
system.beforeEvents.startup.subscribe((init) => {
  init.customCommandRegistry.registerCommand(
    { name: 'bb:menu', description: 'Open the Build Blueprint menu', permissionLevel: CommandPermissionLevel.Any, cheatsRequired: false },
    (origin) => { const p = origin.sourceEntity; if (p && p.typeId === 'minecraft:player') system.run(() => openMenu(p)); return { status: CustomCommandStatus.Success }; });
});
world.afterEvents.playerSpawn.subscribe((e) => {
  const p = e.player;
  restore(p);
  if (e.initialSpawn && !p.getDynamicProperty('bb:gotItem')) {   // a Blueprint for everyone, once
    try { p.getComponent('minecraft:inventory').container.addItem(new ItemStack('bb:blueprint', 1)); p.setDynamicProperty('bb:gotItem', true); } catch {}
    p.sendMessage('§eBuild Blueprint:§r use the §eBlueprint§r item (or type /bb:menu) to pick a build.');
  }
});

// ---------------------------------------------------------------- self test (only from the server console: scriptevent bb:selftest)
system.afterEvents.scriptEventReceive.subscribe((e) => {
  if (e.id !== 'bb:selftest') return;
  const dim = world.getDimension('overworld');
  const ids = buildIds();
  console.warn('BBTEST builds ' + ids.length + ': ' + ids.join(', '));
  // 16 spots, 40 apart (one ticking area of 160 x 160): every build straight, the first two also turned
  const jobs = ids.map((id) => [id, 0]).concat(ids.slice(0, 2).map((id) => [id, 1])).slice(0, 16);
  let pending = 0, slot = 0;
  for (const [id, turns] of jobs) {
    {
      pending++;
      const ox = (slot % 4) * 40, oz = Math.floor(slot / 4) * 40; slot++;
      loadBuild(id, (b) => {
        if (!b) { console.warn('BBTEST ' + id + ' FAIL could not read'); if (--pending === 0) console.warn('BBTEST done'); return; }
        const s = { id, ox, oy: 100, oz, turns, locked: true, layer: 0 };
        system.runJob((function* () {
          let n = 0;
          const stone = BlockPermutation.resolve('minecraft:stone');   // a floor under it, so sand and gravel stay put
          for (let x = 0; x < 40; x++) for (let z = 0; z < 40; z++) { try { dim.getBlock({ x: ox + x, y: 99, z: oz + z }).setPermutation(stone); } catch {} }
          const cells = []; for (const layer of b.layers) for (const c of layer) cells.push(worldCell(s, b, c));
          const placed = yield* placeCells(dim, cells, null, false), bad = 0;
          let ok = 0, miss = 0; const missEx = [];
          for (const layer of b.layers) for (const c of layer) {
            const wc = worldCell(s, b, c);
            let blk; try { blk = dim.getBlock(wc); } catch {}
            if (matches(blk, wc)) ok++; else { miss++; if (missEx.length < 3) missEx.push(`${wc.name}${JSON.stringify(wc.states)} got ${blk ? blk.typeId + JSON.stringify(blk.permutation.getAllStates()) : 'nothing'}`); }
            if (++n % 300 === 0) yield;
          }
          const r = needs({ dimension: dim, getComponent: () => undefined }, { ...s, locked: false }, b, false);
          console.warn(`BBTEST ${id} turn ${turns}: ${b.sx}x${b.sy}x${b.sz} ${b.total} blocks, placed ${placed} (failed ${bad}), match ${ok}/${b.total}${miss ? ' MISS ' + missEx.join(' | ') : ''}, item kinds ${r.length}`);
          if (--pending === 0) console.warn('BBTEST done');
        })());
      });
    }
  }
}, { namespaces: ['bb'] });
