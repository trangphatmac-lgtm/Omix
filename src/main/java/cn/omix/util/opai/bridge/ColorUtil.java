package cn.omix.util.opai.bridge;
public final class ColorUtil {
 public static int m27(){return cn.omix.util.opai.OpaiHudTheme.currentPalette().accent();}
 public static int m26(float health){return java.awt.Color.HSBtoRGB(Math.clamp(health,0,1)/3f,1,1)|0xff000000;}
}