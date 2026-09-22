package cn.omix.script.api;

import cn.omix.command.Command;
import java.util.*;
import java.util.function.*;

public final class CommandHandle {
    private final Command command;
    private Function<String[], List<String>> complete = args -> List.of();
    private boolean faulted;
    public CommandHandle(ScriptContext context, String usage, Consumer<String[]> execute, String... aliases) {
        if (aliases.length == 0) throw new IllegalArgumentException("A command needs at least one alias");
        command = new Command(usage, aliases) {
            @Override public void execute(String[] arguments) {
                context.requireActive();
                if (faulted) throw new IllegalStateException("Script command failed; reload the script.");
                try { execute.accept(Arrays.copyOfRange(arguments, 1, arguments.length)); }
                catch (Throwable error) { cn.omix.util.script.ScriptFailures.rethrowFatal(error); faulted = true; context.error("command:" + aliases[0], error); }
            }
            @Override public List<String> getCompletions(String[] arguments) {
                if (!context.active() || faulted) return List.of();
                try { return List.copyOf(complete.apply(Arrays.copyOfRange(arguments, 1, arguments.length))); }
                catch (Throwable error) { cn.omix.util.script.ScriptFailures.rethrowFatal(error); faulted = true; context.error("completion:" + aliases[0], error); return List.of(); }
            }
        };
    }
    public CommandHandle completions(Function<String[], List<String>> callback) { complete = callback; return this; }
    public Command nativeCommand() { return command; }
}
