import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, join, delimiter } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const home = process.env.DSH_HOME;
if (!home) throw new Error('DSH_HOME must point to the Omix AI data directory');
const bin = join(home, 'bin');
await mkdir(bin, { recursive: true });
const pnpm = join(root, 'node_modules/pnpm/bin/pnpm.cjs');
const quote = text => `'${text.replaceAll("'", "'\\''")}'`;
if (process.platform === 'win32') {
  await writeFile(join(bin, 'pnpm.cmd'), `@echo off\r\n"${process.execPath.replaceAll('%', '%%')}" "${pnpm.replaceAll('%', '%%')}" %*\r\n`);
} else {
  await writeFile(join(bin, 'pnpm'), `#!/bin/sh\nexec ${quote(process.execPath)} ${quote(pnpm)} "$@"\n`, { mode: 0o700 });
}
process.env.PATH = [bin, dirname(process.execPath), process.env.PATH ?? ''].join(delimiter);
const pluginMode = process.argv[2] === 'plugin';
const args = pluginMode
  ? ['--profile', 'omix', 'plugin', ...process.argv.slice(3)]
  : ['--profile', 'omix', '--from-default-profile', 'web', '--patch', join(root, 'omix.patch.yml'), '--no-open', '--host', '127.0.0.1', '--port', '0'];
const child = spawn(process.execPath, [join(root, 'node_modules/@deepseek-ai/dsh/lib/bin.js'), ...args], {
  cwd: home, env: process.env, stdio: ['ignore', 'inherit', 'inherit'],
});
child.once('error', error => { console.error(error.message); process.exitCode = 1; });
child.once('exit', code => { process.exitCode = code ?? 1; });
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => child.kill(signal));
