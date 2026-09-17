import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdtemp, stat, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { runInNewContext } from 'node:vm';
import { apply } from '../plugin/workspace/index.mjs';

test('host creates Workspace and publishes its id without replacing an existing registration', async () => {
  const root = await mkdtemp(join(tmpdir(), 'omix-workspace-'));
  const previous = process.env.OMIX_AI_WORKSPACE;
  const path = join(root, '游戏 directory', 'Workspace');
  process.env.OMIX_AI_WORKSPACE = path;
  const hooks = new Map();
  let registrations = 0;
  const ctx = {
    workspaceRegistry: { create: async (value, title) => {
      assert.equal(value, path); assert.equal(title, 'Workspace');
      assert.ok((await stat(path)).isDirectory());
      registrations++; return { id: 'stable-workspace' };
    } },
    on: (event, fn) => hooks.set(event, fn),
  };
  try {
    await apply(ctx); await apply(ctx);
    const rows = []; hooks.get('webserver/index-inject')(rows);
    assert.equal(registrations, 2);
    assert.deepEqual(rows, [{ kind: 'global', name: '__OMIX_WORKSPACE_ID__', value: 'stable-workspace' }]);
  } finally {
    if (previous === undefined) delete process.env.OMIX_AI_WORKSPACE; else process.env.OMIX_AI_WORKSPACE = previous;
    await rm(root, { recursive: true, force: true });
  }
});

const source = await readFile(new URL('../plugin/workspace/client.js', import.meta.url), 'utf8');
function clientFixture(current, ready = true) {
  let plugin;
  const listeners = new Set();
  const workspace = { phase: ready ? 'ready' : 'loading', items: [{ workspaceId: 'default', sessionIds: ['existing'] }] };
  const session = { phase: ready ? 'ready' : 'loading', current };
  const store = value => ({ getSnapshot: () => value, subscribe: fn => { listeners.add(fn); return () => listeners.delete(fn); } });
  const opened = [];
  let dispose;
  runInNewContext(source, { window: { __OMIX_WORKSPACE_ID__: 'default', __ModuleLoader__: { load: entry => plugin = entry.factory() } }, console });
  plugin.apply({
    workspaces: { list: store(workspace) }, sessions: { list: store(session) },
    uiWorkspace: { openWorkspace: async id => { opened.push(id); } },
    effect: fn => dispose = fn(),
  });
  return { opened, session, workspace, listeners, dispose, notify: () => [...listeners].forEach(fn => fn()) };
}

test('client waits for snapshots then selects Workspace once; manual navigation remains possible', () => {
  const f = clientFixture(undefined, false);
  assert.deepEqual(f.opened, []);
  f.workspace.phase = f.session.phase = 'ready'; f.notify();
  assert.deepEqual(f.opened, ['default']);
  f.session.current = 'manually-selected'; f.notify();
  assert.deepEqual(f.opened, ['default']);
  f.dispose(); assert.equal(f.listeners.size, 0);
});

test('client preserves a conversation in Workspace and replaces another workspace selection on page load', () => {
  assert.deepEqual(clientFixture('existing').opened, []);
  assert.deepEqual(clientFixture('other-workspace-session').opened, ['default']);
});

test('disposed client does not select a workspace when data arrives later', () => {
  const f = clientFixture(undefined, false);
  f.dispose(); f.workspace.phase = f.session.phase = 'ready'; f.notify();
  assert.deepEqual(f.opened, []);
});
