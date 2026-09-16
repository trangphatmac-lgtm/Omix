import { createRequire } from 'node:module';
import { resolve, join } from 'node:path';
import { mkdtemp, mkdir } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { randomUUID } from 'node:crypto';

const runtime = resolve(process.argv[2]);
const require = createRequire(join(runtime, 'package.json'));
await mkdir('build/harness-smoke', { recursive: true });
const home = await mkdtemp(resolve('build/harness-smoke/native-'));
const load = name => import(pathToFileURL(require.resolve(name)).href);
const { Context } = await load('@deepseek-ai/cordis');
const { default: Persistence } = await load('@deepseek-ai/dsh-session-persistence-jsonl');
const { SESSION_FORMAT_VERSION } = await load('@deepseek-ai/dsh-session');
const ctx = new Context();
const persistence = new Persistence(ctx, { root: join(home, 'sessions') });
const id = randomUUID();
const handle = await persistence.create({ version: SESSION_FORMAT_VERSION, id, createdAt: Date.now(), cwd: home, isSeeded: false });
await handle.flush();
await handle.close();
const reopened = await persistence.open(id, 'read');
if (reopened.header.id !== id || (await reopened.read()).events.length !== 0) throw new Error('Session persistence round trip failed');
await reopened.close();
console.log('Native session persistence: create, flush, close, reopen passed');

const pty = require('node-pty');
await new Promise((resolveTest, reject) => {
  const terminal = pty.spawn(process.execPath, ['-e', 'process.stdout.write("omix-pty-ok")'], {
    name: 'xterm-color', cols: 80, rows: 24, cwd: home, env: process.env,
  });
  let text = '';
  const timer = setTimeout(() => { terminal.kill(); reject(new Error('PTY timeout')); }, 15000);
  terminal.onData(chunk => { text += chunk; });
  terminal.onExit(({ exitCode }) => {
    clearTimeout(timer);
    if (exitCode !== 0 || !text.includes('omix-pty-ok')) reject(new Error('PTY failed: '+text));
    else resolveTest();
  });
});
console.log('Native terminal: spawn and output passed');
const sharp = require('sharp');
const image = await sharp({ create: { width: 2, height: 2, channels: 3, background: '#ffffff' } }).png().toBuffer();
if (!image.length) throw new Error('Sharp returned no image');
console.log('Native image processing passed');
process.exit(0);
