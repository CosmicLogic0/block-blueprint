// Builds the Bedrock add-on: puts the starter builds into the behavior pack (as structures), writes the credits,
// and zips both packs into dist/Build-Blueprint-Bedrock.mcaddon (open it and Minecraft Bedrock adds both packs).
// usage: node tools/bedrock/build_addon.mjs
import fs from 'fs'; import path from 'path'; import zlib from 'zlib'; import { fileURLToPath } from 'url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const { buildToMcstructure, zip } = await import(path.join(root, 'renderer/bedrock.js'));
const { schematicToBuild } = await import(path.join(root, 'renderer/schem.js'));
const map = JSON.parse(fs.readFileSync(path.join(root, 'renderer/bedrock-blocks.json'), 'utf8'));
const A = path.join(root, 'bedrock-addon');

// starter builds: [template file, id in the game (bb:<id>)]
const STARTERS = [
  ['m_cozy_cabins_and_cottages_cozy_cherry_cottage.nbt', 'cozy_cherry_cottage'],
  ['m_cozy_cabins_and_cottages_cozy_oak_cabin.nbt', 'cozy_oak_cabin'],
  ['m_cozy_cabins_and_cottages_cozy_oak_windmill.nbt', 'oak_windmill'],
  ['m_combat_structures_beach_house.nbt', 'beach_house'],
  ['m_mvs_moogs_voyager_structure_config_pack_1_20_0_houses_barn.nbt', 'barn'],
  ['m_timo_cherry_farm.nbt', 'cherry_farm'],
  ['m_underground_village_stoneholm_poi_copper_shop.nbt', 'copper_shop'],
  ['m_deserters_camps_oak_big_camp_buildings_big_magician_tent.nbt', 'magician_tent'],
  ['m_flying_castles_9x14_knighttower_3.nbt', 'knight_tower'],
  ['m_berezkas_castle_caste_villagers.nbt', 'small_castle'],
  ['m_christmas_dimension_village_church.nbt', 'church'],
  ['m_archaeology_ruins_jungle_temple_top.nbt', 'jungle_temple'],
  ['m_combat_structures_acacia_traveling_trade_boat.nbt', 'trade_boat'],
  ['m_combat_structures_boat.nbt', 'big_boat'],
];

const index = new Map();
for (const l of fs.readFileSync(path.join(root, 'renderer/templates/mod-index.tsv'), 'utf8').split('\n')) { const c = l.split('\t'); if (c.length > 8) index.set(c[1], c); }
const sdir = path.join(A, 'BP/structures/bb'); fs.rmSync(sdir, { recursive: true, force: true }); fs.mkdirSync(sdir, { recursive: true });
const ldir = path.join(A, 'BP/licenses'); fs.rmSync(ldir, { recursive: true, force: true }); fs.mkdirSync(ldir, { recursive: true });
const credits = ['Build Blueprint for Minecraft Bedrock', '', 'Starter builds, each used under its creator\'s open license (license texts in the licenses folder):', ''];
for (const [file, id] of STARTERS) {
  let u8 = fs.readFileSync(path.join(root, 'renderer/templates/schem', file)); if (u8[0] === 0x1f) u8 = zlib.gunzipSync(u8);
  const r = buildToMcstructure(schematicToBuild(new Uint8Array(u8), file, ''), map);
  fs.writeFileSync(path.join(sdir, id + '.mcstructure'), r.bytes);
  const c = index.get(file) || [];
  const project = (c[8] || '').split('/').pop();
  credits.push(`bb:${id} - "${c[2] || id}" by ${c[6] || '?'} (${c[7] || '?'}) ${c[8] || ''}`);
  const lic = path.join(root, 'renderer/templates/licenses', project + '.txt');
  if (project && fs.existsSync(lic)) fs.copyFileSync(lic, path.join(ldir, project + '.txt'));
  console.log(id, r.size.join('x'), r.missing.length ? 'MISSING ' + r.missing.join(',') : '');
}
credits.push('', 'Bedrock block names: GeyserMC mappings (MIT) https://github.com/GeyserMC/mappings');
fs.writeFileSync(path.join(A, 'BP/CREDITS.txt'), credits.join('\n') + '\n');
fs.copyFileSync(path.join(root, 'renderer/bedrock-blocks-LICENSE.txt'), path.join(ldir, 'GeyserMC-mappings.txt'));

// zip: BP/ and RP/ in one .mcaddon
const files = [];
const walk = (dir, pre) => { for (const n of fs.readdirSync(dir).sort()) { const p = path.join(dir, n); if (fs.statSync(p).isDirectory()) walk(p, pre + n + '/'); else files.push({ name: pre + n, data: new Uint8Array(fs.readFileSync(p)) }); } };
walk(path.join(A, 'BP'), 'Build Blueprint BP/');
walk(path.join(A, 'RP'), 'Build Blueprint RP/');
fs.mkdirSync(path.join(root, 'dist'), { recursive: true });
const out = path.join(root, 'dist/Build-Blueprint-Bedrock.mcaddon');
fs.writeFileSync(out, zip(files));
console.log(out, fs.statSync(out).size, 'bytes,', files.length, 'files');
