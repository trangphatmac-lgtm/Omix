import com.google.gson.JsonPrimitive;
void onLoad() {
    var page = ui.page("panel", """
        <style>body{background:#172033;color:#eee;font:20px system-ui;padding:30px}button{padding:10px}</style>
        <h1>Omix Script Page</h1><button id="read">读取玩家位置</button><pre id="out"></pre>
        <script>document.querySelector('#read').onclick=async()=>document.querySelector('#out').textContent=await omixScript.send('position')</script>
        """, message -> new JsonPrimitive(inWorld() ? mc.player.getEntityPos().toString() : "menu"));
    commands.register("scriptpanel", args -> page.open(), "scriptpanel");
}
