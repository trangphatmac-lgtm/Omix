import {McpServer} from '@modelcontextprotocol/server';
import {StdioServerTransport} from '@modelcontextprotocol/server/stdio';
import {z} from 'zod';
import {readFile, readdir} from 'node:fs/promises';
import {existsSync, realpathSync} from 'node:fs';
import {resolve, join, relative, sep} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {randomUUID} from 'node:crypto';

export class Bridge {
  constructor(gameDir, fetcher = fetch) {
    this.gameDir = resolve(gameDir); this.fetcher = fetcher; this.connection = null; this.agent = null; this.lastCall = 0; this.inFlight = 0;
  }
  async discover() {
    let value;
    try { value = JSON.parse(await readFile(join(this.gameDir, 'Omix/development/bridge.json'), 'utf8')); }
    catch { throw new Error('Omix client is offline or has not initialized. Start this game directory; offline documentation remains available.'); }
    if (value.protocolVersion !== 2 || !/^http:\/\/127\.0\.0\.1:\d+$/.test(value.endpoint) || typeof value.token !== 'string' || !value.token)
      throw new Error('Invalid Omix connection descriptor');
    if (this.connection?.instanceId !== value.instanceId) this.agent = null;
    this.connection = value;
    return value;
  }
  async request(path, {method = 'GET', body, signal} = {}) {
    const connection = await this.discover();
    const response = await this.fetcher(connection.endpoint + path, {method, headers: {Authorization: `Bearer ${connection.token}`, 'Content-Type': 'application/json'},
      body: body === undefined ? undefined : JSON.stringify(body), signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(35000)]) : AbortSignal.timeout(35000)});
    if (!response.ok) throw new Error(`Omix bridge HTTP ${response.status}`);
    const result = await response.json(); if (result.ok === false) throw new Error(result.error || 'Omix tool failed'); return result;
  }
  async snapshot() { return this.request('/v2/snapshot'); }
  async ensureLease() {
    await this.discover();
    if (this.agent && Date.now() - this.lastCall > 45000 && !this.inFlight) await this.release();
    if (!this.agent) this.agent = (await this.request('/v2/sessions', {method: 'POST'})).agentId;
    return this.agent;
  }
  async call(name, args, signal) {
    // Serialize lease establishment and calls, matching the Java game ownership contract.
    const execute = async () => {
      signal?.throwIfAborted();
      const agentId = await this.ensureLease(), snapshot = await this.snapshot(), id = randomUUID();
      this.lastCall = Date.now(); this.inFlight++;
      const cancel = () => this.request(`/v2/calls/${id}`, {method: 'DELETE'}).catch(() => {});
      signal?.addEventListener('abort', cancel, {once: true});
      const heartbeat = setInterval(() => void this.request(`/v2/agents/${agentId}/heartbeat`, {method: 'POST'}).catch(() => {}), 20000);
      try { return (await this.request('/v2/calls', {method: 'POST', signal, body: {id, agentId, worldEpoch: snapshot.worldEpoch, name, arguments: args}})).value; }
      catch (error) { await cancel(); throw error; }
      finally { clearInterval(heartbeat); signal?.removeEventListener('abort', cancel); this.inFlight--; this.lastCall = Date.now(); }
    };
    const result = (this.tail ?? Promise.resolve()).then(execute, execute); this.tail = result.catch(() => {}); return result;
  }
  async release() {
    const agent = this.agent; this.agent = null;
    if (agent) await this.request(`/v2/agents/${agent}/release`, {method: 'POST'}).catch(() => {});
  }
}

const localReference = fileURLToPath(new URL('./reference/', import.meta.url));
const sourceReference = fileURLToPath(new URL('../../../../../docs/script/', import.meta.url));
export async function createServer({gameDir, referenceDir = existsSync(localReference) ? localReference : sourceReference, bridge = new Bridge(gameDir)} = {}) {
  const root = resolve(referenceDir);
  const readReference = async path => {
    if (!/^[A-Za-z0-9_./-]+$/.test(path) || path.includes('..') || path.startsWith('/')) throw new Error('Invalid reference path');
    return readFile(join(root, path), 'utf8');
  };
  const schemas = JSON.parse(await readReference('tools.json'));
  const server = new McpServer({name: 'omix-script', version: '1.0.0'});
  const registered = new Map();
  function register(schema) {
    if (registered.has(schema.name)) return;
    registered.set(schema.name, server.registerTool(schema.name, {description: schema.description, inputSchema: z.fromJSONSchema(schema.parameters)}, async (args, context) => {
      try {
        let value;
        if (schema.name === 'script_reference') value = await readReference(args.path || 'README.md');
        else if (schema.name === 'script_api') {
          const entries = JSON.parse(await readReference('api.json'));
          value = entries.filter(entry => JSON.stringify(entry).toLowerCase().includes((args.query || '').toLowerCase())).slice(0, args.limit || 30);
        } else if (schema.name === 'script_templates') value = JSON.parse(await readReference('examples/index.json'));
        else value = await bridge.call(schema.name, args, context.mcpReq.signal);
        if (value?.mimeType === 'image/png' && value.data) return {content: [{type: 'image', mimeType: value.mimeType, data: value.data}, {type: 'text', text: value.path}]};
        return {content: [{type: 'text', text: typeof value === 'string' ? value : JSON.stringify(value)}]};
      } catch (error) { return {isError: true, content: [{type: 'text', text: error.message}]}; }
    }));
  }
  schemas.forEach(tool => register(tool.function));
  server.registerTool('omix_status', {description: 'Check client connectivity and read current game context. Documentation works offline.', inputSchema: z.object({})}, async () => {
    try { const snapshot = await bridge.snapshot(); snapshot.tools.forEach(tool => register(tool.function)); return {content: [{type: 'text', text: JSON.stringify({online: true, instanceId: snapshot.instanceId, gameContext: snapshot.gameContext, toolContext: snapshot.toolContext})}]}; }
    catch (error) { return {content: [{type: 'text', text: JSON.stringify({online: false, reason: error.message})}]}; }
  });
  server.registerTool('release_game_session', {description: 'Release exclusive game tools and invalidate this Agent’s container snapshots.', inputSchema: z.object({})}, async () => {
    await bridge.release(); return {content: [{type: 'text', text: 'Game session released'}]};
  });
  async function resources(directory) {
    for (const entry of await readdir(directory, {withFileTypes: true})) {
      const path = join(directory, entry.name);
      if (entry.isDirectory()) await resources(path);
      else if (/\.(md|json|java)$/.test(entry.name)) {
        const name = relative(root, path).split(sep).join('/'), uri = `omix://script/${name}`;
        server.registerResource(name, uri, {mimeType: name.endsWith('.json') ? 'application/json' : 'text/plain'}, async () => ({contents: [{uri, mimeType: 'text/plain', text: await readReference(name)}]}));
      }
    }
  }
  await resources(root);
  let stopped = false;
  async function discoverTools() {
    try { const snapshot = await bridge.snapshot(); if (!stopped) snapshot.tools.forEach(tool => register(tool.function)); } catch { /* Stay available offline. */ }
  }
  void discoverTools();
  const timer = setInterval(() => {
    void discoverTools();
    if (!bridge.inFlight && Date.now() - bridge.lastCall > 45000) void bridge.release();
  }, 10000); timer.unref();
  server.server.onclose = () => { stopped = true; clearInterval(timer); void bridge.release(); };
  return {server, close: async () => { stopped = true; clearInterval(timer); await bridge.release(); await server.close(); }};
}

if (process.argv[1] && existsSync(process.argv[1]) && pathToFileURL(realpathSync(process.argv[1])).href === import.meta.url) {
  const index = process.argv.indexOf('--game-dir');
  if (index < 0 || !process.argv[index + 1]) { process.stderr.write('Usage: node server.mjs --game-dir <Minecraft game directory>\n'); process.exitCode = 1; }
  else {
    const instance = await createServer({gameDir: process.argv[index + 1]});
    process.once('SIGTERM', () => void instance.close().finally(() => process.exit()));
    process.once('SIGINT', () => void instance.close().finally(() => process.exit()));
    await instance.server.connect(new StdioServerTransport());
  }
}
