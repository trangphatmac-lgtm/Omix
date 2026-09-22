package cn.omix.util.script;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import javax.tools.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import com.google.gson.Gson;

public final class ScriptCompiler {
    public record Problem(String severity, String message, int line, long column) {}
    public record Compiled(ScriptSource source, Path jar, List<Problem> diagnostics) {
        public URLClassLoader loader(ClassLoader parent) throws IOException { return new URLClassLoader(new URL[]{jar.toUri().toURL()}, parent); }
        public void verifyLinks(ClassLoader loader) throws IOException, ClassNotFoundException {
            try (var archive = new java.util.jar.JarFile(jar.toFile())) {
                for (var entry : archive.stream().filter(value -> value.getName().endsWith(".class")).toList()) {
                    Class<?> type = Class.forName(entry.getName().replace('/', '.').replaceAll("\\.class$", ""), false, loader);
                    type.getDeclaredConstructors(); type.getDeclaredMethods(); type.getDeclaredFields();
                }
            }
        }
        public void discard() { deleteTree(jar.getParent()); }
    }
    public static final class CompileFailure extends IOException {
        public final List<Problem> diagnostics;
        CompileFailure(List<Problem> diagnostics) { super("Script compilation failed"); this.diagnostics = List.copyOf(diagnostics); }
    }
    private final Path cache;
    private final ScriptClasspath classpath;
    public ScriptCompiler(Path cache) { this.cache = cache; this.classpath = new ScriptClasspath(cache.resolve("classpath")); }
    ScriptCompiler(Path cache, ScriptClasspath classpath) { this.cache = cache; this.classpath = classpath; }
    private record Cached(ScriptSource source, byte[] jar, List<Problem> diagnostics) {}
    private final Map<String, Cached> results = new LinkedHashMap<>();
    public Compiled compile(String id, long generation, String body) throws IOException {
        return compile(id, generation, body, 0);
    }
    public Compiled compile(String id, long generation, String body, int leadingLines) throws IOException {
        return compile(id, generation, body, leadingLines, () -> false, phase -> {});
    }
    /** Called serially by the manager; cancellation remains observable while the child is busy. */
    public Compiled compile(String id, long generation, String body, int leadingLines, BooleanSupplier cancelled, Consumer<String> progress) throws IOException {
        String key = ScriptFiles.hash(id + "\n" + leadingLines + "\n" + body);
        Path directory = directory(id, generation);
        Files.createDirectories(directory);
        try {
            checkCancelled(cancelled);
            Cached previous = results.get(key);
            if (previous != null) {
                progress.accept("cache");
                Path jar = directory.resolve("script.jar"); Files.write(jar, previous.jar());
                checkCancelled(cancelled);
                return new Compiled(previous.source(), jar, previous.diagnostics());
            }
            progress.accept("dependencies");
            var environment = classpath.environment();
            checkCancelled(cancelled);
            Path request = directory.resolve("request.json"), response = directory.resolve("response.json"), phase = directory.resolve("phase.txt");
            Path output = directory.resolve("compiler.log");
            Gson gson = new Gson();
            Files.writeString(request, gson.toJson(new ScriptCompilerWorker.Request(cache.toAbsolutePath().toString(), environment, id, generation, body, leadingLines)));
            String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
            var process = new ProcessBuilder(java, "-Xmx768m", "-XX:ActiveProcessorCount=2", "-cp", workerClasspath(),
                    ScriptCompilerWorker.class.getName(), request.toAbsolutePath().toString(), response.toAbsolutePath().toString(), phase.toAbsolutePath().toString())
                    .redirectErrorStream(true).redirectOutput(output.toFile());
            int exit = ScriptCompileProcess.run(process, Duration.ofSeconds(180), cancelled, () -> {
                try { if (Files.isRegularFile(phase)) progress.accept(Files.readString(phase)); }
                catch (IOException ignored) { /* Next poll reads the atomically replaced phase file. */ }
            });
            checkCancelled(cancelled);
            if (exit != 0 || !Files.isRegularFile(response)) throw new IOException("Compiler process exited (" + exit + "): " + logTail(output));
            var result = gson.fromJson(Files.readString(response), ScriptCompilerWorker.Result.class);
            if (result.error() != null) {
                if (result.diagnostics().stream().anyMatch(problem -> problem.severity().equals("ERROR"))) throw new CompileFailure(result.diagnostics());
                throw new IOException(result.error());
            }
            Compiled compiled = new Compiled(result.source(), Path.of(result.jar()), List.copyOf(result.diagnostics()));
            // Rechecking/loading unchanged source reuses bytecode, with a separate artifact and loader per generation.
            if (Files.size(compiled.jar()) <= 2 * 1024 * 1024) {
                if (results.size() >= 8) results.remove(results.keySet().iterator().next());
                results.put(key, new Cached(compiled.source(), Files.readAllBytes(compiled.jar()), compiled.diagnostics()));
            }
            Files.deleteIfExists(request); Files.deleteIfExists(response); Files.deleteIfExists(phase); Files.deleteIfExists(output);
            return compiled;
        } catch (IOException | RuntimeException error) { deleteTree(directory); throw error; }
    }
    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException("Compilation cancelled");
    }
    private static String logTail(Path output) throws IOException {
        if (!Files.exists(output)) return "no compiler output";
        try (var channel = Files.newByteChannel(output)) {
            channel.position(Math.max(0, channel.size() - 8192));
            var buffer = java.nio.ByteBuffer.allocate(8192); channel.read(buffer); buffer.flip();
            return StandardCharsets.UTF_8.decode(buffer).toString();
        }
    }
    private static String workerClasspath() throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        for (Class<?> type : List.of(ScriptCompilerWorker.class, Gson.class, EclipseCompiler.class,
                net.fabricmc.tinyremapper.TinyRemapper.class, net.fabricmc.mappingio.MappingReader.class,
                org.objectweb.asm.ClassReader.class, org.objectweb.asm.commons.ClassRemapper.class, org.objectweb.asm.tree.ClassNode.class)) {
            try { entries.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().toString()); }
            catch (Exception error) { throw new IOException("Cannot locate compiler worker dependency " + type.getName(), error); }
        }
        return String.join(File.pathSeparator, entries);
    }
    private Path directory(String id, long generation) {
        return cache.resolve("generations").resolve("OmixScript_" + id.replaceAll("[^A-Za-z0-9_]", "_") + "_" + generation).toAbsolutePath();
    }
    Compiled compileLocal(String id, long generation, String body, int leadingLines, Consumer<String> progress) throws IOException {
        progress.accept("source");
        String className = "OmixScript_" + id.replaceAll("[^A-Za-z0-9_]", "_") + "_" + generation;
        ScriptSource source = ScriptSource.wrap(className, body);
        if (leadingLines > 0) {
            int[] lines = source.originalLines().clone();
            for (int i = 0; i < lines.length; i++) lines[i] = Math.max(0, lines[i] - leadingLines);
            source = new ScriptSource(source.className(), source.source(), lines);
        }
        progress.accept("classpath");
        List<Path> entries = classpath.prepare();
        Path directory = directory(id, generation);
        Files.createDirectories(directory);
        Path input = directory.resolve(className + ".java");
        Path output = directory.resolve("classes"); Files.createDirectories(output);
        Files.writeString(input, source.source(), StandardCharsets.UTF_8);
        try {
        progress.accept("java");
        var problems = compileSource(source, input, output, entries);
        if (problems.stream().anyMatch(problem -> problem.severity.equals("ERROR"))) throw new CompileFailure(problems);
        progress.accept("remap");
        Path jar = directory.resolve("script.jar"); classpath.remapOutput(output, jar);
        return new Compiled(source, jar, List.copyOf(problems));
        } catch (IOException | RuntimeException error) { deleteTree(directory); throw error; }
    }
    private static void deleteTree(Path directory) {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException ignored) { /* Closed files may stay locked temporarily on some launchers. */ }
    }
    public static List<Problem> compileSource(ScriptSource source, Path input, Path output, List<Path> classpath) throws IOException {
        var compiler = new EclipseCompiler(); var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            // ECJ's file manager defaults SOURCE_PATH to the launcher's classpath. -classpath alone
            // leaves official obfuscated Minecraft classes visible through that second lookup path.
            files.setLocation(StandardLocation.SOURCE_PATH, List.of());
            files.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath);
            String cp = String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList());
            StringWriter compilerOutput = new StringWriter();
            boolean ok = compiler.getTask(compilerOutput, files, diagnostics,
                    List.of("-source", "21", "-target", "21", "-proc:none", "-g", "-encoding", "UTF-8", "-classpath", cp, "-d", output.toString()),
                    null, files.getJavaFileObjects(input.toFile())).call();
            var result = new ArrayList<>(diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR || source.line(d.getLineNumber()) > 0).map(d -> new Problem(d.getKind().name(),
                    d.getMessage(Locale.ROOT), source.line(d.getLineNumber()), d.getColumnNumber())).toList());
            if (!ok && result.stream().noneMatch(problem -> problem.severity.equals("ERROR")))
                result.add(new Problem("ERROR", compilerOutput.toString(), 0, 0));
            return result;
        }
    }
}
