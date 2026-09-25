package cn.omix.util.combat.projectile;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import static cn.omix.util.combat.projectile.ProjectileAuraEngine.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileTargetSelectionTest {
    private record Victim(double distance, boolean crystal, boolean visible, boolean excluded, boolean selected)
            implements Entity {
        public Vec3 position() { return new Vec3(distance, 0, 0); }
        public Vec3 previousPosition() { return position(); }
        public float height() { return 1.8f; }
        public boolean isEndCrystal() { return crystal; }
        public boolean isLiving() { return !crystal; }
        public boolean isRemoved() { return false; }
        public int hurtTime() { return 0; }
    }

    private ProjectileAuraEngine engine(List<Victim> crystals, Entity aura, List<Victim> players) {
        Host host = (Host) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Host.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "playerPresent", "worldPresent" -> true;
                    case "crystalsInPlayerBoxExpandedBy" -> { assertEquals(8.0, args[0]); yield crystals; }
                    case "killAuraTarget" -> aura;
                    case "worldPlayers" -> players;
                    case "playerCanSee" -> ((Victim) args[0]).visible;
                    case "excludedByEntityPolicy" -> ((Victim) args[0]).excluded;
                    case "combatTargetPredicate" -> ((Victim) args[0]).selected;
                    case "squaredDistanceToPlayer" -> Math.pow(((Victim) args[0]).distance, 2);
                    default -> throw new AssertionError(method);
                });
        return new ProjectileAuraEngine(host);
    }

    @Test void nearestVisibleCrystalWinsEvenOutsideConfiguredRangeAndIgnoresPlayerPolicy() {
        var crystal = new Victim(9, true, true, true, false);
        var hidden = new Victim(1, true, false, false, true);
        var aura = new Victim(5, false, true, false, true);
        var engine = engine(List.of(hidden, crystal), aura, List.of(aura));
        engine.settings.range = 4;
        assertSame(crystal, engine.getBestTarget());
    }

    @Test void auraTargetHasPriorityOverCloserPlayersWithoutCheckingAuraEnabled() {
        var aura = new Victim(8, false, true, false, true);
        var player = new Victim(5, false, true, false, true);
        assertSame(aura, engine(List.of(), aura, List.of(player)).getBestTarget());
    }

    @Test void fallbackAppliesBothPoliciesAndVisibilityThenDistance() {
        var friend = new Victim(1, false, true, true, true);
        var filtered = new Victim(2, false, true, false, false);
        var hidden = new Victim(3, false, false, false, true);
        var nearest = new Victim(5, false, true, false, true);
        var far = new Victim(10, false, true, false, true);
        assertSame(nearest, engine(List.of(), friend, List.of(far, friend, filtered, hidden, nearest)).getBestTarget());
    }
}
