import test from 'node:test';
import assert from 'node:assert/strict';
import { request } from 'node:http';
import { Context } from '@deepseek-ai/cordis';
import { LlmRuntime, LlmAdapter } from '@deepseek-ai/dsh-llm';
import { startTranslationServer, translate, validateRequest, validateResult } from '../plugin/translation.mjs';

const token = 'test-only-translation-token-123456';
const body = { provider: 'fixture', model: 'small', target: 'zh-Hans', items: [
  { id: 'a', kind: 'chat', text: '[[s0]]Hello [[p0]][[/s0]]' },
  { id: 'b', kind: 'scoreboard', text: '[[s0]]Kills: [[p0]][[/s0]]' },
] };
const output = { items: [{ id: 'b', text: '[[s0]]击杀: [[p0]][[/s0]]' }, { id: 'a', text: '[[s0]]你好 [[p0]][[/s0]]' }] };
function fixture(stream) {
  const calls = [];
  return {
    calls,
    listProviders: () => [{ id: 'fixture' }],
    listModels: async () => [{ id: 'small', name: 'Small' }, { id: 'image', name: 'Image', inputModalities: ['image'] }],
    resolveModelInfo: async (provider, model) => ({ provider, id: model }),
    async prepareCall(config) {
      return { config, stream: stream ?? (async function* (options) {
        calls.push(options);
        yield { type: 'text-delta', index: 0, text: JSON.stringify(output) };
        yield { type: 'finish', reason: { kind: 'stop' } };
      }) };
    },
  };
}
test('one-shot request uses exact selected route, no tools or session, and validates reordered item ids', async () => {
  const llm = fixture();
  assert.deepEqual(await translate(llm, body, new AbortController().signal), output);
  const call = llm.calls[0];
  assert.equal(call.model, 'small'); assert.equal(call.provider, 'fixture');
  assert.deepEqual(call.tools, []); assert.equal(call.sessionId, undefined);
  assert.equal(call.messages.length, 1); assert.equal(call.messages[0].role, 'user');
  assert.equal(call.messages[0].source.plugin, 'omix-translation');
  assert.match(call.system, /untrusted data/);
});
test('translation selects the lowest advertised reasoning effort without changing provider defaults', async () => {
  for (const [levels, expected] of [
    [['high', 'low', 'minimal', 'medium'], 'minimal'],
    [['high', 'low'], 'low'],
    [['medium', 'none', 'low'], 'none'],
    [['minimal', 'off', 'low'], 'off'],
    [['high'], 'high'],
    [['budget-1024', 'budget-4096'], 'budget-1024'],
    [[], undefined],
  ]) {
    const llm = fixture();
    const reasoning = { defaultEffort: 'high', efforts: levels.map(id => ({ id, name: id })) };
    llm.resolveModelInfo = async (provider, model, signal) => {
      assert.equal(provider, body.provider); assert.equal(model, body.model); assert.ok(signal);
      return { provider, id: model, ...(levels.length ? { reasoning } : {}) };
    };
    await translate(llm, body, new AbortController().signal);
    assert.equal(llm.calls[0].reasoningEffort, expected);
    assert.equal(reasoning.defaultEffort, 'high');
    if (expected === undefined) assert.equal(Object.hasOwn(llm.calls[0], 'reasoningEffort'), false);
  }
});
test('model metadata resolution is cancellable and never dispatches after timeout', async t => {
  const llm = fixture();
  llm.resolveModelInfo = () => new Promise(() => {});
  const server = await startTranslationServer(llm, token, { timeoutMs: 100 }); t.after(() => server.close());
  const response = await fetch(server.endpoint + '/v1/translate', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  assert.equal(response.status, 504); assert.equal(llm.calls.length, 0);
});
test('minimum effort passes the real Harness prepared-call validation before adapter dispatch', async t => {
  const ctx = new Context();
  const llm = new LlmRuntime(ctx);
  const calls = [];
  class Adapter extends LlmAdapter {
    async resolveModel(provider, model) {
      return { provider, id: model, name: model, reasoning: {
        defaultEffort: 'high', efforts: ['low', 'high'].map(id => ({ id, name: id })),
      } };
    }
    async *stream(options) {
      calls.push(options);
      yield { type: 'text-delta', index: 0, text: JSON.stringify(output) };
      yield { type: 'finish', reason: { kind: 'stop' } };
    }
  }
  const dispose = llm.registerAdapter(['fixture'], new Adapter());
  t.after(() => dispose());
  assert.deepEqual(await translate(llm, body, new AbortController().signal), output);
  assert.equal(calls.length, 1); assert.equal(calls[0].reasoningEffort, 'low');
  assert.deepEqual(calls[0].tools, []);
});
test('request limits, supported targets, exact ids and placeholder preservation are enforced', () => {
  assert.throws(() => validateRequest({ ...body, target: 'unsupported' }));
  assert.throws(() => validateRequest({ ...body, items: [body.items[0], body.items[0]] }));
  assert.throws(() => validateRequest({ ...body, items: [{ ...body.items[0], text: 'a'.repeat(8001) }] }));
  assert.throws(() => validateResult(body.items, { items: [output.items[0], output.items[0]] }));
  assert.throws(() => validateResult(body.items, { items: [{ id: 'a', text: '你好' }, output.items[0]] }));
  assert.throws(() => validateResult(body.items, { ...output, explanation: 'extra' }));
  assert.throws(() => validateResult(body.items, { items: [{ id: 'a', text: '[[s0]]§cHello [[p0]][[/s0]]' }, output.items[0]] }));
});
test('truncated output and tool-call finishes are never accepted', async () => {
  for (const reason of ['max-tokens', 'tool-calls', 'error']) {
    const llm = fixture(async function* () {
      yield { type: 'text-delta', text: JSON.stringify(output) };
      yield { type: 'finish', reason: { kind: reason } };
    });
    await assert.rejects(translate(llm, body, new AbortController().signal));
  }
});
test('server authenticates token and exact Host, refuses browsers, and exposes only model metadata', async t => {
  const server = await startTranslationServer(fixture(), token); t.after(() => server.close());
  assert.equal((await fetch(server.endpoint + '/v1/models')).status, 403);
  assert.equal((await fetch(server.endpoint + '/v1/models', { headers: { Authorization: `Bearer ${token}`, Origin: 'http://localhost' } })).status, 403);
  const badHost = await new Promise(resolve => {
    const req = request(server.endpoint + '/v1/models', { headers: { Authorization: `Bearer ${token}`, Host: 'localhost' } }, res => { res.resume(); resolve(res.statusCode); }); req.end();
  });
  assert.equal(badHost, 403);
  const response = await fetch(server.endpoint + '/v1/models', { headers: { Authorization: `Bearer ${token}` } });
  assert.deepEqual(await response.json(), { providers: [{ id: 'fixture', models: [{ id: 'small', name: 'Small' }] }] });
  const translated = await fetch(server.endpoint + '/v1/translate', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  assert.deepEqual(await translated.json(), output);
});
test('timeouts abort an adapter even when its iterator ignores cancellation', async t => {
  const llm = fixture(() => ({ [Symbol.asyncIterator]: () => ({ next: () => new Promise(() => {}) }) }));
  const server = await startTranslationServer(llm, token, { timeoutMs: 100 }); t.after(() => server.close());
  const response = await fetch(server.endpoint + '/v1/translate', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  assert.equal(response.status, 504); assert.deepEqual(await response.json(), { error: 'TIMEOUT' });
});
test('provider failures redact secrets and rate limits remain distinguishable', async t => {
  const llm = fixture(); llm.prepareCall = async () => { throw new Error('api_key=super-secret-url'); };
  const server = await startTranslationServer(llm, token); t.after(() => server.close());
  let response = await fetch(server.endpoint + '/v1/translate', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  assert.equal(response.status, 502); assert.deepEqual(await response.json(), { error: 'PROVIDER_FAILED' });
  llm.prepareCall = fixture(async function* () { yield { type: 'finish', reason: { kind: 'error', failure: { code: 'RATE_LIMIT' } } }; }).prepareCall;
  response = await fetch(server.endpoint + '/v1/translate', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  assert.equal(response.status, 429); assert.deepEqual(await response.json(), { error: 'RATE_LIMIT' });
});
test('closing a runtime cancels its requests and a replacement requires its new token', async () => {
  const first = await startTranslationServer(fixture(), token);
  await first.close();
  const next = await startTranslationServer(fixture(), token + '-next');
  try {
    assert.equal((await fetch(next.endpoint + '/v1/models', { headers: { Authorization: `Bearer ${token}` } })).status, 403);
    assert.equal((await fetch(next.endpoint + '/v1/models', { headers: { Authorization: `Bearer ${token}-next` } })).status, 200);
  } finally { await next.close(); }
});
