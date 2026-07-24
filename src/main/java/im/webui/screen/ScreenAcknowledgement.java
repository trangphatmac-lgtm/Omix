package im.webui.screen;

public final class ScreenAcknowledgement {
    private volatile long resetAtNanos = System.nanoTime();
    private volatile boolean confirmed;

    public void confirm() {
        confirmed = true;
    }

    public void reset() {
        resetAtNanos = System.nanoTime();
        confirmed = false;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public boolean isDesynced() {
        return !confirmed && System.nanoTime() - resetAtNanos >= 1_000_000_000L;
    }
}
