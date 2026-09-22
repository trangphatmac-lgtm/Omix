package cn.omix.script.api;

/** An idempotent handle. Closing removes only this owner's resource. */
@FunctionalInterface
public interface Registration extends AutoCloseable {
    @Override void close();
}
