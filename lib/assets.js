'use strict';
// Gets Minecraft's block textures/models onto the user's PC, from files that are theirs:
//  - Mojang's official client download (default), or
//  - their installed Minecraft .jar, or any .jar/.zip/folder they choose.
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const AdmZip = require('adm-zip');
const { buildCatalog } = require('./catalog');

// The Minecraft version this app targets. Change here (or set MC_VERSION) to target another.
const TARGET_VERSION = process.env.MC_VERSION || '26.3';
const MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';

const WANT = /^assets\/minecraft\/(textures\/block\/[^/]+\.png|textures\/entity\/(bed|bell|chest|shulker|signs)\/[^/]+\.png|textures\/entity\/banner_base\.png|models\/block\/.+\.json|blockstates\/[^/]+\.json)$|^data\/minecraft\/structures?\/.+\.nbt$/;
// ^ also Minecraft's own structures (village houses, igloos, shipwrecks...), shown as templates from the player's own game files

function resetDest(dest) {
  fs.rmSync(path.join(dest, 'assets'), { recursive: true, force: true });
  fs.rmSync(path.join(dest, 'catalog.json'), { force: true });
  fs.rmSync(path.join(dest, 'data'), { recursive: true, force: true });
  fs.rmSync(path.join(dest, 'structures-index.json'), { force: true });
}

function extractZip(zipPath, dest, progress) {
  const zip = new AdmZip(zipPath);
  const entries = zip.getEntries().filter((e) => !e.isDirectory && WANT.test(e.entryName));
  if (!entries.length) throw new Error('No Minecraft block assets were found in that file.');
  resetDest(dest);
  const root = path.resolve(dest) + path.sep;
  entries.forEach((e, i) => {
    const out = path.resolve(dest, e.entryName);
    if (!out.startsWith(root)) return; // zip-slip guard
    fs.mkdirSync(path.dirname(out), { recursive: true });
    fs.writeFileSync(out, e.getData());
    if (i % 40 === 0) progress && progress({ stage: 'Extracting textures…', pct: i / entries.length });
  });
  return entries.length;
}

function copyFolder(src, dest, progress) {
  const base = path.join(src, 'assets', 'minecraft');
  if (!fs.existsSync(base)) throw new Error('That folder has no assets/minecraft inside it.');
  resetDest(dest);
  const files = [];
  const walk = (d) => { for (const n of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, n.name); n.isDirectory() ? walk(p) : files.push(p); } };
  for (const sub of ['textures/block', 'textures/entity', 'models/block', 'blockstates']) { const d = path.join(base, sub); if (fs.existsSync(d)) walk(d); }
  files.forEach((f, i) => {
    const rel = path.relative(src, f).split(path.sep).join('/');
    if (!WANT.test(rel)) return;
    const out = path.join(dest, rel);
    fs.mkdirSync(path.dirname(out), { recursive: true });
    fs.copyFileSync(f, out);
    if (i % 80 === 0) progress && progress({ stage: 'Copying textures…', pct: i / files.length });
  });
}

async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Request failed (${res.status}) for ${url}`);
  return res.json();
}

async function downloadVersion(dest, progress, version = TARGET_VERSION) {
  progress && progress({ stage: 'Contacting Mojang…', pct: 0 });
  const manifest = await getJson(MANIFEST);
  const entry = manifest.versions.find((v) => v.id === version);
  if (!entry) {
    const recent = manifest.versions.slice(0, 8).map((v) => v.id).join(', ');
    throw new Error(`Minecraft ${version} is not in Mojang's version list. Newest versions: ${recent}.`);
  }
  const meta = await getJson(entry.url);
  const client = meta.downloads && meta.downloads.client;
  if (!client) throw new Error(`Minecraft ${version} has no client download.`);
  const tmp = path.join(os.tmpdir(), `mc-client-${version}-${Date.now()}.jar`);
  const res = await fetch(client.url);
  if (!res.ok || !res.body) throw new Error(`Download failed (${res.status}).`);
  const total = Number(res.headers.get('content-length')) || client.size || 0;
  const hash = crypto.createHash('sha1');
  const out = fs.createWriteStream(tmp);
  let got = 0;
  const reader = res.body.getReader();
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    hash.update(value);
    if (!out.write(value)) await new Promise((r) => out.once('drain', r));
    got += value.length;
    progress && progress({ stage: `Downloading Minecraft ${version}…`, pct: total ? got / total : 0 });
  }
  await new Promise((r) => out.end(r));
  try {
    if (client.sha1 && hash.digest('hex') !== client.sha1) throw new Error('Download was corrupted (checksum mismatch). Please try again.');
    extractZip(tmp, dest, progress);
  } finally {
    fs.rmSync(tmp, { force: true });
  }
  return { version, source: 'Mojang download' };
}

function minecraftDirs() {
  const home = os.homedir();
  if (process.platform === 'win32') return [path.join(process.env.APPDATA || path.join(home, 'AppData', 'Roaming'), '.minecraft')];
  if (process.platform === 'darwin') return [path.join(home, 'Library', 'Application Support', 'minecraft')];
  return [path.join(home, '.minecraft')];
}

function findInstalledJars() {
  const found = [];
  for (const dir of minecraftDirs()) {
    const vdir = path.join(dir, 'versions');
    if (!fs.existsSync(vdir)) continue;
    for (const id of fs.readdirSync(vdir)) {
      const jar = path.join(vdir, id, id + '.jar');
      if (fs.existsSync(jar)) found.push({ id, jar, mtime: fs.statSync(jar).mtimeMs });
    }
  }
  // Exact target version first, then other releases, newest first.
  const rank = (v) => (v.id === TARGET_VERSION ? 0 : /^[\d.]+$/.test(v.id) ? 1 : 2);
  return found.sort((a, b) => rank(a) - rank(b) || b.mtime - a.mtime);
}

async function useInstalled(dest, progress) {
  const jars = findInstalledJars();
  if (!jars.length) throw new Error('No installed Minecraft version was found. Start the Minecraft launcher once, or use the download option.');
  let lastErr;
  for (const j of jars) {
    try { extractZip(j.jar, dest, progress); return { version: j.id, source: 'Installed Minecraft' }; } catch (e) { lastErr = e; }
  }
  throw lastErr;
}

async function useFile(dest, file, progress) {
  const st = fs.statSync(file);
  if (st.isDirectory()) copyFolder(file, dest, progress); else extractZip(file, dest, progress);
  return { version: path.basename(file), source: 'Chosen file' };
}

function finish(dest, info, progress) {
  progress && progress({ stage: 'Building block catalog…', pct: 0.98 });
  const stats = buildCatalog(dest);
  fs.writeFileSync(path.join(dest, 'meta.json'), JSON.stringify({ ...info, ...stats, target: TARGET_VERSION, catalogVer: 3 }));
  return { ...info, ...stats };
}

module.exports = { TARGET_VERSION, downloadVersion, useInstalled, useFile, finish, findInstalledJars };
