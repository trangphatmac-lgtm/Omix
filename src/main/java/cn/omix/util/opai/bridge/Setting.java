package cn.omix.util.opai.bridge;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;
import java.util.function.BooleanSupplier;
public abstract class Setting {
 public Value value;
 private String displayName;
 private BooleanSupplier visibility=()->true;
 protected Setting(Value value){this.value=value;}
 protected void register(Feature owner){owner.settings.add(this);owner.nativeModule.getValues().add(value);value.setScriptVisibility(()->visibility.getAsBoolean() && (!(owner instanceof cn.omix.util.opai.OpaiHud hud) || hud.modeActive()));}
 public String getName(){return value.getName();}
 public String getDisplayName(){return displayName==null?getName():displayName;}
 public Setting setDisplayName(String name){displayName=name;return this;}
 public Setting setVisible(BooleanSupplier visible){visibility=visible;return this;}
 public boolean isVisible(){return value.isVisible() && visibility.getAsBoolean();}
 public static java.util.List<Setting> wrapAll(Value value){
  if(value instanceof ColorValue color)return java.util.List.of(new ColorChannel(color,0),new ColorChannel(color,1),new ColorChannel(color,2));
  if(value instanceof TextValue || value instanceof KeyValue)return java.util.List.of(new EditableSetting(value));
  Setting setting=wrap(value);return setting==null?java.util.List.of():java.util.List.of(setting);
 }
 public static Setting wrap(Value value){
  if(value instanceof BoolValue v)return new BooleanSetting(v);
  if(value instanceof NumberValue v)return new NumberSetting(v);
  if(value instanceof ModeValue v)return new ModeSetting(v);
  if(value instanceof MultiBoolValue v)return new MultiSelectSetting(v);
  return null;
 }
}
