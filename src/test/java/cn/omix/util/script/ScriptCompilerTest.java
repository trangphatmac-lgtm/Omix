package cn.omix.util.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptCompilerTest {
    @TempDir Path temp;
    private ScriptCompiler isolatedCompiler() throws Exception {
        Path cache = temp.resolve("cache with spaces");
        var cp = ScriptDependenciesTest.baselineClasspath().stream().map(Path::toString).toList();
        return new ScriptCompiler(cache, new ScriptClasspath(cache.resolve("classpath"),
                new ScriptClasspath.Environment("named", cp, temp.resolve("unused.tiny").toString())));
    }
    @Test void explicitModDependencyCompilesAndRemovingItDoesNotLeakIntoOtherScripts() throws Exception {
        Path archive = temp.resolve("Release 2.2.11/mods/[钠 · 扩展] dependency 100%20 #+!.jar");
        Files.createDirectories(archive.getParent());
        var writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC, "sample/Value", null, "java/lang/Object", null);
        var method = writer.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC, "answer", "()I", null, null);
        method.visitCode(); method.visitIntInsn(org.objectweb.asm.Opcodes.BIPUSH, 42); method.visitInsn(org.objectweb.asm.Opcodes.IRETURN);
        method.visitMaxs(1, 0); method.visitEnd(); writer.visitEnd();
        Path cache = temp.resolve("cache with spaces");
        Path dependency;
        try (var zip = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            Files.createDirectories(zip.getPath("/sample"));
            Files.write(zip.getPath("/sample/Value.class"), writer.toByteArray());
        }
        try (var zip = FileSystems.newFileSystem(archive)) {
            dependency = new ScriptClasspath(cache.resolve("classpath")).materialize(zip.getPath("/"));
        }
        var baseline = ScriptDependenciesTest.baselineClasspath().stream().map(Path::toString).toList();
        var compiler = new ScriptCompiler(cache, new ScriptClasspath(cache.resolve("classpath"), requested -> {
            var cp = new ArrayList<>(baseline);
            for (String id : requested) {
                if (!id.equals("extra_mod")) throw new java.io.IOException("Script dependency Mod is not loaded: " + id);
                cp.add(dependency.toString());
            }
            return new ScriptClasspath.Environment("named", cp, temp.resolve("unused.tiny").toString());
        }));
        String body = "public static int answer() { return sample.Value.answer(); }";
        assertThrows(ScriptCompiler.CompileFailure.class, () -> compiler.compile("BracketDependency", 1, body));
        var compiled = compiler.compile("BracketDependency", 2, "// @depends extra_mod\n" + body);
        try (var parent = new java.net.URLClassLoader(new java.net.URL[]{dependency.toUri().toURL()}, getClass().getClassLoader());
             var loader = compiled.loader(parent)) {
            assertEquals(42, loader.loadClass(compiled.source().className()).getMethod("answer").invoke(null));
        } finally { compiled.discard(); }
        assertThrows(ScriptCompiler.CompileFailure.class, () -> compiler.compile("BracketDependency", 3, body));
        assertThrows(ScriptCompiler.CompileFailure.class, () -> compiler.compile("OtherScript", 4, body));
        var missing = assertThrows(java.io.IOException.class, () -> compiler.compile("Missing", 5, "// @depends missing_mod\n" + body));
        assertTrue(missing.getMessage().contains("missing_mod"));
        var ordinary = compiler.compile("Ordinary", 6, "int answer() { return 42; }"); ordinary.discard();
        var evaluation = compiler.compile("@evaluation", 7, "Object evaluate() {\n// @depends extra_mod\nreturn sample.Value.answer();\n}", 1);
        evaluation.discard();
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
        var cp = ScriptDependenciesTest.baselineClasspath().stream().map(Path::toString).toList();
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
        var cp = ScriptDependenciesTest.baselineClasspath();
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
