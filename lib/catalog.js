'use strict';
// Builds catalog.json from extracted Minecraft assets (blockstates + block models).
// Pure Node, no Electron dependency, so it can also be run from the command line:
//   node lib/catalog.js <assetsRoot>
const fs = require('fs');
const path = require('path');
const { buildCatalogCore } = require('./catalogCore');

function buildCatalog(assetsRoot) {
  const mc = path.join(assetsRoot, 'assets', 'minecraft');
  const catalog = buildCatalogCore({
    readJson: (rel) => { const f = path.join(mc, rel); return fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')) : null; },
    listBlockstates: () => fs.readdirSync(path.join(mc, 'blockstates')).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)),
    texExists: (name) => fs.existsSync(path.join(mc, 'textures', 'block', name + '.png')),
    entityExists: (src) => fs.existsSync(path.join(mc, 'textures', src + '.png')),
  });
  fs.writeFileSync(path.join(assetsRoot, 'catalog.json'), JSON.stringify(catalog));
  return { blocks: Object.keys(catalog.blocks).length, textures: catalog.textures.length };
}

module.exports = { buildCatalog };

if (require.main === module) {
  const root = path.resolve(process.argv[2] || '.');
  console.log(buildCatalog(root));
}
