// Smoke API integration in an isolated client, without connecting or sending to a server.
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {Client} from '@modelcontextprotocol/client';
import {StdioClientTransport} from '@modelcontextprotocol/client/stdio';
if(!process.argv.includes('--game-dir'))throw new Error('Pass --game-dir <isolated client>');
const dir=resolve(process.argv[process.argv.indexOf('--game-dir')+1]);
const client=new Client({name:'omix-controls-smoke',version:'1'}), sources=new Map();
const suffix=Date.now().toString(36), id='QAControls_'+suffix, tool='qa_controls_'+suffix;
async function call(name,args={}) {
 const result=await client.callTool({name,arguments:args});assert.ok(!result.isError,JSON.stringify(result));
 return JSON.parse(result.content[0].text);
}
async function action(id,action) {
 let job=await call('script_action',{id,action});const deadline=Date.now()+180000;
 while(['queued','compiling','applying'].includes(job.state)) {
  assert.ok(Date.now()<deadline,'Timed out '+JSON.stringify(job));await new Promise(r=>setTimeout(r,250));job=await call('script_job',{jobId:job.id});
 }
 return job;
}
async function write(id,source){const result=await call('script_write',{id,source,expectedHash:''});sources.set(id,result.hash);}
const source=`import com.google.gson.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
AtomicInteger sent = new AtomicInteger(), owned = new AtomicInteger(), received = new AtomicInteger();
void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
void onLoad() {
 var target = modules.register("target", "QA Controls ${suffix}", Category.Player);
 target.setting(new BoolValue("Flag", false));
 target.setting(new NumberValue("Amount", 1, 0, 5));
 target.setting(new ModeValue("Choice", "A", "A", "B"));
 target.setting(new TextValue("Text", "old"));
 target.setting(new KeyValue("Bind", 0));
 target.setting(new ColorValue("Tint", Color.RED));
 target.setting(new MultiBoolValue("Options", new BoolValue("One", false), new BoolValue("Two", false)));
 Registration observer = packets.onSend(event -> {
  if (event.getPacket() instanceof HandSwingC2SPacket) {sent.incrementAndGet();packets.replace(event, new HandSwingC2SPacket(Hand.OFF_HAND));}
 });
 packets.onSend(target, event -> {if(event.getPacket() instanceof HandSwingC2SPacket) {owned.incrementAndGet();packets.cancel(event);}});
 packets.onReceive(event -> {if(event.getPacket() instanceof HandSwingC2SPacket) {received.incrementAndGet();packets.cancel(event);}});
 tools.register("${tool}", "Exercise controls in the isolated QA client", "{\\"type\\":\\"object\\"}", args -> {
  String name=target.id();
  modules.setButton(name,"flag",true);check(modules.getButton(name,"FLAG"),"bool");
  modules.setSlider(name,"Amount",2.5);check(modules.getSlider(name,"Amount")==2.5,"number");
  modules.setSetting(name,"Choice","b");check(modules.getSetting(name,"Choice").getAsString().equals("B"),"mode");
  modules.setSetting(name,"Text","中文");check(modules.getSetting(name,"Text").getAsString().equals("中文"),"text");
  modules.setSetting(name,"Bind",82);check(modules.getSetting(name,"Bind").getAsInt()==82,"key value");
  modules.setSetting(name,"Tint",Color.BLUE);check(modules.getSetting(name,"Tint").getAsInt()==Color.BLUE.getRGB(),"color");
  modules.setSetting(name,"Options",Map.of("One",true));check(modules.getSetting(name,"Options").getAsJsonObject().get("One").getAsBoolean(),"multi");
  modules.setKey(name,82);check(modules.getKey(name)==82,"module key");
  check(modules.settings(name).size()==7,"discovery");
  modules.enable(name);check(target.active() && modules.isEnabled(name),"enable lifecycle");
  var event=new PacketEvent(new HandSwingC2SPacket(Hand.MAIN_HAND),PacketEvent.Type.Send);
  client.getEventManager().call(event);
  check(event.isCancelled() && ((HandSwingC2SPacket)event.getPacket()).getHand()==Hand.OFF_HAND,"cancel and replace");
  check(sent.get()==1 && owned.get()==1 && received.get()==0,"direction and ownership");
  // This disconnected connection exercises the actual send Mixin without a socket/server.
  var connection=new ClientConnection(NetworkSide.CLIENTBOUND);
  connection.send(new HandSwingC2SPacket(Hand.MAIN_HAND));
  check(sent.get()==2 && owned.get()==2,"native send cancellation path");
  modules.disable(name);check(!target.active(),"disable lifecycle");
  connection.send(new HandSwingC2SPacket(Hand.MAIN_HAND));
  check(sent.get()==3 && owned.get()==2,"native replacement bypasses recursive callbacks");
  observer.close();connection.send(new HandSwingC2SPacket(Hand.MAIN_HAND));check(sent.get()==3,"manual unsubscribe");
  modules.toggle(name);check(target.active(),"toggle");modules.setEnabled(name,false);
  var incoming=new PacketEvent(new HandSwingC2SPacket(Hand.MAIN_HAND),PacketEvent.Type.Received);
  var thread=Thread.ofPlatform().start(() -> client.getEventManager().call(incoming));
  try {thread.join(2000);}catch(InterruptedException e){throw new RuntimeException(e);}
  check(!thread.isAlive() && incoming.isCancelled() && received.get()==1,"network-thread synchronous cancellation");
  check(!packets.connected(),"run this test in the main menu");
  boolean rejected=false;try {packets.send(new HandSwingC2SPacket(Hand.MAIN_HAND));}catch(IllegalStateException expected){rejected=true;}
  check(rejected,"disconnected send rejected");
  return new JsonPrimitive("module types, lifecycle, packet directions, cancellation, replacement, ownership and disconnected guard passed");
 }).requiresWorld(false);
}
`;
try {
 await client.connect(new StdioClientTransport({command:process.execPath,args:[join(dir,'Omix/scripts/.agent/mcp/server.mjs'),'--game-dir',dir],stderr:'pipe'}));
 for(const name of ['PacketControl','ModuleControl']) {
  const sample='QA'+name+'_'+suffix;await write(sample,await readFile(new URL('../../../docs/script/examples/'+name+'.java',import.meta.url),'utf8'));
  const job=await action(sample,'check');assert.equal(job.state,'checked',JSON.stringify(job));console.log('PASS template '+name);
 }
 await write(id,source);const loaded=await action(id,'load');assert.equal(loaded.state,'loaded',JSON.stringify(loaded));
 const result=await call('custom_'+tool);assert.equal(result.generation,loaded.generation);console.log('PASS '+result.value);
 await action(id,'unload');assert.ok(!(await client.listTools()).tools.some(t=>t.name==='custom_'+tool));
 console.log('PACKET / MODULE API LIVE REMAP SMOKE PASSED');
} finally {
 for(const [id,hash] of sources)try{await action(id,'unload');await call('script_delete',{id,expectedHash:hash});}catch(error){console.error(error.message);}
 try{await client.callTool({name:'release_game_session',arguments:{}});}finally{await client.close();}
}
