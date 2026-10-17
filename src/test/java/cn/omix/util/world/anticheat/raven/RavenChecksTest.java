package cn.omix.util.world.anticheat.raven;

import cn.omix.util.world.anticheat.AntiCheatAlerts;
import cn.omix.util.world.anticheat.AntiCheatSuspicion;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RavenChecksTest {
    private static final class Fixture implements RavenWorld {
        final List<RavenChecks.Detection> flags = new ArrayList<>();
        java.util.function.Consumer<RavenChecks.Detection> flagObserver = ignored -> {};
        final RavenChecks checks = new RavenChecks(this, flag -> { flags.add(flag); flagObserver.accept(flag); });
        final RavenPlayer local = player("local", 0, 4, false);
        final RavenPlayer suspect = player("suspect", 0, 0, true);
        final RavenPlayer other = player("other", 0, 3, true);
        int tick;
        long now = 100_000;
        RavenChecks.Settings settings;
        Fixture(String... selected) { settings = new RavenChecks.Settings(Set.of(selected), 1, 0); }
        public Block getBlockAt(int x, int y, int z) { return new Block(y < 64 ? "stone" : "air"); }
        public List<String> getScoreboard() { return List.of(); }
        public int bottomY() { return -64; }
        void tick() {
            tick++; now += 50;
            for (var p : List.of(local, suspect, other)) p.ticks = tick;
            checks.tick(List.of(local, suspect, other), local, now, settings);
        }
        void warmup() { for (int i = 0; i < 20; i++) tick(); flags.clear(); }
        int vl(String name) { return checks.violation(suspect, name); }
    }

    private static RavenPlayer player(String name, double x, double z, boolean suspect) {
        var p = new RavenPlayer(new Object(), UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name);
        p.position = p.lastPosition = new RavenVec3(x, 64, z);
        p.suspect = suspect; p.ground = true;
        p.heldItem = new RavenItem("iron_sword", "ItemSword", 1, 0, 0);
        return p;
    }

    @Test void catalogContains101UniqueLiveChecks() {
        assertEquals(101, RavenCheck.ALL.size());
        assertEquals(101, RavenCheck.ALL.stream().map(RavenCheck::name).distinct().count());
        assertTrue(RavenCheck.ALL.stream().allMatch(c -> c.threshold() > 0 && c.cooldown() >= 0));
    }

    @TestFactory Stream<DynamicTest> everyCheckRunsWithEmptySidebarAndMixedObservations() {
        return RavenCheck.ALL.stream().map(check -> DynamicTest.dynamicTest(check.name(), () -> {
            Fixture f = new Fixture(check.name());
            Random random = new Random(12);
            for (int i = 0; i < 240; i++) {
                for (var p : List.of(f.suspect, f.other)) {
                    p.lastPosition = p.position;
                    p.position = new RavenVec3(p == f.suspect ? 0 : 0.1, 64 + (i % 80 > 40 ? Math.sin(i * 0.1) : 0), p == f.suspect ? 0 : 3.5);
                    p.prevYaw = p.yaw; p.prevPitch = p.pitch;
                    p.yaw = (p == f.suspect ? 0 : 180) + (float)(random.nextDouble() * 2);
                    p.pitch = (float)(random.nextDouble() * 2);
                    p.swing = i % 4; p.hurtTime = i % 10 == 0 ? 10 : Math.max(0, p.hurtTime - 1);
                    p.sprinting = i % 30 < 15; p.using = i % 24 < 8; p.blocking = p.using;
                    p.ground = i % 80 <= 40; p.water = i % 100 > 80; p.ladder = i % 100 > 90;
                }
                if (i % 7 == 0) f.checks.block(new RavenVec3(1, 63, 0), true, i % 14 == 0, f.now);
                if (i % 9 == 0) f.checks.block(new RavenVec3(0, 63, 0), false, false, f.now);
                if (i % 20 == 0) f.checks.velocity(f.suspect, 0.8);
                assertDoesNotThrow(f::tick);
            }
            assertTrue(f.flags.stream().allMatch(flag -> flag.check().equals(check.name()) && flag.player().suspect));
        }));
    }

    @Test void wrongTurnAccumulatesDecaysAndHonorsThresholdScale() {
        Fixture f = new Fixture("NCP WrongTurn");
        f.warmup();
        f.suspect.pitch = 94;
        for (int i = 0; i < 9; i++) f.tick();
        assertTrue(f.flags.isEmpty());
        f.tick();
        assertEquals("NCP WrongTurn", f.flags.getFirst().check());
        int vl = f.vl("NCP WrongTurn");
        f.suspect.pitch = 0;
        f.tick();
        assertTrue(f.vl("NCP WrongTurn") < vl);
        f.checks.clear(); f.flags.clear();
        f.settings = new RavenChecks.Settings(Set.of("NCP WrongTurn"), 2, 0);
        f.warmup(); f.suspect.pitch = 94;
        for (int i = 0; i < 19; i++) f.tick();
        assertTrue(f.flags.isEmpty());
        f.tick();
        assertFalse(f.flags.isEmpty());
    }

    @Test void checkSelectionResetAndEntityRemovalDiscardViolationHistory() {
        Fixture f = new Fixture("NCP WrongTurn");
        f.warmup(); f.suspect.pitch = 94;
        for (int i = 0; i < 8; i++) f.tick();
        assertTrue(f.vl("NCP WrongTurn") > 0);
        f.settings = new RavenChecks.Settings(Set.of(), 1, 0); f.tick();
        assertEquals(0, f.vl("NCP WrongTurn"));
        f.checks.tick(List.of(f.local), f.local, f.now + 50, f.settings);
        assertFalse(f.checks.anticheatPlayers.containsKey(f.suspect));
        f.checks.clear();
        assertTrue(f.checks.anticheatPlayers.isEmpty());
        assertTrue(f.checks.blockBreaks.isEmpty());
    }

    @Test void backwardsSprintAndSwimmingChecksReceiveTheirRequiredContexts() {
        for (String name : List.of("Grim Sprint", "Grim Swim")) {
            Fixture f = new Fixture(name); f.warmup();
            for (int i = 0; i < 70; i++) {
                f.suspect.sprinting = true;
                f.suspect.water = name.equals("Grim Swim");
                f.suspect.lastPosition = f.suspect.position;
                f.suspect.position = f.suspect.position.offset(0, 0, -0.4);
                f.tick();
            }
            assertTrue(f.flags.stream().anyMatch(flag -> flag.check().equals(name)), name);
        }
    }

    @Test void cooldownLimitsRepeatedFlagsAndIgnoredSuspectsNeverEmit() {
        Fixture f = new Fixture("NCP WrongTurn");
        f.settings = new RavenChecks.Settings(Set.of("NCP WrongTurn"), 1, 10_000);
        f.warmup(); f.suspect.pitch = 100;
        for (int i = 0; i < 100; i++) f.tick();
        assertEquals(1, f.flags.size());
        f.suspect.suspect = false;
        for (int i = 0; i < 240; i++) f.tick();
        assertEquals(1, f.flags.size());
    }

    @Test void suspicionCountsDetectorFlagsNotRawVlOrChatCooldown() {
        Fixture f = new Fixture("NCP WrongTurn");
        var scores = new AntiCheatSuspicion();
        var chat = new AntiCheatAlerts();
        List<RavenChecks.Detection> announced = new ArrayList<>();
        f.flagObserver = flag -> {
            scores.flag(flag.player().uuid, flag.check(), ignored -> false);
            if (chat.allow(flag.player().uuid, flag.check(), f.now, 60_000)) announced.add(flag);
        };
        f.warmup(); f.suspect.pitch = 100;
        for (int i = 0; i < 9; i++) f.tick();
        assertTrue(f.vl("NCP WrongTurn") > 0);
        assertEquals(0, scores.score(f.suspect.uuid));
        f.tick();
        assertEquals(8, scores.score(f.suspect.uuid));
        int firstCount = f.flags.size();
        for (int i = 0; i < 10; i++) f.tick();
        assertEquals(firstCount, f.flags.size()); // Native detector cooldown still applies.
        for (int i = 0; i < 100; i++) f.tick();
        assertTrue(f.flags.size() > 1);
        assertEquals(1, announced.size());
        assertEquals(Math.min(100, f.flags.size() * 8), scores.score(f.suspect.uuid));
        int beforeIgnored = scores.score(f.suspect.uuid);
        f.suspect.suspect = false;
        for (int i = 0; i < 100; i++) f.tick();
        assertEquals(beforeIgnored, scores.score(f.suspect.uuid));
    }

    @Test void fastMineCanCrossItsBalanceThresholdAndDoesNotReplayOneBreak() {
        Fixture f = new Fixture("Grim FastMine"); f.warmup();
        var data = f.checks.anticheatPlayers.get(f.suspect);
        data.put("heldItem", new RavenItem("wooden_pickaxe", "ItemPickaxe", 1, 0, 0));
        data.put("miningCache", new ArrayList<>(List.<Object[]>of(new Object[]{"1,64,0", "obsidian", f.now - 1000, 3})));
        f.checks.block(new RavenVec3(1, 64, 0), true, false, f.now);
        f.checks.FastMineA(data, 0, 1, false);
        assertTrue(f.flags.stream().anyMatch(flag -> flag.check().equals("Grim FastMine")));
        double balance = (double)data.get("FastMineA_BreakBalance");
        f.checks.FastMineA(data, 0, 1, false);
        assertEquals(balance, (double)data.get("FastMineA_BreakBalance"));
    }

    @Test void cancellingFoodUseIsNotFastEatAndShortModernFoodsUseTheirOwnDuration() {
        for (boolean confirmed : List.of(false, true)) {
            Fixture f = new Fixture("NCP FastEat"); f.warmup();
            var data = f.checks.anticheatPlayers.get(f.suspect);
            data.put("using", true);
            data.put("heldItem", new RavenItem("golden_apple", "ItemFood", 2, 32, 0));
            f.checks.FastEatA(data, 0, 1, false);
            data.put("ticksExisted", f.tick + 10); data.put("using", false);
            f.suspect.consumed = confirmed;
            f.checks.FastEatA(data, 0, 1, false);
            assertEquals(confirmed, !f.flags.isEmpty());
        }
        Fixture f = new Fixture("NCP FastEat"); f.warmup();
        var data = f.checks.anticheatPlayers.get(f.suspect);
        data.put("using", true); data.put("heldItem", new RavenItem("dried_kelp", "ItemFood", 2, 16, 0));
        f.checks.FastEatA(data, 0, 1, false);
        data.put("ticksExisted", f.tick + 16); data.put("using", false); f.suspect.consumed = true;
        f.checks.FastEatA(data, 0, 1, false);
        assertTrue(f.flags.isEmpty());
    }

    @Test void adjacentOrdinaryBlocksAreNotBedEvidenceAndPacketHistoryIsBounded() {
        Fixture f = new Fixture("NCP BedNuker"); f.warmup();
        for (int i = 0; i < 1000; i++) f.checks.block(new RavenVec3(i % 2, 64, 1), true, false, f.now);
        assertEquals(200, f.checks.blockBreaks.size());
        var data = f.checks.anticheatPlayers.get(f.suspect);
        data.put("lastSwingTick", f.tick); data.put("BedNukerA_NearDoubles", 3);
        f.checks.BedNukerA(data, 0, 1, false);
        assertTrue(f.flags.isEmpty());
        f.checks.blockBreaks.clear();
        f.checks.block(new RavenVec3(0, 64, 1), true, true, f.now);
        f.checks.block(new RavenVec3(1, 64, 1), true, true, f.now);
        data.put("BedNukerA_NearDoubles", 3);
        f.checks.BedNukerA(data, 0, 1, false);
        assertFalse(f.flags.isEmpty());
    }

    @Test void voidScanIncludesNegativeWorldHeightsAndRayBoxMissesAreNotHits() {
        Fixture f = new Fixture();
        assertFalse(f.checks.isInVoid(new RavenVec3(0, -10, 0)));
        assertEquals(-1, f.checks.rayBoxDistance(new RavenVec3(0, 2, 0), 0, 0, 1, 6, 1, 0, 2, 2, 3, 3));
        assertEquals(2, f.checks.rayBoxDistance(new RavenVec3(0, 2, 0), 0, 0, 1, 6, -0.3, 0, 2, 0.3, 3, 3));
    }

    @Test void allChecksTogetherLeaveIdlePlayersUnflagged() {
        Fixture f = new Fixture();
        f.settings = new RavenChecks.Settings(new HashSet<>(RavenCheck.ALL.stream().map(RavenCheck::name).toList()), 1, 0);
        for (int i = 0; i < 400; i++) f.tick();
        assertTrue(f.flags.isEmpty(), () -> f.flags.toString());
    }

    @Test void modernBlockItemsCanEnterLegitimateScaffoldMaterialGate() {
        Fixture f = new Fixture();
        assertTrue(f.checks.isBedwarsBlock(new RavenItem("wool", "BlockItem", 64, 0, 0)));
        assertTrue(f.checks.isBedwarsBlock(new RavenItem("end_stone", "BlockItem", 64, 0, 0)));
        assertFalse(f.checks.isBedwarsBlock(new RavenItem("stone", "BlockItem", 64, 0, 0)));
        assertFalse(f.checks.isBedwarsBlock(new RavenItem("iron_sword", "ItemSword", 1, 0, 0)));
    }

    @Test void jumpHeightUsesFeetAtTakeoffAndSupportsNegativeY() {
        for (double base : List.of(64.0, -30.0)) {
            for (double peak : List.of(1.25, 2.0)) {
                Fixture f = new Fixture("Intave Jump"); f.warmup();
                var data = f.checks.anticheatPlayers.get(f.suspect);
                data.put("lastOnGroundTick", f.tick);
                data.put("position", new RavenVec3(0, base + 0.42, 0));
                data.put("lastPosition", new RavenVec3(0, base, 0));
                data.put("onGround", false);
                f.checks.JumpA(data, 0, 1, false);
                data.put("ticksExisted", f.tick + 5);
                data.put("position", new RavenVec3(0, base + peak, 0));
                f.checks.JumpA(data, 0, 1, false);
                data.put("ticksExisted", f.tick + 10);
                data.put("lastStopFallingTick", f.tick + 10);
                data.put("onGround", true);
                data.put("position", new RavenVec3(0, base, 0));
                f.checks.JumpA(data, 0, 1, false);
                assertEquals(peak > 1.35, !f.flags.isEmpty(), "base=" + base + " peak=" + peak);
            }
        }
    }

    @Test void resetCannotCarryTargetOrBlockEvidenceIntoAnotherWorld() {
        Fixture f = new Fixture("Grim Velocity"); f.warmup();
        f.checks.velocity(f.suspect, 1);
        f.checks.block(new RavenVec3(1, 64, 0), true, true, f.now);
        f.checks.clear();
        assertTrue(f.checks.anticheatPlayers.isEmpty());
        assertTrue(f.checks.blockBreaks.isEmpty());
        f.tick();
        assertFalse(f.checks.anticheatPlayers.get(f.suspect).containsKey("VelocityD_Expected"));
    }
}
