import * as THREE from 'three';
import { OrbitControls } from './vendor/OrbitControls.js';
import { buildAtlas } from './atlas.js';
import { Baker, buildWorldGeometry, buildBlockGeometry, buildMultiGeometry, encode, decode, inRange, DIR_VEC } from './bake.js';
import { encodeBlueprint } from './export.js';
import { simulate, useBlock } from './redstone.js';
import { schematicToBuild, buildToSchem, isGzip, isSchematicName, dataVersionFor } from './schem.js';
import { bedrockMap, buildToMcpack } from './bedrock.js';

THREE.ColorManagement.enabled = false; // vanilla-style: textures * shade, no color-space conversion

const api = window.api;
window.__bbStarted = true;
// Loading screen: show which step we're on, so it's clear the app is working.
const LSTEPS = ['start', 'tex', 'pics', 'list', 'view'];
function loadStep(step, msg, sub) {
  window.__bbLast = Date.now();
  const i = LSTEPS.indexOf(step);
  document.querySelectorAll('#loadSteps li').forEach((li, j) => { li.className = j < i ? 'done' : j === i ? 'now' : ''; });
  if (msg) document.getElementById('loadMsg').textContent = msg;
  if (i <= 1) loadPct(i === 0 ? 0.02 : 0.05);
  document.getElementById('loadSub').innerHTML = sub || '&nbsp;';
  const slow = document.getElementById('loadSlow'); if (slow && !slow.classList.contains('bad')) slow.className = 'hidden';
}
function loadPct(p) { const b = document.getElementById('loadBar'); if (!b) return; window.__bbLast = Date.now(); b.style.width = Math.round(p * 100) + '%'; }
loadStep('start', 'Starting up…');
const $ = (s) => document.querySelector(s);
const store = {
  get(k, d) { try { const v = localStorage.getItem('bb.' + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem('bb.' + k, JSON.stringify(v)); } catch { /* ignore */ } },
};
function toast(msg, ms = 1800) {
  const t = $('#toast'); t.textContent = msg; t.classList.add('show');
  clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.remove('show'), ms);
}
const pretty = (n) => n.split('_').map((w) => w[0].toUpperCase() + w.slice(1)).join(' ');

// ---------------------------------------------------------------- setup flow
async function ensureAssets() {
  loadStep('tex', 'Checking your Minecraft textures…', 'Only slow the first time after an update.');
  let st = await api.status();
  if (st.ready) return st;
  const el = $('#setup'); el.classList.remove('hidden');
  $('#loading').classList.add('hidden');
  $('#setupMsg').textContent = `First-time setup: get the Minecraft ${st.target} block textures onto this computer.`;
  $('#sDownload').textContent = `⬇ Download Minecraft ${st.target} from Mojang (official)`;
  if (api.isPhone) {
    ['#sDownload', '#sInstalled', '#sFolder'].forEach((q) => $(q).classList.add('hidden'));
    $('#setupMsg').innerHTML = `First-time setup: pick the Minecraft ${st.target} game file (<b>${st.target}.jar</b>) so the app can read the block pictures. It stays on this phone.<br><br>On your PC it is in <b>%APPDATA%\\.minecraft\\versions\\${st.target}</b>. Put it in iCloud Drive, OneDrive or AirDrop it, then choose it here.`;
    $('#sFile').textContent = '📦 Choose the Minecraft .jar…';
    $('#setup .small').textContent = 'The textures are read from your own Minecraft file and stay on this phone.';
  }
  api.onProgress((p) => {
    $('#setupBarWrap').classList.remove('hidden');
    $('#setupStage').textContent = p.stage; $('#setupBar').style.width = Math.round((p.pct || 0) * 100) + '%';
  });
  return new Promise((resolve) => {
    const btns = ['#sDownload', '#sInstalled', '#sFile', '#sFolder'].map($);
    const run = (fn) => async () => {
      $('#setupErr').textContent = '';
      btns.forEach((b) => (b.disabled = true));
      const r = await fn();
      btns.forEach((b) => (b.disabled = false));
      if (r.ok) { el.classList.add('hidden'); $('#loading').classList.remove('hidden'); resolve(r.status); }
      else { $('#setupBarWrap').classList.add('hidden'); if (r.error !== 'cancelled') $('#setupErr').textContent = r.error; }
    };
    $('#sDownload').onclick = run(() => api.download());
    $('#sInstalled').onclick = run(() => api.useInstalled());
    $('#sFile').onclick = run(() => api.chooseFile());
    $('#sFolder').onclick = run(() => api.chooseFolder());
  });
}

// ---------------------------------------------------------------- main
async function main() {
  const status = await ensureAssets();
  const base = api.assetBase;
  loadStep('tex', 'Reading the block list…', status.version ? 'Minecraft ' + status.version + ' textures' : '');
  const catalog = api.getCatalog ? await api.getCatalog() : await (await fetch(base + 'catalog.json')).json();
  const nTex = catalog.textures.length;
  loadStep('pics', 'Loading block pictures…', '0 / ' + nTex);
  const atlas = await buildAtlas(base, catalog.textures, (p) => {
    loadPct(0.05 + p * 0.75);
    $('#loadSub').textContent = p < 0.5 ? Math.round(p * 2 * nTex) + ' / ' + nTex + ' pictures' : 'Packing them together…';
  }, api.loadTexture);
  loadStep('list', 'Building the block list…', 'Working out every block and its shapes'); loadPct(0.82);
  await new Promise((r) => setTimeout(r, 30));   // let the screen update before the heavy step

  const settings = Object.assign({ grid: true, shade: true, blue: true, zoom: 1.2, redstone: true }, store.get('settings', {}));
  if (api.setZoom) api.setZoom(settings.zoom);
  const baker = new Baker(catalog, atlas, settings.shade);

  // Palette list: every block that produced geometry.
  const HIDDEN = new Set(['barrier', 'light', 'structure_void', 'moving_piston', 'piston_head']);
  const allBlocks = Object.keys(catalog.blocks).filter((n) => !HIDDEN.has(n) && baker.bake(n, 0, 0).faces.length).sort();
  $('#verInfo').textContent = `Textures: ${status.version || 'custom'} (${status.source || ''}) · ${allBlocks.length} blocks`;

  loadStep('view', 'Setting up the 3D view…', allBlocks.length + ' blocks ready'); loadPct(0.92);
  // ---- three.js scene
  const canvas = $('#view');
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
  renderer.setClearColor(0x000000, 0);
  // Render at least 1.5x (supersampled) so edges stay sharp even on 100%-scaled screens.
  const isTouch = matchMedia('(pointer: coarse)').matches;
  renderer.setPixelRatio(Math.min(Math.max(devicePixelRatio, 1.5), isTouch ? 2 : 3));
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(50, 1, 0.1, 2000);
  camera.position.set(14, 12, 18);
  const controls = new OrbitControls(camera, canvas);
  controls.enableDamping = true; controls.dampingFactor = 0.12;
  controls.target.set(0, 2, 0);
  // Middle-drag orbits, right-drag pans, wheel zooms. Left button is reserved for building.
  controls.mouseButtons = { LEFT: null, MIDDLE: THREE.MOUSE.ROTATE, RIGHT: THREE.MOUSE.PAN };
  controls.touches = { ONE: THREE.TOUCH.ROTATE, TWO: THREE.TOUCH.DOLLY_PAN }; // phone: one finger orbits, two fingers pinch / pan
  canvas.style.touchAction = 'none';
  let lineMode = false; // phone: when on, one finger draws a line instead of orbiting

  const mat = new THREE.MeshBasicMaterial({ map: atlas.texture, vertexColors: true, alphaTest: 0.5 });
  const ghostMat = new THREE.MeshBasicMaterial({ map: atlas.texture, vertexColors: true, alphaTest: 0.1, transparent: true, opacity: 0.55, depthWrite: false });
  const transMat = new THREE.MeshBasicMaterial({ map: atlas.texture, vertexColors: true, transparent: true, alphaTest: 0.02, depthWrite: false });
  const worldMesh = new THREE.Mesh(new THREE.BufferGeometry(), [mat, transMat]);
  worldMesh.frustumCulled = false;
  scene.add(worldMesh);
  const ghost = new THREE.Mesh(new THREE.BufferGeometry(), ghostMat);
  ghost.visible = false; scene.add(ghost);

  const edgeGeo = new THREE.EdgesGeometry(new THREE.BoxGeometry(1.01, 1.01, 1.01));
  const mkBox = (color) => { const l = new THREE.LineSegments(edgeGeo, new THREE.LineBasicMaterial({ color })); l.visible = false; scene.add(l); return l; };
  const hoverBox = mkBox(0xffffff), boxA = mkBox(0xffd23f), boxB = mkBox(0xffd23f);
  const measureLine = new THREE.Line(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(), new THREE.Vector3()]), new THREE.LineBasicMaterial({ color: 0xffd23f }));
  measureLine.visible = false; scene.add(measureLine);

  const grid = new THREE.GridHelper(64, 64, 0xffffff, 0xffffff);
  grid.material.transparent = true; grid.material.opacity = 0.35; grid.material.depthWrite = false;
  grid.position.set(0, 0, 0);
  const axes = new THREE.GridHelper(64, 8, 0xffffff, 0xffffff); axes.material.transparent = true; axes.material.opacity = 0.35; axes.material.depthWrite = false;
  scene.add(grid, axes);

  // ---- state
  const world = new Map(); // key -> {b,v,r}
  let undoStack = [], redoStack = [];
  let tool = 'build';
  let buildY = 0;
  let layerVal = 1, allLayers = true;
  const hotbar = store.get('hotbar', null) || ['stone', 'cobblestone', 'oak_planks', 'bricks', 'glass', 'white_wool', 'oak_log', 'sand', 'stone_bricks'];
  for (let i = 0; i < 9; i++) if (!catalog.blocks[hotbar[i]]) hotbar[i] = allBlocks[i] || allBlocks[0];
  let sel = 0, curV = 0, curR = 0;
  let maxBlockY = -1;
  let dirty = true, measure = null; // {a,b}

  const visMaxY = () => (allLayers ? Infinity : layerVal - 1);
  const planeY = () => (allLayers ? buildY : Math.min(buildY, layerVal - 1));
  const curBlock = () => hotbar[sel];

  // ---- thumbnails
  const thumbR = new THREE.WebGLRenderer({ antialias: false, alpha: true });
  thumbR.outputColorSpace = THREE.LinearSRGBColorSpace; thumbR.setPixelRatio(1); thumbR.setSize(160, 160); thumbR.setClearColor(0, 0);
  const thumbScene = new THREE.Scene();
  const thumbCam = new THREE.OrthographicCamera(-0.92, 0.92, 0.92, -0.92, 0.1, 20);
  thumbCam.position.set(2.2, 1.9, 2.2); thumbCam.lookAt(0, 0, 0);
  const thumbMesh = new THREE.Mesh(new THREE.BufferGeometry(), mat); thumbMesh.position.set(-0.5, -0.5, -0.5);
  thumbScene.add(thumbMesh);
  const thumbCache = new Map();
  function thumb(name) {
    let u = thumbCache.get(name);
    if (u) return u;
    thumbMesh.geometry.dispose();
    thumbMesh.geometry = buildBlockGeometry(baker.bake(name, 0, 0));
    thumbR.render(thumbScene, thumbCam);
    u = thumbR.domElement.toDataURL();
    thumbCache.set(name, u);
    return u;
  }

  // ---- palette UI
  const CATS = [
    ['All', () => true],
    ['Stone', (n) => /stone|brick|cobble|deepslate|granite|diorite|andesite|tuff|basalt|blackstone|sandstone|prismarine|quartz|purpur|netherrack|calcite|dripstone|mud|obsidian|terracotta(?!.*_(white|orange|magenta))/.test(n)],
    ['Wood', (n) => /(oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|bamboo|crimson|warped|pale_oak)/.test(n) && !/leaves|sapling|propagule|roots|fungus|nylium|wart|stem$/.test(n) || /^(stripped_)/.test(n)],
    ['Natural', (n) => /dirt|grass_block|sand$|gravel|clay|snow|ice|moss|podzol|mycelium|water|lava|leaves|sapling|flower|fern|vine|mushroom|coral|kelp|seagrass|cactus|bamboo|sugar|melon|pumpkin|hay|sponge|azalea|dandelion|poppy|tulip|orchid|allium|lily|rose|peony|lilac|crop|wheat|carrots|potatoes|beetroots|cocoa|berry|bush/.test(n)],
    ['Ores', (n) => /ore$|raw_|(iron|gold|diamond|emerald|lapis|redstone|coal|copper|netherite|amethyst)_block|ancient_debris|budding/.test(n)],
    ['Colored', (n) => /(white|orange|magenta|light_blue|yellow|lime|pink|gray|light_gray|cyan|purple|blue|brown|green|red|black)_(wool|concrete|concrete_powder|terracotta|glazed_terracotta|stained_glass|stained_glass_pane|carpet|bed|banner|candle|shulker_box)/.test(n)],
    ['Redstone', (n) => /redstone|piston|observer|repeater|comparator|hopper|dropper|dispenser|lever|button|pressure_plate|rail|tnt|lamp|target|daylight|sculk|tripwire|note_block|jukebox|lectern|crafter/.test(n)],
    ['Functional', (n) => /table|furnace|smoker|blast|anvil|grindstone|loom|cartography|fletching|smithing|stonecutter|barrel|brewing|cauldron|enchanting|bookshelf|beacon|conduit|composter|ladder|door|trapdoor|fence|gate|torch|lantern|chain|bars|pane|glass|campfire|scaffolding|bell|flower_pot|frame/.test(n)],
  ];
  let curCat = 'All', query = '';
  const gridEl = $('#grid');
  const io = new IntersectionObserver((ents) => {
    for (const e of ents) if (e.isIntersecting) {
      const img = e.target.firstChild;
      if (!img.src) img.src = thumb(e.target.dataset.name);
      io.unobserve(e.target);
    }
  }, { root: gridEl, rootMargin: '200px' });
  function renderPalette() {
    io.disconnect(); gridEl.textContent = '';
    const catFn = CATS.find((c) => c[0] === curCat)[1];
    const q = query.trim().toLowerCase().replace(/\s+/g, '_');
    const list = allBlocks.filter((n) => (!q || n.includes(q)) && catFn(n));
    $('#palCount').textContent = `${list.length} block${list.length === 1 ? '' : 's'}`;
    const frag = document.createDocumentFragment();
    for (const n of list) {
      const d = document.createElement('div');
      d.className = 'slot' + (n === curBlock() ? ' sel' : ''); d.dataset.name = n; d.title = pretty(n);
      d.appendChild(document.createElement('img'));
      frag.appendChild(d);
    }
    gridEl.appendChild(frag);
    gridEl.querySelectorAll('.slot').forEach((s) => io.observe(s));
    gridEl.scrollTop = 0;
  }
  const catsEl = $('#cats');
  for (const [name] of CATS) {
    const b = document.createElement('button'); b.className = 'cat' + (name === curCat ? ' on' : ''); b.textContent = name;
    b.onclick = () => { curCat = name; catsEl.querySelectorAll('.cat').forEach((x) => x.classList.toggle('on', x === b)); renderPalette(); };
    catsEl.appendChild(b);
  }
  $('#search').addEventListener('input', (e) => { query = e.target.value; renderPalette(); });
  gridEl.addEventListener('click', (e) => {
    const s = e.target.closest('.slot'); if (!s) return;
    setSlotBlock(s.dataset.name);
    if (tool !== 'build') setTool('build');
    if (innerWidth <= 820 && !$('#palette').classList.contains('collapsed')) togglePalette(); // phone: close the sheet after choosing
  });

  // ---- hotbar
  const hotEl = $('#hotbar');
  function renderHotbar() {
    hotEl.textContent = '';
    hotbar.forEach((n, i) => {
      const d = document.createElement('div'); d.className = 'hslot' + (i === sel ? ' sel' : ''); d.title = pretty(n);
      d.innerHTML = `<span>${i + 1}</span><img src="${thumb(n)}">`;
      d.onclick = () => selectSlot(i);
      hotEl.appendChild(d);
    });
    updateCurrent();
  }
  function updateCurrent() {
    const n = curBlock(); const cnt = baker.variantCount(n);
    $('#curName').textContent = pretty(n);
    const lab = baker.variantLabel(n, curV);
    $('#curSub').textContent = [cnt > 1 ? `state: ${lab.replace(/,/g, ', ')} (V)` : '', `rotation ${curR * 90}° (R)`].filter(Boolean).join('  ·  ');
    gridEl.querySelectorAll('.slot.sel').forEach((x) => x.classList.remove('sel'));
    const s = gridEl.querySelector(`.slot[data-name="${n}"]`); if (s) s.classList.add('sel');
    updateGhost();
  }
  function selectSlot(i) { sel = i; curV = 0; renderHotbar(); }
  function setSlotBlock(n) { hotbar[sel] = n; curV = 0; store.set('hotbar', hotbar); renderHotbar(); }

  // ---- world edits
  function applyChanges(changes, record = true) {
    for (const c of changes) { if (c.next) world.set(c.k, c.next); else world.delete(c.k); }
    if (record) { undoStack.push(changes); if (undoStack.length > 500) undoStack.shift(); redoStack = []; }
    afterEdit();
  }
  function undo() { const c = undoStack.pop(); if (!c) return; redoStack.push(c); for (const x of [...c].reverse()) { if (x.prev) world.set(x.k, x.prev); else world.delete(x.k); } afterEdit(); }
  function redo() { const c = redoStack.pop(); if (!c) return; undoStack.push(c); for (const x of c) { if (x.next) world.set(x.k, x.next); else world.delete(x.k); } afterEdit(); }
  let saveT;
  function afterEdit() {
    dirty = true; recomputeBounds(); measure = null; hideMeasure();
    clearTimeout(saveT); saveT = setTimeout(() => store.set('autosave', serialize()), 400);
  }
  function recomputeBounds() {
    maxBlockY = -1; for (const k of world.keys()) { const y = decode(k).y; if (y > maxBlockY) maxBlockY = y; }
    const H = Math.max(1, maxBlockY + 1);
    const sl = $('#layer'); sl.max = H;
    if (allLayers) layerVal = H; else layerVal = Math.min(layerVal, H);
    sl.value = layerVal; updateLayerUI();
  }
  function updateLayerUI() {
    const H = Math.max(1, maxBlockY + 1);
    $('#layerLabel').textContent = allLayers ? 'All layers' : `Layer: ${layerVal}`;
    $('#firstLayer').style.display = allLayers && maxBlockY > 0 ? '' : 'none';
    $('#layer').max = H; $('#layer').value = allLayers ? H : layerVal;
    $('#levelPill').textContent = `Build level ${buildY}` + (allLayers || buildY < layerVal ? '' : ' (hidden)');
    dirty = true;
  }
  function serialize() {
    return JSON.stringify({ format: 'block-blueprint', version: 1, mc: status.version || null,
      blocks: [...world].map(([k, b]) => { const p = decode(k); return [p.x, p.y, p.z, b.b, baker.variantLabel(b.b, b.v), b.r]; }) });
  }
  // Codes copied from a Minecraft world carry every property (waterlogged=false, powered=false...).
  // Pick the variant whose own properties all match, preferring the one that matches the most.
  function closestVariant(variants, label) {
    if (!label || label === 'default') return 0;
    const want = new Map(label.split(',').filter(Boolean).map((p) => p.split('=')));
    let best = 0, bestN = -1;
    variants.forEach((q, i) => {
      if (q.label === 'default' || !q.label) { if (bestN < 0) { best = i; bestN = 0; } return; }
      const kv = q.label.split(',').filter(Boolean).map((p) => p.split('='));
      if (kv.every(([k, val]) => !want.has(k) || want.get(k) === val) && kv.filter(([k]) => want.has(k)).length > bestN) { best = i; bestN = kv.filter(([k]) => want.has(k)).length; }
    });
    return best;
  }
  function load(text) {
    const data = JSON.parse(text);
    if (data.format !== 'block-blueprint') throw new Error('Not a Block Blueprint file');
    world.clear(); let skipped = 0;
    for (let [x, y, z, name, label, r] of data.blocks) {
      x = Math.round(x); y = Math.round(y); z = Math.round(z);
      if (!catalog.blocks[name] || !inRange(x, y, z)) { skipped++; continue; }
      let v = catalog.blocks[name].variants.findIndex((q) => q.label === label); if (v < 0) v = closestVariant(catalog.blocks[name].variants, label);
      world.set(encode(x, y, z), { b: name, v, r: r || 0 });
    }
    undoStack = []; redoStack = []; allLayers = true; afterEdit(); fitView();
    if (skipped) toast(`${skipped} blocks were skipped (not in this Minecraft version)`, 3500);
  }

  // ---- picking (voxel DDA + build plane)
  const ray = new THREE.Raycaster(); const ndc = new THREE.Vector2();
  function pickAt(clientX, clientY) {
    const r = canvas.getBoundingClientRect();
    ndc.set(((clientX - r.left) / r.width) * 2 - 1, -((clientY - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(ndc, camera);
    const o = ray.ray.origin, d = ray.ray.direction, maxY = visMaxY();
    let x = Math.floor(o.x), y = Math.floor(o.y), z = Math.floor(o.z);
    const step = [Math.sign(d.x), Math.sign(d.y), Math.sign(d.z)];
    const tD = [Math.abs(1 / d.x), Math.abs(1 / d.y), Math.abs(1 / d.z)];
    const tM = [
      step[0] > 0 ? (x + 1 - o.x) / d.x : step[0] < 0 ? (x - o.x) / d.x : Infinity,
      step[1] > 0 ? (y + 1 - o.y) / d.y : step[1] < 0 ? (y - o.y) / d.y : Infinity,
      step[2] > 0 ? (z + 1 - o.z) / d.z : step[2] < 0 ? (z - o.z) / d.z : Infinity];
    let hit = null, tBlock = Infinity; const n = [0, 0, 0];
    const c = [x, y, z];
    for (let i = 0; i < 700; i++) {
      const ax = tM[0] < tM[1] ? (tM[0] < tM[2] ? 0 : 2) : (tM[1] < tM[2] ? 1 : 2);
      const t = tM[ax]; if (t > 500) break;
      c[ax] += step[ax]; tM[ax] += tD[ax]; n[0] = n[1] = n[2] = 0; n[ax] = -step[ax];
      if (inRange(c[0], c[1], c[2]) && c[1] <= maxY && world.has(encode(c[0], c[1], c[2]))) { hit = { x: c[0], y: c[1], z: c[2], n: n.slice() }; tBlock = t; break; }
    }
    let plane = null, tPlane = Infinity;
    if (d.y !== 0) {
      const t = (planeY() - o.y) / d.y;
      if (t > 0) { const px = o.x + d.x * t, pz = o.z + d.z * t; if (Math.abs(px) < 400 && Math.abs(pz) < 400) { plane = { x: Math.floor(px), y: planeY(), z: Math.floor(pz) }; tPlane = t; } }
    }
    return { hit: hit && tBlock <= tPlane ? hit : null, plane: !hit || tPlane < tBlock ? plane : null };
  }

  function targetFor(p) {
    if (tool === 'build') {
      let t = null;
      if (p.hit) t = { x: p.hit.x + p.hit.n[0], y: p.hit.y + p.hit.n[1], z: p.hit.z + p.hit.n[2] };
      else if (p.plane) t = p.plane;
      if (t && inRange(t.x, t.y, t.z) && t.y <= 255 && !world.has(encode(t.x, t.y, t.z))) return t;
      return null;
    }
    return p.hit ? { x: p.hit.x, y: p.hit.y, z: p.hit.z } : null;
  }

  let hoverT = null, ptr = null, ptrDirty = false;
  function updateGhost() {
    if (tool === 'build' && hoverT) {
      ghost.geometry.dispose(); ghost.geometry = buildBlockGeometry(baker.bake(curBlock(), curV, curR));
      ghost.position.set(hoverT.x, hoverT.y, hoverT.z); ghost.visible = true;
    } else ghost.visible = false;
  }
  function refreshHover() {
    if (!ptr || (drag && drag.active)) return;
    const p = pickAt(ptr.x, ptr.y); hoverT = targetFor(p);
    if (hoverT) {
      hoverBox.position.set(hoverT.x + 0.5, hoverT.y + 0.5, hoverT.z + 0.5); hoverBox.visible = true;
      hoverBox.material.color.set(tool === 'erase' ? 0xff5a5a : tool === 'measure' ? 0xffd23f : 0xffffff);
      $('#coords').textContent = `X ${hoverT.x}  Y ${hoverT.y}  Z ${hoverT.z}`;
    } else { hoverBox.visible = false; $('#coords').textContent = ''; }
    updateGhost();
  }

  // ---- measure
  const measureEl = $('#measureLabel');
  function hideMeasure() { boxA.visible = boxB.visible = measureLine.visible = false; measureEl.style.display = 'none'; }
  function doMeasure(cell) {
    if (!measure || measure.b) { measure = { a: cell }; hideMeasure(); boxA.position.set(cell.x + .5, cell.y + .5, cell.z + .5); boxA.visible = true; toast('Click a second block to measure'); return; }
    measure.b = cell; const { a, b } = measure;
    boxB.position.set(b.x + .5, b.y + .5, b.z + .5); boxB.visible = true;
    measureLine.geometry.setFromPoints([new THREE.Vector3(a.x + .5, a.y + .5, a.z + .5), new THREE.Vector3(b.x + .5, b.y + .5, b.z + .5)]); measureLine.visible = true;
    const dx = Math.abs(a.x - b.x), dy = Math.abs(a.y - b.y), dz = Math.abs(a.z - b.z);
    measure.text = `X ${dx}  Y ${dy}  Z ${dz}  ·  ${Math.hypot(dx, dy, dz).toFixed(1)} blocks`;
    measureEl.textContent = measure.text; measureEl.style.display = 'block';
  }
  const v3 = new THREE.Vector3();
  function placeMeasureLabel() {
    if (!measure || !measure.b) return;
    v3.set((measure.a.x + measure.b.x) / 2 + .5, (measure.a.y + measure.b.y) / 2 + .5, (measure.a.z + measure.b.z) / 2 + .5).project(camera);
    const r = canvas.getBoundingClientRect();
    measureEl.style.left = (v3.x * .5 + .5) * r.width + r.left + 'px'; measureEl.style.top = (-v3.y * .5 + .5) * r.height + r.top - 18 + 'px';
    measureEl.style.display = v3.z < 1 ? 'block' : 'none';
  }

  // ---- line placement: hold left button on a spot, drag, release -> a straight line of blocks.
  // The line follows the cursor on a vertical plane through the start block and snaps to one axis
  // (up/down is favoured), so dragging upward builds a tower.
  let drag = null; // {s, cells, active}
  // The line follows the cursor in SCREEN space: pick the axis (x / y / z) whose on-screen direction best
  // matches where the cursor moved from the start block, then extend exactly as far as the cursor reaches.
  const pj = new THREE.Vector3();
  function toPx(x, y, z, r) {
    pj.set(x, y, z).project(camera);
    return [(pj.x * 0.5 + 0.5) * r.width + r.left, (-pj.y * 0.5 + 0.5) * r.height + r.top, pj.z];
  }
  function lineCells(s, clientX, clientY) {
    const r = canvas.getBoundingClientRect();
    const cx = s.x + 0.5, cy = s.y + 0.5, cz = s.z + 0.5;
    const c0 = toPx(cx, cy, cz, r);
    const D = [clientX - c0[0], clientY - c0[1]];
    const dl = Math.hypot(D[0], D[1]);
    if (dl < 8) return [s];
    const axes = [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
    let best = null;
    axes.forEach((ax, i) => {
      const c1 = toPx(cx + ax[0], cy + ax[1], cz + ax[2], r);
      const v = [c1[0] - c0[0], c1[1] - c0[1]], vl = Math.hypot(v[0], v[1]);
      if (vl < 1e-3) return;
      const cos = Math.abs(D[0] * v[0] + D[1] * v[1]) / (dl * vl) * (i === 1 ? 1.12 : 1); // slight preference for up/down
      if (!best || cos > best.cos) best = { i, cos, ax };
    });
    if (!best) return [s];
    // walk along that axis (both directions) to the cell whose centre is closest to the cursor
    let bestK = 0, bestD = dl;
    for (const sg of [1, -1]) {
      for (let k = 1; k <= 64; k++) {
        const p = toPx(cx + sg * k * best.ax[0], cy + sg * k * best.ax[1], cz + sg * k * best.ax[2], r);
        const d = Math.hypot(clientX - p[0], clientY - p[1]);
        if (d < bestD) { bestD = d; bestK = sg * k; } else if (d > bestD + 40) break;
      }
    }
    const cells = [], n = Math.abs(bestK), sg = Math.sign(bestK) || 1, key = ['x', 'y', 'z'][best.i];
    for (let i = 0; i <= n; i++) { const c = { x: s.x, y: s.y, z: s.z }; c[key] += sg * i; cells.push(c); }
    return cells.filter((c) => inRange(c.x, c.y, c.z) && c.y <= 255 && !world.has(encode(c.x, c.y, c.z)));
  }
  function showLineGhost(cells) {
    ghost.geometry.dispose();
    ghost.geometry = buildMultiGeometry(baker.bake(curBlock(), curV, curR), cells);
    ghost.position.set(0, 0, 0); ghost.visible = cells.length > 0;
  }

  // ---- click handling
  let down = null;
  canvas.addEventListener('contextmenu', (e) => e.preventDefault());
  canvas.addEventListener('mousedown', (e) => { if (e.button === 1) e.preventDefault(); }); // no middle-click autoscroll
  const endDrag = () => { drag = null; controls.enabled = true; ptrDirty = true; if (tool === 'build') updateGhost(); };
  const touches = new Set(); // phone: fingers currently down
  canvas.addEventListener('pointerdown', (e) => {
    const isFinger = e.pointerType === 'touch';
    if (isFinger) {
      touches.add(e.pointerId);
      if (touches.size > 1) { down = null; endDrag(); showLineGhost([]); return; } // second finger: pinch / pan, never place
    }
    if (e.button === 0) {
      drag = null; // never keep a stale drag from an earlier press
      down = { x: e.clientX, y: e.clientY, t: performance.now(), b: 0, finger: isFinger };
      if (tool === 'build' && (!isFinger || lineMode)) {
        const s = targetFor(pickAt(e.clientX, e.clientY));
        if (s) { drag = { s, cells: [s], active: false }; if (isFinger) controls.enabled = false; try { canvas.setPointerCapture(e.pointerId); } catch { /* ignore */ } }
      }
    } else {
      // middle / right press: orbit or pan. It cancels any line in progress instead of corrupting it.
      if (drag) { drag = null; if (down && down.b === 0) down = null; showLineGhost([]); }
      down = { x: e.clientX, y: e.clientY, t: performance.now(), b: e.button };
    }
  });
  canvas.addEventListener('pointermove', (e) => {
    ptr = { x: e.clientX, y: e.clientY }; ptrDirty = true;
    if (drag && down && down.b === 0 && (e.buttons & 1) && Math.hypot(e.clientX - down.x, e.clientY - down.y) > 6) {
      drag.active = true; drag.cells = lineCells(drag.s, e.clientX, e.clientY); showLineGhost(drag.cells);
      const last = drag.cells[drag.cells.length - 1] || drag.s;
      hoverBox.position.set(last.x + 0.5, last.y + 0.5, last.z + 0.5); hoverBox.visible = true;
      $('#coords').textContent = `${drag.cells.length} block${drag.cells.length === 1 ? '' : 's'}`;
    } else if (drag && !(e.buttons & 1)) endDrag(); // button was released somewhere we did not see
  });
  canvas.addEventListener('pointerleave', () => { if (drag) return; ptr = null; hoverT = null; hoverBox.visible = false; updateGhost(); });
  canvas.addEventListener('pointercancel', (e) => { touches.delete(e.pointerId); down = null; endDrag(); });
  canvas.addEventListener('lostpointercapture', () => { if (drag && !drag.active) endDrag(); });
  addEventListener('blur', () => { down = null; endDrag(); });
  canvas.addEventListener('pointerup', (e) => {
    touches.delete(e.pointerId);
    controls.enabled = true;
    if (e.button === 0 && drag) {
      const d = drag; drag = null; down = null;
      try { canvas.releasePointerCapture(e.pointerId); } catch { /* ignore */ }
      const cells = d.active ? d.cells : [d.s];
      if (cells.length) applyChanges(cells.map((c) => ({ k: encode(c.x, c.y, c.z), prev: null, next: { b: curBlock(), v: curV, r: curR } })));
      if (cells.length > 1) toast(`Placed ${cells.length} blocks (Ctrl+Z undoes the whole line)`);
      ptrDirty = true; return;
    }
    if (!down || down.b !== e.button) return;
    const moved = Math.hypot(e.clientX - down.x, e.clientY - down.y), quick = performance.now() - down.t < 600;
    const btn = down.b; down = null;
    if (moved > (e.pointerType === 'touch' ? 10 : 5) || !quick) return;
    const p = pickAt(e.clientX, e.clientY);
    ptr = { x: e.clientX, y: e.clientY };
    if (btn === 1) { if (p.hit) pickBlock(p.hit); return; }
    if (btn === 2) { eraseAt(p); return; }
    if (tool === 'build') {
      const t = targetFor(p); // fingers have no hover: a tap places straight away (undo is one tap away)
      if (t) applyChanges([{ k: encode(t.x, t.y, t.z), prev: null, next: { b: curBlock(), v: curV, r: curR } }]);
    } else if (tool === 'turn' || tool === 'tilt') turnPlaced(tool === 'turn' ? 'rotate' : 'tilt');
    else if (tool === 'erase') eraseAt(p);
    else if (tool === 'use') { if (p.hit) useAt(p.hit); }
    else if (tool === 'pick') { if (p.hit) { pickBlock(p.hit); setTool('build'); } }
    else if (tool === 'measure') { if (p.hit) doMeasure({ x: p.hit.x, y: p.hit.y, z: p.hit.z }); }
    ptrDirty = true;
  });
  // Use tool: flip levers, press buttons, open doors / trapdoors / gates, change repeater delay...
  function variantWith(name, v, change) {
    const vs = catalog.blocks[name].variants, cur = parseProps(baker.variantLabel(name, v)), want = { ...cur, ...change };
    let best = -1, bestMiss = 1e9;
    vs.forEach((va, i) => { const q = parseProps(va.label); if (!Object.keys(change).every((k) => q[k] === change[k])) return; const miss = Object.keys(want).filter((k) => q[k] !== want[k]).length; if (miss < bestMiss) { bestMiss = miss; best = i; } });
    return best;
  }
  function useAt(h) {
    const k = encode(h.x, h.y, h.z), b = world.get(k); if (!b) return;
    const change = useBlock(b.b, baker.variantLabel(b.b, b.v));
    if (!change) { toast(`${pretty(b.b)} has nothing to use`); return; }
    const changes = [];
    const i = variantWith(b.b, b.v, change); if (i < 0) return;
    changes.push({ k, prev: b, next: { ...b, v: i } });
    if (/_door$/.test(b.b)) { // the other half of the door
      for (const dy of [1, -1]) { const k2 = encode(h.x, h.y + dy, h.z), b2 = world.get(k2); if (b2 && b2.b === b.b) { const j = variantWith(b2.b, b2.v, change); if (j >= 0) changes.push({ k: k2, prev: b2, next: { ...b2, v: j } }); } }
    }
    applyChanges(changes); ptrDirty = true;
  }
  function eraseAt(p) {
    if (!p.hit) return; const k = encode(p.hit.x, p.hit.y, p.hit.z); const prev = world.get(k);
    applyChanges([{ k, prev, next: null }]);
  }
  function pickBlock(h) {
    const b = world.get(encode(h.x, h.y, h.z)); if (!b) return;
    hotbar[sel] = b.b; curV = b.v; curR = b.r; store.set('hotbar', hotbar); renderHotbar(); toast(`Picked ${pretty(b.b)}`);
  }

  // ---- tools / keys / buttons
  function setTool(t) {
    tool = t; document.querySelectorAll('.tool').forEach((b) => b.classList.toggle('active', b.dataset.tool === t));
    if (t !== 'measure') { measure = null; hideMeasure(); }
    $('#hint').textContent = isTouch && tool === 'build' ? 'Tap: place · drag: orbit · two fingers: zoom / move · ↻ ⤴ ⋮ change the held block · ▮ line mode: drag to place a line' : {
      build: 'Click: place (hold + drag = line) · Right-click: remove · Middle-drag: orbit · Right-drag: pan · Wheel: zoom · WASD/QE: fly · R rotate · T tilt · Shift+R/T turn the block under the cursor · V state · [ ] level',
      turn: 'Click a placed block to rotate it', tilt: 'Click a placed block to tilt it', use: 'Click a lever or button to flip it · doors, trapdoors and gates open · repeaters change delay (live redstone preview)',
      erase: 'Click a block to remove it', pick: 'Click a block to copy it into the selected slot', measure: 'Click two blocks to measure the distance',
    }[t];
    refreshHover();
  }
  document.querySelectorAll('.tool').forEach((b) => (b.onclick = () => setTool(b.dataset.tool)));
  // ---- tilt (the "other axis" of R, like Roblox Studio's T): move to the next valid orientation
  const parseProps = (label) => (label === 'default' ? {} : Object.fromEntries(label.split(',').map((p) => p.split('='))));
  function tiltTarget(name, props) {
    const p = { ...props };
    if ('axis' in p) { p.axis = { y: 'z', z: 'x', x: 'y' }[p.axis]; return { p, key: 'axis' }; }
    if ('face' in p && { floor: 1, wall: 1, ceiling: 1 }[p.face]) { p.face = { floor: 'wall', wall: 'ceiling', ceiling: 'floor' }[p.face]; return { p, key: 'face' }; }
    if ('half' in p && (p.half === 'top' || p.half === 'bottom')) { p.half = p.half === 'top' ? 'bottom' : 'top'; return { p, key: 'half' }; }
    if (name.endsWith('_slab') && 'type' in p && p.type !== 'double') { p.type = p.type === 'top' ? 'bottom' : 'top'; return { p, key: 'type' }; }
    if ('facing' in p && catalog.blocks[name].variants.some((v) => /facing=(up|down)/.test(v.label))) {
      p.facing = { up: 'north', north: 'down', down: 'south', south: 'up' }[p.facing] || 'up'; return { p, key: 'facing' };
    }
    return null;
  }
  function tiltVariant(name, v) {
    const t = tiltTarget(name, parseProps(baker.variantLabel(name, v))); if (!t) return -1;
    let best = -1, bestMiss = 1e9;
    catalog.blocks[name].variants.forEach((va, i) => {
      const q = parseProps(va.label); if (q[t.key] !== t.p[t.key]) return;
      const miss = Object.keys(t.p).filter((k) => q[k] !== t.p[k]).length;
      if (miss < bestMiss) { bestMiss = miss; best = i; }
    });
    return best;
  }
  function tiltHeld() {
    const i = tiltVariant(curBlock(), curV);
    if (i < 0) { toast(`${pretty(curBlock())} can't be tilted`); return; }
    curV = i; updateCurrent();
  }
  function turnPlaced(kind) {
    if (!ptr) return;
    const h = pickAt(ptr.x, ptr.y).hit; if (!h) { toast('Point at a placed block first'); return; }
    const k = encode(h.x, h.y, h.z), b = world.get(k);
    let next;
    if (kind === 'rotate') next = { ...b, r: (b.r + 1) % 4 };
    else { const i = tiltVariant(b.b, b.v); if (i < 0) { toast(`${pretty(b.b)} can't be tilted`); return; } next = { ...b, v: i }; }
    applyChanges([{ k, prev: b, next }]); ptrDirty = true;
  }
  const cycle = (dir) => { const n = baker.variantCount(curBlock()); curV = (curV + dir + n) % n; updateCurrent(); };
  addEventListener('keydown', (e) => {
    if (!$('#tpl').classList.contains('hidden')) return;
    if (e.target.id === 'search') { if (e.key === 'Escape') e.target.blur(); return; }
    const k = e.key.toLowerCase();
    if ((e.ctrlKey || e.metaKey) && k === 'z') { e.preventDefault(); e.shiftKey ? redo() : undo(); return; }
    if ((e.ctrlKey || e.metaKey) && k === 'y') { e.preventDefault(); redo(); return; }
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    if (k >= '1' && k <= '9') selectSlot(+k - 1);
    else if (k === 'b') setTool('build'); else if (k === 'u') setTool('use'); else if (k === 'x') setTool('erase'); else if (k === 'p') setTool('pick'); else if (k === 'm') setTool('measure');
    else if (k === 'r') { e.shiftKey ? turnPlaced('rotate') : (curR = (curR + 1) % 4, updateCurrent()); }
    else if (k === 't') { e.shiftKey ? turnPlaced('tilt') : tiltHeld(); }
    else if (k === 'v') cycle(e.shiftKey ? -1 : 1);
    else if (k === '[' || k === 'pagedown') { buildY = Math.max(0, buildY - 1); syncLevel(); }
    else if (k === ']' || k === 'pageup') { buildY = Math.min(255, buildY + 1); syncLevel(); }
    else if (k === 'f') fitView();
    else if (k === 'g') { settings.grid = !settings.grid; applySettings(); }
    else if (k === 'tab') { e.preventDefault(); togglePalette(); }
    else if (k === 'escape') { measure = null; hideMeasure(); }
    else if (k === '/') { e.preventDefault(); $('#search').focus(); }
  });
  // ---- phone controls (shown only on narrow / touch screens)
  const lineBtn = $('#tbLine');
  $('#tbRot').onclick = () => { curR = (curR + 1) % 4; updateCurrent(); };
  $('#tbTilt').onclick = tiltHeld;
  $('#tbState').onclick = () => cycle(1);
  lineBtn.onclick = () => { lineMode = !lineMode; lineBtn.classList.toggle('on', lineMode); toast(lineMode ? 'Line mode: drag one finger to place a line' : 'Line mode off: drag orbits'); };
  $('#tbBlocks').onclick = () => togglePalette();
  function syncLevel() { updateLayerUI(); ptrDirty = true; }

  $('#btnUndo').onclick = undo; $('#btnRedo').onclick = redo;
  $('#btnSave').onclick = async () => { const r = await api.saveText('my-build.json', serialize()); if (r.ok) toast(r.note || 'Saved', r.note ? 4500 : 1800); };
  $('#btnOpen').onclick = async () => { const r = await api.openText(); if (r.ok) { try { load(r.text); toast('Opened'); } catch (err) { toast(err.message, 3500); } } };
  $('#btnShot').onclick = async () => {
    renderer.render(scene, camera);
    const c = document.createElement('canvas'); c.width = canvas.width; c.height = canvas.height; const x = c.getContext('2d');
    const g = x.createLinearGradient(0, 0, 0, c.height); g.addColorStop(0, '#1b6fc4'); g.addColorStop(1, '#0a3f8c');
    x.fillStyle = g; x.fillRect(0, 0, c.width, c.height); x.drawImage(canvas, 0, 0);
    const r = await api.savePng('blueprint.png', c.toDataURL('image/png')); if (r.ok && r.note !== '') toast('Screenshot saved');
  };
  $('#btnFit').onclick = fitView;
  $('#btnFull').onclick = () => { document.fullscreenElement ? document.exitFullscreen() : document.documentElement.requestFullscreen(); };
  // only one side panel at a time; Esc closes whatever is open
  const PANELS = ['#settings', '#materials', '#exporter'];
  function togglePanel(id) { const open = $(id).classList.contains('hidden'); for (const p of PANELS) $(p).classList.add('hidden'); if (open) $(id).classList.remove('hidden'); return open; }
  $('#btnGear').onclick = () => togglePanel('#settings');
  $('#setClose').onclick = () => $('#settings').classList.add('hidden');
  const help = (on) => $('#help').classList.toggle('hidden', !on);
  $('#btnHelp').onclick = () => help(true);
  $('#btnHelp2').onclick = () => { $('#settings').classList.add('hidden'); help(true); };
  $('#helpClose').onclick = () => help(false);
  $('#help').addEventListener('pointerdown', (e) => { if (e.target.id === 'help') help(false); });
  addEventListener('keydown', (e) => {
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;
    if (e.key === '?') { help($('#help').classList.contains('hidden')); e.preventDefault(); return; }
    if (e.key === 'Escape') {
      if (!$('#help').classList.contains('hidden')) { help(false); return; }
      for (const p of PANELS) $(p).classList.add('hidden');
    }
  });
  $('#btnMaterials').onclick = () => { $('#settings').classList.add('hidden'); $('#exporter').classList.add('hidden'); renderMaterials(); $('#materials').classList.toggle('hidden'); };
  $('#matClose').onclick = () => $('#materials').classList.add('hidden');
  $('#btnClear').onclick = () => { if (!world.size) return; if (confirm('Remove every block from the build?')) { applyChanges([...world].map(([k, b]) => ({ k, prev: b, next: null }))); toast('Cleared (Ctrl+Z to undo)'); } };
  // ---- updates (desktop app only)
  if (api.updates) {
    let shown = false;
    const show = (s) => {
      const t = { idle: `Version ${s.current}`, checking: 'Checking for updates...', latest: `Version ${s.current} - up to date`,
        downloading: `Downloading ${s.version || 'update'}${s.percent ? ' ' + s.percent + '%' : ''}...`, ready: `Version ${s.version} ready - restart to update`, installing: `Installing ${s.version || "the update"}... the app will reopen by itself`,
        available: `Version ${s.version} is out (you have ${s.current})`, error: `Couldn't check for updates${s.error ? ': ' + s.error : ''}` }[s.state] || `Version ${s.current}`;
      $('#updText').textContent = t;
      $('#btnUpd').textContent = s.state === 'ready' ? 'Restart & update' : s.state === 'available' ? 'Download' : 'Check for updates';
      if ((s.state === 'ready' || s.state === 'available') && !shown) {
        shown = true;
        $('#updBannerText').textContent = s.state === 'ready' ? `Block Blueprint ${s.version} is ready.` : `Block Blueprint ${s.version} is out!`;
        $('#updBannerBtn').textContent = s.state === 'ready' ? 'Restart & update' : 'Download';
        $('#updBanner').classList.remove('hidden');
      }
    };
    api.updates.onState(show);
    api.updates.get().then(show);
    $('#btnUpd').onclick = async () => { const s = await api.updates.get(); if (s.state === 'ready' || s.state === 'available') api.updates.install(); else api.updates.check(); };
    $('#updBannerBtn').onclick = () => api.updates.install();
    $('#updBannerClose').onclick = () => $('#updBanner').classList.add('hidden');
  } else { $('#updBox').style.display = 'none'; }

  $('#btnReassets').onclick = async () => {
    if (!confirm('Remove the current texture data and choose a texture source again?')) return;
    await api.resetAssets(); location.reload();
  };

  // ---- export to Minecraft
  let exp = null, copied = new Set();
  async function copyText(t) {
    try { await navigator.clipboard.writeText(t); return true; } catch { /* fall through */ }
    const ta = document.createElement('textarea'); ta.value = t; document.body.appendChild(ta); ta.select();
    let ok = false; try { ok = document.execCommand('copy'); } catch { /* ignore */ } ta.remove(); return ok;
  }
  function renderExportLines() {
    const box = $('#expLines'); box.textContent = '';
    exp.lines.forEach((l, i) => {
      const d = document.createElement('div'); d.className = 'eline' + (copied.has(i) ? ' done' : '');
      const t = document.createElement('span'); t.className = 't'; t.textContent = `${i + 1}/${exp.lines.length}  ${l}`;
      const b = document.createElement('button'); b.textContent = 'Copy';
      b.onclick = async () => { if (await copyText(l)) { copied.add(i); renderExportLines(); } };
      d.append(t, b); box.appendChild(d);
    });
    const left = exp.lines.length - copied.size;
    $('#expNext').textContent = left ? `Copy next line ▶ (${left} left)` : 'All copied ✓';
  }
  async function openExport() {
    $('#settings').classList.add('hidden'); $('#materials').classList.add('hidden');
    if (!world.size) { toast('Build something first'); return; }
    exp = await encodeBlueprint(world, baker, $('#expName').value.trim() || 'My Build'); copied = new Set();
    const [x, y, z] = exp.size;
    $('#expStats').textContent = `${exp.blocks} blocks · ${exp.types} types · ${x}×${y}×${z} · ${exp.lines.length} line${exp.lines.length === 1 ? '' : 's'} to paste`;
    renderExportLines(); $('#exporter').classList.remove('hidden');
    $('#expSend').textContent = editingMc ? `Update “${editingMc.name}” in Minecraft` : 'Send to Minecraft';
  }
  $('#btnExport').onclick = () => ($('#exporter').classList.contains('hidden') ? openExport() : $('#exporter').classList.add('hidden'));
  $('#expClose').onclick = () => $('#exporter').classList.add('hidden');
  $('#expName').addEventListener('change', openExport);
  $('#expName').addEventListener('keydown', (e) => e.stopPropagation());
  $('#expReset').onclick = () => { copied = new Set(); renderExportLines(); };
  $('#expNext').onclick = async () => {
    const i = exp.lines.findIndex((_, k) => !copied.has(k)); if (i < 0) return;
    if (await copyText(exp.lines[i])) { copied.add(i); renderExportLines(); toast(`Copied line ${i + 1} of ${exp.lines.length} - paste it in Minecraft chat`); }
  };
  if (!api.sendToMinecraft) $('#expSend').style.display = 'none';
  $('#expSend').onclick = async () => {
    const r = await api.sendToMinecraft($('#expName').value.trim() || 'My Build', exp.text, editingMc && editingMc.file);
    tplIndex = null;
    toast(r.ok ? (r.again ? 'Updated in Minecraft!' : 'Sent!') + ' In-game press Page Up → Saved builds' : (r.error || 'Could not send'), 4000);
  };
  $('#expCode').onclick = async () => {
    const data = JSON.parse(serialize()); data.name = $('#expName').value.trim() || 'My Build';
    const code = await toCode(data);
    toast((await copyText(code)) ? 'Build code copied - in-game press Page Up → + Paste build' : 'Could not copy', 4000);
  };
  $('#expAll').onclick = async () => { if (await copyText(exp.lines.join('\n'))) toast('Copied all lines (paste them one at a time in-game)'); };
  $('#expSave').onclick = async () => { const r = await api.saveTextFile(`${($('#expName').value.trim() || 'blueprint').replace(/[^\w-]+/g, '_')}-commands.txt`, exp.lines.join('\n')); if (r.ok) toast(r.note || 'Saved', r.note ? 4500 : 1800); };

  function renderMaterials() {
    const counts = new Map(); for (const b of world.values()) counts.set(b.b, (counts.get(b.b) || 0) + 1);
    const rows = [...counts].sort((a, b) => b[1] - a[1]);
    $('#matTotal').textContent = `${world.size} blocks · ${rows.length} types`;
    const list = $('#matList'); list.textContent = '';
    for (const [n, c] of rows) {
      const d = document.createElement('div'); d.className = 'mrow';
      const st = Math.floor(c / 64), rem = c % 64;
      d.innerHTML = `<img src="${thumb(n)}"><span class="n">${pretty(n)}</span><span class="c"><b>${c}</b><br>${st ? st + ' stack' + (st > 1 ? 's' : '') + (rem ? ' + ' + rem : '') : ''}</span>`;
      list.appendChild(d);
    }
  }

  function applySettings() {
    grid.visible = axes.visible = settings.grid;
    const st = $('#stage'); st.classList.toggle('blue', settings.blue); st.classList.toggle('dark', !settings.blue);
    $('#setRed').checked = settings.redstone;
    $('#setGrid').checked = settings.grid; $('#setShade').checked = settings.shade; $('#setBlue').checked = settings.blue;
    $('#setZoom').value = settings.zoom; $('#zoomVal').textContent = Math.round(settings.zoom * 100) + '%';
    store.set('settings', settings);
  }
  $('#setZoom').oninput = (e) => { settings.zoom = +e.target.value; if (api.setZoom) api.setZoom(settings.zoom); applySettings(); };
  $('#setGrid').onchange = (e) => { settings.grid = e.target.checked; applySettings(); };
  $('#setBlue').onchange = (e) => { settings.blue = e.target.checked; applySettings(); };
  $('#setRed').onchange = (e) => { settings.redstone = e.target.checked; dirty = true; applySettings(); };
  $('#setShade').onchange = (e) => { settings.shade = e.target.checked; baker.setShading(settings.shade); thumbCache.clear(); renderPalette(); renderHotbar(); dirty = true; applySettings(); };

  // layer slider
  $('#layer').addEventListener('input', (e) => {
    const H = Math.max(1, maxBlockY + 1), v = +e.target.value;
    if (v >= H) { allLayers = true; layerVal = H; } else { allLayers = false; layerVal = Math.max(1, v); }
    updateLayerUI(); ptrDirty = true;
  });
  const stepLayer = (d) => {
    const H = Math.max(1, maxBlockY + 1);
    let v = (allLayers ? H : layerVal) + d; v = Math.max(1, Math.min(H, v));
    allLayers = v >= H; layerVal = v; updateLayerUI(); ptrDirty = true;
  };
  $('#layerPrev').onclick = () => stepLayer(-1); $('#layerNext').onclick = () => stepLayer(1);
  $('#firstLayer').onclick = () => { allLayers = false; layerVal = 1; updateLayerUI(); ptrDirty = true; };

  function togglePalette() {
    const c = $('#palette').classList.toggle('collapsed');
    $('#palToggle').classList.toggle('collapsed', c); $('#palToggle').textContent = c ? '❯' : '❮'; $('#levelPill').classList.toggle('shift', c);
  }
  $('#palToggle').onclick = togglePalette;

  function fitView() {
    let min = [1e9, 1e9, 1e9], max = [-1e9, -1e9, -1e9];
    for (const k of world.keys()) { const p = decode(k); min = [Math.min(min[0], p.x), Math.min(min[1], p.y), Math.min(min[2], p.z)]; max = [Math.max(max[0], p.x + 1), Math.max(max[1], p.y + 1), Math.max(max[2], p.z + 1)]; }
    if (min[0] > max[0]) { min = [-4, 0, -4]; max = [4, 4, 4]; }
    const c = new THREE.Vector3((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2);
    const size = Math.max(max[0] - min[0], max[1] - min[1], max[2] - min[2], 6);
    const dist = size * 1.7 + 4;
    controls.target.copy(c); camera.position.set(c.x + dist * 0.7, c.y + dist * 0.55, c.z + dist * 0.8);
  }

  // ---- resize + loop
  function resize() {
    const w = innerWidth, h = innerHeight; renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix();
  }
  addEventListener('resize', resize); resize();

  // ---- fly camera like Roblox Studio: W/S forward/back, A/D left/right, Q/E down/up, Shift = faster
  const held = new Set();
  const FLY = new Set(['w', 'a', 's', 'd', 'q', 'e']);
  addEventListener('keydown', (e) => { if (e.target.tagName === 'INPUT' || !$('#tpl').classList.contains('hidden') || e.ctrlKey || e.metaKey || e.altKey) return; const k = e.key.toLowerCase(); if (FLY.has(k)) held.add(k); if (e.key === 'Shift') held.add('shift'); });
  addEventListener('keyup', (e) => { held.delete(e.key.toLowerCase()); if (e.key === 'Shift') held.delete('shift'); });
  addEventListener('blur', () => held.clear());
  let lastT = performance.now();
  const fwd = new THREE.Vector3(), right = new THREE.Vector3(), mv = new THREE.Vector3();
  function fly(dt) {
    if (!held.size || (held.size === 1 && held.has('shift'))) return;
    camera.getWorldDirection(fwd); fwd.y = 0; if (fwd.lengthSq() < 1e-6) fwd.set(0, 0, -1); fwd.normalize();
    right.crossVectors(fwd, camera.up).normalize();
    mv.set(0, 0, 0);
    if (held.has('w')) mv.add(fwd); if (held.has('s')) mv.sub(fwd);
    if (held.has('d')) mv.add(right); if (held.has('a')) mv.sub(right);
    if (held.has('e')) mv.y += 1; if (held.has('q')) mv.y -= 1;
    if (!mv.lengthSq()) return;
    const dist = camera.position.distanceTo(controls.target);
    const speed = Math.max(8, dist * 0.9) * (held.has('shift') ? 3 : 1);
    mv.normalize().multiplyScalar(speed * dt);
    camera.position.add(mv); controls.target.add(mv); ptrDirty = true;
  }

  function frame() {
    requestAnimationFrame(frame);
    const now = performance.now(), dt = Math.min(0.1, (now - lastT) / 1000); lastT = now;
    fly(dt);
    controls.update();
    if (dirty) {
      worldMesh.geometry.dispose(); worldMesh.geometry = buildWorldGeometry(settings.redstone ? simulate(world, baker) : world, visMaxY(), baker); dirty = false;
      $('#palCount').title = `${world.size} blocks placed`;
    }
    grid.position.y = axes.position.y = planeY() + 0.002;
    if (ptrDirty) { ptrDirty = false; refreshHover(); }
    placeMeasureLabel();
    renderer.render(scene, camera);
  }

  // ---------------------------------------------------------------- template gallery
  const TPLSRC = api.templates || {
    index: async () => (await fetch('templates/index.json')).json(),
    get: async (id) => {
      const r = await fetch(`templates/${id}.json`); if (r.ok) return r.text();
      for (const ext of ['schem', 'litematic', 'nbt']) {   // templates kept as schematic files
        const q = await fetch(`templates/schem/${id}.${ext}`).catch(() => null);
        if (q && q.ok) { let u8 = new Uint8Array(await q.arrayBuffer()); if (isGzip(u8)) u8 = await pipe(u8, new DecompressionStream('gzip')); return JSON.stringify(schematicToBuild(u8, id)); }
      }
      return 'null';
    },
    thumb: (id) => `templates/thumbs/${id}.jpg`,
  };
  let tplIndex = null, tplCat = 'All', tplQuery = '', tplCur = null, tplCells = null, tplGroup = '';
  const tplCache = new Map();
  // Minecraft-style panel background: a real block texture from the user's own game files
  function tileURL(name) {
    const i = atlas.index[name]; if (!i) return null;
    const T = atlas.tile, c = document.createElement('canvas'); c.width = c.height = T;
    const COLS = atlas.size / T;
    c.getContext('2d').drawImage(atlas.canvas, (i % COLS) * T, Math.floor(i / COLS) * T, T, T, 0, 0, T, T);
    return c.toDataURL();
  }
  const bgUrl = tileURL('cobblestone') || tileURL('stone');
  if (bgUrl) $('#tplPanel').style.setProperty('--mc-bg', `url(${bgUrl})`);

  async function openTemplates() {
    $('#tpl').classList.remove('hidden');
    if (!tplIndex) {
      const mine = await UB.list();
      const fromMc = api.mcBuilds ? await api.mcBuilds.list().catch(() => []) : [];
      const fromGame = api.mcStructures ? await api.mcStructures.list().catch(() => []) : [];
      tplIndex = [...fromMc, ...mine, ...(await TPLSRC.index()), ...fromGame]; renderTplTabs();
    }
    showTplGrid();
  }
  function closeTemplates() { $('#tpl').classList.add('hidden'); tplViewOn = false; }
  function renderTplTabs() {
    // "Minecraft Buildings" (Minecraft's own villages, trial chambers...) is its own section, never mixed into All / the other tabs
    const MCB = 'Minecraft Buildings', hasMcb = tplIndex.some((t) => t.cat === MCB);
    const cats = ['All', 'My Builds', ...(api.mcBuilds ? ['From Minecraft'] : []), ...new Set(tplIndex.map((t) => t.cat).filter((c) => c !== 'My Builds' && c !== 'From Minecraft' && c !== MCB)), ...(hasMcb ? [MCB] : [])];
    const el = $('#tplTabs'); el.textContent = '';
    for (const c of cats) {
      const n = c === 'All' ? tplIndex.filter((t) => t.cat !== MCB).length : tplIndex.filter((t) => t.cat === c).length;
      const b = document.createElement('button'); b.className = 'mcbtn' + (c === tplCat ? ' on' : ''); b.textContent = `${c} (${n})`;
      b.onclick = () => { tplCat = c; el.querySelectorAll('.mcbtn').forEach((x) => x.classList.toggle('on', x === b)); showTplGrid(); };
      if (c === MCB) { b.classList.add('mcbTab'); b.title = "Minecraft's own buildings (villages, trial chambers, shipwrecks...), read from your game files"; }
      el.appendChild(b);
    }
    if (hasMcb) {   // dropdown: which kind of Minecraft building (only shown on that tab)
      const sel = document.createElement('select'); sel.id = 'mcbGroup'; sel.className = 'mcbtn';
      const groups = [...new Set(tplIndex.filter((t) => t.cat === MCB).map((t) => t.group))].sort();
      sel.innerHTML = '<option value="">All Minecraft buildings</option>' + groups.map((g) => `<option>${g}</option>`).join('');
      sel.value = tplGroup; sel.style.display = tplCat === MCB ? '' : 'none';
      sel.onchange = () => { tplGroup = sel.value; showTplGrid(); };
      el.appendChild(sel);
    }
  }
  // card previews are drawn only for cards on screen (there are thousands of templates)
  const thumbObs = new IntersectionObserver((ents) => {
    for (const en of ents) {
      if (!en.isIntersecting) continue;
      const d = en.target; thumbObs.unobserve(d);
      thumbFor(d._tpl).then((u) => { if (u) d.querySelector('img').src = u; }).catch(() => {});
    }
  }, { root: null, rootMargin: '200px' });
  function showTplGrid() {
    thumbObs.disconnect();
    $('#tplDetail').classList.add('hidden'); $('#tplGrid').classList.remove('hidden'); $('#tplTabs').classList.remove('hidden'); tplViewOn = false;
    const q = tplQuery.trim().toLowerCase();
    const MCB = 'Minecraft Buildings', sel = $('#mcbGroup'); if (sel) sel.style.display = tplCat === MCB ? '' : 'none';
    const list = tplIndex.filter((t) => (tplCat === 'All' ? t.cat !== MCB : t.cat === tplCat) && (tplCat !== MCB || !tplGroup || t.group === tplGroup)
      && (!q || (t.name + ' ' + t.desc + ' ' + t.cat).toLowerCase().includes(q)));
    const g = $('#tplGrid'); g.textContent = '';
    for (const t of list) {
      const d = document.createElement('div'); d.className = 'tcard';
      d.innerHTML = `<img loading="lazy" alt=""><b></b><span></span>`;
      const img = d.querySelector('img');
      if (t.cat === 'From Minecraft' || t.src === 'game' || t.schem) { d.classList.add('fromMc'); d.classList.add(t.schem ? 'freeBadge' : 'mcBadge'); d._tpl = t; thumbObs.observe(d); }   // badge: from your game, or a free community build   // preview drawn when the card scrolls into view
      else img.src = t.cat === 'My Builds' ? (t.thumb || '') : TPLSRC.thumb(t.id);
      d.querySelector('b').textContent = t.name;
      d.querySelector('span').textContent = `${t.size.join(' × ')} · ${t.blocks.toLocaleString()} blocks`;
      d.onclick = () => openTplDetail(t);
      g.appendChild(d);
    }
    if (!list.length) g.innerHTML = tplCat === 'From Minecraft' ? '<div class="mcsub">Nothing from Minecraft yet. In-game, copy a build with the Insert key (or paste one in Page Up &rarr; Saved builds) and it shows up here.</div>'
      : tplCat === 'My Builds' ? '<div class="mcsub">Nothing here yet. Build something, then press "＋ Save my build", or import a build code.</div>'
      : q ? '<div class="mcsub">No builds match.</div>'
      : '<div class="mcsub">No templates yet. Save your builds with "＋ Save my build" or import a build code - they show up under My Builds.</div>';
  }
  // the build's JSON, wherever it comes from (built-in, My Builds, From Minecraft, Minecraft's own structures)
  async function rawFor(t) {
    if (t.src === 'game') return api.mcStructures.get(t.file);
    if (t.cat === 'From Minecraft') return api.mcBuilds.get(t.file);
    if (t.cat === 'My Builds') return UB.get(t.id);
    return TPLSRC.get(t.id);
  }
  async function loadTplCells(id) {
    if (tplCache.has(id)) return tplCache.get(id);
    const e = tplIndex && tplIndex.find((t) => t.id === id);
    const data = JSON.parse(await rawFor(e || { id }));
    const cells = [];
    for (let [x, y, z, name, label, r] of data.blocks) {
      if (!catalog.blocks[name]) continue;
      let v = catalog.blocks[name].variants.findIndex((q) => q.label === label); if (v < 0) v = closestVariant(catalog.blocks[name].variants, label);
      cells.push({ x: Math.round(x), y: Math.round(y), z: Math.round(z), b: name, v, r: r || 0 });
    }
    tplCache.set(id, cells); return cells;
  }
  // Pictures for builds that come from Minecraft (they have no saved thumbnail): rendered once, kept for this session.
  const mcThumbs = new Map(); let mcThumbR = null, thumbQ = Promise.resolve();
  function thumbFor(t) {
    const key = (t.file || t.id) + ':' + (t.created || 0);
    if (mcThumbs.has(key)) return Promise.resolve(mcThumbs.get(key));
    thumbQ = thumbQ.then(async () => {
      if (mcThumbs.has(key)) return mcThumbs.get(key);
      const cells = await loadTplCells(t.id);
      if (!mcThumbR) { const c = document.createElement('canvas'); c.width = 320; c.height = 240; mcThumbR = new THREE.WebGLRenderer({ canvas: c, antialias: true, alpha: true, preserveDrawingBuffer: true }); mcThumbR.outputColorSpace = THREE.LinearSRGBColorSpace; mcThumbR.setClearColor(0, 0); }
      const sc = new THREE.Scene(), cam = new THREE.PerspectiveCamera(40, 4 / 3, 0.1, 3000);
      const m = new Map(); let mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
      for (const c of cells) { m.set(encode(c.x, c.y, c.z), c); mn = [Math.min(mn[0], c.x), Math.min(mn[1], c.y), Math.min(mn[2], c.z)]; mx = [Math.max(mx[0], c.x + 1), Math.max(mx[1], c.y + 1), Math.max(mx[2], c.z + 1)]; }
      const geo = buildWorldGeometry(m, Infinity, baker); const mesh = new THREE.Mesh(geo, [mat, transMat]); mesh.frustumCulled = false; sc.add(mesh);
      const ctr = new THREE.Vector3((mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2), size = Math.max(mx[0] - mn[0], mx[1] - mn[1], mx[2] - mn[2], 4);
      cam.position.set(ctr.x + size * 1.0, ctr.y + size * 0.6, ctr.z + size * 1.1); cam.lookAt(ctr);
      mcThumbR.render(sc, cam); geo.dispose();
      const out = document.createElement('canvas'); out.width = 320; out.height = 240; const x = out.getContext('2d');
      const g = x.createLinearGradient(0, 0, 0, 240); g.addColorStop(0, '#1b6fc4'); g.addColorStop(1, '#0a3f8c'); x.fillStyle = g; x.fillRect(0, 0, 320, 240);
      x.drawImage(mcThumbR.domElement, 0, 0);
      const url = out.toDataURL('image/jpeg', 0.82); mcThumbs.set(key, url); return url;
    }).catch(() => null);
    return thumbQ;
  }
  // 3D preview
  let tplR = null, tplScene, tplCam, tplCtl, tplMesh, tplViewOn = false;
  function tplPreview(cells) {
    const cv = $('#tplView');
    if (!tplR) {
      tplR = new THREE.WebGLRenderer({ canvas: cv, antialias: true, alpha: true });
      tplR.outputColorSpace = THREE.LinearSRGBColorSpace; tplR.setClearColor(0, 0); tplR.setPixelRatio(Math.min(2, devicePixelRatio || 1));
      tplScene = new THREE.Scene(); tplCam = new THREE.PerspectiveCamera(45, 1, 0.1, 3000);
      tplCtl = new OrbitControls(tplCam, cv); tplCtl.enableDamping = true; tplCtl.autoRotate = true; tplCtl.autoRotateSpeed = 1.2;
      tplMesh = new THREE.Mesh(new THREE.BufferGeometry(), [mat, transMat]); tplMesh.frustumCulled = false; tplScene.add(tplMesh);
    }
    const m = new Map(); let mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
    for (const c of cells) { m.set(encode(c.x, c.y, c.z), c); mn = [Math.min(mn[0], c.x), Math.min(mn[1], c.y), Math.min(mn[2], c.z)]; mx = [Math.max(mx[0], c.x + 1), Math.max(mx[1], c.y + 1), Math.max(mx[2], c.z + 1)]; }
    tplMesh.geometry.dispose(); tplMesh.geometry = buildWorldGeometry(settings.redstone ? simulate(m, baker) : m, Infinity, baker);
    const ctr = new THREE.Vector3((mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2);
    const size = Math.max(mx[0] - mn[0], mx[1] - mn[1], mx[2] - mn[2], 4);
    tplCtl.target.copy(ctr); tplCam.position.set(ctr.x + size * 1.05, ctr.y + size * 0.55, ctr.z + size * 1.15);
    tplViewOn = true;
    const loop = () => {
      if (!tplViewOn) return;
      const w = cv.clientWidth, h = cv.clientHeight;
      if (cv.width !== Math.floor(w * tplR.getPixelRatio()) || cv.height !== Math.floor(h * tplR.getPixelRatio())) { tplR.setSize(w, h, false); tplCam.aspect = w / Math.max(1, h); tplCam.updateProjectionMatrix(); }
      tplCtl.update(); tplR.render(tplScene, tplCam); requestAnimationFrame(loop);
    };
    requestAnimationFrame(loop);
  }
  async function openTplDetail(t) {
    tplCur = t;
    $('#tplGrid').classList.add('hidden'); $('#tplTabs').classList.add('hidden'); $('#tplDetail').classList.remove('hidden');
    $('#tdCat').textContent = t.cat; $('#tdName').textContent = t.name; $('#tdDesc').textContent = t.desc;
    $('#tdStats').textContent = `${t.size.join(' × ')} blocks wide/tall/deep · ${t.blocks.toLocaleString()} blocks · ${t.types} block types`;
    $('#tdMats').textContent = '';
    tplCells = await loadTplCells(t.id);
    const counts = new Map(); for (const c of tplCells) counts.set(c.b, (counts.get(c.b) || 0) + 1);
    for (const [n, c] of [...counts].sort((a, b) => b[1] - a[1])) {
      const d = document.createElement('div'); d.className = 'mslot'; d.title = `${pretty(n)}: ${c}`;
      d.innerHTML = `<img src="${thumb(n)}"><i>${c >= 1000 ? (c / 1000).toFixed(1) + 'k' : c}</i>`;
      $('#tdMats').appendChild(d);
    }
    $('#tdAdd').style.display = world.size ? '' : 'none';
    $('#tdDel').style.display = t.cat === 'My Builds' ? '' : 'none';
    $('#tdUse').title = t.cat === 'From Minecraft' ? 'Open it here to change it - then Export > Send to Minecraft updates it in-game' : '';
    $('#tdUse').textContent = world.size ? 'Replace my build with this' : 'Use this build';
    tplPreview(tplCells);
  }
  let editingMc = null;   // { file, name } when the build came from Minecraft and Send should update it
  function useTemplate(add) {
    if (!tplCells) return;
    if (!add) { editingMc = tplCur && tplCur.cat === 'From Minecraft' ? { file: tplCur.file, name: tplCur.name } : null; if (editingMc) $('#expName').value = editingMc.name; }
    let dx = 0, dz = 0;
    if (add && world.size) {
      let wmx = -1e9, wz0 = 1e9, wz1 = -1e9; for (const k of world.keys()) { const p = decode(k); wmx = Math.max(wmx, p.x); wz0 = Math.min(wz0, p.z); wz1 = Math.max(wz1, p.z); }
      let tmn = 1e9, tz0 = 1e9, tz1 = -1e9; for (const c of tplCells) { tmn = Math.min(tmn, c.x); tz0 = Math.min(tz0, c.z); tz1 = Math.max(tz1, c.z); }
      dx = wmx + 4 - tmn; dz = Math.round((wz0 + wz1) / 2 - (tz0 + tz1) / 2);
    }
    const changes = [];
    if (!add) for (const [k, b] of world) changes.push({ k, prev: b, next: null });
    for (const c of tplCells) {
      const x = c.x + dx, z = c.z + dz; if (!inRange(x, c.y, z)) continue;
      const k = encode(x, c.y, z);
      changes.push({ k, prev: add ? world.get(k) || null : null, next: { b: c.b, v: c.v, r: c.r } });
    }
    applyChanges(changes); allLayers = true; recomputeBounds(); fitView(); closeTemplates();
    $('#expName').value = tplCur.name.slice(0, 40);
    toast(`${tplCur.name} placed - Ctrl+Z undoes it`, 3200);
  }
  $('#btnTemplates').onclick = openTemplates;
  $('#tplClose').onclick = closeTemplates;
  $('#tdBack').onclick = showTplGrid;
  $('#tdUse').onclick = () => useTemplate(false);
  $('#tdAdd').onclick = () => useTemplate(true);
  $('#tplSearch').addEventListener('input', (e) => { tplQuery = e.target.value; showTplGrid(); });
  $('#tplSearch').addEventListener('keydown', (e) => e.stopPropagation());
  $('#tpl').addEventListener('pointerdown', (e) => { if (e.target.id === 'tpl') closeTemplates(); });
  addEventListener('keydown', (e) => { if (e.key === 'Escape' && !$('#tpl').classList.contains('hidden')) { if (!$('#tplDetail').classList.contains('hidden')) showTplGrid(); else closeTemplates(); } }, true);

  // ---- user builds: save / share codes / import
  const UB = api.userBuilds || (() => { // browser / phone: keep them in localStorage
    const all = () => store.get('userBuilds', {});
    return {
      list: async () => Object.entries(all()).map(([id, d]) => ({ id, name: d.name, desc: d.desc || '', thumb: d.thumb, size: d.size, blocks: d.blocks.length, types: new Set(d.blocks.map((x) => x[3])).size, cat: 'My Builds', created: d.created || 0 })).sort((a, b) => b.created - a.created),
      get: async (id) => JSON.stringify(all()[id] || null),
      save: async (id, text) => { const a = all(); a[id] = JSON.parse(text); store.set('userBuilds', a); return true; },
      del: async (id) => { const a = all(); delete a[id]; store.set('userBuilds', a); return true; },
    };
  })();
  const b64u = (u8) => { let s = ''; for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode(...u8.subarray(i, i + 0x8000)); return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); };
  const unb64u = (t) => { const s = atob(t.replace(/-/g, '+').replace(/_/g, '/')); const u = new Uint8Array(s.length); for (let i = 0; i < s.length; i++) u[i] = s.charCodeAt(i); return u; };
  async function pipe(u8, stream) { return new Uint8Array(await new Response(new Blob([u8]).stream().pipeThrough(stream)).arrayBuffer()); }
  async function toCode(data) { const { thumb, ...rest } = data; return 'BB1:' + b64u(await pipe(new TextEncoder().encode(JSON.stringify(rest)), new CompressionStream('deflate-raw'))); }
  async function fromCode(text) {
    text = text.trim();
    if (text.startsWith('{')) return JSON.parse(text);
    const m = /BB1:([A-Za-z0-9_-]+)/.exec(text.replace(/\s+/g, ''));
    if (!m) throw new Error('That is not a build code (it should start with BB1:)');
    return JSON.parse(new TextDecoder().decode(await pipe(unb64u(m[1]), new DecompressionStream('deflate-raw'))));
  }
  function snapshot() {
    renderer.render(scene, camera);
    const c = document.createElement('canvas'); c.width = 320; c.height = 240; const x = c.getContext('2d');
    const g = x.createLinearGradient(0, 0, 0, 240); g.addColorStop(0, '#1b6fc4'); g.addColorStop(1, '#0a3f8c'); x.fillStyle = g; x.fillRect(0, 0, 320, 240);
    const s = canvas.width / canvas.height, w = s > 4 / 3 ? canvas.height * 4 / 3 : canvas.width, h = w * 3 / 4;
    x.drawImage(canvas, (canvas.width - w) / 2, (canvas.height - h) / 2, w, h, 0, 0, 320, 240);
    return c.toDataURL('image/jpeg', 0.8);
  }
  function dialog({ title, msg, name = null, text = null, readonly = false, ok = 'OK' }) {
    return new Promise((res) => {
      $('#dlgTitle').textContent = title; $('#dlgMsg').textContent = msg || '';
      $('#dlgName').style.display = name === null ? 'none' : ''; $('#dlgName').value = name || '';
      $('#dlgText').style.display = text === null ? 'none' : ''; $('#dlgText').value = text || ''; $('#dlgText').readOnly = readonly;
      $('#dlgOk').textContent = ok; $('#tplDlg').classList.remove('hidden');
      const done = (v) => { $('#tplDlg').classList.add('hidden'); $('#dlgOk').onclick = $('#dlgCancel').onclick = null; res(v); };
      $('#dlgOk').onclick = () => done({ name: $('#dlgName').value.trim(), text: $('#dlgText').value });
      $('#dlgCancel').onclick = () => done(null);
      setTimeout(() => (name !== null ? $('#dlgName') : $('#dlgText')).focus(), 30);
    });
  }
  ['#dlgName', '#dlgText'].forEach((q) => $(q).addEventListener('keydown', (e) => e.stopPropagation()));
  const newId = () => 'u' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  async function saveUserBuild(data) {
    const id = newId();
    let mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
    for (const [x, y, z] of data.blocks) { mn = [Math.min(mn[0], x), Math.min(mn[1], y), Math.min(mn[2], z)]; mx = [Math.max(mx[0], x), Math.max(mx[1], y), Math.max(mx[2], z)]; }
    data.size = [mx[0] - mn[0] + 1, mx[1] - mn[1] + 1, mx[2] - mn[2] + 1]; data.created = Date.now();
    await UB.save(id, JSON.stringify(data)); tplIndex = null; tplCat = 'My Builds';
    return id;
  }
  $('#tplSaveMine').onclick = async () => {
    if (!world.size) { toast('Build something first, then save it here'); return; }
    const thumbUrl = (closeTemplates(), snapshot());
    const r = await dialog({ title: 'Save my build', msg: 'It will appear in the "My Builds" tab.', name: $('#expName').value.trim() || 'My Build', ok: 'Save' });
    await openTemplates(); if (!r) return;
    const data = JSON.parse(serialize()); data.name = (r.name || 'My Build').slice(0, 40); data.desc = 'Saved from my own build.'; data.thumb = thumbUrl;
    await saveUserBuild(data); await openTemplates(); toast('Saved to My Builds');
  };
  $('#tplImport').onclick = async () => {
    const r = await dialog({ title: 'Import a build code', msg: 'Paste a code that starts with BB1: (or a saved .json blueprint).', text: '', ok: 'Import' });
    if (!r || !r.text.trim()) return;
    try {
      const data = await fromCode(r.text);
      if (data.format !== 'block-blueprint' || !Array.isArray(data.blocks)) throw new Error('That code has no blocks in it');
      data.name = (data.name || 'Imported build').slice(0, 40); data.desc = data.desc || 'Imported from a build code.';
      const id = await saveUserBuild(data);
      await openTemplates(); const e = tplIndex.find((t) => t.id === id); if (e) openTplDetail(e);
      toast('Imported into My Builds');
    } catch (err) { toast(err.message || 'Could not read that code', 4000); }
  };
  // ---- schematic files (.schem / .litematic / .nbt): open, drag in, save
  async function importSchematic(file) {
    try {
      if (file.size > 50_000_000) throw new Error('That file is too big');
      let u8 = new Uint8Array(await file.arrayBuffer());
      if (isGzip(u8)) u8 = await pipe(u8, new DecompressionStream('gzip'));
      const data = schematicToBuild(u8, file.name.replace(/\.[^.]+$/, ''), 'Imported from ' + file.name + '.');
      if (!data.blocks.length) throw new Error('That schematic has no blocks in it');
      data.name = String(data.name).slice(0, 40);
      const id = await saveUserBuild(data);
      await openTemplates(); const e = tplIndex.find((t) => t.id === id); if (e) openTplDetail(e);
      toast(`Imported "${data.name}" into My Builds (${data.blocks.length} blocks)`);
    } catch (err) { toast(err.message || 'Could not read that file', 4500); }
  }
  $('#tplOpenFile').onclick = () => $('#schemFile').click();
  $('#schemFile').onchange = (e) => { const f = e.target.files[0]; e.target.value = ''; if (f) importSchematic(f); };
  addEventListener('dragover', (e) => { if ([...(e.dataTransfer?.items || [])].some((i) => i.kind === 'file')) e.preventDefault(); });
  addEventListener('drop', (e) => {
    const f = [...(e.dataTransfer?.files || [])].find((x) => isSchematicName(x.name));
    if (!f) return; e.preventDefault(); importSchematic(f);
  });
  $('#tdSchem').onclick = async () => {
    try {
      const raw = await rawFor(tplCur);
      const data = JSON.parse(raw); data.name = tplCur.name;
      const gz = await pipe(buildToSchem(data, dataVersionFor(status.version)), new CompressionStream('gzip'));
      const name = (String(tplCur.name).toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '') || 'build') + '.schem';
      if (api.saveSchem) { const r = await api.saveSchem(name, b64u(gz).replace(/-/g, '+').replace(/_/g, '/')); if (r.ok) toast('Saved ' + r.path, 4000); return; }
      const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([gz])); a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 5000);
    } catch (err) { toast(err.message || 'Could not save the .schem file', 4000); }
  };
  // ---- Minecraft Bedrock: a .mcstructure file (structure block / "/structure load")
  async function saveBedrock(data, title) {
    try {
      const r = buildToMcpack(data, await bedrockMap(), title);
      let ok = false;
      if (api.saveBedrock) { const s = await api.saveBedrock(r.id + '.mcpack', b64u(r.bytes).replace(/-/g, '+').replace(/_/g, '/')); ok = s.ok; }
      else { const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([r.bytes])); a.download = r.id + '.mcpack'; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 5000); ok = true; }
      if (!ok) return;
      await dialog({ title: 'Saved for Bedrock', ok: 'Got it',
        msg: `1. Open the .mcpack file - Minecraft Bedrock starts and adds it.  2. In your world's settings, Behavior Packs: turn on "Block Blueprint: ${String(title || 'Build').slice(0, 40)}".  3. In the world, type  /structure load blueprint:${r.id} ~ ~ ~  (or use a structure block set to Load with the name blueprint:${r.id}). Size ${r.size.join(' x ')}.`
          + (r.missing.length ? ` ${r.missing.length} kind${r.missing.length > 1 ? 's' : ''} of block Bedrock doesn't have ${r.missing.length > 1 ? 'were' : 'was'} left out: ${r.missing.slice(0, 6).join(', ')}${r.missing.length > 6 ? '...' : ''}.` : '') });
    } catch (err) { toast(err.message || 'Could not save the Bedrock file', 4000); }
  }
  $('#tdBedrock').onclick = async () => { const data = JSON.parse(await rawFor(tplCur)); saveBedrock(data, tplCur.name); };
  $('#expBedrock').onclick = () => { if (!world.size) { toast('Build something first'); return; } saveBedrock(JSON.parse(serialize()), $('#expName').value.trim() || 'My Build'); };
  $('#tdCode').onclick = async () => {
    const raw = await rawFor(tplCur);
    const data = JSON.parse(raw); data.name = tplCur.name;
    const code = await toCode(data);
    const copied = await copyText(code);
    await dialog({ title: 'Build code', msg: (copied ? 'Copied! ' : '') + 'Paste this code to share the build, or send it to Claude to add it to the built-in templates.', text: code, readonly: true, ok: 'Done' });
  };
  $('#tdDel').onclick = async () => {
    if (!tplCur || tplCur.cat !== 'My Builds') return;
    if (!confirm(`Delete "${tplCur.name}" from My Builds?`)) return;
    await UB.del(tplCur.id); tplIndex = null; await openTemplates(); toast('Deleted');
  };

  // ---- go
  applySettings(); renderPalette(); renderHotbar(); setTool('build');
  if (innerWidth <= 820) togglePalette(); // phone: start with the block sheet closed
  const saved = store.get('autosave', null);
  if (saved) { loadStep('view', 'Opening your last build…'); try { load(saved); } catch { /* ignore bad autosave */ } }
  loadPct(1);
  recomputeBounds(); if (!world.size) fitView();
  $('#loading').classList.add('hidden');
  window.__bb = { world, catalog, allBlocks, baker, scene, camera, controls, fitView, load, serialize, renderer, openTemplates, openTplDetail, useTemplate, refresh: () => { dirty = true; } }; // debug handle
  frame();
}

main().catch((e) => {
  console.error(e);
  const l = $('#loading'); l.classList.remove('hidden');
  $('#loadMsg').textContent = 'Something went wrong while loading';
  const slow = $('#loadSlow'); slow.className = 'bad';
  slow.innerHTML = 'Error: ' + String(e && e.message || e).replace(/</g, '&lt;') + '<br>Try closing and reopening the app. If it keeps happening, try Settings → Change textures, or reinstall from GitHub.';
});
