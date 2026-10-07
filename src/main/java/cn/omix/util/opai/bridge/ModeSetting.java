package cn.omix.util.opai.bridge;
import cn.omix.module.value.impl.ModeValue;
public final class ModeSetting extends Setting implements ChoiceSetting {
 public ModeSetting(ModeValue v){super(v);}
 public ModeSetting(String n,Feature o,String initial,String[] choices){this(new ModeValue(n,initial,choices));register(o);}
 public String m224(){return ((ModeValue)value).getValue();}
 public String[] m227(){return ((ModeValue)value).getModes();}
 public boolean m228(String s){return ((ModeValue)value).is(s);}
 public void m226(String s){((ModeValue)value).setValue(s);}
 public String[] options(){return m227();} public String selectionLabel(){return m224();}
 public boolean selected(int index){return m228(options()[index]);} public void select(int index){m226(options()[index]);}
}
