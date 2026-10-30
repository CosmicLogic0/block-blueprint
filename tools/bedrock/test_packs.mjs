// Bedrock server test: makes .mcpack files from a few builds, unpacks them into the server's behavior_packs,
// and writes the server commands that load each build and check some of its blocks.
// usage: node test_packs.mjs <repo> <bds dir>
import fs from 'fs'; import path from 'path'; import zlib from 'zlib'; import { execSync } from 'child_process';
const [root, bds] = process.argv.slice(2);
const { buildToMcpack, toBedrock } = await import(path.join(root, 'renderer/bedrock.js'));
const { schematicToBuild } = await import(path.join(root, 'renderer/schem.js'));
const map = JSON.parse(fs.readFileSync(path.join(root, 'renderer/bedrock-blocks.json'), 'utf8'));
const builds = [['House 1', JSON.parse(fs.readFileSync(path.join(root, 'renderer/templates/house1.json'), 'utf8'))]];
for (const f of ['m_cozy_cabins_and_cottages_cozy_cherry_cottage.nbt', 'm_mvs_moogs_voyager_structure_config_pack_1_20_0_houses_large_warped_tower.nbt']) {
  let u8 = fs.readFileSync(path.join(root, 'renderer/templates/schem', f)); if (u8[0] === 0x1f) u8 = zlib.gunzipSync(u8);
  builds.push([f.replace(/^m_|\.nbt$/g, '').slice(-24), schematicToBuild(new Uint8Array(u8), f, '')]);
}
const packs = [], cmds = ['tickingarea add 0 90 0 200 160 64 bb', 'time set noon'], checks = [];
let x0 = 0;
for (const [title, b] of builds) {
  const r = buildToMcpack(b, map, title);
  const dir = path.join(bds, 'behavior_packs', r.id); fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync('/tmp/' + r.id + '.mcpack', r.bytes); execSync(`unzip -o -q /tmp/${r.id}.mcpack -d "${dir}"`);
  const man = JSON.parse(fs.readFileSync(path.join(dir, 'manifest.json'), 'utf8'));
  packs.push({ pack_id: man.header.uuid, version: man.header.version });
  cmds.push(`structure load blueprint:${r.id} ${x0} 100 0`);
  // a few blocks to check, spread through the build
  const mn = [Infinity, Infinity, Infinity]; for (const [x, y, z] of b.blocks) { mn[0] = Math.min(mn[0], x); mn[1] = Math.min(mn[1], y); mn[2] = Math.min(mn[2], z); }
  const step = Math.max(1, Math.floor(b.blocks.length / 8));
  for (let i = 0; i < b.blocks.length; i += step) {
    const [x, y, z, id, label] = b.blocks[i]; const t = toBedrock(map, id, label); if (!t) continue;
    checks.push(`testforblock ${x0 + x - mn[0]} ${100 + y - mn[1]} ${z - mn[2]} ${t.name.replace('minecraft:', '')}`);
  }
  x0 += r.size[0] + 4;
}
fs.writeFileSync(path.join(bds, 'packs.json'), JSON.stringify(packs));
fs.writeFileSync(path.join(bds, 'cmds1.txt'), cmds.join('\n') + '\n');
fs.writeFileSync(path.join(bds, 'cmds2.txt'), checks.join('\n') + '\n');
console.log(packs.length, 'packs,', checks.length, 'checks');
