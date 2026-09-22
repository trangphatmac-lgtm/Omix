package cn.omix.util.script;

/** Isolate script errors without pretending a damaged JVM or forced thread termination is recoverable. */
public final class ScriptFailures {
    private ScriptFailures() {}
    public static void rethrowFatal(Throwable error) {
        if (error instanceof VirtualMachineError fatal) throw fatal;
        if (error instanceof ThreadDeath fatal) throw fatal;
    }
    public static String commandName(String name) {
        StringBuilder normalized = new StringBuilder();
        name.codePoints().filter(Character::isLetterOrDigit).map(Character::toLowerCase).forEach(normalized::appendCodePoint);
        return normalized.toString();
    }
}
