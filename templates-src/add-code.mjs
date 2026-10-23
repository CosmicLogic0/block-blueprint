// node templates-src/add-code.mjs <id> "<Category>" "<BB1:code>"  -> adds a user-sent build to the built-in templates
import fs from 'node:fs'; import zlib from 'node:zlib'; import path from 'node:path'; import { fileURLToPath } from 'node:url';
const [id, cat, code] = process.argv.slice(2);
const raw = Buffer.from(code.replace(/^.*BB1:/, '').replace(/\s+/g, '').replace(/-/g, '+').replace(/_/g, '/'), 'base64');
const data = JSON.parse(zlib.inflateRawSync(raw).toString());
const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'renderer', 'templates');
let mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
for (const [x, y, z] of data.blocks) { mn = [Math.min(mn[0], x), Math.min(mn[1], y), Math.min(mn[2], z)]; mx = [Math.max(mx[0], x), Math.max(mx[1], y), Math.max(mx[2], z)]; }
data.size = [mx[0] - mn[0] + 1, mx[1] - mn[1] + 1, mx[2] - mn[2] + 1];
fs.writeFileSync(path.join(dir, id + '.json'), JSON.stringify(data));
const idx = JSON.parse(fs.readFileSync(path.join(dir, 'index.json'), 'utf8')).filter((e) => e.id !== id);
idx.push({ id, name: data.name, cat, desc: data.desc || '', size: data.size, blocks: data.blocks.length, types: new Set(data.blocks.map((b) => b[3])).size, tags: ['community'] });
fs.writeFileSync(path.join(dir, 'index.json'), JSON.stringify(idx, null, 1));
console.log('added', id, data.blocks.length, 'blocks');
