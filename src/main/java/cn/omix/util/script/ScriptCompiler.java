package cn.omix.util.script;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import javax.tools.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.net.*;

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
    public Compiled compile(String id, long generation, String body) throws IOException {
        return compile(id, generation, body, 0);
    }
    public Compiled compile(String id, long generation, String body, int leadingLines) throws IOException {
        String className = "OmixScript_" + id.replaceAll("[^A-Za-z0-9_]", "_") + "_" + generation;
        ScriptSource source = ScriptSource.wrap(className, body);
        if (leadingLines > 0) {
            int[] lines = source.originalLines().clone();
            for (int i = 0; i < lines.length; i++) lines[i] = Math.max(0, lines[i] - leadingLines);
            source = new ScriptSource(source.className(), source.source(), lines);
        }
        List<Path> entries = classpath.prepare();
        Path directory = cache.resolve("generations").resolve(className);
        Files.createDirectories(directory);
        Path input = directory.resolve(className + ".java");
        Path output = directory.resolve("classes"); Files.createDirectories(output);
        Files.writeString(input, source.source(), StandardCharsets.UTF_8);
        try {
        var problems = compileSource(source, input, output, entries);
        if (problems.stream().anyMatch(problem -> problem.severity.equals("ERROR"))) throw new CompileFailure(problems);
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
