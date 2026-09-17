import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { mkdtemp, mkdir, writeFile, readFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { once } from 'node:events';

const runtime = resolve(process.argv[2] ?? 'src/main/java/im/src-ai-harness');
await mkdir('build/harness-smoke', { recursive: true });
const home = await mkdtemp(resolve('build/harness-smoke/home-'));
const workspace = join(home, 'Game directory 测试', 'Workspace');
let defaultWorkspaceId;
const bridge = createServer((req, res) => {
  res.setHeader('content-type', 'application/json');
  res.end(JSON.stringify(req.url === '/v1/reference' ? { text: 'Omix smoke fixture.' } : {
    protocolVersion: 1, worldEpoch: 1, gameContext: 'Smoke world', toolContext: 'Use getinventory',
    tools: [{ type: 'function', function: { name: 'getinventory', description: 'Read game inventory', parameters: { type: 'object', properties: {}, additionalProperties: false } } }],
  }));
});
bridge.listen(0, '127.0.0.1');
await once(bridge, 'listening');
async function launchAndCheck(serve = false) {
  const child = spawn(process.execPath, [join(runtime, 'launch.mjs')], {
    env: { ...process.env, DSH_HOME: home, OMIX_AI_WORKSPACE: workspace, OMIX_AI_BRIDGE: `http://127.0.0.1:${bridge.address().port}`, OMIX_AI_TOKEN: 'smoke-only' },
    stdio: ['ignore', 'pipe', 'pipe'], detached: process.platform !== 'win32',
  });
  let output = '';
  let url;
  for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => {
    output += chunk;
    url ??= output.match(/dsh web: (http:\/\/127\.0\.0\.1:\d+\/\?token=[^\s]+)/)?.[1];
  });
  try {
    const deadline = Date.now() + 90000;
    while (!url && child.exitCode === null && Date.now() < deadline) await new Promise(r => setTimeout(r, 100));
    if (!url) throw new Error('Startup failed: ' + output.slice(-12000));
    const exchange = await fetch(url, { redirect: 'manual' });
    if (![302, 303].includes(exchange.status)) throw new Error(`Token exchange HTTP ${exchange.status}`);
    const cookie = exchange.headers.getSetCookie().map(c => c.split(';')[0]).join('; ');
    if (!cookie || !exchange.headers.get('location')) throw new Error('No authenticated browser cookie');
    const root = new URL('/', url);
    const index = await fetch(root, { headers: { cookie } });
    const html = await index.text();
    if (index.status !== 200 || !html.includes('<html')) throw new Error('No Web shell');
    if (!html.includes('__OMIX_WORKSPACE_ID__') || !html.includes('omix-harness-workspace'))
      throw new Error('Default Workspace or its client plugin is missing from the authenticated page');
    const workspaceId = html.match(/globalThis\["__OMIX_WORKSPACE_ID__"\] = "([^"]+)"/)?.[1];
    if (!workspaceId || (defaultWorkspaceId && workspaceId !== defaultWorkspaceId))
      throw new Error('Default Workspace registration was not preserved across restart');
    defaultWorkspaceId = workspaceId;
    const denied = await fetch(new URL('/api/remote.mux', root));
    if (![401, 403].includes(denied.status)) throw new Error('Unauthenticated API was not rejected: ' + denied.status);
    console.log('Harness smoke passed: profile boot, Omix plugin activation, token/cookie exchange, Web shell, API authentication.');
    console.log('Fixture home: ' + home);
    if (serve) {
      await writeFile('build/harness-smoke/browser-url.txt', url, { mode: 0o600 });
      console.log('UI fixture ready; URL stored in build/harness-smoke/browser-url.txt');
      await new Promise(resolve => { process.once('SIGTERM', resolve); process.once('SIGINT', resolve); });
    }
  } finally {
    const exited = child.exitCode !== null || child.signalCode !== null ? Promise.resolve() : once(child, 'exit');
    if (process.platform !== 'win32') { try { process.kill(-child.pid, 'SIGTERM'); } catch {} }
    else await new Promise(r => { const stop = spawn('taskkill', ['/pid', String(child.pid), '/t', '/f']); stop.once('exit', r); });
    await exited;
  }
}

try {
  await launchAndCheck();
  const manifestPath = join(home, 'profiles/omix/package.json');
  const manifest = await readFile(manifestPath, 'utf8');
  const patchPath = join(home, 'profiles/omix/cordis.patch.yml');
  const patch = '# Omix restart smoke: preserve user overrides\n[]\n';
  await writeFile(patchPath, patch);
  await launchAndCheck(process.argv.includes('--serve'));
  if (await readFile(manifestPath, 'utf8') !== manifest || await readFile(patchPath, 'utf8') !== patch)
    throw new Error('Restart changed the existing profile or user overrides');
  console.log('Restart with the same profile and preserved user overrides passed.');
} finally {
  bridge.close();
}
