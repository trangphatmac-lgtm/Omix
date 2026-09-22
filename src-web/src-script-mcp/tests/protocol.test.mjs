import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, mkdir, writeFile, readFile, rm, symlink} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {createServer as httpServer} from 'node:http';
import {Client} from '@modelcontextprotocol/client';
import {StdioClientTransport} from '@modelcontextprotocol/client/stdio';
import {Bridge} from '../server.mjs';
const serverPath = resolve(import.meta.dirname, '../server.mjs');
const gameTools = JSON.parse(await readFile(new URL('../../../docs/script/game-tools.json', import.meta.url), 'utf8'));
async function connect(gameDir, entry = serverPath) {
  const client = new Client({name:'omix-test',version:'1.0.0'});
  const transport = new StdioClientTransport({command:process.execPath,args:[entry,'--game-dir',gameDir],stderr:'pipe'});
  let stderr=''; transport.stderr?.on('data',data=>stderr+=data);
  await client.connect(transport);
  return {client, close:async()=>{await client.close(); assert.ok(!stderr.includes('SyntaxError'),stderr);}};
}
async function fixture() {
  let definitions = [...gameTools, ...['getplayer','wait'].map(name => ({type:'function', function:{name,description:'test game tool',parameters:{type:'object',properties:{},required:[],additionalProperties:false}}}))];
  const directory = await mkdtemp(join(tmpdir(),'omix-mcp-'));
  let snapshotGate;
  let instance='first', sequence=0; const leases=new Set(), calls=[], cancellations=[], releases=[];
  const server = httpServer(async (req,res)=>{
    assert.equal(req.headers.authorization,'Bearer test-token');
    let body=''; for await(const chunk of req) body+=chunk;
    const args=body?JSON.parse(body):{}, path=req.url;
    res.setHeader('Content-Type','application/json');
    const send=value=>res.end(JSON.stringify(value));
    if(path==='/v2/reference') return send({text:'Live Harness module and command reference'});
    if(path==='/v2/snapshot') {
      const snapshot={protocolVersion:2,instanceId:instance,worldEpoch:7,gameContext:'Fixture player in world',toolContext:'Fixture tool context',tools:definitions};
      if(snapshotGate) {const gate=snapshotGate;snapshotGate=undefined;gate.entered.resolve();await gate.release.promise;}
      return send(snapshot);
    }
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
  return {directory,calls,cancellations,releases,leases,holdNextSnapshot:()=>{snapshotGate={entered:Promise.withResolvers(),release:Promise.withResolvers()};return snapshotGate;},setTools:value=>{definitions=value;},restart:async()=>{instance='second';leases.clear();await descriptor();},close:async()=>{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));await rm(directory,{recursive:true,force:true});}};
}

test('real stdio MCP initializes offline, validates tools, and reads resources',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'omix-offline-'));const session=await connect(dir);
  try{
    const {tools}=await session.client.listTools();assert.ok(tools.some(t=>t.name==='script_write'));
    for (const {function:schema} of gameTools) assert.ok(tools.some(t=>t.name===schema.name),schema.name);
    assert.match(session.client.getInstructions(), /same Minecraft game tools/);
    const gameOffline=await session.client.callTool({name:'getinventory',arguments:{}});
    assert.equal(gameOffline.isError,true); assert.match(gameOffline.content[0].text,/offline/);
    const reference=await session.client.callTool({name:'omix_reference',arguments:{}});
    assert.match(reference.content[0].text,/docs\/commands.md/); assert.match(reference.content[0].text,/docs\/modules\/combat.md/);
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


test('all executor tools are callable over real stdio with their original names and arguments', async () => {
  const f=await fixture(), session=await connect(f.directory);
  const examples={
    run_minecraft_command:{command:'/help'}, run_client_command:{command:'.toggle Sprint'},
    run_baritone_command:{command:'#stop'}, getinventory:{}, getnearbyblock:{range:5},
    getlookingblock:{}, getnearbyentity:{range:10}, getlookingentity:{}, getspecificblock:{pos:'~ ~-1 ~'},
    getallconfig:{}, getscoreboard:{}, getchatmessage:{messagenumber:20},
    sendchatmessage:{message:'fixture only'}, getcommandsuggestion:{perfix:'.tog'},
    getnearbycontainer:{range:5}, opencontainer:{pos:'1 64 2'}, getcontainer:{},
    clickcontainerslot:{snapshotId:'snapshot-1',slot:4,action:'QUICK_MOVE',button:0},
    closecontainer:{snapshotId:'snapshot-2'},
    configurepacketslogger:{enabled:true,settings:{'Chat Output':false,Detail:true}},
    getpacketlogs:{limit:10,direction:'SENT',includeDetails:true},clearpacketlogs:{sessionId:'capture-1'},
  };
  try {
    // No omix_status prerequisite: hosts that only discover tools once still see every game tool.
    const listed=(await session.client.listTools()).tools;
    assert.deepEqual(new Set(Object.keys(examples)),new Set(gameTools.map(t=>t.function.name)));
    for(const [name,args] of Object.entries(examples)) {
      assert.ok(listed.some(t=>t.name===name),name);
      const result=await session.client.callTool({name,arguments:args});
      assert.ok(!result.isError,JSON.stringify(result));
      assert.equal(JSON.parse(result.content[0].text).name,name);
      assert.deepEqual(f.calls.at(-1).arguments,args);
      assert.equal(f.calls.at(-1).worldEpoch,7);
    }
    assert.equal(new Set(f.calls.map(c=>c.agentId)).size,1);
    const status=JSON.parse((await session.client.callTool({name:'omix_status',arguments:{}})).content[0].text);
    assert.equal(status.worldEpoch,7); assert.equal(status.gameContext,'Fixture player in world');
    assert.equal(status.toolContext,'Fixture tool context'); assert.ok(status.availableTools.includes('getpacketlogs'));
    assert.equal((await session.client.callTool({name:'omix_reference',arguments:{}})).content[0].text,'Live Harness module and command reference');
  } finally {await session.close(); await f.close();}
});

test('live tool changes update descriptions and validation, emit notifications, and remove old extensions', async () => {
  const f=await fixture(), session=await connect(f.directory);
  try {
    await session.client.callTool({name:'omix_status',arguments:{}});
    let changes=0;
    session.client.setNotificationHandler('notifications/tools/list_changed',()=>{changes++;});
    const updated=structuredClone(gameTools);
    const tool=updated.find(t=>t.function.name==='getnearbyblock').function;
    tool.description='Updated live scan capability';tool.parameters.properties.range.maximum=5;
    f.setTools(updated);
    await session.client.callTool({name:'omix_status',arguments:{}});
    const listed=(await session.client.listTools()).tools;
    const live=listed.find(t=>t.name===tool.name);
    assert.equal(live.description,tool.description);assert.equal(live.inputSchema.properties.range.maximum,5);
    assert.ok(!listed.some(t=>t.name==='getplayer'));
    const invalid=await session.client.callTool({name:tool.name,arguments:{range:10}});
    assert.equal(invalid.isError,true);assert.equal(f.calls.length,0);
    assert.ok(!(await session.client.callTool({name:tool.name,arguments:{range:5}})).isError);
    for(let i=0;i<100 && !changes;i++) await new Promise(r=>setTimeout(r,5));
    assert.ok(changes>0);
  } finally {await session.close();await f.close();}
});

test('an MCP client started offline can call game tools after the game connects without restarting MCP', async () => {
  const f=await fixture();
  await rm(join(f.directory,'Omix/development/bridge.json'));
  const session=await connect(f.directory);
  try {
    assert.ok((await session.client.listTools()).tools.some(t=>t.name==='getcontainer'));
    assert.equal((await session.client.callTool({name:'getinventory',arguments:{}})).isError,true);
    await f.restart();
    assert.ok(!(await session.client.callTool({name:'getinventory',arguments:{}})).isError);
    assert.equal(f.calls[0].name,'getinventory');
  } finally {await session.close();await f.close();}
});


test('bridge preserves Java game-tool rejection details instead of only the HTTP code',async()=>{
  const f=await fixture();
  const bridge=new Bridge(f.directory,async()=>({ok:false,status:400,json:async()=>({ok:false,error:'Enter a world before inspecting inventory.'})}));
  try {await assert.rejects(bridge.snapshot(),/Enter a world before inspecting inventory/);}
  finally {await f.close();}
});

test('polling a load job immediately publishes custom tools; unloading removes them without a timer', async () => {
  const f=await fixture(), session=await connect(f.directory);
  const custom={type:'function',function:{name:'custom_probe',description:'Script probe',parameters:{type:'object',properties:{count:{type:'integer',minimum:1,maximum:3}},required:['count'],additionalProperties:false}}};
  try {
    await session.client.callTool({name:'omix_status',arguments:{}});
    f.setTools([...gameTools,custom]);
    await session.client.callTool({name:'script_job',arguments:{jobId:'load-job'}});
    assert.ok((await session.client.listTools()).tools.some(tool=>tool.name==='custom_probe'));
    assert.equal((await session.client.callTool({name:'custom_probe',arguments:{count:0}})).isError,true);
    const result=await session.client.callTool({name:'custom_probe',arguments:{count:2}});
    assert.equal(JSON.parse(result.content[0].text).generation,42);
    assert.deepEqual(f.calls.at(-1).arguments,{count:2});
    f.setTools(gameTools);
    await session.client.callTool({name:'script_action',arguments:{id:'Probe',action:'unload'}});
    assert.ok(!(await session.client.listTools()).tools.some(tool=>tool.name==='custom_probe'));
    await assert.rejects(session.client.callTool({name:'custom_probe',arguments:{count:2}}), /not found/);
  } finally {await session.close();await f.close();}
});


test('a load refresh waits for an older in-flight snapshot and then discovers the committed tools', async () => {
  const f=await fixture(), session=await connect(f.directory);
  let gate;
  try {
    await session.client.callTool({name:'omix_status',arguments:{}});
    gate=f.holdNextSnapshot();
    const stale=session.client.callTool({name:'omix_status',arguments:{}});
    await gate.entered.promise;
    f.setTools([...gameTools,{function:{name:'custom_after_commit',description:'Freshly loaded',parameters:{type:'object',properties:{},additionalProperties:false}}}]);
    const job=session.client.callTool({name:'script_job',arguments:{jobId:'finished'}});
    for(let i=0;i<200 && !f.calls.some(call=>call.name==='script_job');i++) await new Promise(resolve=>setTimeout(resolve,5));
    assert.ok(f.calls.some(call=>call.name==='script_job'));
    gate.release.resolve();
    await Promise.all([stale,job]);
    assert.ok((await session.client.listTools()).tools.some(tool=>tool.name==='custom_after_commit'));
  } finally {gate?.release.resolve();await session.close();await f.close();}
});
