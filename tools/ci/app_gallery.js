// Pictures of the free builds in the app's Templates window, one per kind (for showing people what's there).
// usage: node app_gallery.js <app executable> <out dir>   (run after app_smoke.js, so the textures are already there)
const { _electron: electron } = require('playwright');
const path = require('path');
(async () => {
  const [exe, out] = process.argv.slice(2);
  const app = await electron.launch({ executablePath: exe, args: process.platform === 'linux' ? ['--no-sandbox'] : [] });
  const w = await app.firstWindow();
  await app.evaluate(({ BrowserWindow }) => { const b = BrowserWindow.getAllWindows()[0]; b.setSize(1600, 1000); b.center(); });
  await w.waitForSelector('#loading', { state: 'hidden', timeout: 300000 });
  await w.waitForTimeout(3000);
  await w.evaluate(() => document.querySelector('#btnTemplates').click());
  await w.waitForTimeout(4000);
  for (const [i, kind] of ['Castles & Forts', 'Houses', 'Ships', 'Towers', 'Ruins & Temples', 'Village Buildings'].entries()) {
    const ok = await w.evaluate((k) => { const b = [...document.querySelectorAll('button.mcbtn')].find((x) => x.textContent.startsWith(k + ' (')); if (b) b.click(); return !!b; }, kind);
    await w.waitForTimeout(15000);   // thumbnails are drawn one by one
    await w.screenshot({ path: path.join(out, `gallery-${i + 1}.png`) });
    console.log('gallery', kind, ok);
  }
  await app.close();
})().catch((e) => { console.log('GALLERY FAILED ' + e); process.exit(1); });
