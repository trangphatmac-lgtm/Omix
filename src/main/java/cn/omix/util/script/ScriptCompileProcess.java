package cn.omix.util.script;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** A stuck compiler must not retain the queue or a thread inside the game JVM. */
final class ScriptCompileProcess {
    private ScriptCompileProcess() {}

    static int run(ProcessBuilder builder, Duration timeout, BooleanSupplier cancelled, Runnable progress) throws IOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException("Compilation cancelled");
        Process process = builder.start();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (true) {
                if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException("Compilation cancelled");
                if (System.nanoTime() >= deadline) throw new IOException("Script compilation timed out after " + timeout.toSeconds() + " seconds");
                progress.run();
                if (process.waitFor(100, TimeUnit.MILLISECONDS)) return process.exitValue();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Compilation interrupted");
        } finally {
            // destroyForcibly is asynchronous. Reap before the next job uses the classpath cache.
            boolean interrupted = Thread.interrupted();
            try {
                if (process.isAlive()) process.destroyForcibly();
                if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IOException("Compiler process did not terminate");
            } catch (InterruptedException error) {
                interrupted = true;
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
    }
}
