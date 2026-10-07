package cn.omix.util.opai.bridge;
import cn.omix.module.value.impl.*;
/** The source slider style edits native HSB channels without introducing persisted proxy values. */
public final class ColorChannel extends NumberSetting {
 private final ColorValue color;private final int channel;
 public ColorChannel(ColorValue color,int channel){super(new NumberValue(color.getName(),0,0,100,1));this.value=color;this.color=color;this.channel=channel;}
 @Override public String getDisplayName(){return color.getName()+" "+new String[]{"Hue","Saturation","Brightness"}[channel];}
 @Override public double m220(){return (channel==0?color.getHue():channel==1?color.getSaturation():color.getBrightness())*100;}
 @Override public double m218(){return 0;}@Override public double m219(){return 100;}@Override public double m222(){return 1;}
 @Override public void m223(double next){float v=(float)Math.clamp(next/100,0,1);color.setHSB(channel==0?v:color.getHue(),channel==1?v:color.getSaturation(),channel==2?v:color.getBrightness());}
}
