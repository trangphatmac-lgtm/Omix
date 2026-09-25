package cn.omix.util.combat.projectile;

import java.util.Comparator;
import java.util.List;

public class ProjectileAuraEngine {
    public enum Mode { EGG_AND_SNOWBALL, ROD, AUTO }
    public enum Hand { MAIN_HAND, OFF_HAND }
    public enum Priority { HIGH }

    public static final class Settings {
        public Mode mode = Mode.EGG_AND_SNOWBALL;
        public float range = 12.0f;
        public boolean dynamicDelay = true;
        public float throwDelayMs = 500.0f;
        public float rodTimeoutMs = 300.0f;
        public boolean requiresKillAura = true;
        public boolean pauseDuringAttack = true;
        public boolean silent = true;

        public boolean hideDynamicDelay() { return mode == Mode.ROD; }
        public boolean hideThrowDelay() { return mode == Mode.ROD; }
        public boolean hideRodTimeout() { return mode != Mode.ROD && mode != Mode.AUTO; }
    }

    public record Vec3(double x, double y, double z) {
        public Vec3 add(Vec3 b) { return new Vec3(x + b.x, y + b.y, z + b.z); }
        public Vec3 multiply(double d) { return new Vec3(x*d, y*d, z*d); }
        public Vec3 subtract(double a, double b, double c) { return new Vec3(x-a,y-b,z-c); }
        public double distanceTo(Vec3 b) {
            double dx=x-b.x, dy=y-b.y, dz=z-b.z;
            return Math.sqrt(dx*dx + dy*dy + dz*dz);
        }
    }
    public static final class Rotation {
        public final float yaw, pitch;
        public Rotation(float yaw, float pitch) { this.yaw=yaw; this.pitch=pitch; }
    }
    public record ProjectileChoice(Hand hand, int slot, boolean rod) {}
    public record AimSolution(Rotation rotation, int closestStep) {}
    public record TrajectoryResult(double missDistance, int closestStep) {}

    public interface Item {
        boolean isEmpty();
        boolean isFishingRod();
    }
    public interface Entity {
        Vec3 position();
        Vec3 previousPosition();
        float height();
        boolean isEndCrystal();
        boolean isLiving();
        boolean isRemoved();
        int hurtTime();
    }

    public interface Host {
        long nowMs();
        boolean moduleEnabled();
        boolean playerPresent();
        boolean worldPresent();
        boolean networkPresent();
        boolean interactionManagerPresent();
        Vec3 playerEyePosition();
        double squaredDistanceToPlayer(Entity e);
        float distanceToPlayer(Entity e);
        List<Entity> crystalsInPlayerBoxExpandedBy(double blocks);
        List<Entity> worldPlayers();
        Entity killAuraTarget();
        boolean killAuraEnabled();
        boolean killAuraAttacking();
        boolean excludedByEntityPolicy(Entity e);
        boolean combatTargetPredicate(Entity e);
        boolean playerCanSee(Entity e);
        boolean scaffoldEnabled();
        boolean blinkEnabled();
        boolean playerUsingItem();
        boolean externalMovementBusy();
        boolean noSlowBusy();
        boolean crosshairIsEntityHit();
        Entity crosshairEntity();
        Item mainHandItem();
        Item offHandItem();
        Item hotbarItem(int slot);
        int selectedSlot();
        // Exact external utility, includes its own special-item exclusion policy.
        boolean isAllowedEggOrSnowball(Item stack);
        float yawBetween(Vec3 from, Vec3 to);
        boolean worldIsAir(int x, int y, int z);
        boolean rayHitsBlock(Vec3 start, Vec3 end);
        boolean requestRotation(Rotation rotation, double maxTurn, int ticks, Priority priority);
        boolean rotationActive();
        Rotation currentRotation();
        Rotation requestedRotation();
        Priority rotationPriority();
        void setRotationActive(boolean active);
        boolean requestSlot(Object owner, int slot, Priority priority);
        void releaseSlot(Object owner);
        void releaseSlotImmediately(Object owner);
        int originalSlotFor(Object owner);
        boolean ownsSlotRequest(Object owner);
        void interactItem(Hand hand);
        void sendHandSwingPacket(Hand hand);
    }

    public final Settings settings;
    private final Host host;
    public long lastThrowAt;
    public boolean rodOut;
    public long rodTimeoutAt;
    public long rodFlightDeadline;
    public int renderedOriginalSlot=-1;
    public int requestedSlot=-1;
    public Hand rodHand=Hand.MAIN_HAND;
    public Entity previousThrowTarget;
    public long previousExpectedHitAt;
    public ProjectileChoice pendingChoice;
    public Entity pendingTarget;
    public AimSolution pendingAim;
    public Rotation ownedRotation;

    public ProjectileAuraEngine(Host host) { this(host, new Settings()); }
    public ProjectileAuraEngine(Host host, Settings settings) {
        this.host=host; this.settings=settings;
    }

    public void onDisable() {
        clearPending();
        rodOut=false;
        releaseSlotAndRotation(false);
        releaseOwnedRotation();
    }

    public void releaseSlotAndRotation(boolean immediate) {
        requestedSlot=-1;
        renderedOriginalSlot=-1;
        if (immediate) host.releaseSlotImmediately(this);
        else host.releaseSlot(this);
        releaseOwnedRotation();
    }
    public void releaseOwnedRotation() {
        if (ownedRotation != null && host.rotationActive()
                && host.requestedRotation() == ownedRotation
                && host.rotationPriority() == Priority.HIGH) {
            host.setRotationActive(false);
        }
        ownedRotation=null;
    }
    public void renewRodSlot() {
        if (requestedSlot >= 0) host.requestSlot(this, requestedSlot, Priority.HIGH);
    }
    public boolean canContinueProjectileCombat() {
        if (!host.playerPresent() || !host.worldPresent()) return false;
        Entity target=getBestTarget();
        if (target == null) return false;
        boolean crystal=target.isEndCrystal();
        if (!crystal && settings.requiresKillAura && !host.killAuraEnabled()) return false;
        double distanceSquared=host.squaredDistanceToPlayer(target);
        if (distanceSquared > (double)(settings.range * settings.range)) return false;
        if (!crystal && distanceSquared <= 10.240000000000002) return false;
        return !shouldPauseForAttack();
    }
    public void reelAndRelease() { reelRod(true, false); }
    public void reelRod(boolean release) { reelRod(release, false); }
    public void reelRod(boolean release, boolean immediate) {
        if (rodOut && host.playerPresent() && host.interactionManagerPresent()
                && host.networkPresent()) {
            host.interactItem(rodHand);
            host.sendHandSwingPacket(rodHand);
        }
        rodOut=false;
        if (release) releaseSlotAndRotation(immediate);
    }
    public Item onRenderHand(Hand hand, Item originalRenderStack) {
        if (hand == Hand.MAIN_HAND && isSilentSlotActive())
            return host.hotbarItem(renderedOriginalSlot);
        return originalRenderStack;
    }
    public void onInput() {
        if (!shouldPauseForAttack()) return;
        clearPending();
        if (rodOut) reelRod(true, true);
        else releaseSlotAndRotation(true);
    }
    public void onUpdate() {
        boolean busy=isConflictingAction(); // evaluated before the null-context checks
        if (!host.playerPresent() || !host.networkPresent()
                || !host.interactionManagerPresent() || !host.worldPresent()
                || busy || shouldPauseForAttack()) {
            cancelPending(false);
            if (rodOut) reelAndRelease();
            return;
        }
        Entity target=getBestTarget();
        if (isWithinMeleeRange(target)) {
            clearPending();
            if (rodOut) reelRod(true, true);
            else releaseSlotAndRotation(true);
            return;
        }
        if (pendingChoice != null) {
            if (target != pendingTarget || target == null || target.isRemoved()) cancelPending(true);
            else executePendingThrow();
            return;
        }
        if (rodOut) {
            long now=host.nowMs();
            renewRodSlot();
            if (now > rodTimeoutAt || now >= rodFlightDeadline)
                reelRod(!canContinueProjectileCombat());
            return;
        }
        if (target == null) { releaseOwnedRotation(); return; }
        if (!target.isEndCrystal() && settings.requiresKillAura && !host.killAuraEnabled()) {
            releaseOwnedRotation(); return;
        }
        Vec3 start=host.playerEyePosition();
        if (host.squaredDistanceToPlayer(target) > (double)(settings.range*settings.range)) return;
        ProjectileChoice choice=findProjectile();
        if (choice == null) { releaseOwnedRotation(); return; }
        Vec3 predicted=predictTargetPosition(target, start);
        AimSolution aim=solveAim(start, predicted, target);
        if (aim == null) return;
        long now=host.nowMs();
        if (!canThrowNow(choice, target, aim.closestStep()+1, now)) return;
        if (!host.requestRotation(aim.rotation(), 180.0, 2, Priority.HIGH)) return;
        ownedRotation=aim.rotation();
        if (!prepareSlot(choice)) return;
        pendingChoice=choice;
        pendingTarget=target;
        pendingAim=aim;
    }

    public void executePendingThrow() {
        ProjectileChoice choice=pendingChoice;
        Entity target=pendingTarget;
        AimSolution aim=pendingAim;
        clearPending();
        if (choice == null || target == null || aim == null || target.isRemoved()
                || shouldPauseForAttack() || !host.rotationActive()
                || host.currentRotation() == null || ownedRotation != aim.rotation()
                || host.requestedRotation() != ownedRotation
                || (choice.slot() >= 0 && host.selectedSlot() != choice.slot())) {
            releaseSlotAndRotation(false);
            return;
        }
        long now=host.nowMs();
        host.interactItem(choice.hand());
        host.sendHandSwingPacket(choice.hand());
        if (choice.rod()) {
            rodOut=true;
            rodHand=choice.hand();
            rodTimeoutAt=now+(long)settings.rodTimeoutMs;
            rodFlightDeadline=now+aim.closestStep()*50L+300L;
        } else {
            // Immediate release when the victim is already in melee range.
            boolean immediate=host.playerPresent() && host.distanceToPlayer(target) <= 3.2;
            releaseSlotAndRotation(immediate);
            if (settings.dynamicDelay) {
                previousThrowTarget=target;
                previousExpectedHitAt=now+(aim.closestStep()+1)*50L;
            }
        }
        releaseOwnedRotation();
        lastThrowAt=now;
    }

    public void clearPending() { pendingChoice=null; pendingTarget=null; pendingAim=null; }
    public void cancelPending() { cancelPending(false); }
    public void cancelPending(boolean immediate) { clearPending(); releaseSlotAndRotation(immediate); }


    public boolean prepareSlot(ProjectileChoice choice) {
        if (choice.slot() < 0 || host.selectedSlot() == choice.slot()) return true;
        if (!host.requestSlot(this, choice.slot(), Priority.HIGH)) {
            releaseSlotAndRotation(false);
            return false;
        }
        requestedSlot=choice.slot();
        if (settings.silent) renderedOriginalSlot=host.originalSlotFor(this);
        return true;
    }

    public boolean isSilentSlotActive() {
        return host.moduleEnabled() && settings.silent && host.ownsSlotRequest(this)
                && host.playerPresent() && renderedOriginalSlot >= 0 && renderedOriginalSlot < 9;
    }

    public boolean shouldPauseForAttack() {
        return settings.pauseDuringAttack && host.killAuraEnabled() && host.killAuraAttacking();
    }

    public boolean canThrowNow(ProjectileChoice choice, Entity target, int flightTicks, long now) {
        if (choice.rod()) return true;
        if ((float)(now-lastThrowAt) < settings.throwDelayMs) return false;
        if (!settings.dynamicDelay) return true;
        long predictedArrival=now+flightTicks*50L;
        long targetReadyAt=target.isLiving() ? now+target.hurtTime()*50L : 0;
        if (target == previousThrowTarget)
            targetReadyAt=Math.max(targetReadyAt, previousExpectedHitAt+(long)settings.throwDelayMs);
        return predictedArrival >= targetReadyAt;
    }


    public AimSolution solveAim(Vec3 start, Vec3 predicted, Entity target) {
        float yaw=host.yawBetween(start,predicted);
        float bestPitch=0;
        double bestDistance=Double.MAX_VALUE;
        int bestStep=0;
        for (float pitch=-90; pitch<=90; pitch+=0.5f) {
            TrajectoryResult candidate=simulateTrajectory(start,yaw,pitch,target,predicted);
            if (candidate != null && candidate.missDistance() < bestDistance) {
                bestDistance=candidate.missDistance();
                bestPitch=pitch;
                bestStep=candidate.closestStep();
            }
            if (bestDistance < 0.1) break;
        }
        return bestDistance < 1.4 ? new AimSolution(new Rotation(yaw,bestPitch),bestStep) : null;
    }

    public TrajectoryResult simulateTrajectory(Vec3 start,float yaw,float pitch,Entity target,Vec3 predicted) {
        if (!host.playerPresent() || !host.worldPresent()) return null;
        double yr=Math.toRadians(yaw), pr=Math.toRadians(pitch);
        Vec3 velocity=new Vec3(-Math.sin(yr)*Math.cos(pr)*1.45,
                -Math.sin(pr)*1.45, Math.cos(yr)*Math.cos(pr)*1.45);
        Vec3 position=start;
        double closest=Double.MAX_VALUE;
        int closestStep=0;
        for (int step=0;step<60;step++) {
            Vec3 next=position.add(velocity);
            double distance=next.distanceTo(predicted);
            if (distance < closest) { closest=distance; closestStep=step; }
            if (host.rayHitsBlock(position,next)) break;
            position=next;
            velocity=velocity.multiply(0.99).subtract(0,0.03,0);
            if (position.y() < target.position().y()-3.0) break;
        }
        return new TrajectoryResult(closest,closestStep);
    }

    public Vec3 predictTargetPosition(Entity entity,Vec3 start) {
        if (!host.worldPresent()) return entity.position();
        Vec3 pos=entity.position(), old=entity.previousPosition();
        double vx=pos.x()-old.x(), vy=pos.y()-old.y(), vz=pos.z()-old.z();
        double x=pos.x(), y=pos.y(), z=pos.z();
        double travelBudget=Math.pow(pos.distanceTo(start),1.25)/1.45;
        for (int tick=0;tick<travelBudget;tick++) {
            x+=vx; y+=vy; z+=vz;
            vy=(vy-0.08)*0.98;
            if (vy<0 && !host.worldIsAir((int)Math.floor(x),(int)Math.floor(y-0.1),(int)Math.floor(z))) {
                vy=0;
                y=Math.floor(y)+1.0;
            }
        }
        return new Vec3(x,y+entity.height()*0.5,z);
    }

    public Entity getBestTarget() {
        if (!host.playerPresent() || !host.worldPresent()) return null;
        Entity crystal=host.crystalsInPlayerBoxExpandedBy(8.0).stream()
                .filter(host::playerCanSee)
                .min(Comparator.comparingDouble(host::squaredDistanceToPlayer)).orElse(null);
        if (crystal != null) return crystal;
        Entity auraTarget=host.killAuraTarget();
        if (auraTarget != null && !host.excludedByEntityPolicy(auraTarget)
                && host.combatTargetPredicate(auraTarget) && host.playerCanSee(auraTarget)) return auraTarget;
        return host.worldPlayers().stream()
                .filter(e -> !host.excludedByEntityPolicy(e))
                .filter(host::combatTargetPredicate).filter(host::playerCanSee)
                .min(Comparator.comparingDouble(host::squaredDistanceToPlayer)).orElse(null);
    }

    public ProjectileChoice findProjectile() {
        if (!host.playerPresent()) return null;
        if (settings.mode == Mode.EGG_AND_SNOWBALL || settings.mode == Mode.AUTO) {
            if (isThrowable(host.offHandItem())) return new ProjectileChoice(Hand.OFF_HAND,-1,false);
            if (isThrowable(host.mainHandItem())) return new ProjectileChoice(Hand.MAIN_HAND,host.selectedSlot(),false);
            for (int slot=0;slot<9;slot++)
                if (isThrowable(host.hotbarItem(slot))) return new ProjectileChoice(Hand.MAIN_HAND,slot,false);
        }
        if (settings.mode == Mode.ROD || settings.mode == Mode.AUTO) {
            if (isRod(host.offHandItem())) return new ProjectileChoice(Hand.OFF_HAND,-1,true);
            if (isRod(host.mainHandItem())) return new ProjectileChoice(Hand.MAIN_HAND,host.selectedSlot(),true);
            for (int slot=0;slot<9;slot++)
                if (isRod(host.hotbarItem(slot))) return new ProjectileChoice(Hand.MAIN_HAND,slot,true);
        }
        return null;
    }

    public boolean isThrowable(Item item) { return host.isAllowedEggOrSnowball(item); }
    public boolean isRod(Item item) { return !item.isEmpty() && item.isFishingRod(); }

    public boolean isConflictingAction() {
        if (!host.playerPresent()) return false;
        return host.scaffoldEnabled() || host.blinkEnabled() || host.playerUsingItem()
                || host.externalMovementBusy() || host.noSlowBusy()
                || (host.crosshairIsEntityHit() && host.crosshairEntity().equals(host.killAuraTarget()));
    }

    public boolean isWithinMeleeRange(Entity target) {
        return target != null && !target.isEndCrystal() && host.distanceToPlayer(target) <= 3.2;
    }
}
