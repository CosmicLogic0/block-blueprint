// Opens the built app (Mac / Linux test), does the first-time setup (downloads the textures from Mojang),
// then takes pictures of the editor and the templates. usage: node app_smoke.js <app executable> <out dir>
const { _electron: electron } = require('playwright');
const path = require('path');
(async () => {
  const [exe, out] = process.argv.slice(2);
  const app = await electron.launch({ executablePath: exe, args: process.platform === 'linux' ? ['--no-sandbox'] : [] });
  const w = await app.firstWindow();
  const log = [];
  app.process().stdout.on('data', (d) => log.push('main: ' + String(d).trim()));
  app.process().stderr.on('data', (d) => log.push('main-err: ' + String(d).trim().slice(0, 300)));
  process.on('exit', () => { console.log(log.slice(-60).join('\n')); });
  globalThis.__w = w;
  w.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') log.push(m.type() + ': ' + m.text()); });
  w.on('pageerror', (e) => log.push('pageerror: ' + e.message));
  await w.waitForTimeout(4000);
  await w.screenshot({ path: path.join(out, 'app-1-setup.png') });
  if (await w.isVisible('#sDownload')) {
    await w.click('#sDownload');
    await w.waitForSelector('#setup', { state: 'hidden', timeout: 300000 });
  }
  await w.waitForSelector('#loading', { state: 'hidden', timeout: 300000 });
  await w.waitForTimeout(5000);
  await w.screenshot({ path: path.join(out, 'app-2-editor.png') });
  await w.evaluate(() => document.querySelector('#btnTemplates').click());
  await w.waitForTimeout(6000);
  await w.screenshot({ path: path.join(out, 'app-3-templates.png') });
  console.log('APP OK');
  await app.close();
})().catch(async (e) => {
  console.log('APP FAILED ' + String(e).split('\n')[0]);
  try { const w = globalThis.__w; await w.screenshot({ path: path.join(process.argv[3], 'app-fail.png') });
        console.log('loading text: ' + await w.evaluate(() => (document.querySelector('#loading') || {}).innerText)); } catch (x) {}
  process.exit(1);
});
