package im.webui.screen;

public final class ScreenAcknowledgement {
    private volatile boolean confirmed;

    public void confirm() {
        confirmed = true;
    }

    public void reset() {
        confirmed = false;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

}
