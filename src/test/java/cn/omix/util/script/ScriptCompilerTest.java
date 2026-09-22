package cn.omix.util.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptCompilerTest {
    @TempDir Path temp;
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
