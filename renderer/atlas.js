import * as THREE from 'three';

export const COLS = 64; // 64x64 tiles = room for 4096 textures

// Packs every block texture (first square frame) into one nearest-filtered atlas.
// Tile size follows the sharpest texture found (16 for vanilla, up to 64 for HD packs).
export async function buildAtlas(base, names, onProgress, loadBitmap) {
  if (names.length + 1 > COLS * COLS) throw new Error('Too many textures for the atlas');

  // A name is either a block texture ("stone") or a cut-out of an entity sheet ("entity/bed/red@6,6,16,16/64").
  const crops = names.map((n) => {
    const m = /^(.+)@(\d+),(\d+),(\d+),(\d+)\/(\d+)$/.exec(n);
    return m ? { src: m[1], r: [+m[2], +m[3], +m[4], +m[5]], ref: +m[6] } : null;
  });
  const fetchBmp = async (rel) => {
    if (loadBitmap) return loadBitmap(rel);
    const res = await fetch(`${base}assets/minecraft/textures/${rel}.png`);
    if (!res.ok) throw new Error('missing ' + rel);
    return createImageBitmap(await res.blob());
  };

  // Pass 1: load everything and find the native resolution (from block textures only).
  const bitmaps = new Array(names.length);
  const sheets = new Map(); // entity sheet path -> bitmap | null
  let next = 0, loaded = 0, maxW = 16;
  async function loader() {
    while (next < names.length) {
      const i = next++;
      try {
        if (crops[i]) {
          const src = crops[i].src;
          if (!sheets.has(src)) sheets.set(src, fetchBmp(src).catch(() => null));
          bitmaps[i] = await sheets.get(src);
        } else {
          bitmaps[i] = await fetchBmp('block/' + names[i]);
          maxW = Math.max(maxW, bitmaps[i].width);
        }
      } catch { bitmaps[i] = null; }
      if (++loaded % 40 === 0) onProgress && onProgress(0.5 * loaded / names.length);
    }
  }
  await Promise.all(Array.from({ length: 24 }, loader));
  let TILE = 16; while (TILE < maxW && TILE < 64) TILE *= 2;
  const SIZE = TILE * COLS;

  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = SIZE;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  ctx.imageSmoothingEnabled = false;

  // Tile 0 = "missing texture" checker.
  const h = TILE / 2;
  ctx.fillStyle = '#000'; ctx.fillRect(0, 0, TILE, TILE);
  ctx.fillStyle = '#f800f8'; ctx.fillRect(0, 0, h, h); ctx.fillRect(h, h, h, h);

  // Pass 2: draw into tiles (every texture scaled with nearest-neighbour to the tile size).
  const index = {}, opaque = {}, translucent = {};
  names.forEach((name, i) => {
    const idx = i + 1, bmp = bitmaps[i];
    if (!bmp) { index[name] = 0; opaque[name] = true; return; }
    index[name] = idx;
    const tx = (idx % COLS) * TILE, ty = Math.floor(idx / COLS) * TILE;
    const cr = crops[i];
    if (cr) {
      const f = bmp.width / cr.ref;
      ctx.drawImage(bmp, cr.r[0] * f, cr.r[1] * f, cr.r[2] * f, cr.r[3] * f, tx, ty, TILE, TILE); // entity sheets are shared, so they stay open
    } else {
      ctx.drawImage(bmp, 0, 0, bmp.width, bmp.width, tx, ty, TILE, TILE); // top square = first animation frame
      bmp.close && bmp.close();
    }
    const d = ctx.getImageData(tx, ty, TILE, TILE).data;
    let solid = true, semi = 0;
    for (let p = 3; p < d.length; p += 4) { if (d[p] < 250) solid = false; if (d[p] > 8 && d[p] < 245) semi++; }
    opaque[name] = solid;
    translucent[name] = semi > (d.length / 4) * 0.1; // stained glass, ice, water: drawn see-through instead of cut out
    if (i % 80 === 0) onProgress && onProgress(0.5 + 0.5 * i / names.length);
  });

  const texture = new THREE.CanvasTexture(canvas);
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.NearestFilter;
  texture.generateMipmaps = false;
  texture.anisotropy = 8;
  texture.needsUpdate = true;
  return { canvas, texture, index, opaque, translucent, tile: TILE, size: SIZE };
}
