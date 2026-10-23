// Phone version of the desktop app's `window.api`: textures live in this phone's own storage (IndexedDB),
// read from the Minecraft .jar the person picks. Nothing is uploaded anywhere.
import { unzipSync, gunzipSync, strFromU8 } from 'fflate';
import { buildCatalogCore } from '../lib/catalogCore.js';

const TARGET = '26.3';
const WANT = /^assets\/minecraft\/(textures\/block\/[^/]+\.png|textures\/entity\/(bed|bell|chest|shulker|signs)\/[^/]+\.png|textures\/entity\/banner_base\.png|models\/block\/.+\.json|blockstates\/[^/]+\.json)$/;

// ---- tiny IndexedDB key/value wrapper (all calls fail soft)
let dbp;
function db() {
  if (!dbp) dbp = new Promise((res, rej) => {
    const r = indexedDB.open('block-blueprint', 1);
    r.onupgradeneeded = () => r.result.createObjectStore('kv');
    r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error);
  });
  return dbp;
}
const tx = async (mode, fn) => { const d = await db(); return new Promise((res, rej) => { const t = d.transaction('kv', mode); const out = fn(t.objectStore('kv')); t.oncomplete = () => res(out && out.result !== undefined ? out.result : undefined); t.onerror = () => rej(t.error); t.onabort = () => rej(t.error); }); };
const kvGet = (k) => tx('readonly', (s) => s.get(k));
const kvSet = (k, v) => tx('readwrite', (s) => s.put(v, k));

let progressCb = () => {};
const texCache = new Map(); // 'block/stone' -> Blob

async function loadAllTextures() {
  if (texCache.size) return;
  const d = await db();
  await new Promise((res, rej) => {
    const c = d.transaction('kv', 'readonly').objectStore('kv').openCursor(IDBKeyRange.bound('tex:', 'tex:￿'));
    c.onsuccess = () => { const cur = c.result; if (!cur) return res(); texCache.set(cur.key.slice(4), cur.value); cur.continue(); };
    c.onerror = () => rej(c.error);
  });
}

async function importJar(file) {
  progressCb({ stage: 'Reading the file…', pct: 0.05 });
  const buf = new Uint8Array(await file.arrayBuffer());
  progressCb({ stage: 'Unpacking…', pct: 0.2 });
  let files;
  try { files = unzipSync(buf, { filter: (f) => WANT.test(f.name) }); } catch (e) { throw new Error('That file could not be opened as a Minecraft .jar / .zip.'); }
  const names = Object.keys(files);
  if (!names.some((n) => n.includes('/blockstates/'))) throw new Error('No Minecraft block data was found in that file. Pick the game .jar (for example 26.3.jar).');

  progressCb({ stage: 'Building the block list…', pct: 0.45 });
  const dec = new TextDecoder(); const jsonCache = new Map(); const P = 'assets/minecraft/';
  const io = {
    readJson: (rel) => { if (jsonCache.has(rel)) return jsonCache.get(rel); const f = files[P + rel]; let v = null; if (f) { try { v = JSON.parse(dec.decode(f)); } catch { v = null; } } jsonCache.set(rel, v); return v; },
    listBlockstates: () => names.filter((n) => n.startsWith(P + 'blockstates/')).map((n) => n.slice((P + 'blockstates/').length, -5)),
    texExists: (name) => !!files[P + 'textures/block/' + name + '.png'],
    entityExists: (src) => !!files[P + 'textures/' + src + '.png'],
  };
  const catalog = buildCatalogCore(io);

  progressCb({ stage: 'Saving on this phone…', pct: 0.7 });
  const d = await db();
  await new Promise((res, rej) => { const t = d.transaction('kv', 'readwrite'); t.objectStore('kv').clear(); t.oncomplete = res; t.onerror = () => rej(t.error); });
  texCache.clear();
  const pngs = names.filter((n) => n.endsWith('.png'));
  await new Promise((res, rej) => {
    const t = d.transaction('kv', 'readwrite'); const s = t.objectStore('kv');
    for (const n of pngs) { const rel = n.slice((P + 'textures/').length, -4); s.put(new Blob([files[n]], { type: 'image/png' }), 'tex:' + rel); }
    s.put(catalog, 'catalog');
    t.oncomplete = res; t.onerror = () => rej(t.error); t.onabort = () => rej(t.error);
  });
  const meta = { ready: true, target: TARGET, catalogVer: 3, version: file.name.replace(/\.(jar|zip)$/i, ''), source: 'Chosen file', blocks: Object.keys(catalog.blocks).length };
  await kvSet('meta', meta);
  progressCb({ stage: 'Done', pct: 1 });
  return meta;
}

function pickFile(accept) {
  return new Promise((resolve) => {
    const inp = document.createElement('input'); inp.type = 'file'; if (accept) inp.accept = accept;
    inp.style.display = 'none'; document.body.appendChild(inp);
    let done = false;
    const finish = (f) => { if (done) return; done = true; inp.remove(); resolve(f); };
    inp.onchange = () => finish(inp.files && inp.files[0] || null);
    inp.addEventListener('cancel', () => finish(null));
    inp.click();
  });
}

// Sandboxed pages cannot download files or open the share sheet, so each way out has a fallback that always works.
function overlay(html) {
  const o = document.createElement('div');
  o.style.cssText = 'position:fixed;inset:0;z-index:99;background:rgba(5,15,35,.92);display:flex;flex-direction:column;gap:10px;padding:max(16px,env(safe-area-inset-top)) 16px 16px;color:#f2f5f9;font:15px system-ui,sans-serif;overflow:auto';
  o.innerHTML = html; document.body.appendChild(o); return o;
}
const btn = 'padding:12px 16px;border:0;border-radius:11px;font:inherit;font-weight:700;background:#4fc3ff;color:#04202f';
const btnAlt = 'padding:12px 16px;border:0;border-radius:11px;font:inherit;font-weight:700;background:#2f3540;color:#f2f5f9';

async function copyToClipboard(text) {
  try { await navigator.clipboard.writeText(text); return true; } catch { /* fall through */ }
  const ta = document.createElement('textarea'); ta.value = text; ta.style.cssText = 'position:fixed;opacity:0'; document.body.appendChild(ta);
  ta.select(); ta.setSelectionRange(0, text.length); let ok = false; try { ok = document.execCommand('copy'); } catch { /* ignore */ } ta.remove(); return ok;
}

// Text files (builds, command lists): share sheet if allowed, otherwise copy so it can be pasted into Notes.
async function giveText(name, text, mime) {
  const file = new File([text], name, { type: mime });
  try { if (navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file], title: name }); return { ok: true }; } } catch (e) { if (e && e.name === 'AbortError') return { ok: false, error: 'cancelled' }; }
  if (await copyToClipboard(text)) return { ok: true, note: 'Copied. Paste it into Notes to keep it (Open ▸ Paste restores it).' };
  return new Promise((resolve) => {
    const o = overlay(`<b>Select all and copy this text, then paste it into Notes:</b><textarea readonly style="flex:1;min-height:50vh;border-radius:10px;padding:10px;background:#14161a;color:#f2f5f9;font:12px monospace">${text.replace(/&/g, '&amp;').replace(/</g, '&lt;')}</textarea><button style="${btn}">Done</button>`);
    o.querySelector('textarea').focus(); o.querySelector('textarea').select();
    o.querySelector('button').onclick = () => { o.remove(); resolve({ ok: true }); };
  });
}

async function giveFile(name, blob) {
  const file = new File([blob], name, { type: blob.type || 'application/octet-stream' });
  try { if (navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file], title: name }); return { ok: true }; } } catch (e) { if (e && e.name === 'AbortError') return { ok: false, error: 'cancelled' }; }
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; document.body.appendChild(a); a.click();
  setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 4000);
  return { ok: true };
}

// Pictures: show the image so it can be pressed-and-held and saved to Photos.
async function showPicture(dataUrl) {
  return new Promise((resolve) => {
    const o = overlay(`<b>Press and hold the picture, then choose "Save to Photos".</b><img src="${dataUrl}" style="max-width:100%;border-radius:10px;-webkit-touch-callout:default;-webkit-user-select:auto"><button style="${btn}">Close</button>`);
    o.querySelector('button').onclick = () => { o.remove(); resolve({ ok: true, note: '' }); };
  });
}

// Open a build: paste text (from Notes) or choose a saved file.
function askForBuild() {
  return new Promise((resolve) => {
    const o = overlay(`<b>Open a saved build</b><textarea placeholder="Paste a saved build here" style="flex:1;min-height:40vh;border-radius:10px;padding:10px;background:#14161a;color:#f2f5f9;font:12px monospace;-webkit-user-select:text"></textarea>
      <button data-a="paste" style="${btn}">Open pasted build</button><button data-a="file" style="${btnAlt}">Choose a file…</button><button data-a="x" style="${btnAlt}">Cancel</button>`);
    o.addEventListener('click', async (e) => {
      const a = e.target.dataset && e.target.dataset.a; if (!a) return;
      if (a === 'paste') { const t = o.querySelector('textarea').value.trim(); if (!t) return; o.remove(); resolve({ ok: true, text: t }); }
      else if (a === 'file') { const f = await pickFile(''); if (f) { o.remove(); resolve({ ok: true, text: await f.text() }); } }
      else { o.remove(); resolve({ ok: false }); }
    });
  });
}

// built-in templates are packed into the page (see build step): index + gzipped build data + thumbnails
let tplData = null;
const TPL = () => window.__BB_TPL || { index: [], gz: '', thumbs: {} };
const templates = {
  index: async () => TPL().index,
  get: async (id) => {
    if (!tplData) { const b = atob(TPL().gz); const u = new Uint8Array(b.length); for (let i = 0; i < b.length; i++) u[i] = b.charCodeAt(i); tplData = JSON.parse(strFromU8(gunzipSync(u))); }
    return JSON.stringify(tplData[id]);
  },
  thumb: (id) => TPL().thumbs[id] || '',
};

window.api = {
  isPhone: true,
  templates,
  assetBase: 'phone://local/',
  async status() {
    let m = null; try { m = await kvGet('meta'); } catch { /* storage unavailable */ }
    return m && m.catalogVer === 3 ? { ...m, ready: true } : { ready: false, target: TARGET }; // older imports: pick the jar again to get glass panes, redstone dust...
  },
  onProgress(cb) { progressCb = cb; },
  async chooseFile() {
    const f = await pickFile('');
    if (!f) return { ok: false, error: 'cancelled' };
    try { const status = await importJar(f); return { ok: true, status }; } catch (e) { return { ok: false, error: e.message || String(e) }; }
  },
  async download() { return { ok: false, error: 'Not available on the phone' }; },
  async useInstalled() { return { ok: false, error: 'Not available on the phone' }; },
  async chooseFolder() { return { ok: false, error: 'Not available on the phone' }; },
  async getCatalog() { await loadAllTextures(); const c = await kvGet('catalog'); if (!c) throw new Error('Texture data is missing - pick the Minecraft .jar again.'); return c; },
  async loadTexture(rel) {
    const b = texCache.get(rel); if (!b) throw new Error('missing ' + rel);
    return createImageBitmap(b);
  },
  async resetAssets() { const d = await db(); await new Promise((res) => { const t = d.transaction('kv', 'readwrite'); t.objectStore('kv').clear(); t.oncomplete = res; }); texCache.clear(); },
  setZoom() {},
  saveText: (name, text) => giveText(name, text, 'application/json'),
  saveTextFile: (name, text) => giveText(name, text, 'text/plain'),
  async savePng(name, dataUrl) { try { const b = await (await fetch(dataUrl)).blob(); const f = new File([b], name, { type: 'image/png' }); if (navigator.canShare && navigator.canShare({ files: [f] })) { await navigator.share({ files: [f] }); return { ok: true }; } } catch (e) { if (e && e.name === 'AbortError') return { ok: false, error: 'cancelled' }; } return showPicture(dataUrl); },
  openText: askForBuild,
};
