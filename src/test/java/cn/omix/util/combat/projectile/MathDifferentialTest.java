package cn.omix.util.combat.projectile;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public class MathDifferentialTest {
    static class Fixture extends NativeDifferentialTest.Fixture {
        ProjectileAuraEngine.Vec3 pos(String prefix) {return new ProjectileAuraEngine.Vec3(d(prefix+"x",0),d(prefix+"y",0),d(prefix+"z",0));}
        @Override ProjectileAuraEngine.Entity entity(String label) {
            return (ProjectileAuraEngine.Entity)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ProjectileAuraEngine.Entity.class},(p,m,a)->switch(m.getName()) {
                case "toString" -> label;
                case "equals" -> p==a[0];
                case "position" -> pos("p");
                case "previousPosition" -> pos("q");
                case "height" -> (float)d("height",1.8);
                case "isLiving" -> !b("crystal",false);
                case "isEndCrystal" -> b("crystal",false);
                case "hurtTime" -> (int)n("hurtTime",0);
                default -> false;
            });
        }
        @Override public Object invoke(Object p,Method m,Object[] a) {
            return switch(m.getName()) {
                case "worldIsAir" -> !b("ground",false);
                case "rayHitsBlock" -> b("wall",false);
                case "yawBetween" -> {
                    var u=(ProjectileAuraEngine.Vec3)a[0];var v=(ProjectileAuraEngine.Vec3)a[1];
                    yield (float)(Math.toDegrees(Math.atan2(v.z()-u.z(),v.x()-u.x()))-90);
                }
                default -> super.invoke(p,m,a);
            };
        }
        ProjectileAuraEngine core() {
            var host=(ProjectileAuraEngine.Host)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ProjectileAuraEngine.Host.class},this);
            var c=new ProjectileAuraEngine(host);
            c.settings.dynamicDelay=b("a",true);c.settings.throwDelayMs=(float)d("C",500);
            c.lastThrowAt=n("last",0);c.previousExpectedHitAt=n("lastHit",0);
            c.previousThrowTarget=b("same",false)?target:null;
            return c;
        }
    }
    public static void main(String[] args)throws Exception {
        int count=0;
        for(String line:Files.readAllLines(Path.of(args[0]))) {
            String[] a=line.split("\t",-1);Fixture f=new Fixture();
            for(String e:a[2].split(";")){int p=e.indexOf('=');f.o.put(e.substring(0,p),e.substring(p+1));}
            var c=f.core();String actual;
            switch(a[1]) {
                case "predict" -> {var v=c.predictTargetPosition(f.target,f.pos("s"));actual=v.x()+","+v.y()+","+v.z();}
                case "simulate" -> {var r=c.simulateTrajectory(f.pos("s"),(float)f.d("yaw",0),(float)f.d("pitch",0),f.target,f.pos("t"));actual=r==null?"null":r.missDistance()+","+r.closestStep();}
                case "solve" -> {var r=c.solveAim(f.pos("s"),f.pos("t"),f.target);actual=r==null?"null":r.rotation().yaw+","+r.rotation().pitch+","+r.closestStep();}
                case "cooldown" -> actual=""+c.canThrowNow(f.choice(),f.target,(int)f.n("ticks",1),f.n("now",10000));
                default -> throw new AssertionError(a[1]);
            }
            boolean equal=actual.equals(a[3]);
            if(!equal && !List.of("null","true","false").contains(actual) && !a[3].equals("null")) {
                String[] x=actual.split(","), y=a[3].split(",");equal=x.length==y.length;
                for(int i=0;equal&&i<x.length;i++) {
                    double p=Double.parseDouble(x[i]),q=Double.parseDouble(y[i]);
                    equal=Math.abs(p-q)<=1e-8*Math.max(1,Math.abs(q));
                }
            }
            if(!equal)throw new AssertionError("Case "+a[0]+" "+a[1]+"\n"+a[2]+"\nExpected "+a[3]+" actual "+actual);
            count++;
        }
        System.out.println("PASS: "+count+" original-bytecode / readable-Java math and cooldown comparisons (tolerance 1e-8)");
    }
}
