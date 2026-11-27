// Rendert conversa_erklaervideo.html Bild für Bild mit Chrome (headless) und setzt die Bilder mit ffmpeg zu einer MP4 zusammen.
// Aufruf:  node render.mjs --lang de|en [--short] [--frames 3.5,20]   (mit --frames nur einzelne PNG-Vorschaubilder)
// Liegt conversa_ton_<lang>.m4a daneben (python build_audio.py <lang>), wird der Ton mit eingebaut.
import { spawn } from 'node:child_process';
import { writeFileSync, mkdtempSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
const CHROME = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const FFMPEG = process.env.FFMPEG || 'ffmpeg';
const FPS = 30;

const args = process.argv.slice(2);
const fi = args.indexOf('--frames');
const previewFrames = fi >= 0 ? args[fi + 1].split(',').map(Number) : null;
const li = args.indexOf('--lang');
const LANG = li >= 0 ? args[li + 1] : 'de';
const SHORT = args.includes('--short');   // 9:16 für YouTube Shorts
const VW = SHORT ? 1080 : 1920, VH = SHORT ? 1920 : 1080;
const PORT = 9339 + (LANG === 'en' ? 2 : 0) + (SHORT ? 4 : 0);
const out = path.join(dir, SHORT ? `conversa_short_${LANG}.mp4` : `conversa_erklaervideo_${LANG}.mp4`);
const audio = path.join(dir, `conversa_ton_${LANG}.m4a`);

const profile = mkdtempSync(path.join(tmpdir(), 'conversa-render-'));
const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  '--hide-scrollbars', `--window-size=${VW},${VH}`, '--force-device-scale-factor=1', '--mute-audio', 'about:blank'],
  { stdio: 'ignore' });

const sleep = ms => new Promise(r => setTimeout(r, ms));
let target;
for (let i = 0; i < 100 && !target; i++) {
  try { target = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find(t => t.type === 'page'); }
  catch { await sleep(150); }
}
if (!target) throw new Error('Chrome nicht erreichbar');

const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
let id = 0; const pending = new Map();
ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } };
const send = (method, params = {}) => new Promise((res, rej) => {
  const i = ++id; pending.set(i, m => m.error ? rej(new Error(method + ': ' + JSON.stringify(m.error))) : res(m.result));
  ws.send(JSON.stringify({ id: i, method, params }));
});
const evaluate = async expr => (await send('Runtime.evaluate', { expression: expr, returnByValue: true })).result.value;

await send('Emulation.setDeviceMetricsOverride', { width: VW, height: VH, deviceScaleFactor: 1, mobile: false });
const url = pathToFileURL(path.join(dir, 'conversa_erklaervideo.html')).href + `?render=1&lang=${LANG}${SHORT ? '&format=short' : ''}`;
await send('Page.navigate', { url });
for (let i = 0; i < 200 && !(await evaluate('window.__ready === true').catch(() => false)); i++) await sleep(100);

const shot = async t => {
  await evaluate(`renderAt(${t}); true`);
  const r = await send('Page.captureScreenshot', { format: 'png', clip: { x: 0, y: 0, width: VW, height: VH, scale: 1 } });
  return Buffer.from(r.data, 'base64');
};

try {
  if (previewFrames) {
    for (const s of previewFrames) {
      const f = path.join(dir, `vorschau_${SHORT ? 'short_' : ''}${LANG}_${String(s).replace('.', '_')}s.png`);
      writeFileSync(f, await shot(s)); console.log(f);
    }
  } else {
    const withAudio = existsSync(audio);
    const ff = spawn(FFMPEG, ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-',
      ...(withAudio ? ['-i', audio, '-map', '0:v', '-map', '1:a', '-c:a', 'aac', '-b:a', '192k'] : []),
      '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', '-shortest', out],
      { stdio: ['pipe', 'inherit', 'inherit'] });
    const DUR = await evaluate('window.DUR');
    const total = Math.round(FPS * DUR);
    const started = Date.now();
    for (let f = 0; f < total; f++) {
      const buf = await shot(f / FPS);
      if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
      if (f % 60 === 0) console.log(`Bild ${f}/${total} (${Math.round((Date.now() - started) / 1000)} s)`);
    }
    ff.stdin.end();
    const code = await new Promise(r => ff.on('close', r));
    if (code !== 0) throw new Error('ffmpeg Exit-Code ' + code);
    console.log('Fertig: ' + out);
  }
} finally {
  ws.close(); chrome.kill();
  await sleep(500);
  try { rmSync(profile, { recursive: true, force: true }); } catch {}
}
