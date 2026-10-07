package cn.omix.util.opai.clickgui;
import cn.omix.Client;
import cn.omix.util.opai.bridge.ConfigManager;
import java.io.IOException;
import java.util.List;
public final class OpaiConfigRepository implements OpaiConfigPanel.Backend {
 public static String validName(String name){return ConfigManager.validName(name);}
 public List<String> names(){return List.of(ConfigManager.getConfigNames());}
 public void create(String name,boolean blank)throws IOException{
  name=validName(name);if(ConfigManager.m38(name))throw new IOException("Configuration already exists");
  var config=Client.instance.getConfigManager().saveConfigChecked(name);
  if(config==null)throw new IOException("Could not create configuration");
  if(blank){
   var data=((cn.omix.config.impl.ModuleConfig)config).snapshot();
   for(var entry:data.entrySet())if(entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has("enabled"))entry.getValue().getAsJsonObject().addProperty("enabled",false);
   ((cn.omix.config.impl.ModuleConfig)config).writeChecked(data);
  }
 }
 public void update(String name)throws IOException{if(!ConfigManager.m38(name))throw new IOException("Configuration no longer exists");ConfigManager.m32(name);}
 public void load(String name){ConfigManager.m33(name);}
 public void delete(String name)throws IOException{if(!ConfigManager.m36(name))throw new IOException("Cannot delete Default or missing configuration");}
 public void openFolder(){net.minecraft.util.Util.getOperatingSystem().open(ConfigManager.getConfigDir());}
}
