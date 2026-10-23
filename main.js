'use strict';
const { app, BrowserWindow, ipcMain, dialog, protocol, Menu } = require('electron');
const path = require('path');
const fs = require('fs');
const assets = require('./lib/assets');
const { buildCatalog } = require('./lib/catalog');

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
  if (!meta || meta.entityTried || fs.existsSync(path.join(DATA_DIR, 'assets', 'minecraft', 'textures', 'entity', 'chest'))) return;
  fs.writeFileSync(path.join(DATA_DIR, 'meta.json'), JSON.stringify({ ...meta, entityTried: true }));
  try {
    let info = null;
    const jar = assets.findInstalledJars().find((j) => j.id === meta.version);
    if (jar) info = await assets.useFile(DATA_DIR, jar.jar);
    else if (meta.source === 'Mojang download') info = await assets.downloadVersion(DATA_DIR, null, meta.version);
    if (info) assets.finish(DATA_DIR, { ...info, entityTried: true });
  } catch (e) { console.warn('texture refresh skipped:', e.message); }
}
function readMeta() { try { return JSON.parse(fs.readFileSync(path.join(DATA_DIR, 'meta.json'), 'utf8')); } catch { return null; } }

function createWindow() {
  win = new BrowserWindow({
    width: 1400, height: 880, minWidth: 900, minHeight: 600,
    backgroundColor: '#0f5cb5', title: 'Block Blueprint', icon: path.join(__dirname, 'assets', 'icon.ico'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false },
  });
  Menu.setApplicationMenu(null);
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

// Updates: the installed app checks GitHub Releases for a newer version, downloads it quietly and offers a restart.
function checkForUpdates() {
  if (!app.isPackaged) return;
  let au;
  try { au = require('electron-updater').autoUpdater; } catch { return; }
  au.autoDownload = true;
  au.on('update-downloaded', async (info) => {
    const r = await dialog.showMessageBox({ type: 'info', buttons: ['Restart now', 'Later'], defaultId: 0, title: 'Update ready',
      message: `Block Blueprint ${info.version} is ready.`, detail: 'Restart to use the new version (your builds are kept). If you pick Later it installs next time you close the app.' });
    if (r.response === 0) au.quitAndInstall();
  });
  au.on('error', () => { /* offline or no release yet - try again next launch */ });
  au.checkForUpdates().catch(() => {});
}

app.whenReady().then(() => {
  setTimeout(checkForUpdates, 4000);
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
  ipcMain.handle('mcb:list', () => {
    const dir = MCB(); const out = [];
    for (const f of fs.readdirSync(dir)) {
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
  ipcMain.handle('mcb:get', (_e, f) => {
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
  ipcMain.handle('tpl:get', (_e, id) => { if (!/^[a-z0-9_]+$/.test(String(id))) return null; try { return fs.readFileSync(path.join(TPL, id + '.json'), 'utf8'); } catch { return null; } });

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
