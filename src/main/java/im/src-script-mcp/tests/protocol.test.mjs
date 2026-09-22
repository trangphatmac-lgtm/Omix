import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, mkdir, writeFile, rm, symlink} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {createServer as httpServer} from 'node:http';
import {Client} from '@modelcontextprotocol/client';
import {StdioClientTransport} from '@modelcontextprotocol/client/stdio';
import {Bridge} from '../server.mjs';
const serverPath = resolve(import.meta.dirname, '../server.mjs');
async function connect(gameDir, entry = serverPath) {
  const client = new Client({name:'omix-test',version:'1.0.0'});
  const transport = new StdioClientTransport({command:process.execPath,args:[entry,'--game-dir',gameDir],stderr:'pipe'});
  let stderr=''; transport.stderr?.on('data',data=>stderr+=data);
  await client.connect(transport);
  return {client, close:async()=>{await client.close(); assert.ok(!stderr.includes('SyntaxError'),stderr);}};
}
async function fixture() {
  const directory = await mkdtemp(join(tmpdir(),'omix-mcp-'));
  let instance='first', sequence=0; const leases=new Set(), calls=[], cancellations=[], releases=[];
  const server = httpServer(async (req,res)=>{
    assert.equal(req.headers.authorization,'Bearer test-token');
    let body=''; for await(const chunk of req) body+=chunk;
    const args=body?JSON.parse(body):{}, path=req.url;
    res.setHeader('Content-Type','application/json');
    const send=value=>res.end(JSON.stringify(value));
    if(path==='/v2/snapshot') return send({protocolVersion:2,instanceId:instance,worldEpoch:7,tools:['getplayer','wait'].map(name=>({type:'function',function:{name,description:'test game tool',parameters:{type:'object',properties:{},required:[],additionalProperties:false}}}))});
    if(path==='/v2/sessions') { const agentId='agent-'+(++sequence); leases.add(agentId); return send({agentId}); }
    if(path.startsWith('/v2/agents/') && path.endsWith('/release')) {const agent=path.split('/')[3];leases.delete(agent);releases.push(agent);return send({ok:true});}
    if(path.startsWith('/v2/agents/')) return send({ok:true});
    if(path.startsWith('/v2/calls/') && req.method==='DELETE') {cancellations.push(path.split('/').pop());return send({ok:true});}
    if(path==='/v2/calls') {
      assert.ok(leases.has(args.agentId)); calls.push(args);
      if(args.name==='wait') return; // Abort/DELETE must release a pending request.
      return send({ok:true,value:{name:args.name,generation:42,agentId:args.agentId}});
    }
    res.statusCode=404;send({error:'notfound'});
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  await mkdir(join(directory,'Omix/development'),{recursive:true});
  async function descriptor() { await writeFile(join(directory,'Omix/development/bridge.json'),JSON.stringify({protocolVersion:2,endpoint:`http://127.0.0.1:${server.address().port}`,token:'test-token',instanceId:instance})); }
  await descriptor();
  return {directory,calls,cancellations,releases,leases,restart:async()=>{instance='second';leases.clear();await descriptor();},close:async()=>{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));await rm(directory,{recursive:true,force:true});}};
}

test('real stdio MCP initializes offline, validates tools, and reads resources',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'omix-offline-'));const session=await connect(dir);
  try{
    const {tools}=await session.client.listTools();assert.ok(tools.some(t=>t.name==='script_write'));
    const offline=await session.client.callTool({name:'omix_status',arguments:{}});assert.equal(JSON.parse(offline.content[0].text).online,false);
    const docs=await session.client.callTool({name:'script_reference',arguments:{path:'README.md'}});assert.match(docs.content[0].text,/Omix/);
    const resources=await session.client.listResources();assert.ok(resources.resources.some(r=>r.uri==='omix://script/common-knowledge.md'));
    const resource=await session.client.readResource({uri:'omix://script/common-knowledge.md'});assert.match(resource.contents[0].text,/RotationRequest/);
    const templates=await session.client.callTool({name:'script_templates',arguments:{}});assert.ok(JSON.parse(templates.content[0].text).includes('Sprint'));
    const invalid=await session.client.callTool({name:'script_write',arguments:{id:'Test'}});assert.equal(invalid.isError,true);
    const traversal=await session.client.callTool({name:'script_reference',arguments:{path:'../secret'}});assert.equal(traversal.isError,true);
    const unavailable=await session.client.callTool({name:'script_status',arguments:{}});assert.equal(unavailable.isError,true);
  }finally{await session.close();await rm(dir,{recursive:true,force:true});}
});

test('real MCP dispatch discovers game tools and preserves source arguments and generation',async()=>{
  const f=await fixture(), session=await connect(f.directory);
  try{
    await session.client.callTool({name:'omix_status',arguments:{}});
    assert.ok((await session.client.listTools()).tools.some(t=>t.name==='getplayer'));
    const source={id:'Hello',source:'void onLoad() {}',expectedHash:'abc'};
    const result=await session.client.callTool({name:'script_write',arguments:source});
    assert.equal(JSON.parse(result.content[0].text).generation,42); assert.deepEqual(f.calls[0].arguments,source);
    await session.client.callTool({name:'getplayer',arguments:{}});assert.equal(f.calls[1].agentId,f.calls[0].agentId);
    await session.client.callTool({name:'release_game_session',arguments:{}});assert.equal(f.leases.size,0);
  }finally{await session.close();await f.close();}
});

test('bridge reconnects to a new instance and independent MCP clients use different leases',async()=>{
  const f=await fixture(), a=new Bridge(f.directory), b=new Bridge(f.directory);
  try{
    const first=await a.call('script_status',{}); const second=await b.call('script_status',{});assert.notEqual(first.agentId,second.agentId);
    await f.restart();const next=await a.call('script_status',{});assert.notEqual(next.agentId,first.agentId);
    await a.release();await b.release();
  }finally{await f.close();}
});

test('cancellation sends DELETE and does not retry a possibly executed action',async()=>{
  const f=await fixture(), bridge=new Bridge(f.directory), abort=new AbortController();
  try{
    const pending=bridge.call('wait',{},abort.signal);const rejection=assert.rejects(pending);
    for(let i=0;i<100 && !f.calls.length;i++) await new Promise(r=>setTimeout(r,5));
    assert.equal(f.calls.length,1);abort.abort();await rejection;
    assert.equal(f.cancellations[0],f.calls[0].id); assert.equal(f.calls.length,1);
    await bridge.release();
  }finally{await f.close();}
});


test('real MCP cancellation propagates across stdio and frees a pending bridge request',async()=>{
  const f=await fixture(), session=await connect(f.directory), abort=new AbortController();
  try {
    await session.client.callTool({name:'omix_status',arguments:{}});
    const pending=session.client.callTool({name:'wait',arguments:{}},{signal:abort.signal});
    const rejection=assert.rejects(pending);
    for(let i=0;i<200 && !f.calls.length;i++) await new Promise(r=>setTimeout(r,5));
    assert.equal(f.calls.length,1); abort.abort(); await rejection;
    for(let i=0;i<200 && !f.cancellations.length;i++) await new Promise(r=>setTimeout(r,5));
    assert.equal(f.cancellations[0],f.calls[0].id);
    await session.client.callTool({name:'release_game_session',arguments:{}});
    assert.equal(f.leases.size,0);
  } finally {await session.close();await f.close();}
});


test('stdio launcher works through a symbolic game-directory path',async()=>{
  const directory=await mkdtemp(join(tmpdir(),'omix-mcp-link-'));
  const entry=join(directory,'linked-server.mjs');await symlink(serverPath,entry);
  const session=await connect(directory,entry);
  try {assert.ok((await session.client.listTools()).tools.some(t=>t.name==='script_status'));}
  finally {await session.close();await rm(directory,{recursive:true,force:true});}
});
