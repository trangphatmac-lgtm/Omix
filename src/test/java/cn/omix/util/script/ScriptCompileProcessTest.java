package cn.omix.util.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class ScriptCompileProcessTest {
    @TempDir Path temp;

    public static class Child {
        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(args[0]), Long.toString(ProcessHandle.current().pid()));
            if (args[1].equals("hang")) Thread.sleep(60_000);
        }
    }
    private ProcessBuilder child(Path pid, String mode) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "-cp",
                Path.of(Child.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                Child.class.getName(), pid.toString(), mode).redirectErrorStream(true).redirectOutput(temp.resolve("child.log").toFile());
    }
    private void assertReaped(Path pid) throws Exception {
        assertTrue(Files.exists(pid), "Child must have started before it is cancelled");
        long value = Long.parseLong(Files.readString(pid));
        assertFalse(ProcessHandle.of(value).map(ProcessHandle::isAlive).orElse(false));
    }
    @Test void cancellationKillsActiveProcessAndNextJobCanRun() throws Exception {
        Path pid = temp.resolve("cancel.pid");
        assertThrows(CancellationException.class, () -> ScriptCompileProcess.run(child(pid, "hang"), Duration.ofSeconds(10), () -> Files.exists(pid), () -> {}));
        assertReaped(pid);
        assertEquals(0, ScriptCompileProcess.run(child(temp.resolve("next.pid"), "exit"), Duration.ofSeconds(10), () -> false, () -> {}));
    }
    @Test void timeoutKillsActiveProcessAndNextJobCanRun() throws Exception {
        Path pid = temp.resolve("timeout.pid");
        IOException error = assertThrows(IOException.class, () -> ScriptCompileProcess.run(child(pid, "hang"), Duration.ofSeconds(2), () -> false, () -> {}));
        assertTrue(error.getMessage().contains("timed out"));
        assertReaped(pid);
        assertEquals(0, ScriptCompileProcess.run(child(temp.resolve("next.pid"), "exit"), Duration.ofSeconds(10), () -> false, () -> {}));
    }
    @Test void cancelledQueuedJobNeverStartsAProcess() throws Exception {
        Path pid = temp.resolve("queued.pid");
        assertThrows(CancellationException.class, () -> ScriptCompileProcess.run(child(pid, "hang"), Duration.ofSeconds(10), () -> true, () -> {}));
        assertFalse(Files.exists(pid));
    }
}
