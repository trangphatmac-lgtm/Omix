package cn.omix.util.combat.projectile;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Native selection is tested separately from the external item-exclusion policy. */
public class SelectionDifferentialTest {
    record Stack(String kind) implements ProjectileAuraEngine.Item {
        public boolean isEmpty() { return kind.equals("empty"); }
        public boolean isFishingRod() { return kind.equals("rod"); }
    }
    public static void main(String[] args) throws Exception {
        int count=0;
        for (String line:Files.readAllLines(Path.of(args[0]))) {
            String[] a=line.split("\t",-1);
            Map<String,String> slots=new HashMap<>();
            for (String s:a[3].split(",")) if (!s.isEmpty()) {
                String[] pair=s.split("="); slots.put(pair[0],pair[1]);
            }
            ProjectileAuraEngine.Host h=(ProjectileAuraEngine.Host)Proxy.newProxyInstance(
                SelectionDifferentialTest.class.getClassLoader(),new Class[]{ProjectileAuraEngine.Host.class},(p,m,v)->switch(m.getName()) {
                    case "playerPresent" -> Boolean.parseBoolean(a[2]);
                    case "selectedSlot" -> 2;
                    case "offHandItem" -> new Stack(slots.getOrDefault("off","empty"));
                    case "mainHandItem" -> new Stack(slots.getOrDefault("2","empty"));
                    case "hotbarItem" -> new Stack(slots.getOrDefault(v[0].toString(),"empty"));
                    case "isAllowedEggOrSnowball" -> ((Stack)v[0]).kind().equals("egg");
                    default -> throw new AssertionError(m);
                });
            var settings=new ProjectileAuraEngine.Settings();
            settings.mode=switch(a[1]) {case "Rod"->ProjectileAuraEngine.Mode.ROD;case "Auto"->ProjectileAuraEngine.Mode.AUTO;default->ProjectileAuraEngine.Mode.EGG_AND_SNOWBALL;};
            var module=new ProjectileAuraEngine(h,settings);
            String actual;
            if (a[0].equals("select")) {
                var c=module.findProjectile();
                actual=c==null?"null":c.hand()+","+c.slot()+","+c.rod();
            } else actual=Boolean.toString(switch(a[0]) {
                case "lambda_new_0" -> settings.hideDynamicDelay();
                case "lambda_new_1" -> settings.hideThrowDelay();
                case "lambda_new_2" -> settings.hideRodTimeout();
                default -> throw new AssertionError(a[0]);
            });
            if (!actual.equals(a[4])) throw new AssertionError(line+" actual="+actual);
            count++;
        }
        System.out.println("PASS: "+count+" original-native / readable-Java selection and visibility comparisons");
    }
}
