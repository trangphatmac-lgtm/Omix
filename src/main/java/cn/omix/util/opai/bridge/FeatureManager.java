package cn.omix.util.opai.bridge;
import cn.omix.Client;
import cn.omix.module.Module;
import cn.omix.module.impl.render.HUD;
import cn.omix.module.impl.combat.Aura;
import java.util.*;
public final class FeatureManager {
 private static final Map<Module,Feature> CACHE=new IdentityHashMap<>();
 public static Feature targets;
 public static List<Feature> getModules(){
  var modules=Client.instance.getModuleManager().getModuleMap().values();CACHE.keySet().retainAll(modules);
  var result=new ArrayList<Feature>();
  for(var module:modules){var feature=CACHE.computeIfAbsent(module, key -> { var adapter = new Feature(key); if (key instanceof HUD h) adapter.settings.addAll(h.getOpai().settings); return adapter; });feature.refresh();result.add(feature);if(module instanceof cn.omix.module.impl.player.Targets)targets=feature;}
  return result;
 }
 public static cn.omix.util.opai.OpaiHud hud(){var hud=Client.instance.getModuleManager().getModule(HUD.class);return hud==null?null:hud.getOpai();}
 public static Aura aura(){return Client.instance.getModuleManager().getModule(Aura.class);}
 public static MultiSelectSetting targets(){getModules();return (MultiSelectSetting)targets.settings.getFirst();}
}
