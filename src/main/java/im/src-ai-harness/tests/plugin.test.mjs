import test from 'node:test';
import assert from 'node:assert/strict';
import { apply, bridgeClient, identity } from '../plugin/omix.mjs';

function fixture(extraTools = []) {
  const requests = [];
  const tools = new Map();
  const hooks = new Map();
  const sections = [];
  let epoch = 2;
  let failure;
  let holdCalls = false;
  const fetcher = async (url, options) => {
    requests.push({ url, options });
    if (holdCalls && url.endsWith('/v1/calls')) {
      return new Promise((_resolve, reject) => {
        options.signal.addEventListener('abort', () => reject(options.signal.reason), { once: true });
      });
    }
    const value = url.endsWith('/v1/reference') ? { text: 'module reference' }
      : url.endsWith('/v1/snapshot') ? { protocolVersion: 1, worldEpoch: epoch, gameContext: 'game '+epoch, toolContext: 'commands', tools: [
        { function: { name: 'getcommandsuggestion', description: 'suggestions', parameters: { type: 'object', properties: { perfix: { type: 'string' } }, required: ['perfix'], additionalProperties: false } } },
        ...extraTools.map(schema => ({ function: schema })),
      ] } : url.endsWith('/v1/calls') ? failure ?? { ok: true, value: { status: 'awaiting_sync' } } : { ok: true };
    return new Response(JSON.stringify(value), { status: 200 });
  };
  const ctx = { tools: { register: tool => { tools.set(tool.name, tool); return () => { if (tools.get(tool.name) === tool) tools.delete(tool.name); }; } }, systemPrompt: { section: section => sections.push(section) }, on: (key, fn) => hooks.set(key, fn), effect: () => {} };
  return { requests, tools, hooks, sections, ctx, fetcher, setEpoch: n => epoch = n, fail: e => failure = e, holdCalls: () => holdCalls = true };
}

test('validates bridge location, passes auth and preserves errors', async () => {
  assert.throws(() => bridgeClient('http://example.org', 'key'));
  const request = bridgeClient('http://127.0.0.1:8', 'key', async (_url, options) => {
    assert.equal(options.headers.Authorization, 'Bearer key');
    return new Response('{"ok":false,"error":"snapshot stale"}');
  });
  await assert.rejects(request('/v1/calls'), /snapshot stale/);
});

test('plugin refreshes schemas and context, maps domain results and releases ownership', async () => {
  const f = fixture(); const oldFetch = globalThis.fetch;
  const oldEndpoint = process.env.OMIX_AI_BRIDGE, oldToken = process.env.OMIX_AI_TOKEN;
  globalThis.fetch = f.fetcher; process.env.OMIX_AI_BRIDGE = 'http://127.0.0.1:8'; process.env.OMIX_AI_TOKEN = 'fixture';
  try {
    await apply(f.ctx);
    const tool = f.tools.get('getcommandsuggestion');
    assert.deepEqual(tool.parameters.required, ['perfix']);
    assert.ok(f.sections[0].text.includes('module reference'));
    const agent = { session: { id: 'test-session' } };
    const assembly = { tools: [tool], contexts: [] };
    const assemble = f.hooks.get('system-prompt/assemble');
    f.setEpoch(3);
    await assemble(assembly, { agent }, async () => assembly);
    assert.match(assembly.contexts[0].text, /game 3/);
    const exec = { agent, callId: 'abc', token: Symbol('dispatch'), signal: new AbortController().signal };
    assert.deepEqual(await tool.execute({ perfix: '/help' }, exec), { status: 'awaiting_sync' });
    const call = JSON.parse(f.requests.find(r => r.url.endsWith('/v1/calls')).options.body);
    assert.equal(call.worldEpoch, 3); assert.equal(call.agentId, identity('test-session'));
    await tool.execute({ perfix: '/help' }, exec);
    await tool.execute({ perfix: '/help' }, { ...exec, token: Symbol('next dispatch') });
    const ids = f.requests.filter(r => r.url.endsWith('/v1/calls')).map(r => JSON.parse(r.options.body).id);
    assert.equal(ids[0], ids[1], 'retry of a dispatch preserves its bridge ID');
    assert.notEqual(ids[0], ids[2], 'reused provider call IDs do not replay an older action');
    f.fail({ ok: false, error: 'stale snapshot' });
    await assert.rejects(tool.execute({ perfix: '' }, { ...exec, callId: 'def' }), /stale snapshot/);
    await f.hooks.get('agent/turn-stopping')({ agent });
    assert.ok(f.requests.some(r => r.url.endsWith('/release')));
    const aborted = AbortSignal.abort();
    const count = f.requests.length;
    await assert.rejects(tool.execute({}, { ...exec, signal: aborted }));
    assert.equal(f.requests.length, count);
    f.holdCalls();
    const controller = new AbortController();
    const pending = tool.execute({ perfix: '' }, { ...exec, token: Symbol('cancel dispatch'), signal: controller.signal });
    const pendingId = JSON.parse(f.requests.at(-1).options.body).id;
    controller.abort();
    await assert.rejects(pending, { name: 'AbortError' });
    assert.equal(f.requests.filter(r => r.options.method === 'DELETE' && r.url.endsWith('/' + pendingId)).length, 1);
  } finally {
    globalThis.fetch = oldFetch;
    if (oldEndpoint === undefined) delete process.env.OMIX_AI_BRIDGE; else process.env.OMIX_AI_BRIDGE = oldEndpoint;
    if (oldToken === undefined) delete process.env.OMIX_AI_TOKEN; else process.env.OMIX_AI_TOKEN = oldToken;
  }
});


test('packet tools use dynamic schemas, epoch-bound dispatch and preserve structured logs', async () => {
  const schemas = ['configurepacketslogger', 'getpacketlogs', 'clearpacketlogs'].map(name => ({
    name, description: 'packet tool', parameters: { type: 'object', properties: {}, additionalProperties: false },
  }));
  const f = fixture(schemas);
  const oldFetch = globalThis.fetch;
  const oldEndpoint = process.env.OMIX_AI_BRIDGE, oldToken = process.env.OMIX_AI_TOKEN;
  globalThis.fetch = f.fetcher;
  process.env.OMIX_AI_BRIDGE = 'http://127.0.0.1:8'; process.env.OMIX_AI_TOKEN = 'fixture';
  try {
    await apply(f.ctx);
    for (const schema of schemas) assert.ok(f.tools.has(schema.name));
    assert.ok(f.sections[0].text.includes('packet contents'));
    const agent = { session: { id: 'packet-analysis' } };
    const assembly = { tools: [...f.tools.values()], contexts: [] };
    await f.hooks.get('system-prompt/assemble')(assembly, { agent }, async () => assembly);
    const result = { sessionId: 'capture-1', nextCursor: 'capture-1:7', hasMore: false, missed: 2,
      logs: [{ packet: 'minecraft:system_chat', details: 'untrusted packet content', direction: 'RECEIVED' }] };
    f.fail({ ok: true, value: result });
    const args = { limit: 20, includeDetails: true };
    assert.deepEqual(await f.tools.get('getpacketlogs').execute(args, {
      agent, token: Symbol('packet-read'), signal: new AbortController().signal,
    }), result);
    const call = JSON.parse(f.requests.find(r => r.url.endsWith('/v1/calls')).options.body);
    assert.equal(call.name, 'getpacketlogs');
    assert.equal(call.worldEpoch, 2);
    assert.deepEqual(call.arguments, args);
    assert.equal(call.agentId, identity('packet-analysis'));
    await f.hooks.get('agent/turn-stopping')({ agent });
    assert.ok(f.requests.some(r => r.url.endsWith('/release')));
  } finally {
    globalThis.fetch = oldFetch;
    if (oldEndpoint === undefined) delete process.env.OMIX_AI_BRIDGE; else process.env.OMIX_AI_BRIDGE = oldEndpoint;
    if (oldToken === undefined) delete process.env.OMIX_AI_TOKEN; else process.env.OMIX_AI_TOKEN = oldToken;
  }
});

test('v2 Harness shares one lease across concurrent tools, attaches screenshots and releases it', async () => {
  const requests = [], tools = new Map(), hooks = new Map(), attachments = [];
  const oldFetch = globalThis.fetch, oldEndpoint = process.env.OMIX_AI_BRIDGE, oldToken = process.env.OMIX_AI_TOKEN;
  const ctx = {tools:{register:tool=>{tools.set(tool.name,tool);return ()=>{if(tools.get(tool.name)===tool)tools.delete(tool.name);};}}, systemPrompt:{section:()=>{}},
    on:(name,callback)=>hooks.set(name,callback), effect:()=>{},
    attachments:{saveImage:async image=>{attachments.push(image);return {id:'image-'+attachments.length};}}};
  globalThis.fetch = async (url, options) => {
    requests.push({url,options});
    const value = url.endsWith('/snapshot') ? {protocolVersion:2,worldEpoch:4,gameContext:'menu',tools:[{function:{name:'script_screenshot',description:'screenshot',parameters:{type:'object',properties:{}}}}]}
      : url.endsWith('/reference') ? {text:'reference'} : url.endsWith('/sessions') ? {agentId:'leased-agent'}
      : url.endsWith('/calls') ? {ok:true,value:{path:'/screenshot.png',mimeType:'image/png',data:Buffer.from('png bytes').toString('base64')}} : {ok:true};
    await Promise.resolve(); return new Response(JSON.stringify(value));
  };
  process.env.OMIX_AI_BRIDGE='http://127.0.0.1:8';process.env.OMIX_AI_TOKEN='fixture';
  const agent={session:{id:'studio'}};
  try {
    await apply(ctx);
    const assembly={tools:[...tools.values()],contexts:[]};
    await hooks.get('system-prompt/assemble')(assembly,{agent},async()=>assembly);
    const tool=tools.get('script_screenshot');
    const results=await Promise.all([1,2].map(n=>tool.execute({}, {agent,token:Symbol('call-'+n),signal:new AbortController().signal})));
    assert.equal(requests.filter(r=>r.url.endsWith('/v2/sessions')).length,1);
    assert.equal(attachments.length,2);assert.equal(attachments[0].mediaType,'image/png');assert.ok(Buffer.isBuffer(attachments[0].data));
    assert.equal(tool.output.render({},results[0])[0].type,'image');assert.equal(results[0].data,undefined);
    for(const request of requests.filter(r=>r.url.endsWith('/v2/calls'))) assert.equal(JSON.parse(request.options.body).agentId,'leased-agent');
    await hooks.get('agent/turn-stopping')({agent});
    assert.ok(requests.some(r=>r.url.endsWith('/v2/agents/leased-agent/release')));
  } finally {
    await hooks.get('agent/turn-stopping')?.({agent});globalThis.fetch=oldFetch;
    if(oldEndpoint===undefined)delete process.env.OMIX_AI_BRIDGE;else process.env.OMIX_AI_BRIDGE=oldEndpoint;
    if(oldToken===undefined)delete process.env.OMIX_AI_TOKEN;else process.env.OMIX_AI_TOKEN=oldToken;
  }
});

test('new, replaced and unloaded script tools reach the current real Harness prompt and registry', async () => {
  const {Context} = await import('@deepseek-ai/cordis');
  const {SystemPrompt} = await import('@deepseek-ai/dsh-system-prompt');
  const {ToolRuntime} = await import('@deepseek-ai/dsh-tools');
  const {createScope} = await import('@deepseek-ai/dsh-scope');
  const ctx = new Context();
  new SystemPrompt(ctx, {}); new ToolRuntime(ctx);
  const extra = [], f = fixture(extra);
  const previous = {fetch:globalThis.fetch, endpoint:process.env.OMIX_AI_BRIDGE, token:process.env.OMIX_AI_TOKEN};
  globalThis.fetch=f.fetcher; process.env.OMIX_AI_BRIDGE='http://127.0.0.1:8'; process.env.OMIX_AI_TOKEN='fixture';
  const agent = {session:{id:'dynamic'}};
  try {
    await apply(ctx);
    assert.ok(!(await ctx.systemPrompt.assemble({agent})).tools.some(t=>t.name==='custom_test'));
    const restrictedKey = {};
    const restricted = createScope(ctx, restrictedKey);
    restricted.ctx.tools.restrict({allow:['getcommandsuggestion']});
    extra.push({name:'custom_test',description:'v1',parameters:{type:'object',properties:{value:{type:'integer',minimum:1}},required:['value'],additionalProperties:false}});
    const filtered = await ctx.systemPrompt.assemble({agent, scope:restrictedKey});
    assert.ok(!filtered.tools.some(tool=>tool.name==='custom_test'), 'dynamic registration respects agent restrictions');
    await restricted.dispose();
    let assembled = await ctx.systemPrompt.assemble({agent});
    assert.equal(assembled.tools.find(t=>t.name==='custom_test').description,'v1');
    assert.ok(ctx.tools.get('custom_test'));
    const execution = await ctx.tools.execute({name:'custom_test',arguments:{value:2},callId:'custom-dispatch',agent,signal:new AbortController().signal});
    assert.equal(execution.isError, false, JSON.stringify(execution));
    assert.deepEqual(execution.value, {status:'awaiting_sync'});
    extra[0] = {...extra[0],description:'v2',parameters:{type:'object',properties:{text:{type:'string'}},required:['text'],additionalProperties:false}};
    assembled = await ctx.systemPrompt.assemble({agent});
    assert.equal(assembled.tools.find(t=>t.name==='custom_test').description,'v2');
    assert.deepEqual(ctx.tools.get('custom_test').parameters.required,['text']);
    extra.length=0;
    assembled = await ctx.systemPrompt.assemble({agent});
    assert.ok(!assembled.tools.some(t=>t.name==='custom_test'));
    assert.equal(ctx.tools.get('custom_test'),undefined);
  } finally {
    globalThis.fetch=previous.fetch;
    if(previous.endpoint===undefined)delete process.env.OMIX_AI_BRIDGE;else process.env.OMIX_AI_BRIDGE=previous.endpoint;
    if(previous.token===undefined)delete process.env.OMIX_AI_TOKEN;else process.env.OMIX_AI_TOKEN=previous.token;
  }
});
