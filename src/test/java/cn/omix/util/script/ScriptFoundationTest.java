package cn.omix.util.script;

import cn.omix.script.api.Registration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptFoundationTest {
    @TempDir Path temp;
    @Test void sourceImportsKeepOriginalLinesAndIgnoreLiteralsAndComments() {
        String text = "// import missing.Foo;\nimport java.time.\n Duration;\nString value = \"import not.AType;\";\nvoid onLoad() { int x = missing; }\n";
        var source = ScriptSource.wrap("Sample", text);
        assertEquals(1, source.source().split("import java.time", -1).length - 1);
        int generated = 1 + (int) source.source().substring(0, source.source().indexOf("void onLoad")).chars().filter(c -> c == '\n').count();
        assertEquals(5, source.line(generated));
        assertTrue(source.source().contains("\"import not.AType;\""));
        assertThrows(IllegalArgumentException.class, () -> ScriptSource.wrap("Bad", "package bad;"));
        assertEquals(0, source.line(-1));
    }
    @Test void textBlockAndEscapedQuotesNeverBecomeImports() {
        String text = "String text = \"\"\"\nimport nonexistent.Test;\n\"\"\";\nchar quote = '\\'';";
        var result = ScriptSource.wrap("TextBlock", text);
        assertTrue(result.source().contains(text));
        assertFalse(result.source().startsWith("import nonexistent"));
    }
    @Test void compareAndSwapSourcesRejectOverwriteTraversalAndSymlinks() throws Exception {
        var files = new ScriptFiles(temp.resolve("scripts"));
        var initial = files.write("Hello", "// 中文", "");
        assertEquals(List.of("Hello"), files.list());
        assertEquals(initial, files.read("Hello.java"));
        assertThrows(IllegalStateException.class, () -> files.write("Hello", "new", ""));
        var next = files.write("Hello", "changed", initial.hash());
        assertThrows(IllegalStateException.class, () -> files.delete("Hello", initial.hash()));
        assertThrows(IllegalArgumentException.class, () -> files.read("../escape"));
        Path outside = Files.writeString(temp.resolve("outside.java"), "untouched");
        Files.createSymbolicLink(files.root().resolve("Link.java"), outside);
        assertThrows(java.io.IOException.class, () -> files.write("Link", "bad", ScriptFiles.hash("untouched")));
        files.delete("Hello", next.hash()); assertEquals(List.of(), files.list());
        assertEquals("untouched", Files.readString(outside));
    }
    @Test void scopeInvalidatesBeforeCleanupAndClosesLateResources() {
        var scope = new ScriptScope(); var calls = new ArrayList<String>();
        scope.activate();
        scope.own((Registration) () -> { assertFalse(scope.active()); calls.add("first"); });
        scope.own((Registration) () -> { calls.add("second"); throw new AssertionError("cleanup"); });
        assertThrows(IllegalStateException.class, scope::close);
        assertEquals(List.of("second", "first"), calls); scope.close();
        assertThrows(IllegalStateException.class, () -> scope.own((Registration) () -> calls.add("late")));
        assertEquals(List.of("second", "first", "late"), calls);
        assertThrows(IllegalStateException.class, scope::activate);
    }
    @Test void scopesCanPauseAndResumeForRollback() {
        var scope = new ScriptScope(); var count = new java.util.concurrent.atomic.AtomicInteger();
        scope.own((Registration) count::incrementAndGet); scope.activate(); scope.pause();
        assertFalse(scope.active()); assertEquals(0, count.get()); scope.activate(); assertTrue(scope.active());
        scope.close(); assertEquals(1, count.get());
    }
    @Test void reflectionMappingIncludesDescriptorsAndDeclaringMembers() throws Exception {
        String tiny = "tiny\t2\t0\tintermediary\tnamed\nc\ttest/class_1\ttest/Entity\n\tf\tI\tfield_1\tage\n\tm\t()Ltest/class_1;\tmethod_1\tcopy\n";
        var mappings = new ScriptMappings(new java.io.StringReader(tiny), "intermediary");
        assertEquals("test.class_1", mappings.className("test.Entity"));
        assertEquals("method_1", mappings.methodName("test.Entity", "copy", "()Ltest/Entity;"));
        assertEquals("field_1", mappings.fieldName("test.Entity", "age", "I"));
        assertEquals("([Ltest/class_1;)Ltest/class_1;", mappings.descriptor("([Ltest/Entity;)Ltest/Entity;"));
        assertEquals("java.lang.String", mappings.className("java.lang.String"));
    }
    @Test void officialGameArchivesDoNotPolluteTheNamedCompilationView() throws Exception {
        Path official = temp.resolve("official.jar"), runtime = temp.resolve("runtime.jar");
        for (Path path : List.of(official, runtime)) {
            try (var jar = new java.util.jar.JarOutputStream(Files.newOutputStream(path))) {
                jar.putNextEntry(new java.util.jar.JarEntry("net/minecraft/client/main/Main.class")); jar.closeEntry();
                jar.putNextEntry(new java.util.jar.JarEntry(path.equals(runtime) ? "net/minecraft/class_310.class" : "cn.class")); jar.closeEntry();
            }
        }
        assertFalse(ScriptClasspath.isRuntimeMinecraft(official, "intermediary"));
        assertTrue(ScriptClasspath.isRuntimeMinecraft(runtime, "intermediary"));
        assertFalse(ScriptClasspath.isRuntimeMinecraft(runtime, "named"));
    }

}
