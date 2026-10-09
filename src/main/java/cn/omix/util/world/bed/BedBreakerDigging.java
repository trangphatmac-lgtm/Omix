package cn.omix.util.world.bed;

/** Executes one normal input-phase mining step; rotation planning emits no digging packets. */
public final class BedBreakerDigging {
    public interface Effects {
        void start(boolean instant);
        void stop();
        void swing();
        void progress(float progress);
        void complete();
    }

    private final BedBreakerProgress progress;
    private boolean started;

    public BedBreakerDigging(BedBreakerProgress progress) { this.progress = progress; }
    public boolean started() { return started; }

    public void interact(float delta, boolean instant, boolean aimed, double speed, Effects effects) {
        if (!started) {
            if (!aimed) return;
            // Vanilla also instant-breaks when the equipped tool reaches a full block per tick.
            instant |= delta >= 1;
            effects.start(instant);
            started = true;
            progress.start();
            effects.swing();
            if (instant) { complete(effects); return; }
        }
        if (delta <= 0 || !Float.isFinite(delta)) return;
        progress.advance(delta, speed);
        effects.progress(progress.progress(speed, 1));
        // Snap can accumulate damage without an active aim, but STOP needs confirmed sent aim.
        if (progress.ready(speed) && aimed) {
            effects.stop();
            effects.swing();
            complete(effects);
        }
    }

    private void complete(Effects effects) {
        started = false; // Cleanup must not append ABORT after a completed START/STOP.
        effects.complete();
    }

    public void reset() { started = false; progress.resetDamage(); }
}
