package cn.omix.util.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptCompilerTest {
    @TempDir Path temp;
    private ScriptCompiler isolatedCompiler() {
        Path cache = temp.resolve("cache with spaces");
        var cp = Arrays.asList(System.getProperty("omix.test.classpath").split(java.io.File.pathSeparator));
        return new ScriptCompiler(cache, new ScriptClasspath(cache.resolve("classpath"),
                new ScriptClasspath.Environment("named", cp, temp.resolve("unused.tiny").toString())));
    }
    @Test void isolatedCompilerCachesUnchangedBytecodeButKeepsGenerationArtifactsIndependent() throws Exception {
        ScriptCompiler compiler = isolatedCompiler();
        List<String> phases = new ArrayList<>();
        var first = compiler.compile("Cached", 1, "int answer() { return 42; }", 0, () -> false, phases::add);
        assertTrue(Files.isRegularFile(first.jar()));
        byte[] expected = Files.readAllBytes(first.jar()); first.discard(); phases.clear();
        var second = compiler.compile("Cached", 2, "int answer() { return 42; }", 0, () -> false, phases::add);
        assertEquals(List.of("cache"), phases);
        assertNotEquals(first.jar(), second.jar()); assertArrayEquals(expected, Files.readAllBytes(second.jar()));
        second.discard();
        phases.clear();
        var changed = compiler.compile("Cached", 3, "int answer() { return 43; }", 0, () -> false, phases::add);
        assertFalse(phases.contains("cache")); assertNotEquals(second.source().className(), changed.source().className());
        changed.discard();
    }
    @Test void isolatedCompilerReportsOriginalEvaluationLinesAndRecoversAfterErrors() throws Exception {
        ScriptCompiler compiler = isolatedCompiler();
        var error = assertThrows(ScriptCompiler.CompileFailure.class, () -> compiler.compile("@evaluation", 1,
                "Object evaluate() {\nreturn unknownValue;\n}", 1));
        assertEquals(1, error.diagnostics.stream().filter(p -> p.severity().equals("ERROR")).findFirst().orElseThrow().line());
        var valid = compiler.compile("@evaluation", 2, "Object evaluate() {\nreturn 42;\n}", 1);
        assertTrue(Files.isRegularFile(valid.jar())); valid.discard();
    }
    @Test void unavailableProgressFileDoesNotHideDiagnosticsOrPreventCompilation() throws Exception {
        Path cache = temp.resolve("worker cache"), request = temp.resolve("request.json"), response = temp.resolve("response.json");
        Path progress = temp.resolve("blocked-progress");
        Files.createDirectories(progress);
        Files.writeString(progress.resolve("occupied"), "prevent replacement on every platform");
        var cp = Arrays.asList(System.getProperty("omix.test.classpath").split(java.io.File.pathSeparator));
        var environment = new ScriptClasspath.Environment("named", cp, temp.resolve("unused.tiny").toString());
        var gson = new com.google.gson.Gson();
        for (int generation = 1; generation <= 2; generation++) {
            String body = generation == 1 ? "int answer() { return unknownValue; }" : "int answer() { return 42; }";
            Files.writeString(request, gson.toJson(new ScriptCompilerWorker.Request(cache.toString(), environment, "Progress", generation, body, 0)));
            ScriptCompilerWorker.main(new String[]{request.toString(), response.toString(), progress.toString()});
            var result = gson.fromJson(Files.readString(response), ScriptCompilerWorker.Result.class);
            if (generation == 1) {
                assertTrue(result.diagnostics().stream().anyMatch(problem -> problem.severity().equals("ERROR")));
                assertTrue(result.error().contains("CompileFailure"), result.error());
            } else {
                assertNull(result.error());
                assertTrue(Files.isRegularFile(Path.of(result.jar())));
            }
        }
    }
    private List<ScriptCompiler.Problem> compile(String name, String body) throws Exception {
        var source = ScriptSource.wrap(name, body); Path input = temp.resolve(name + ".java"); Files.writeString(input, source.source());
        Path output = temp.resolve(name); Files.createDirectories(output);
        var cp = Arrays.stream(System.getProperty("omix.test.classpath").split(java.io.File.pathSeparator)).map(Path::of).toList();
        return ScriptCompiler.compileSource(source, input, output, cp);
    }
    @Test void bundledExamplesCompileAgainstActualMinecraftAndClientClasses() throws Exception {
        try (var files = Files.list(Path.of(System.getProperty("omix.test.root"), "docs/script/examples"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                var errors = compile("Test" + file.getFileName().toString().replace(".java", ""), Files.readString(file)).stream().filter(p -> p.severity().equals("ERROR")).toList();
                assertTrue(errors.isEmpty(), file + ": " + errors);
            }
        }
    }
    @Test void compilerErrorsUseOriginalFragmentLineAndColumn() throws Exception {
        var errors = compile("Broken", "void onLoad() {\n    int x = doesNotExist;\n}").stream().filter(p -> p.severity().equals("ERROR")).toList();
        assertFalse(errors.isEmpty()); assertEquals(2, errors.getFirst().line()); assertTrue(errors.getFirst().column() > 0);
    }
    @Test void java21RecordsSwitchExpressionsAndTextBlocksWorkWithoutAnnotationProcessing() throws Exception {
        var errors = compile("Modern", "record R(int x) {}\nString t = \"\"\"\nhello\n\"\"\";\nint value(Object o) { return switch (o) { case R(int x) -> x; default -> 0; }; }").stream().filter(p -> p.severity().equals("ERROR")).toList();
        assertTrue(errors.isEmpty(), errors.toString());
    }
    @Test void launcherSourcePathCannotShadowClientPackages() throws Exception {
        Path poison = temp.resolve("launcher"); Files.createDirectories(poison);
        var writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC, "cn", null, "java/lang/Object", null);
        writer.visitEnd(); Files.write(poison.resolve("cn.class"), writer.toByteArray());
        String previous = System.getProperty("java.class.path");
        try {
            System.setProperty("java.class.path", poison.toString());
            var errors = compile("Unshadowed", "Object inspect() { return cn.omix.util.script.ScriptState.capture(modules.get(\"Speed\")); }")
                    .stream().filter(p -> p.severity().equals("ERROR")).toList();
            assertTrue(errors.isEmpty(), errors.toString());
        } finally { System.setProperty("java.class.path", previous); }
    }

}
