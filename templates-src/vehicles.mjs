// Vehicles kit: curved hulls, fuselages, sails, wheels.
import { hash3 } from './lib.mjs';

/**
 * Ship hull along x (bow at +x). len, beam (width), depth. Shell only, deck at the top.
 * mat = { hull, stripe, keel, deck, rail }   sheer = how much bow/stern rise.
 */
export function hull(b, len, beam, depth, mat, opts = {}) {
  const { sheer = 2, bowSharp = 1.6, sternFlat = 0.6, y0 = 0 } = opts;
  const half = beam / 2;
  const widthAt = (t) => { // t: 0 stern .. 1 bow
    if (t > 0.65) return half * Math.pow(Math.max(0, 1 - (t - 0.65) / 0.35), 1 / bowSharp);
    if (t < 0.15) return half * (sternFlat + (1 - sternFlat) * (t / 0.15));
    return half;
  };
  const deckAt = (t) => y0 + depth + Math.round(sheer * (Math.pow(Math.abs(t - 0.5) * 2, 3)));
  for (let x = 0; x < len; x++) {
    const t = x / (len - 1);
    const w = widthAt(t), top = deckAt(t);
    for (let y = y0; y <= top; y++) {
      const yr = (y - y0) / Math.max(1, depth); // 0 keel .. 1 deck
      const wy = w * Math.min(1, Math.sqrt(Math.max(0.02, yr * 1.3)));
      const iw = Math.round(wy);
      for (let z = -iw; z <= iw; z++) {
        const shell = Math.abs(z) >= iw - 0 || y === y0 || x === 0 || x === len - 1;
        if (!shell && y < top) continue;
        let m = mat.hull;
        if (y === y0) m = mat.keel || mat.hull;
        if (mat.stripe && y === top - 1 && Math.abs(z) === iw) m = mat.stripe;
        if (y === top) m = Math.abs(z) === iw ? (mat.rail || mat.hull) : mat.deck;
        b.set(x, y, z, m);
      }
      // inner hull fill at the deck so it isn't hollow from above
    }
    // railing on the deck edge (below)
    if (mat.fence) { const iw = Math.round(w); if (iw > 0) { b.set(x, top + 1, -iw, mat.fence); b.set(x, top + 1, iw, mat.fence); } }
  }
  // smooth the curved sides: shell cells that overhang the row below become upside-down stairs
  if (mat.stairs) {
    const cells = [];
    b.each((x, y, z, n) => { if (n === mat.hull && z !== 0 && !b.has(x, y - 1, z) && y > y0) cells.push([x, y, z]); });
    for (const [x, y, z] of cells) b.stairs(x, y, z, mat.stairs, z > 0 ? 'north' : 'south', 'top');
    const bow = [];
    b.each((x, y, z, n) => { if (n === mat.hull && !b.has(x + 1, y, z) && b.has(x, y + 1, z) && !b.has(x + 1, y - 1, z) && x > len * 0.6) bow.push([x, y, z]); });
    for (const [x, y, z] of bow) if (b.get(x, y, z) === mat.hull) b.stairs(x, y, z, mat.stairs, 'west', 'top');
  }
  return { deckAt, widthAt };
}

/** Square sail on a mast at x, from y0 up h, width w (z-span), billowing toward +x. */
export function squareSail(b, x, y0, h, w, cloth = 'white_wool', billow = 1) {
  for (let y = y0; y < y0 + h; y++) for (let z = -w; z <= w; z++) {
    const bz = Math.abs(z) / w, by = Math.abs((y - y0) / (h - 1) - 0.5) * 2;
    const out = Math.round(billow * (1 - bz * bz) * (1 - by * by * 0.6));
    b.set(x + out, y, z, cloth);
  }
  for (let z = -w - 1; z <= w + 1; z++) { b.log(x, y0 + h, z, 'stripped_dark_oak_log', 'z'); b.log(x, y0 - 1, z, 'stripped_dark_oak_log', 'z'); }
}
/** Triangular (fore-and-aft) sail from a mast at (x,z=0) toward -x along a boom. */
export function triSail(b, x, y0, h, len, cloth = 'white_wool', side = 0) {
  for (let y = y0; y < y0 + h; y++) {
    const k = Math.round(len * (1 - (y - y0) / h));
    for (let i = 1; i <= k; i++) b.set(x - i, y, side + (i > len * 0.5 && (y - y0) < h * 0.5 ? 1 : 0), cloth);
  }
  for (let i = 0; i <= len; i++) b.log(x - i, y0 - 1, 0, 'stripped_spruce_log', 'x');
}

/** Wheel: black concrete with a hubcap button on the outside face. */
export function wheel(b, x, y, z, side) {
  b.set(x, y, z, 'black_concrete');
  b.set(x, y, z + side, 'polished_blackstone_button', { face: 'wall', facing: side > 0 ? 'south' : 'north' });
}

/** Cylinder along x from x0..x1 with radius r(x), centred on (y=cy, z=0). shell only. */
export function tube(b, x0, x1, cy, r, matAt) {
  for (let x = x0; x <= x1; x++) {
    const rr = typeof r === 'function' ? r(x) : r;
    for (let y = -Math.ceil(rr); y <= Math.ceil(rr); y++) for (let z = -Math.ceil(rr); z <= Math.ceil(rr); z++) {
      const d = Math.hypot(y, z);
      if (d > rr + 0.35) continue;
      const shell = d > rr - 0.75 || x === x0 || x === x1;
      if (!shell) continue;
      b.set(x, cy + y, z, matAt(x, cy + y, z, d, rr));
    }
  }
}
export { hash3 };
