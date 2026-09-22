// Read-only live bridge checks using a real stdio MCP client. Use an isolated game instance.
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve, join} from 'node:path';
import {Client} from '@modelcontextprotocol/client';
import {StdioClientTransport} from '@modelcontextprotocol/client/stdio';
const option = name => process.argv[process.argv.indexOf(name) + 1];
if (!process.argv.includes('--game-dir')) throw new Error('Usage: node smoke.mjs --game-dir <isolated game> [--server <exported server.mjs>]');
const gameDir=resolve(option('--game-dir'));
const entry=process.argv.includes('--server') ? resolve(option('--server')) : join(gameDir,'Omix/scripts/.agent/mcp/server.mjs');
const expected=JSON.parse(await readFile(new URL('../../../../../../docs/script/game-tools.json',import.meta.url),'utf8'));
const client=new Client({name:'omix-live-smoke',version:'1.0.0'});
const transport=new StdioClientTransport({command:process.execPath,args:[entry,'--game-dir',gameDir],stderr:'pipe'});
try {
  await client.connect(transport);
  const listed=(await client.listTools()).tools;
  for(const {function:tool} of expected) assert.ok(listed.some(t=>t.name===tool.name),tool.name);
  console.log(`PASS first tools/list includes all ${expected.length} executor tools (${listed.length} total)`);
  const call=async(name,args={})=>{
    const response=await client.callTool({name,arguments:args});
    assert.ok(!response.isError,JSON.stringify(response));
    return response.content[0].text;
  };
  const status=JSON.parse(await call('omix_status'));
  assert.equal(status.online,true);assert.equal(typeof status.worldEpoch,'number');
  assert.ok(status.gameContext);assert.ok(status.toolContext.includes('getinventory'));
  console.log('PASS live game/AI tool context and world generation');
  for(const [name,args] of [['getallconfig',{}],['getpacketlogs',{limit:1}]]) {
    const value=await call(name,args);assert.ok(value.length>2);
    console.log('PASS MinecraftCommandToolExecutor dispatch: '+name);
  }
  if (status.gameContext.includes('Not connected to a world')) {
    const rejected=await client.callTool({name:'getcommandsuggestion',arguments:{perfix:'.scr'}});
    assert.equal(rejected.isError,true);assert.match(rejected.content[0].text,/not connected to a world/);
    console.log('PASS world-required tool preserves the Java executor rejection detail');
  } else {
    await call('getcommandsuggestion',{perfix:'.scr'});
    console.log('PASS MinecraftCommandToolExecutor dispatch: getcommandsuggestion');
  }
  const reference=await call('omix_reference');
  assert.match(reference,/docs\/commands.md/);assert.match(reference,/docs\/modules\/combat.md/);
  console.log('PASS same live Harness module/command reference');
  await call('release_game_session');
  console.log('LIVE MCP GAME ACCESS SMOKE PASSED');
} finally {await client.close();}
