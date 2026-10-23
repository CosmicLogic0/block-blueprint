'use strict';
// Pure catalog builder: no Node or browser APIs, so the desktop app and the phone app share it.
// io = { readJson(rel) -> object|null   (rel like 'models/block/stone.json' or 'blockstates/x.json'),
//        listBlockstates() -> ['stone', ...],  texExists(name) -> boolean }

const stripNs = (id) => String(id).replace(/^minecraft:/, '');

// Variants whose key matches more of these look most like the "default" placement.
const PREFERRED = ['facing=north', 'half=bottom', 'half=lower', 'shape=straight', 'open=false', 'type=bottom',
  'axis=y', 'face=floor', 'powered=false', 'hinge=left', 'lit=false', 'waterlogged=false', 'attachment=floor'];
const score = (key) => PREFERRED.reduce((n, p) => n + (key.split(',').includes(p) ? 1 : 0), 0);


const CONNECTING = /(_fence|_wall|_pane|^iron_bars|^redstone_wire)$/;
function defaultProps(name) {
  if (name === 'redstone_wire') return { north: 'none', east: 'none', south: 'none', west: 'none', power: '0' };
  const wall = /_wall$/.test(name), v = wall ? 'none' : 'false';
  return { north: v, east: v, south: v, west: v, up: 'true', down: 'false' };
}
// blockstate "when" clause against a props map (values are strings; "a|b" = either)
function matchWhen(when, props) {
  if (when.OR) return when.OR.some((w) => matchWhen(w, props));
  if (when.AND) return when.AND.every((w) => matchWhen(w, props));
  return Object.entries(when).every(([k, val]) => String(val).split('|').includes(String(props[k])));
}

function buildCatalogCore(io) {
  const texExists = (name) => io.texExists(name);

  const rawCache = new Map();
  const chainCache = new Map();
  function loadRaw(id) {
    id = stripNs(id);
    if (rawCache.has(id)) return rawCache.get(id);
    let r = null;
    try { r = io.readJson('models/' + id + '.json') || null; } catch { r = null; }
    rawCache.set(id, r);
    return r;
  }
  function chain(id) {
    id = stripNs(id);
    if (id.startsWith('builtin/')) return { textures: {}, elements: null };
    if (chainCache.has(id)) return chainCache.get(id);
    chainCache.set(id, { textures: {}, elements: null }); // cycle guard
    const raw = loadRaw(id);
    let out;
    if (!raw) out = { textures: {}, elements: null };
    else {
      const p = raw.parent ? chain(raw.parent) : { textures: {}, elements: null };
      out = { textures: Object.assign({}, p.textures, raw.textures || {}), elements: raw.elements || p.elements };
    }
    chainCache.set(id, out);
    return out;
  }
  // 26.x models may write a texture as { sprite: 'block/x', force_translucent: true } instead of a plain string.
  const texName = (t) => (t && typeof t === 'object' && typeof t.sprite === 'string' ? t.sprite : t);
  function resolveTex(t, textures) {
    let guard = 0;
    t = texName(t);
    while (typeof t === 'string' && t[0] === '#' && guard++ < 12) t = texName(textures[t.slice(1)]);
    if (typeof t !== 'string' || t[0] === '#') return null;
    t = stripNs(t);
    if (!t.startsWith('block/')) return null;
    t = t.slice(6);
    return texExists(t) ? t : null;
  }

  const models = {};
  const usedTextures = new Set();
  const resolved = new Map();
  function resolveModel(id) {
    id = stripNs(id);
    if (resolved.has(id)) return resolved.get(id);
    let result = null;
    const c = chain(id);
    if (c.elements) {
      const elements = [];
      for (const el of c.elements) {
        const faces = {};
        for (const [dir, f] of Object.entries(el.faces || {})) {
          const tex = resolveTex(f.texture, c.textures);
          if (!tex) continue;
          usedTextures.add(tex);
          const face = { tex };
          if (f.uv) face.uv = f.uv;
          if (f.rotation) face.rotation = f.rotation;
          if (f.cullface) face.cullface = f.cullface;
          if (f.tintindex !== undefined) face.tintindex = f.tintindex;
          faces[dir] = face;
        }
        if (!Object.keys(faces).length) continue;
        const e = { from: el.from, to: el.to, faces };
        if (el.rotation) e.rotation = el.rotation;
        elements.push(e);
      }
      if (elements.length) { models[id] = { elements }; result = id; }
    }
    resolved.set(id, result);
    return result;
  }

  const blocks = {};
  const mpBlocks = {};
  for (const name of io.listBlockstates().sort()) {
    let bs;
    try { bs = io.readJson('blockstates/' + name + '.json'); } catch { continue; }
    if (!bs) continue;
    const cand = []; // {key, parts:[{model,x,y}]}
    const one = (e) => (Array.isArray(e) ? e[0] : e);
    if (bs.variants) {
      for (const [key, val] of Object.entries(bs.variants)) cand.push({ key, entries: [one(val)] });
    } else if (bs.multipart) {
      const entries = [];
      for (const part of bs.multipart) if (!part.when) entries.push(one(part.apply));
      if (CONNECTING.test(name)) {
        // Fences, walls, panes and bars: keep the full rule list so the renderer can connect them to neighbours.
        const rules = [];
        for (const part of bs.multipart) {
          if (!part.when) continue;
          const e = one(part.apply); const m = e && e.model && resolveModel(e.model);
          if (m) rules.push({ when: part.when, part: { m, x: e.x || 0, y: e.y || 0 } });
        }
        const base = [];
        for (const e of entries) { const m = e && e.model && resolveModel(e.model); if (m) base.push({ m, x: e.x || 0, y: e.y || 0 }); }
        mpBlocks[name] = { base, rules };
        const def = defaultProps(name);
        const parts = base.concat(rules.filter((r) => matchWhen(r.when, def)).map((r) => r.part));
        if (parts.length) { blocks[name] = { variants: [{ label: 'default', parts }], mp: mpBlocks[name] }; continue; }
      }
      if (entries.length) cand.push({ key: '', entries });
    }
    const variants = [];
    const seen = new Set();
    cand.sort((a, b) => score(b.key) - score(a.key));
    for (const c of cand) {
      const parts = [];
      for (const e of c.entries) {
        if (!e || !e.model) continue;
        const m = resolveModel(e.model);
        if (m) parts.push({ m, x: e.x || 0, y: e.y || 0 });
      }
      if (!parts.length) continue;
      // stairs / trapdoors keep every state so corner shapes and the exported facing are exact
      const sig = /_stairs$|_trapdoor$/.test(name) ? c.key : JSON.stringify(parts);
      if (seen.has(sig)) continue;
      seen.add(sig);
      variants.push({ label: c.key || 'default', parts });
      if (variants.length >= 64) break;
    }
    if (variants.length) blocks[name] = { variants };
  }

  // Fluids have no blockstate files; add simple cube versions.
  for (const [name, tex] of [['water', 'water_still'], ['lava', 'lava_still']]) {
    if (!texExists(tex)) continue;
    const faces = {};
    for (const d of ['down', 'up', 'north', 'south', 'west', 'east']) faces[d] = { tex, cullface: d, ...(name === 'water' ? { tintindex: 0 } : {}) };
    models['__' + name] = { elements: [{ from: [0, 0, 0], to: [16, 16, 16], faces }] };
    usedTextures.add(tex);
    blocks[name] = { variants: [{ label: 'default', parts: [{ m: '__' + name, x: 0, y: 0 }] }] };
  }

  const ent = require('./entityModels').buildEntityBlocks(io, models, blocks, usedTextures);
  return { textures: [...usedTextures].sort(), models, blocks, entity: ent };
}

module.exports = { buildCatalogCore, matchWhen };
