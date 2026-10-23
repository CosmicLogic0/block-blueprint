// node templates-src/gen.mjs [id ...]   -> renderer/templates/<id>.json + index.json
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Build } from './lib.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.join(here, '..', 'renderer', 'templates');
fs.mkdirSync(out, { recursive: true });

const all = [];
for (const f of fs.readdirSync(path.join(here, 'builds')).filter((f) => f.endsWith('.mjs')).sort()) {
  const mod = await import(path.join(here, 'builds', f));
  all.push(...mod.default);
}
const only = process.argv.slice(2);
const index = fs.existsSync(path.join(out, 'index.json')) ? JSON.parse(fs.readFileSync(path.join(out, 'index.json'), 'utf8')) : [];
const byId = new Map(index.map((e) => [e.id, e]));
let ok = 0;
for (const t of all) {
  if (only.length && !only.includes(t.id)) continue;
  const b = new Build({ name: t.name, seed: t.seed || 7 });
  try { t.build(b); } catch (e) { console.error(`✗ ${t.id}: ${e.message}`); process.exitCode = 1; continue; }
  const j = b.toJSON();
  fs.writeFileSync(path.join(out, t.id + '.json'), JSON.stringify(j));
  const types = new Set(j.blocks.map((x) => x[3])).size;
  byId.set(t.id, { id: t.id, name: t.name, cat: t.cat, desc: t.desc, size: j.size, blocks: j.blocks.length, types, tags: t.tags || [] });
  console.log(`✓ ${t.id.padEnd(26)} ${String(j.blocks.length).padStart(6)} blocks  ${j.size.join('x')}`);
  ok++;
}
// keep catalogue order = order of definition
const order = all.map((t) => t.id);
const list = [...byId.values()].filter((e) => order.includes(e.id) && !e.id.startsWith('_')).sort((a, b) => order.indexOf(a.id) - order.indexOf(b.id));
fs.writeFileSync(path.join(out, 'index.json'), JSON.stringify(list, null, 1));
console.log(`${ok} built, ${list.length} in index`);
