package im.webui.screen;

public final class WebScreenManager {
    private final ScreenAcknowledgement acknowledgement = new ScreenAcknowledgement();
    private volatile WebScreenType current;

    public void open(WebScreenType type) {
        current = type;
        acknowledgement.reset();
    }

    public void close() {
        current = null;
        acknowledgement.reset();
    }

    public String currentRoute() {
        WebScreenType type = current;
        return type == null ? "none" : type.routeName();
    }

    public boolean acknowledge(String routeName) {
        WebScreenType type = current;
        if (type == null || !type.routeName().equals(routeName)) {
            return false;
        }
        acknowledgement.confirm();
        return true;
    }

    public boolean isAcknowledged() {
        return acknowledgement.isConfirmed();
    }

    public boolean isDesynced() {
        return acknowledgement.isDesynced();
    }

    public WebScreenType current() {
        return current;
    }
}
