// Creates and removes QA-only scripts in an explicitly selected isolated game directory.
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve, join} from 'node:path';
import {Client} from '@modelcontextprotocol/client';
import {StdioClientTransport} from '@modelcontextprotocol/client/stdio';
if (!process.argv.includes('--game-dir')) throw new Error('Usage: node custom-tools-smoke.mjs --game-dir <isolated game>');
const gameDir=resolve(process.argv[process.argv.indexOf('--game-dir')+1]);
const entry=join(gameDir,'Omix/scripts/.agent/mcp/server.mjs');
const client=new Client({name:'omix-custom-tool-smoke',version:'1.0.0'});
const transport=new StdioClientTransport({command:process.execPath,args:[entry,'--game-dir',gameDir],stderr:'pipe'});
const suffix=Date.now().toString(36), id='QATools_'+suffix, rival='QARival_'+suffix, sample='QASample_'+suffix;
const local='qa_'+suffix, name='custom_'+local, sources=new Map();
const parse=result=>{assert.ok(!result.isError,JSON.stringify(result));return JSON.parse(result.content[0].text);};
const call=async(tool,args={})=>parse(await client.callTool({name:tool,arguments:args}));
async function write(script,source) {
  const result=await call('script_write',{id:script,source,expectedHash:sources.get(script)??''});
  sources.set(script,result.hash);return result;
}
async function action(script,action) {
  let job=await call('script_action',{id:script,action});
  const deadline=Date.now()+180000;
  while(['queued','compiling','applying'].includes(job.state)) {
    assert.ok(Date.now()<deadline,'Compilation timed out: '+JSON.stringify(job));
    await new Promise(resolve=>setTimeout(resolve,250));
    job=await call('script_job',{jobId:job.id});
  }
  return job;
}
const source=(version,activationFailure=false)=>`import com.google.gson.*;
void onLoad() {
 tools.register("${local}", "QA tool version ${version}", """
 ${JSON.stringify(version===1?{type:'object',properties:{count:{type:'integer',minimum:1,maximum:3}},required:['count']}:{type:'object',properties:{label:{type:'string',maxLength:30}},required:['label']})}
 """, args -> {
   JsonObject result = new JsonObject();
   result.addProperty("version", ${version});
   result.addProperty("width", mc.getWindow().getScaledWidth());
   result.addProperty("modules", modules.list().size());
   result.add("echo", args.deepCopy()); return result;
 }).requiresWorld(false);
 tools.register("${local}_world", "QA world required", "{\\"type\\":\\"object\\"}", args -> JsonNull.INSTANCE);
 tools.register("${local}_failure", "QA throwing callback", "{\\"type\\":\\"object\\"}", args -> {throw new IllegalStateException("QA callback error");}).requiresWorld(false);
 ${activationFailure?'script.afterCommit(() -> {throw new IllegalStateException("QA activation failure");});':''}
}`;
try {
  await client.connect(transport);
  const template=await readFile(new URL('../../../../../../docs/script/examples/CustomTools.java',import.meta.url),'utf8');
  await write(sample,template);
  assert.equal((await action(sample,'check')).state,'checked');
  console.log('PASS complete CustomTools template compiles against the installed runtime');
  await write(id,source(1));
  const checked=await action(id,'check'); assert.equal(checked.state,'checked',JSON.stringify(checked));
  assert.ok(!(await client.listTools()).tools.some(tool=>tool.name===name));
  const loaded=await action(id,'load');assert.equal(loaded.state,'loaded',JSON.stringify(loaded));
  assert.ok((await client.listTools()).tools.some(tool=>tool.name===name));
  let result=await call(name,{count:2});assert.equal(result.generation,loaded.generation);assert.equal(result.value.version,1);assert.ok(result.value.width>0);assert.ok(result.value.modules>20);
  console.log('PASS create/check/load/discover/call from a single real MCP session; native Minecraft and module API works');
  assert.equal((await client.callTool({name,arguments:{count:0}})).isError,true);
  const status=await call('omix_status');
  if(status.gameContext.includes('Not connected to a world')) assert.equal((await client.callTool({name:name+'_world',arguments:{}})).isError,true);
  assert.equal((await client.callTool({name:name+'_failure',arguments:{}})).isError,true);
  await call('omix_status'); assert.ok(!(await client.listTools()).tools.some(tool=>tool.name===name+'_failure'));
  const logs=await call('script_logs',{id});assert.ok(logs.entries.some(entry=>entry.callback==='tool:'+name+'_failure'&&entry.line>0));
  assert.equal((await call(name,{count:1})).value.version,1);
  console.log('PASS validation/world rejection, source-mapped callback failure and isolation from other tools');
  await write(rival,source(1)); const conflict=await action(rival,'load');assert.equal(conflict.state,'failed',JSON.stringify(conflict));
  assert.equal((await call(name,{count:1})).generation,loaded.generation);
  await write(id,source(2,true)); const rollback=await action(id,'reload');assert.equal(rollback.state,'failed',JSON.stringify(rollback));
  assert.equal((await call(name,{count:1})).generation,loaded.generation);
  console.log('PASS cross-script registration conflict and failed activation restore old callable generation');
  await write(id,source(2));const reloaded=await action(id,'reload');assert.equal(reloaded.state,'loaded',JSON.stringify(reloaded));
  const schema=(await client.listTools()).tools.find(tool=>tool.name===name).inputSchema;
  assert.deepEqual(schema.required,['label']); result=await call(name,{label:'中文'});assert.equal(result.generation,reloaded.generation);assert.equal(result.value.version,2);
  await write(id,'void onLoad() { invalid java; }');assert.equal((await action(id,'reload')).state,'failed');
  assert.equal((await call(name,{label:'still running'})).generation,reloaded.generation);
  console.log('PASS schema/handler replacement, compile-error preservation and actual generation reporting');
  await action(id,'unload');assert.ok(!(await client.listTools()).tools.some(tool=>tool.name===name));
  await assert.rejects(client.callTool({name,arguments:{label:'removed'}}),/not found/);
  console.log('CUSTOM SCRIPT TOOLS LIVE MCP SMOKE PASSED');
} finally {
  for (const [script,hash] of sources) {
    try {await action(script,'unload');await call('script_delete',{id:script,expectedHash:hash});}
    catch(error){console.error('QA cleanup '+script+': '+error.message);}
  }
  try {await client.callTool({name:'release_game_session',arguments:{}});} finally {await client.close();}
}
