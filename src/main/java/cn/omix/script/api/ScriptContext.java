package cn.omix.script.api;

import cn.omix.Client;
import cn.omix.util.script.*;
import java.nio.file.Path;
import java.util.*;

/** One prepared/running generation. Registration is restricted to onLoad; effects require activation. */
public final class ScriptContext implements Registration {
    private final String id;
    private final long generation;
    private final ScriptLog log;
    private final Path data;
    private final ScriptSource source;
    private final ScriptScope scope = new ScriptScope();
    private boolean preparing = true;
    private volatile Throwable lastError;
    private final Set<String> ids = new HashSet<>();
    private final List<ModuleHandle> modules = new ArrayList<>();
    private final List<ModeHandle> modes = new ArrayList<>();
    private final List<CommandHandle> commands = new ArrayList<>();
    private final List<Registration> installed = new ArrayList<>();
    private final List<java.util.function.Supplier<Registration>> installers = new ArrayList<>();
    public void installWith(java.util.function.Supplier<Registration> installer) { ensurePreparing(); installers.add(installer); }
    private final List<Runnable> starters = new ArrayList<>();
    public ScriptContext(String id, long generation, Path data, ScriptLog log, ScriptSource source) {
        this.id = id; this.generation = generation; this.data = data; this.log = log; this.source = source;
    }
    public String id() { return id; }
    public long generation() { return generation; }
    public Path dataDirectory() { return data; }
    public boolean active() { return scope.active(); }
    public void requireActive() { if (!active()) throw new IllegalStateException("Script generation is not active: " + id); }
    public void ensurePreparing() { if (!preparing || scope.closed()) throw new IllegalStateException("Register features only in onLoad."); }
    public String qualify(String localId) {
        ensurePreparing();
        if (localId == null || !localId.matches("[A-Za-z][A-Za-z0-9_-]{0,79}") || !ids.add(localId.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Invalid or duplicate feature id: " + localId);
        return "script:" + id + "/" + localId;
    }
    public <T extends AutoCloseable> T own(T resource) { return scope.own(resource); }
    public void release(AutoCloseable resource) { scope.release(resource); }
    public void afterCommit(Runnable starter) { ensurePreparing(); starters.add(starter); }
    public void add(ModuleHandle module) { ensurePreparing(); modules.add(module); }
    public void add(ModeHandle mode) { ensurePreparing(); modes.add(mode); }
    public void add(CommandHandle command) { ensurePreparing(); commands.add(command); }
    public List<ModuleHandle> modules() { return List.copyOf(modules); }
    public List<ModeHandle> modes() { return List.copyOf(modes); }
    public List<CommandHandle> commands() { return List.copyOf(commands); }
    public void prepared() { preparing = false; }
    public void validate(ScriptContext old) {
        var replacedModules = new HashSet<cn.omix.module.Module>(); var replacedCommands = new HashSet<cn.omix.command.Command>();
        var replacedModes = new HashSet<ModeHandle>();
        if (old != null) {
            old.modules.forEach(handle -> replacedModules.add(handle.nativeModule()));
            old.commands.forEach(handle -> replacedCommands.add(handle.nativeCommand())); replacedModes.addAll(old.modes);
        }
        Set<String> names = new HashSet<>(), aliases = new HashSet<>(), choices = new HashSet<>();
        for (var module : modules) {
            if (!names.add(ScriptFailures.commandName(module.nativeModule().getName()))) throw new IllegalArgumentException("Duplicate module display name");
            Client.instance.getModuleManager().validateRegistration(module.nativeModule(), replacedModules);
            String root = ScriptFailures.commandName(module.nativeModule().getName());
            if (root.isBlank()) throw new IllegalArgumentException("Module name needs a command-compatible identifier");
            if (Client.instance.getCommandManager().getCommands().stream().filter(command -> !replacedCommands.contains(command)).flatMap(command -> Arrays.stream(command.getAliases())).anyMatch(alias -> ScriptFailures.commandName(alias).equals(root)))
                throw new IllegalArgumentException("Module name collides with a command: " + root);
        }
        for (var command : commands) {
            for (String alias : command.nativeCommand().getAliases()) {
                if (!aliases.add(alias.toLowerCase(Locale.ROOT)) || names.contains(ScriptFailures.commandName(alias))) throw new IllegalArgumentException("Duplicate command alias: " + alias);
            }
            Client.instance.getCommandManager().validateRegistration(command.nativeCommand(), replacedCommands);
        }
        Map<cn.omix.module.Module, Set<String>> settingNames = new IdentityHashMap<>();
        for (var mode : modes) {
            for (var value : mode.settings()) if (!settingNames.computeIfAbsent(mode.module(), ignored -> new HashSet<>()).add(value.getName().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Duplicate mode setting: " + value.getName());
            if (!choices.add(mode.module().getId() + ":" + mode.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate mode name");
            ModeHost.validate(mode, replacedModes);
        }
    }
    public void install() {
        try {
            for (var module : modules) installed.add(Client.instance.getModuleManager().register(module.nativeModule()));
            for (var mode : modes) installed.add(ModeHost.install(mode));
            for (var command : commands) installed.add(Client.instance.getCommandManager().register(command.nativeCommand()));
            for (var installer : installers) installed.add(installer.get());
            scope.activate();

        } catch (RuntimeException | LinkageError error) { detach(); throw error; }
    }
    public void committed() { for (Runnable starter : starters) starter.run(); }
    public void detach() {
        scope.pause();
        for (var registration : new ArrayList<>(installed).reversed()) {
            try { registration.close(); } catch (Throwable error) { ScriptFailures.rethrowFatal(error); error("unregister", error); }
        }
        installed.clear();
    }
    public void invoke(String callback, Runnable action) {
        try { action.run(); } catch (Throwable error) { cn.omix.util.script.ScriptFailures.rethrowFatal(error); error(callback, error); }
    }
    public void log(Object message) { log.add(id, generation, "INFO", message, 0); }
    public void assertHealthy() { if (lastError != null) throw new IllegalStateException("Script activation failed", lastError); }
    public void error(String callback, Throwable error) {
        lastError = error;
        int line = 0;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && visited.add(cause) && line == 0; cause = cause.getCause()) {
            for (StackTraceElement frame : cause.getStackTrace()) if (frame.getClassName().startsWith(source.className())) { line = source.line(frame.getLineNumber()); break; }
        }
        log.add(id, generation, "ERROR", callback + ": " + error, line, callback);
    }
    @Override public void close() {
        detach();
        try { scope.close(); } catch (Exception error) { error("cleanup", error); }
    }
}
