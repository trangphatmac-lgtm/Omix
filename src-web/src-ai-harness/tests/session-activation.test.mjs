import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { serializeActivation } from '../plugin/session-activation.mjs';

// Exercise the controller actually shipped in lib/index.js, with only the
// persistence/factory boundary replaced by a deterministic publication barrier.
const source = await readFile(new URL('../node_modules/@deepseek-ai/dsh-api-session-controller/lib/index.js', import.meta.url), 'utf8');
const start = source.indexOf('var ApiSessionAgentController = class {');
const end = source.indexOf('\nfunction agentModelSelection', start);
assert.ok(start >= 0 && end > start, 'Pinned controller layout changed');
const Controller = runInNewContext(source.slice(start, end) + '\nApiSessionAgentController', {
  hasApiSessionSubagentOwner: () => false,
  RemoteError: class extends Error { constructor(code, message) { super(message); this.code = code; } },
  ApiSessionNotFound: class extends Error {}, ApiSessionSubagentOwnership: class extends Error {},
});
function fixture() {
  const live = new Map();
  const entered = Promise.withResolvers(), release = Promise.withResolvers();
  let writer = false, acquisitions = 0;
  const controller = new Controller({
    agents: { get: id => live.get(id) }, sessions: { get: () => undefined },
    typert: { lookups: { configure() {} }, contexts: { configureHost() {} } },
  });
  const acquire = async id => {
    if (writer) throw new Error('SessionAlreadyOwnedError: active write handle');
    writer = true; acquisitions++; entered.resolve();
    await release.promise;
    const agent = { id, session: { header: { cwd: '/workspace' } } };
    live.set(id, agent); return agent;
  };
  controller.createOrAdopt = async id => live.get(id) ?? acquire(id);
  controller.resume = acquire;
  return { controller, entered, release, acquisitions: () => acquisitions };
}

test('upstream split activation maps reproduce the reported active writer failure', async () => {
  const f = fixture();
  const creating = f.controller.ensureSession('same', '/workspace', true);
  await f.entered.promise;
  const resolving = await f.controller.resolveAgent('same');
  assert.match(resolving.error.message, /SessionAlreadyOwnedError/);
  f.release.resolve(); await creating;
});

for (const first of ['create', 'resume']) test(`serialize ${first} against concurrent history promotion and adoption`, async () => {
  const f = fixture(), dispose = serializeActivation(f.controller);
  const create = () => f.controller.ensureSession('same', '/workspace', true);
  const resume = () => f.controller.resolveObservedAgent({ header: { id: 'same' } });
  const a = first === 'create' ? create() : resume();
  await f.entered.promise;
  const b = first === 'create' ? resume() : create();
  const c = f.controller.resolveAgent('same');
  f.release.resolve();
  const results = await Promise.all([a, b, c]);
  assert.ok(results.every(result => !result.error));
  assert.equal(f.acquisitions(), 1);
  assert.strictEqual(results[2].agent, first === 'create' ? results[0] : results[1]);
  await dispose();
});

test('different sessions progress independently; failed activation allows retry and preserves arguments', async () => {
  const gate = Promise.withResolvers();
  let calls = 0;
  const controller = {
    async resolve(id, observation) {
      if (id === 'blocked') await gate.promise;
      if (id === 'retry' && calls++ === 0) throw new Error('setup failed');
      return { id, observation, receiver: this };
    },
    async ensureSession(id, cwd) { return { id, cwd }; },
  };
  const original = controller.resolve;
  const dispose = serializeActivation(controller);
  const blocked = controller.resolve('blocked');
  assert.equal((await controller.ensureSession('other', '/other')).cwd, '/other');
  await assert.rejects(controller.resolve('retry'), /setup failed/);
  const observation = {};
  const result = await controller.resolve('retry', observation);
  assert.strictEqual(result.observation, observation);
  assert.strictEqual(result.receiver, controller);
  gate.resolve(); await blocked; await dispose();
  assert.strictEqual(controller.resolve, original);
});

test('unloading rejects queued activation without stranding it or retaining wrappers', async () => {
  const gate = Promise.withResolvers(), entered = Promise.withResolvers();
  const controller = { resolve: async () => { entered.resolve(); await gate.promise; }, ensureSession() {} };
  const dispose = serializeActivation(controller);
  const active = controller.resolve('same'); await entered.promise;
  const queued = controller.ensureSession('same');
  const rejected = assert.rejects(queued, /disposed/);
  const disposed = dispose(); gate.resolve();
  await Promise.all([active, rejected, disposed]);
});
