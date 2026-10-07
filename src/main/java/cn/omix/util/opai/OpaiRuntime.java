package cn.omix.util.opai;
import cn.omix.Client;
import cn.omix.util.opai.bridge.FeatureManager;
import cn.omix.util.opai.render.*;
import cn.omix.util.opai.island.DynamicIslandManager;
import net.minecraft.client.MinecraftClient;
public final class OpaiRuntime {
 public static void renderNano(){
  var mc=MinecraftClient.getInstance();if(Client.instance==null||Client.instance.getModuleManager()==null||mc.getOverlay()!=null)return;
  var hud=FeatureManager.hud();var screen=mc.currentScreen;
  boolean editor=screen instanceof cn.omix.ui.opai.HudEditorScreen;
  boolean widgets=mc.player!=null&&mc.world!=null&&hud!=null&&(hud.isEnabled()||editor)&&!mc.options.hudHidden;
  if(!(screen instanceof NanoGui)&&!widgets)return;
  if(!NVGRenderer.beginFrame())return;
  try{
   if(widgets){hud.renderNano();DynamicIslandManager.renderNano();DynamicIslandManager.renderChestOverlay();}
   if(screen instanceof NanoGui gui){gui.renderNano();if(screen instanceof cn.omix.ui.opai.OpaiClickGuiScreen opai)opai.drawHudEditButton();}
   if(editor)((cn.omix.ui.opai.HudEditorScreen)screen).renderOutline();
  }finally{NVGRenderer.endFrame();NVGRenderer.clearScissors();}
 }
}
