package cn.omix.util.opai.bridge;
import cn.omix.module.Module;
import cn.omix.module.Category;
import cn.omix.util.IMinecraft;
import cn.omix.module.value.Value;
import java.util.*;
/** Live view of a native module; never duplicates its enabled state or settings. */
public class Feature implements IMinecraft {
 public final Module nativeModule;
 public final List<Setting> settings = new ArrayList<>();
 public Feature(Module module) { nativeModule = module; }
 public void refresh() {
  var previous = new IdentityHashMap<Value,List<Setting>>();
  for (var setting: settings) previous.computeIfAbsent(setting.value, ignored -> new ArrayList<>()).add(setting);
  settings.clear();
  for(var value:nativeModule.getValues()) { var adapters=previous.get(value); if(adapters==null)adapters=Setting.wrapAll(value); settings.addAll(adapters); }
 }
 public String getName(){return nativeModule.getName();}
 public String getDisplayName(){return getName()+(nativeModule.getSuffix().isEmpty()?"":" "+nativeModule.getSuffix());}
 public Category getCategory(){return nativeModule.getCategory();}
 public boolean isEnabled(){return nativeModule.isEnabled();}
 public boolean isHidden(){return nativeModule.isHidden();}
 public int getKey(){return nativeModule.getKey();}
 public void setKey(int key){nativeModule.setKey(key<=0?-1:key);}
 public void toggle(){nativeModule.toggle();}
 public void setEnabled(boolean enabled){nativeModule.setEnabled(enabled);}
 public void onDisable(){}
}
