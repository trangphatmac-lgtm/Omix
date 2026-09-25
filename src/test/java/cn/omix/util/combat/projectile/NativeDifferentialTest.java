package cn.omix.util.combat.projectile;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Compares reconstructed Java with recorded runs of original machine code. */
public class NativeDifferentialTest {
    static class Fixture implements InvocationHandler {
        Map<String,String> o=new HashMap<>(); List<String> fx=new ArrayList<>();
        final ProjectileAuraEngine.Rotation rot=new ProjectileAuraEngine.Rotation(10,10);
        final ProjectileAuraEngine.Entity target=entity("target"), other=entity("other");
        ProjectileAuraEngine.Entity entity(String label) {
            return (ProjectileAuraEngine.Entity)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ProjectileAuraEngine.Entity.class},(p,m,a)->switch(m.getName()) {
                case "toString" -> label;
                case "equals" -> p==a[0];
                case "isRemoved" -> b("removed",false);
                case "isEndCrystal" -> b("crystal",false);
                case "isLiving" -> !b("crystal",false);
                case "position", "previousPosition" -> new ProjectileAuraEngine.Vec3(6,1,4);
                case "height" -> 1.8f;
                case "hurtTime" -> (int)n("hurtTime",0);
                default -> throw new AssertionError(m);
            });
        }
        boolean b(String k,boolean d) { return o.containsKey(k)?o.get(k).equals("true"):d; }
        long n(String k,long d) { return o.containsKey(k)?Long.parseLong(o.get(k)):d; }
        double d(String k,double v) { return o.containsKey(k)?Double.parseDouble(o.get(k)):v; }
        boolean helper(String k,boolean v) { return b("helpers."+k,v); }
        boolean nul(String k) { return "null".equals(o.get(k)); }
        ProjectileAuraEngine.ProjectileChoice choice() { return new ProjectileAuraEngine.ProjectileChoice(ProjectileAuraEngine.Hand.MAIN_HAND,(int)n("choiceSlot",2),b("rod",false)); }
        ProjectileAuraEngine.AimSolution aim() { return new ProjectileAuraEngine.AimSolution(rot,(int)n("flightTicks",5)); }
        public Object invoke(Object p,Method m,Object[] a) {
            return switch(m.getName()) {
                case "nowMs" -> n("now",10000);
                case "moduleEnabled" -> b("enabled",true);
                case "playerPresent" -> b("player",true);
                case "worldPresent" -> b("world",true);
                case "networkPresent" -> b("connected",true);
                case "interactionManagerPresent" -> true;
                case "killAuraEnabled" -> b("killAura",true);
                case "squaredDistanceToPlayer" -> d("squaredDistanceTo",10);
                case "distanceToPlayer" -> (float)d("distanceTo",10);
                case "playerEyePosition" -> new ProjectileAuraEngine.Vec3(0,1.62,0);
                case "selectedSlot" -> (int)n("slot",2);
                case "requestRotation" -> {fx.add("requestRotation"); yield b("lililillllll",false);}
                case "rotationActive" -> b("liiililllil",false);
                case "currentRotation" -> nul("t")?null:rot;
                case "requestedRotation" -> rot;
                case "rotationPriority" -> ProjectileAuraEngine.Priority.HIGH;
                case "setRotationActive" -> {fx.add("rotationOff"); yield null;}
                case "requestSlot" -> {fx.add("requestSlot:"+a[1]);yield b("slotAccepted",true);}
                case "releaseSlot" -> {fx.add("releaseSlot");yield null;}
                case "releaseSlotImmediately" -> {fx.add("releaseImmediate");yield null;}
                case "originalSlotFor" -> (int)n("originalSlot",1);
                case "ownsSlotRequest" -> b("ownsSlot",true);
                case "interactItem" -> {fx.add("use:"+a[0]);yield null;}
                case "sendHandSwingPacket" -> {fx.add("swing:"+a[0]);yield null;}
                default -> throw new AssertionError("Unexpected Host call: "+m);
            };
        }
        ProjectileAuraEngine make() {
            var host=(ProjectileAuraEngine.Host)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ProjectileAuraEngine.Host.class},this);
            var c=new ProjectileAuraEngine(host) {
                @Override public boolean isConflictingAction(){return helper("lliiiii",true);}
                @Override public boolean shouldPauseForAttack(){return helper("liiiilillill",false);}
                @Override public Entity getBestTarget(){return nul("helpers.liiiliillll")?null:target;}
                @Override public boolean isWithinMeleeRange(Entity t){return helper("lilllililli",true);}
                @Override public boolean canContinueProjectileCombat(){return helper("llllllliill",true);}
                @Override public ProjectileChoice findProjectile(){return nul("helpers.lllilllll")?null:choice();}
                @Override public Vec3 predictTargetPosition(Entity e,Vec3 s){return new Vec3(6,1,4);}
                @Override public AimSolution solveAim(Vec3 s,Vec3 v,Entity e){return nul("helpers.lilllllli")?null:aim();}
                @Override public boolean canThrowNow(ProjectileChoice c,Entity e,int i,long n){return helper("llllliiil",true);}
            };
            c.settings.silent=b("Z",true);c.settings.dynamicDelay=b("a",true);
            c.settings.requiresKillAura=b("x",true);c.settings.range=(float)d("I",12);
            c.settings.rodTimeoutMs=(float)d("V",300);
            c.lastThrowAt=n("f.y",0);c.rodOut=b("f.o",false);c.rodTimeoutAt=n("f.r",0);c.rodFlightDeadline=n("f.g",0);
            c.renderedOriginalSlot=(int)n("f.m",-1);c.requestedSlot=(int)n("f.u",-1);
            c.previousExpectedHitAt=n("f.D",0);
            if (o.containsKey("f.e")&&!nul("f.e"))c.pendingChoice=choice();
            if (o.containsKey("f.S")&&!nul("f.S"))c.pendingTarget=o.get("f.S").equals("other")?other:target;
            if (o.containsKey("f.O")&&!nul("f.O"))c.previousThrowTarget=target;
            if (o.containsKey("f.v")&&!nul("f.v"))c.pendingAim=aim();
            if (o.containsKey("f.k")&&!nul("f.k"))c.ownedRotation=rot;
            return c;
        }
        String state(ProjectileAuraEngine c) {
            return String.join("|", ""+c.lastThrowAt,""+c.rodOut,""+c.rodTimeoutAt,""+c.rodFlightDeadline,
                    ""+c.renderedOriginalSlot,""+c.requestedSlot,c.rodHand.toString(),
                    c.previousThrowTarget==null?"null":"target",""+c.previousExpectedHitAt,
                    c.pendingChoice==null?"null":"choice",c.pendingTarget==null?"null":c.pendingTarget==other?"other":"target",
                    c.pendingAim==null?"null":"solution",c.ownedRotation==null?"null":"rotation");
        }
    }
    public static void main(String[] args)throws Exception {
        int count=0;
        for(String line:Files.readAllLines(Path.of(args[0]))) {
            String[] col=line.split("\t",-1);Fixture f=new Fixture();
            for(String entry:col[2].split(";")) {int p=entry.indexOf('=');if(p>=0)f.o.put(entry.substring(0,p),entry.substring(p+1));}
            var c=f.make();String result="void";
            switch(col[1]) {
                case "lliiillilil" -> c.onDisable();
                case "lillilllliil" -> c.onUpdate();
                case "lliilililli" -> c.onInput();
                case "lllliiilll" -> c.executePendingThrow();
                case "lllliiiilll" -> c.reelRod(f.b("arg.release",true),f.b("arg.immediate",false));
                case "lililiilli" -> result=""+c.prepareSlot(f.choice());
                case "lambda_new_0" -> result=""+c.settings.hideDynamicDelay();
                default -> throw new AssertionError(col[1]);
            }
            String actual=f.state(c)+"\t"+String.join(",",f.fx)+"\t"+result;
            String expected=String.join("\t",col[3],col[4],col[5]);
            if(!actual.equals(expected))throw new AssertionError("Case "+col[0]+" "+col[1]+"\n"+col[2]+"\nExpected "+expected+"\nActual   "+actual);
            count++;
        }
        System.out.println("PASS: "+count+" original-native / readable-Java state and effect comparisons");
    }
}
