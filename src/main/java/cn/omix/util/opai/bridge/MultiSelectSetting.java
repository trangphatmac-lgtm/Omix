package cn.omix.util.opai.bridge;
import cn.omix.module.value.impl.*;
import java.util.*;
public final class MultiSelectSetting extends Setting implements ChoiceSetting {
 public MultiSelectSetting(MultiBoolValue v){super(v);}
 public MultiSelectSetting(String n,Feature o,String[] options,Collection<String> defaults){this(new MultiBoolValue(n,Arrays.stream(options).map(s->new BoolValue(s,defaults.contains(s))).toArray(BoolValue[]::new)));register(o);}
 public String[] options(){return ((MultiBoolValue)value).getValues().stream().filter(BoolValue::isVisible).map(BoolValue::getName).toArray(String[]::new);}
 public List<String> selectedValues(){return Arrays.stream(options()).filter(this::contains).toList();}
 public boolean contains(String name){return ((MultiBoolValue)value).isEnabled(name);}
 public void setSelected(Collection<String> names){for(String option:options())((MultiBoolValue)value).setValue(option,names.contains(option));}
 public String selectionLabel(){return selectedValues().size()+" Selected";}
 public boolean selected(int i){return contains(options()[i]);}
 public boolean multiple(){return true;}
 public void select(int i){((MultiBoolValue)value).setValue(options()[i],!selected(i));}
 public void setLegacyBooleanPrefix(String prefix){}
}
