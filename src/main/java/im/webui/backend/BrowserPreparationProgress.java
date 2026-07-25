package im.webui.backend;

public record BrowserPreparationProgress(
        String task,
        float progress,
        long bytesRead,
        long totalBytes
) {
    public static final BrowserPreparationProgress IDLE =
            new BrowserPreparationProgress("Waiting", -1.0F, 0L, 0L);

    public BrowserPreparationProgress {
        task = task == null || task.isBlank() ? "Preparing Chromium" : task;
        progress = progress < 0.0F ? -1.0F : Math.min(1.0F, progress);
        bytesRead = Math.max(0L, bytesRead);
        totalBytes = Math.max(0L, totalBytes);
    }

    public static BrowserPreparationProgress indeterminate(String task) {
        return new BrowserPreparationProgress(task, -1.0F, 0L, 0L);
    }

    public static BrowserPreparationProgress determinate(String task, float progress) {
        return new BrowserPreparationProgress(task, progress, 0L, 0L);
    }

    public static BrowserPreparationProgress file(
            String task,
            long bytesRead,
            long totalBytes
    ) {
        float progress = totalBytes > 0L ? (float) bytesRead / totalBytes : -1.0F;
        return new BrowserPreparationProgress(task, progress, bytesRead, totalBytes);
    }
}
