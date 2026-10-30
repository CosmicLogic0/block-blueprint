'use strict';
const { app, BrowserWindow, ipcMain, dialog, protocol, Menu, shell, net } = require('electron');
const path = require('path');
const fs = require('fs');
const assets = require('./lib/assets');
const { buildCatalog } = require('./lib/catalog');

// Linux: Chromium turns 3D off for many graphics drivers it doesn't know; the 3D view needs it, so ask for it anyway.
if (process.platform === 'linux') app.commandLine.appendSwitch('ignore-gpu-blocklist');

// Only one copy of the app at a time: a second copy left running would block updates from installing.
if (!app.requestSingleInstanceLock()) { app.exit(0); }
app.on('second-instance', () => { if (win && !win.isDestroyed()) { if (win.isMinimized()) win.restore(); win.show(); win.focus(); } });

protocol.registerSchemesAsPrivileged([
  { scheme: 'mcassets', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true } },
]);

// Everything the app needs lives next to the program (portable): ./data
const argIdx = process.argv.indexOf('--assets');
// Installed copy: data lives in the user's AppData and saves in Documents (the install folder is read-only).
// Run from the folder (start.bat): everything stays next to the app, like before.
const DATA_DIR = argIdx > 0 ? path.resolve(process.argv[argIdx + 1]) : app.isPackaged ? path.join(app.getPath('userData'), 'data') : path.join(__dirname, 'data');
const DEFAULT_SAVE_DIR = app.isPackaged ? path.join(app.getPath('documents'), 'Block Blueprint') : path.join(__dirname, 'blueprints');
fs.mkdirSync(DATA_DIR, { recursive: true });
fs.mkdirSync(DEFAULT_SAVE_DIR, { recursive: true });

const MIME = { '.png': 'image/png', '.json': 'application/json' };
let win;

function status() {
  const ready = fs.existsSync(path.join(DATA_DIR, 'catalog.json'));
  if (ready) return { ready, target: assets.TARGET_VERSION, ...(readMeta() || {}) };
  // Assets present but catalog missing (e.g. --assets test dir): build it.
  if (fs.existsSync(path.join(DATA_DIR, 'assets', 'minecraft', 'blockstates'))) {
    const s = buildCatalog(DATA_DIR);
    return { ready: true, target: assets.TARGET_VERSION, version: 'custom', source: 'Local assets', ...s };
  }
  return { ready: false, target: assets.TARGET_VERSION };
}
// Older installs have no entity textures (beds, chests, bells, shulkers, signs, banners). Refresh once, quietly,
// from the same source they were made from; if that is not possible the app keeps working as before.
const CATALOG_VER = 3; // bump when catalogCore changes so existing installs rebuild their block list
async function maybeUpgradeAssets() {
  const meta = readMeta();
  if (meta && meta.catalogVer !== CATALOG_VER && fs.existsSync(path.join(DATA_DIR, 'assets', 'minecraft', 'blockstates'))) {
    try { const s = buildCatalog(DATA_DIR); fs.writeFileSync(path.join(DATA_DIR, 'meta.json'), JSON.stringify({ ...readMeta(), ...s, catalogVer: CATALOG_VER })); }
    catch (e) { console.warn('catalog rebuild skipped:', e.message); }
  }
  const hasEntity = fs.existsSync(path.join(DATA_DIR, 'assets', 'minecraft', 'textures', 'entity', 'chest'));
  const hasStructures = ['structure', 'structures'].some((d) => fs.existsSync(path.join(DATA_DIR, 'data', 'minecraft', d)));
  // re-read the game files once when something new is wanted from them: entity textures, or Minecraft's structures (templates)
  if (!meta || ((meta.entityTried || hasEntity) && (meta.structuresTried || hasStructures))) return;
  fs.writeFileSync(path.join(DATA_DIR, 'meta.json'), JSON.stringify({ ...meta, entityTried: true, structuresTried: true }));
  try {
    let info = null;
    const jar = assets.findInstalledJars().find((j) => j.id === meta.version);
    if (jar) info = await assets.useFile(DATA_DIR, jar.jar);
    else if (meta.source === 'Mojang download') info = await assets.downloadVersion(DATA_DIR, null, meta.version);
    if (info) assets.finish(DATA_DIR, { ...info, entityTried: true, structuresTried: true });
  } catch (e) { console.warn('texture refresh skipped:', e.message); }
}
function readMeta() { try { return JSON.parse(fs.readFileSync(path.join(DATA_DIR, 'meta.json'), 'utf8')); } catch { return null; } }

function createWindow() {
  win = new BrowserWindow({
    width: 1400, height: 880, minWidth: 900, minHeight: 600,
    backgroundColor: '#0f5cb5', title: 'Block Blueprint', icon: path.join(__dirname, 'assets', process.platform === 'win32' ? 'icon.ico' : 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false },
  });
  // Mac: keep the small app menu, without it Cmd+C / Cmd+V / Cmd+Q don't work. Windows and Linux: no menu bar.
  Menu.setApplicationMenu(process.platform === 'darwin' ? Menu.buildFromTemplate([{ role: 'appMenu' }, { role: 'editMenu' }, { role: 'windowMenu' }]) : null);
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

// ---- Updates -------------------------------------------------------------------------------------------------
// Installed app (from the Setup .exe): checks GitHub Releases, downloads a newer version quietly, offers a restart.
// Run from the folder (start.bat): can't replace itself, so it just tells you a new version is out and links to it.
const RELEASES = 'https://github.com/CosmicLogic0/block-blueprint/releases/latest';
const upd = { state: 'idle', version: null, error: null };
// Windows (Setup .exe) and the Linux app file can replace themselves. The Mac app and the Linux .deb can't
// (an unsigned Mac app isn't allowed to), so those just say a new version is out and link to the download page.
const SELF_UPDATE = app.isPackaged && (process.platform === 'win32' || (process.platform === 'linux' && !!process.env.APPIMAGE));
let au = null;
function sendUpd() { for (const w of BrowserWindow.getAllWindows()) w.webContents.send('upd:state', { ...upd, current: app.getVersion(), installed: app.isPackaged }); }
function newer(a, b) { const p = (v) => String(v).replace(/^v/, '').split('.').map((n) => parseInt(n, 10) || 0); const x = p(a), y = p(b); for (let i = 0; i < 3; i++) { if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) > (y[i] || 0); } return false; }
async function checkForUpdates(manual) {
  upd.state = 'checking'; upd.error = null; sendUpd();
  if (SELF_UPDATE) {
    try {
      if (!au) {
        au = require('electron-updater').autoUpdater;
        au.autoDownload = true;
        // Install only when the person says so (Restart now). Installing quietly on close broke updates when the
        // app was opened again while the installer was still running.
        au.autoInstallOnAppQuit = false;
        au.on('update-available', (i) => { upd.state = 'downloading'; upd.version = i.version; sendUpd(); });
        au.on('update-not-available', () => { upd.state = 'latest'; sendUpd(); });
        au.on('download-progress', (p) => { upd.state = 'downloading'; upd.percent = Math.round(p.percent); sendUpd(); });
        au.on('error', (e) => { upd.state = 'error'; upd.error = String(e && e.message || e).slice(0, 200); sendUpd(); });
        au.on('update-downloaded', async (info) => {
          upd.state = 'ready'; upd.version = info.version; sendUpd();
          const r = await dialog.showMessageBox({ type: 'info', buttons: ['Restart now', 'Later'], defaultId: 0, title: 'Update ready',
            message: `Block Blueprint ${info.version} is ready.`, detail: 'The app closes for a few seconds, updates, and opens again by itself. Your builds are kept. If you pick Later, you can update any time from Settings.' });
          if (r.response === 0) installUpdate();
        });
      }
      await au.checkForUpdates();
    } catch (e) { upd.state = 'error'; upd.error = String(e && e.message || e).slice(0, 200); sendUpd(); }
    return;
  }
  try {
    const r = await net.fetch('https://api.github.com/repos/CosmicLogic0/block-blueprint/releases/latest', { headers: { 'User-Agent': 'block-blueprint' } });
    const j = await r.json();
    upd.version = String(j.tag_name || '').replace(/^v/, '');
    upd.state = upd.version && newer(upd.version, app.getVersion()) ? 'available' : 'latest';
  } catch (e) { upd.state = manual ? 'error' : 'idle'; upd.error = 'Could not reach GitHub'; }
  sendUpd();
}
ipcMain.handle('upd:check', () => checkForUpdates(true));
ipcMain.handle('upd:get', () => ({ ...upd, current: app.getVersion(), installed: app.isPackaged }));
ipcMain.handle('upd:install', () => { if (au && upd.state === 'ready') installUpdate(); else shell.openExternal(RELEASES); });

// Close every window for real (nothing may keep the app alive), then run the installer silently and reopen the app.
function installUpdate() {
  if (!au) return;
  upd.state = 'installing'; sendUpd();
  app.removeAllListeners('window-all-closed');
  for (const w of BrowserWindow.getAllWindows()) { w.removeAllListeners('close'); w.destroy(); }
  setImmediate(() => { try { au.quitAndInstall(true, true); } catch (e) { app.exit(0); } });
}

app.whenReady().then(() => {
  setTimeout(() => checkForUpdates(false), 4000);
  protocol.handle('mcassets', async (req) => {
    const rel = decodeURIComponent(new URL(req.url).pathname).replace(/^\/+/, '');
    const root = path.resolve(DATA_DIR) + path.sep;
    const file = path.resolve(DATA_DIR, rel);
    if (!file.startsWith(root)) return new Response('forbidden', { status: 403 });
    try {
      const buf = await fs.promises.readFile(file);
      return new Response(buf, { headers: { 'content-type': MIME[path.extname(file)] || 'application/octet-stream', 'access-control-allow-origin': '*' } });
    } catch { return new Response('not found', { status: 404 }); }
  });

  const progress = (p) => win && !win.isDestroyed() && win.webContents.send('assets:progress', p);
  const wrap = (fn) => async (...a) => {
    try { const info = await fn(...a); return { ok: true, status: { ready: true, target: assets.TARGET_VERSION, ...assets.finish(DATA_DIR, info, progress) } }; }
    catch (e) { return { ok: false, error: e.message || String(e) }; }
  };

  ipcMain.handle('assets:status', async () => { await maybeUpgradeAssets(); return status(); });
  ipcMain.handle('assets:download', wrap(() => assets.downloadVersion(DATA_DIR, progress)));
  ipcMain.handle('assets:installed', wrap(() => assets.useInstalled(DATA_DIR, progress)));
  ipcMain.handle('assets:choose', async () => {
    const r = await dialog.showOpenDialog(win, {
      title: 'Choose a Minecraft .jar / resource pack .zip',
      filters: [{ name: 'Minecraft jar or zip', extensions: ['jar', 'zip'] }], properties: ['openFile'],
    });
    if (r.canceled || !r.filePaths[0]) return { ok: false, error: 'cancelled' };
    return wrap(() => assets.useFile(DATA_DIR, r.filePaths[0], progress))();
  });
  ipcMain.handle('assets:chooseFolder', async () => {
    const r = await dialog.showOpenDialog(win, { title: 'Choose a folder containing "assets"', properties: ['openDirectory'] });
    if (r.canceled || !r.filePaths[0]) return { ok: false, error: 'cancelled' };
    return wrap(() => assets.useFile(DATA_DIR, r.filePaths[0], progress))();
  });

  ipcMain.handle('assets:reset', () => {
    fs.rmSync(path.join(DATA_DIR, 'assets'), { recursive: true, force: true });
    for (const f of ['catalog.json', 'meta.json']) fs.rmSync(path.join(DATA_DIR, f), { force: true });
    return true;
  });

  ipcMain.handle('file:save', async (_e, name, text) => {
    const r = await dialog.showSaveDialog(win, { title: 'Save blueprint', defaultPath: path.join(DEFAULT_SAVE_DIR, name), filters: [{ name: 'Blueprint', extensions: ['json'] }] });
    if (r.canceled || !r.filePath) return { ok: false };
    fs.writeFileSync(r.filePath, text);
    return { ok: true, path: r.filePath };
  });
  ipcMain.handle('file:saveTxt', async (_e, name, text) => {
    const r = await dialog.showSaveDialog(win, { title: 'Save commands', defaultPath: path.join(DEFAULT_SAVE_DIR, name), filters: [{ name: 'Text file', extensions: ['txt'] }] });
    if (r.canceled || !r.filePath) return { ok: false };
    fs.writeFileSync(r.filePath, text);
    return { ok: true, path: r.filePath };
  });
  ipcMain.handle('file:open', async () => {
    const r = await dialog.showOpenDialog(win, { title: 'Open blueprint', defaultPath: DEFAULT_SAVE_DIR, filters: [{ name: 'Blueprint', extensions: ['json'] }], properties: ['openFile'] });
    if (r.canceled || !r.filePaths[0]) return { ok: false };
    return { ok: true, path: r.filePaths[0], text: fs.readFileSync(r.filePaths[0], 'utf8') };
  });
  ipcMain.handle('file:saveSchem', async (_e, name, b64) => {
    const r = await dialog.showSaveDialog(win, { title: 'Save as .schem', defaultPath: path.join(DEFAULT_SAVE_DIR, name), filters: [{ name: 'Schematic (WorldEdit, Litematica, Axiom)', extensions: ['schem'] }] });
    if (r.canceled || !r.filePath) return { ok: false };
    fs.writeFileSync(r.filePath, Buffer.from(String(b64), 'base64'));
    return { ok: true, path: r.filePath };
  });
  ipcMain.handle('file:saveBedrock', async (_e, name, b64) => {
    const r = await dialog.showSaveDialog(win, { title: 'Save for Minecraft Bedrock', defaultPath: path.join(DEFAULT_SAVE_DIR, name), filters: [{ name: 'Minecraft Bedrock pack', extensions: ['mcpack'] }] });
    if (r.canceled || !r.filePath) return { ok: false };
    fs.writeFileSync(r.filePath, Buffer.from(String(b64), 'base64'));
    return { ok: true, path: r.filePath };
  });
  ipcMain.handle('file:savePng', async (_e, name, dataUrl) => {
    const r = await dialog.showSaveDialog(win, { title: 'Save screenshot', defaultPath: path.join(DEFAULT_SAVE_DIR, name), filters: [{ name: 'PNG image', extensions: ['png'] }] });
    if (r.canceled || !r.filePath) return { ok: false };
    fs.writeFileSync(r.filePath, Buffer.from(dataUrl.split(',')[1], 'base64'));
    return { ok: true, path: r.filePath };
  });

  // built-in template gallery (renderer/templates)
  const TPL = path.join(__dirname, 'renderer', 'templates');
  // Send to Minecraft: one shared folder every Build Blueprint mod reads, wherever the modpack is installed.
  const MCB = () => { const d = path.join(require('os').homedir(), 'BlockBlueprint', 'builds'); fs.mkdirSync(d, { recursive: true }); return d; };
  // .schem / .litematic / .nbt files: read with the same code the app window uses (renderer/schem.js)
  let schemMod = null;
  const SCHEM = async () => schemMod || (schemMod = await import(require('url').pathToFileURL(path.join(__dirname, 'renderer', 'schem.js')).href));
  async function readSchemFile(file, fallbackName, desc) {
    const S = await SCHEM(); let b = fs.readFileSync(file);
    if (b.length > 50_000_000) throw new Error('too big');
    if (S.isGzip(b)) b = require('zlib').gunzipSync(b, { maxOutputLength: 200_000_000 });
    return S.schematicToBuild(new Uint8Array(b), fallbackName, desc);
  }
  const schemOk = (f) => /^[^\\/:*?"<>|]+\.(schem|litematic|nbt)$/i.test(String(f)) && !String(f).includes('..');
  // decode the mod's blueprint text (same format as renderer/export.js) into the app's block list
  function decodeBbp(text) {
    const packed = Buffer.from(text.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
    const raw = require('zlib').inflateRawSync(packed.subarray(4));
    let p = 0; const u8 = () => raw[p++];
    const vi = () => { let r = 0, s = 0, b; do { b = u8(); r += (b & 0x7f) * 2 ** s; s += 7; } while (b & 0x80); return r; };
    const str = () => { const n = vi(); const s = raw.toString('utf8', p, p + n); p += n; return s; };
    if (u8() !== 1) throw new Error('unknown format');
    const sx = vi(), sy = vi(), sz = vi(); const name = str();
    const pal = []; for (let i = vi(); i > 0; i--) pal.push(str());
    const split = pal.map((s) => { const b = s.indexOf('['); let id = b < 0 ? s : s.slice(0, b); id = id.replace(/^minecraft:/, '');
      const label = b < 0 ? 'default' : s.slice(b + 1, -1).split(',').filter(Boolean).sort().join(',') || 'default'; return [id, label]; });
    const blocks = []; const total = sx * sy * sz; const ox = Math.floor(sx / 2), oz = Math.floor(sz / 2);
    for (let i = 0; i < total;) { const run = vi(), v = vi();
      if (v) for (let k = i; k < i + run; k++) { const x = k % sx, z = Math.floor(k / sx) % sz, y = Math.floor(k / (sx * sz)); blocks.push([x - ox, y, z - oz, split[v - 1][0], split[v - 1][1], 0]); }
      i += run; }
    return { name, size: [sx, sy, sz], blocks };
  }
  const bbpOk = (f) => /^[\w.-]+\.bbp$/.test(String(f));
  ipcMain.handle('mcb:list', async () => {
    const dir = MCB(); const out = [];
    for (const f of fs.readdirSync(dir)) {
      if (schemOk(f)) {   // schematic files players dropped in the shared folder
        try { const d = await readSchemFile(path.join(dir, f), f.replace(/\.[^.]+$/, ''));
          out.push({ id: 'mc_' + f.replace(/[^\w]/g, '_'), file: f, name: d.name, desc: 'A schematic file (' + f.split('.').pop() + ') from your BlockBlueprint/builds folder.',
            size: d.size, blocks: d.blocks.length, types: new Set(d.blocks.map((b) => b[3])).size, cat: 'From Minecraft', created: fs.statSync(path.join(dir, f)).mtimeMs });
        } catch { /* skip unreadable file */ }
        continue;
      }
      if (!bbpOk(f)) continue;
      try {
        const [name, text] = fs.readFileSync(path.join(dir, f), 'utf8').split('\n');
        const d = decodeBbp(text.trim());
        out.push({ id: 'mc_' + f.replace(/[^\w]/g, '_'), file: f, name: (name || d.name || 'Build').trim(), desc: 'Saved in Minecraft with the Build Blueprint mod. Open it, change it, then Send to Minecraft to update it there.',
          size: d.size, blocks: d.blocks.length, types: new Set(d.blocks.map((b) => b[3])).size, cat: 'From Minecraft', created: fs.statSync(path.join(dir, f)).mtimeMs });
      } catch { /* skip broken file */ }
    }
    return out.sort((a, b) => b.created - a.created);
  });
  ipcMain.handle('mcb:get', async (_e, f) => {
    if (schemOk(f)) { try { return JSON.stringify(await readSchemFile(path.join(MCB(), f), f.replace(/\.[^.]+$/, ''))); } catch { return null; } }
    if (!bbpOk(f)) return null;
    try { const [name, text] = fs.readFileSync(path.join(MCB(), f), 'utf8').split('\n'); const d = decodeBbp(text.trim());
      return JSON.stringify({ format: 'block-blueprint', version: 1, name: (name || d.name).trim(), blocks: d.blocks, size: d.size }); } catch { return null; }
  });
  ipcMain.handle('mc:send', (_e, name, text, file) => {
    const dir = MCB();
    name = String(name || 'My Build').replace(/[\r\n]/g, ' ').slice(0, 40);
    text = String(text || '').trim();
    if (!/^[A-Za-z0-9_=-]+$/.test(text)) return { ok: false, error: 'Nothing to send' };
    if (file && bbpOk(file) && fs.existsSync(path.join(dir, file))) { fs.writeFileSync(path.join(dir, file), name + '\n' + text + '\n'); return { ok: true, dir, again: true }; }
    for (const f of fs.readdirSync(dir)) {
      if (!f.endsWith('.bbp')) continue;
      try { const l = fs.readFileSync(path.join(dir, f), 'utf8').split('\n'); if ((l[1] || '').trim() === text) { fs.writeFileSync(path.join(dir, f), name + '\n' + text + '\n'); return { ok: true, dir, again: true }; } } catch { /* skip */ }
    }
    const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '') || 'build';
    let dest = path.join(dir, slug + '.bbp');
    for (let i = 2; fs.existsSync(dest); i++) dest = path.join(dir, `${slug}_${i}.bbp`);
    fs.writeFileSync(dest, name + '\n' + text + '\n');
    return { ok: true, dir };
  });
  ipcMain.handle('tpl:index', () => { try { return JSON.parse(fs.readFileSync(path.join(TPL, 'index.json'), 'utf8')); } catch { return []; } });
  ipcMain.handle('tpl:get', async (_e, id) => {
    if (!/^[a-z0-9_]+$/.test(String(id))) return null;
    try { return fs.readFileSync(path.join(TPL, id + '.json'), 'utf8'); } catch { /* not a json template */ }
    for (const ext of ['schem', 'litematic', 'nbt']) {   // templates kept as schematic files (much smaller)
      const f = path.join(TPL, 'schem', id + '.' + ext);
      if (fs.existsSync(f)) { try { return JSON.stringify(await readSchemFile(f, id)); } catch { return null; } }
    }
    return null;
  });

  // Minecraft's own structures (village houses, igloos, shipwrecks, ruins...), read from the player's own game files
  // like the textures, so nothing of Mojang's is shipped with the app. Listed once, then cached.
  const STRUCT_GROUPS = { village: 'Village', bastion: 'Bastion', end_city: 'End City', ancient_city: 'Ancient City', trial_chambers: 'Trial Chambers',
    shipwreck: 'Shipwreck', underwater_ruin: 'Ocean Ruin', trail_ruins: 'Trail Ruins', pillager_outpost: 'Pillager Outpost', woodland_mansion: 'Woodland Mansion',
    igloo: 'Igloo', fossil: 'Fossil', nether_fossils: 'Nether Fossil', ruined_portal: 'Ruined Portal', desert_pyramid: 'Desert Pyramid', jungle_pyramid: 'Jungle Temple',
    swamp_hut: 'Swamp Hut', ocean_monument: 'Ocean Monument', stronghold: 'Stronghold', mineshaft: 'Mineshaft', nether_fortress: 'Nether Fortress' };
  const STRUCT_SKIP = new Set(['jigsaw', 'structure_block', 'structure_void', 'air', 'cave_air', 'void_air']);
  const structDirs = () => ['structure', 'structures'].map((d) => path.join(DATA_DIR, 'data', 'minecraft', d)).filter((d) => fs.existsSync(d));
  const titleCase = (s) => s.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
  async function readStructure(rel) {
    for (const d of structDirs()) {
      const f = path.resolve(d, rel);
      if (!f.startsWith(path.resolve(d) + path.sep) || !f.endsWith('.nbt') || !fs.existsSync(f)) continue;
      const b = await readSchemFile(f, titleCase(path.basename(rel, '.nbt')));
      b.blocks = b.blocks.filter((x) => !STRUCT_SKIP.has(x[3]));
      return b;
    }
    return null;
  }
  ipcMain.handle('mcs:list', async () => {
    const files = [];
    for (const d of structDirs()) { const walk = (sub) => { for (const n of fs.readdirSync(path.join(d, sub), { withFileTypes: true })) { const r = sub ? sub + '/' + n.name : n.name; n.isDirectory() ? walk(r) : r.endsWith('.nbt') && files.push(r); } }; walk(''); }
    const cacheFile = path.join(DATA_DIR, 'structures-index.json');
    try { const c = JSON.parse(fs.readFileSync(cacheFile, 'utf8')); if (c.ver === 1 && c.count === files.length) return c.list; } catch { /* rebuild */ }
    const list = [];
    for (const rel of files) {
      try {
        const b = await readStructure(rel); if (!b || b.blocks.length < 20) continue;   // skip tiny connector pieces
        const parts = rel.replace(/\.nbt$/, '').split('/'), group = STRUCT_GROUPS[parts[0]] || titleCase(parts[0]);
        let mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
        for (const [x, y, z] of b.blocks) { mn = [Math.min(mn[0], x), Math.min(mn[1], y), Math.min(mn[2], z)]; mx = [Math.max(mx[0], x), Math.max(mx[1], y), Math.max(mx[2], z)]; }
        list.push({ id: 'gs_' + rel.replace(/[^\w]/g, '_'), file: rel, src: 'game', cat: 'Minecraft Buildings', created: 0,
          name: titleCase(parts[parts.length - 1]).slice(0, 40),
          desc: `Minecraft's own ${group} piece (${parts.slice(0, -1).map(titleCase).join(' › ') || group}), read from your game files.`,
          size: [mx[0] - mn[0] + 1, mx[1] - mn[1] + 1, mx[2] - mn[2] + 1], blocks: b.blocks.length, types: new Set(b.blocks.map((x) => x[3])).size, group });
      } catch { /* skip */ }
    }
    list.sort((a, b) => a.group.localeCompare(b.group) || a.name.localeCompare(b.name, undefined, { numeric: true }));
    try { fs.writeFileSync(cacheFile, JSON.stringify({ ver: 1, count: files.length, list })); } catch { /* fine */ }
    return list;
  });
  ipcMain.handle('mcs:get', async (_e, rel) => { try { const b = await readStructure(String(rel)); return b ? JSON.stringify(b) : null; } catch { return null; } });

  // user-saved builds: one json file each in <data>/user-builds
  const UB = path.join(DATA_DIR, 'user-builds'); fs.mkdirSync(UB, { recursive: true });
  ipcMain.handle('ub:list', () => fs.readdirSync(UB).filter((f) => f.endsWith('.json')).map((f) => { try { const d = JSON.parse(fs.readFileSync(path.join(UB, f), 'utf8')); return { id: f.slice(0, -5), name: d.name, desc: d.desc || '', thumb: d.thumb || null, size: d.size, blocks: d.blocks.length, types: new Set(d.blocks.map((x) => x[3])).size, cat: 'My Builds', created: d.created || 0 }; } catch { return null; } }).filter(Boolean).sort((a, b) => b.created - a.created));
  ipcMain.handle('ub:get', (_e, id) => { if (!/^[a-z0-9_-]+$/i.test(id)) return null; try { return fs.readFileSync(path.join(UB, id + '.json'), 'utf8'); } catch { return null; } });
  ipcMain.handle('ub:save', (_e, id, text) => { if (!/^[a-z0-9_-]+$/i.test(id)) return false; fs.writeFileSync(path.join(UB, id + '.json'), text); return true; });
  ipcMain.handle('ub:del', (_e, id) => { if (!/^[a-z0-9_-]+$/i.test(id)) return false; fs.rmSync(path.join(UB, id + '.json'), { force: true }); return true; });

  createWindow();
  app.on('activate', () => { if (!BrowserWindow.getAllWindows().length) createWindow(); });
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('activate', () => { if (app.isReady() && BrowserWindow.getAllWindows().length === 0) createWindow(); });   // Mac: click the Dock icon
