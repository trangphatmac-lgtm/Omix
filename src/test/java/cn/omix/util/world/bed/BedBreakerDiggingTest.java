package cn.omix.util.world.bed;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BedBreakerDiggingTest {
    @Test void survivalStartAndStopStayBeforeMovementAndTickEnd() {
        var host = new Host();
        for (int tick = 0; tick < 4; tick++) {
            host.mining.interact(.25F, false, true, 0, host);
            host.endTick(true);
        }
        assertEquals(List.of(Packet.SLOT, Packet.START, Packet.SWING, Packet.MOVE, Packet.END, Packet.TRANSACTION,
                Packet.MOVE, Packet.END, Packet.TRANSACTION,
                Packet.MOVE, Packet.END, Packet.TRANSACTION,
                Packet.STOP, Packet.SWING, Packet.RESTORE, Packet.MOVE, Packet.END, Packet.TRANSACTION), host.packets);
        assertEquals(0, orderViolations(host.packets));
        assertEquals(1, host.completed);
        assertFalse(host.mining.started());
    }

    @Test void oldPostMotionSequenceReproducesBothReportedOrderChecks() {
        assertEquals(3, orderViolations(List.of(Packet.MOVE, Packet.SWING, Packet.START, Packet.END, Packet.TRANSACTION)));
        assertEquals(3, orderViolations(List.of(Packet.MOVE, Packet.SWING, Packet.STOP, Packet.END, Packet.TRANSACTION)));
    }

    @Test void plannedButUnsentAimCannotStartAndSnapWaitsForConfirmedFinishAim() {
        var host = new Host();
        host.mining.interact(.4F, false, false, 0, host);
        assertTrue(host.packets.isEmpty());
        assertFalse(host.mining.started());

        host.mining.interact(.4F, false, true, 0, host);
        host.endTick(true);
        host.mining.interact(.4F, false, false, 0, host);
        host.endTick(true);
        host.mining.interact(.4F, false, false, 0, host);
        host.endTick(false);
        assertTrue(host.progress.ready(0));
        assertFalse(host.packets.contains(Packet.STOP));
        host.mining.interact(.4F, false, true, 0, host);
        host.endTick(true);
        assertTrue(host.packets.contains(Packet.STOP));
        assertEquals(0, orderViolations(host.packets));
    }

    @Test void creativeAndToolInstabreakSendStartThenSwingWithoutStopOrAbort() {
        for (boolean creative : new boolean[]{false, true}) {
            var host = new Host();
            host.mining.interact(creative ? .1F : 1.2F, creative, true, 0, host);
            host.endTick(true);
            assertEquals(List.of(Packet.SLOT, Packet.START, Packet.SWING, Packet.RESTORE,
                    Packet.MOVE, Packet.END, Packet.TRANSACTION), host.packets);
            assertTrue(host.predictedStart);
            assertEquals(1, host.completed);
            assertEquals(0, orderViolations(host.packets));
        }
    }

    @Test void inputCompletionDoesNotConsumeOneOfTheFourFollowingCooldownTicks() {
        var host = new Host();
        host.mining.interact(1, false, true, 0, host);
        // LivingUpdate only plans; cooldown is consumed at the next client-tick head.
        host.endTick(true);
        for (int tick = 0; tick < 4; tick++) assertTrue(host.progress.waiting(false));
        assertFalse(host.progress.waiting(false));
    }

    @Test void resettingSessionCannotTransferDamageToANewTarget() {
        var host = new Host();
        host.mining.interact(.6F, false, true, 0, host);
        host.mining.reset();
        host.packets.clear();
        host.mining.interact(.6F, false, true, 0, host);
        assertFalse(host.packets.contains(Packet.STOP));
        assertEquals(.6F, host.progress.progress(0, 1), .00001F);
    }

    private enum Packet { SLOT, START, STOP, SWING, RESTORE, MOVE, END, TRANSACTION }

    private static final class Host implements BedBreakerDigging.Effects {
        final BedBreakerProgress progress = new BedBreakerProgress();
        final BedBreakerDigging mining = new BedBreakerDigging(progress);
        final List<Packet> packets = new ArrayList<>();
        boolean predictedStart;
        int completed;

        public void start(boolean instant) {
            predictedStart = instant;
            packets.add(Packet.SLOT);
            packets.add(Packet.START);
        }
        public void stop() { packets.add(Packet.STOP); }
        public void swing() { packets.add(Packet.SWING); }
        public void progress(float amount) { assertTrue(amount >= 0 && amount <= 1); }
        public void complete() {
            assertFalse(mining.started(), "Completion cleanup must not send ABORT");
            packets.add(Packet.RESTORE);
            progress.finish();
            completed++;
        }
        void endTick(boolean moved) {
            if (moved) packets.add(Packet.MOVE);
            packets.add(Packet.END);
            packets.add(Packet.TRANSACTION);
        }
    }

    /** Relevant 1.21.11 conditions from the supplied Grim PacketOrderO, Post and GrimProcessor. */
    private static int orderViolations(List<Packet> trace) {
        boolean afterMovement = false;
        boolean sentFlying = false;
        boolean postAction = false;
        int flags = 0;
        for (Packet packet : trace) {
            if (packet == Packet.MOVE) {
                afterMovement = sentFlying = true;
                postAction = false;
            } else if (packet == Packet.END) {
                // Post uses END as its tick marker only when the tick had no movement.
                if (!afterMovement) { sentFlying = true; postAction = false; }
                afterMovement = false;
            } else if (packet == Packet.TRANSACTION) {
                if (sentFlying && postAction) flags++;
                sentFlying = postAction = false;
            } else {
                if (afterMovement) flags++;
                if (sentFlying && packet != Packet.SWING) postAction = true;
            }
        }
        return flags;
    }
}
