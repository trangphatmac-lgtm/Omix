package im.webui.backend;

public record BrowserLoadState(
        Status status,
        int httpStatusCode,
        int errorCode,
        String errorText,
        String failedUrl
) {
    public enum Status {
        IDLE,
        LOADING,
        SUCCESS,
        FAILURE
    }

    public static BrowserLoadState idle() {
        return new BrowserLoadState(Status.IDLE, 0, 0, "", "");
    }

    public static BrowserLoadState loading() {
        return new BrowserLoadState(Status.LOADING, 0, 0, "", "");
    }

    public static BrowserLoadState success(int statusCode) {
        return new BrowserLoadState(Status.SUCCESS, statusCode, 0, "", "");
    }

    public static BrowserLoadState failure(int errorCode, String errorText, String failedUrl) {
        return new BrowserLoadState(Status.FAILURE, 0, errorCode, errorText, failedUrl);
    }

    public boolean isCompleted() {
        return status == Status.SUCCESS || status == Status.FAILURE;
    }
}
