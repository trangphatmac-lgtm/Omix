package cn.omix.util.opai.bridge;
import cn.omix.module.value.impl.NumberValue;
public class NumberSetting extends Setting {
 public NumberSetting(NumberValue v){super(v);}
 public NumberSetting(String n,Feature o,double initial,double min,double max,double step){this(new NumberValue(n,initial,min,max,step));register(o);}
 public double m220(){return ((NumberValue)value).getValue();}
 public double m218(){return ((NumberValue)value).getMin();}
 public double m219(){return ((NumberValue)value).getMax();}
 public double m222(){return ((NumberValue)value).getInc();}
 public void m223(double next){double step=m222();((NumberValue)value).setValue(step>0?m218()+Math.round((next-m218())/step)*step:next);}
}
