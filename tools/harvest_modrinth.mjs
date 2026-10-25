// Finds openly licensed structure builds on Modrinth and turns them into Block Blueprint templates.
// Runs on GitHub Actions (.github/workflows/harvest.yml); results go to branch "templates-harvest" for review.
//  - only projects whose Modrinth license is in ALLOWED (no "non-commercial", no copyleft for code)
//  - skips packs that remake / repair / improve Minecraft's own structures (those parts belong to Mojang)
//  - keeps .nbt structure files made only of vanilla blocks, big enough to be a build
//  - writes out/schem/<id>.nbt, out/index.json (template entries with credit), out/licenses/<slug>.txt, out/report.txt
import fs from 'fs'; import path from 'path'; import zlib from 'zlib';
import AdmZip from 'adm-zip';
import * as S from '../renderer/schem.js';

const ALLOWED = ['CC0-1.0', 'MIT', 'Apache-2.0', 'Unlicense', 'CC-BY-4.0', 'CC-BY-3.0', 'CC-BY-SA-4.0', '0BSD', 'Zlib', 'BSD-2-Clause', 'BSD-3-Clause', 'ISC'];
const DERIVED = /\b(vanilla|better|improved|overhaul|revamp|repair|unwrecked|remake|reimagin|retexture|rework|redone|compat|enhanced|tweaks?|fix(es)?|backport|legacy)\b/i;
const BUILDY = /structure|village|house|tower|ship|castle|dungeon|ruin|temple|building|town|city|outpost|fort|camp|tavern|inn|mansion|cabin|cottage|bridge|church|windmill|lighthouse|shrine|monument/i;
const OUT = process.argv[2] || 'out';
const MAX_PER_PROJECT = 60, MIN_BLOCKS = 60, MAX_BYTES = 120_000_000;
const SKIP_BLOCKS = new Set(['jigsaw', 'structure_block', 'structure_void', 'air', 'cave_air', 'void_air']);
fs.mkdirSync(path.join(OUT, 'schem'), { recursive: true }); fs.mkdirSync(path.join(OUT, 'licenses'), { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function api(u) {
  for (let i = 0; i < 4; i++) {
    const r = await fetch('https://api.modrinth.com/v2/' + u, { headers: { 'User-Agent': 'CosmicLogic0/block-blueprint template-harvest (github.com/CosmicLogic0/block-blueprint)' } });
    if (r.status === 429) { await sleep(20000); continue; }
    if (!r.ok) throw new Error(r.status + ' ' + u);
    await sleep(250);
    return r.json();
  }
  throw new Error('rate limited');
}
const CATS = [[/house|home|cottage|cabin|hut/i, 'Houses'], [/tower|lighthouse|windmill/i, 'Towers'], [/ship|boat|galleon|airship/i, 'Ships'],
  [/castle|fort|keep|citadel|bastion|wall/i, 'Castles & Forts'], [/temple|ruin|shrine|church|chapel|tomb|crypt/i, 'Ruins & Temples'],
  [/well|fountain|statue|garden|bridge|lamp|market|stall|decor/i, 'Decor'], [/camp|tent|outpost/i, 'Camps & Outposts'],
  [/tavern|inn|shop|bakery|smith|farm|barn|mill|stable|library/i, 'Village Buildings']];
const titleCase = (s) => s.replace(/[_-]+/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()).trim();

const report = [], index = [];
let total = 0, bytes = 0;
const hits = [];
for (const type of ['datapack', 'mod']) {
  for (let off = 0; off < 3000; off += 100) {
    const facets = JSON.stringify([[`project_type:${type}`], ['categories:worldgen'], ALLOWED.map((l) => 'license:' + l)]);
    const r = await api(`search?limit=100&offset=${off}&index=downloads&facets=${encodeURIComponent(facets)}`);
    hits.push(...r.hits.map((h) => ({ ...h, type })));
    if (r.hits.length < 100) break;
  }
}
report.push(`Modrinth projects with an open license in worldgen: ${hits.length}`);
for (const h of hits) {
  const text = `${h.title} ${h.description}`;
  if (!ALLOWED.includes(h.license)) continue;
  if (DERIVED.test(text)) { report.push(`skip (remakes/changes existing structures): ${h.slug}`); continue; }
  if (!BUILDY.test(text)) continue;
  if (bytes > MAX_BYTES * 20) { report.push('stopped: download budget used'); break; }
  try {
    const versions = await api(`project/${h.project_id}/version`);
    const v = versions[0]; if (!v) continue;
    const file = v.files.find((f) => f.primary) || v.files[0];
    if (!file || file.size > MAX_BYTES) { report.push(`skip (file too big): ${h.slug}`); continue; }
    const buf = Buffer.from(await (await fetch(file.url)).arrayBuffer()); bytes += buf.length;
    const zip = new AdmZip(buf);
    const ents = zip.getEntries().filter((e) => !e.isDirectory && /(^|\/)data\/[^/]+\/structures?\/.+\.nbt$/i.test(e.entryName));
    const lic = zip.getEntries().find((e) => /^(license|licence|copying)(\.(txt|md))?$/i.test(path.basename(e.entryName)));
    let kept = 0;
    for (const e of ents) {
      if (kept >= MAX_PER_PROJECT) break;
      try {
        let raw = e.getData(); if (S.isGzip(raw)) raw = zlib.gunzipSync(raw);
        const b = S.schematicToBuild(new Uint8Array(raw), path.basename(e.entryName, '.nbt'));
        const blocks = b.blocks.filter((x) => !SKIP_BLOCKS.has(x[3]));
        if (blocks.length < MIN_BLOCKS) continue;
        if (blocks.some((x) => String(x[3]).includes(':'))) continue;   // modded blocks: the app can't show them
        const types = new Set(blocks.map((x) => x[3]));
        if (types.size < 4) continue;                                    // a slab of one block isn't a build
        const id = ('m_' + h.slug + '_' + e.entryName.replace(/^.*structures?\//, '').replace(/\.nbt$/, '')).toLowerCase().replace(/[^a-z0-9]+/g, '_').slice(0, 80);
        if (index.some((x) => x.id === id)) continue;
        fs.writeFileSync(path.join(OUT, 'schem', id + '.nbt'), e.getData());
        let mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
        for (const [x, y, z] of blocks) { mn = [Math.min(mn[0], x), Math.min(mn[1], y), Math.min(mn[2], z)]; mx = [Math.max(mx[0], x), Math.max(mx[1], y), Math.max(mx[2], z)]; }
        const nm = titleCase(path.basename(e.entryName, '.nbt')).slice(0, 40);
        const cat = (CATS.find(([re]) => re.test(e.entryName + ' ' + h.title)) || [null, 'Community Builds'])[1];
        const url = `https://modrinth.com/${h.type}/${h.slug}`;
        index.push({ id, name: nm, cat, schem: true, size: [mx[0] - mn[0] + 1, mx[1] - mn[1] + 1, mx[2] - mn[2] + 1], blocks: blocks.length, types: types.size,
          desc: `From "${h.title}" by ${h.author} (${h.license}). ${url}`, credit: `${h.author} (${h.license})`, source: url, license: h.license, tags: ['open-license'] });
        kept++; total++;
      } catch { /* not a structure file we can read */ }
    }
    if (kept) {
      fs.writeFileSync(path.join(OUT, 'licenses', h.slug + '.txt'), `${h.title} by ${h.author}\n${url(h)}\nLicense: ${h.license}\n\n` + (lic ? lic.getData().toString('utf8') : '(no license file in the download; license as listed on Modrinth)') + '\n');
    }
    report.push(`${kept ? 'KEPT ' + kept : 'none'}: ${h.slug} (${h.license}, ${ents.length} structure files)`);
  } catch (err) { report.push(`error ${h.slug}: ${err.message}`); }
}
function url(h) { return `https://modrinth.com/${h.type}/${h.slug}`; }
fs.writeFileSync(path.join(OUT, 'index.json'), JSON.stringify(index, null, 1));
fs.writeFileSync(path.join(OUT, 'report.txt'), [`templates: ${total}`, `downloaded: ${(bytes / 1e6).toFixed(0)} MB`, ...report].join('\n') + '\n');
console.log('templates', total);
