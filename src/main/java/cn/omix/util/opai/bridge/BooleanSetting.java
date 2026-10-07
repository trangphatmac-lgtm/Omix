package cn.omix.util.opai.bridge;
import cn.omix.module.value.impl.BoolValue;
public final class BooleanSetting extends Setting {
 public BooleanSetting(BoolValue v){super(v);}
 public BooleanSetting(String name,Feature owner,boolean initial){this(new BoolValue(name,initial));register(owner);}
 public boolean m215(){return ((BoolValue)value).getValue();}
 public void m217(boolean next){((BoolValue)value).setValue(next);}
}
