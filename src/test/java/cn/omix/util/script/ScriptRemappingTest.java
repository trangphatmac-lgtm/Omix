package cn.omix.util.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptRemappingTest {
    @TempDir Path temp;
    @Test void multiReleaseViewSelectsRuntimeClassesWithoutMultiplyingTheMappingGraph() throws Exception {
        var manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        Path original = temp.resolve("multi.jar");
        try (var jar = new java.util.jar.JarOutputStream(Files.newOutputStream(original), manifest)) {
            for (var entry : Map.of("sample/Value.class", "base", "META-INF/versions/21/sample/Value.class", "java21",
                    "META-INF/versions/99/sample/Value.class", "future", "module-info.class", "module", "assets/large.bin", "resource").entrySet()) {
                jar.putNextEntry(new java.util.jar.JarEntry(entry.getKey())); jar.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)); jar.closeEntry();
            }
        }
        var classpath = new ScriptClasspath(temp.resolve("views"));
        Path view = classpath.runtimeView(original);
        try (var jar = new java.util.jar.JarFile(view.toFile())) {
            assertEquals(List.of("sample/Value.class"), jar.stream().map(java.util.jar.JarEntry::getName).toList());
            assertEquals("java21", new String(jar.getInputStream(jar.getJarEntry("sample/Value.class")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        assertEquals(view, classpath.runtimeView(original));
    }
    @Test void mappedArtifactsExecuteInheritedMethodsLambdasAndNestedClassesWithSharedParentIdentity() throws Exception {
        Path sources = temp.resolve("source"), classes = temp.resolve("classes"); Files.createDirectories(classes);
        Map<String,String> inputs=Map.of(
                "named/Base.java", "package named; public class Base { public String describe() { return \"mapped\"; } }",
                "named/Child.java", "package named; public class Child extends Base {}",
                "TestProgram.java", "import named.Child; public class TestProgram { class Inner { String value() { return new Child().describe(); } } public String run() { java.util.function.Supplier<String> value = () -> new Inner().value(); return value.get(); } }");
        var paths=new ArrayList<java.io.File>();
        for(var entry:inputs.entrySet()) {Path file=sources.resolve(entry.getKey());Files.createDirectories(file.getParent());Files.writeString(file,entry.getValue());paths.add(file.toFile());}
        var ecj=new EclipseCompiler();
        try(var files=ecj.getStandardFileManager(null,null,null)) {
            assertTrue(ecj.getTask(new java.io.StringWriter(),files,null,List.of("-21","-proc:none","-d",classes.toString()),null,files.getJavaFileObjectsFromFiles(paths)).call());
        }
        Path mappings=temp.resolve("mapping.tiny"); Files.writeString(mappings,"tiny\t2\t0\tnamed\tintermediary\nc\tnamed/Base\truntime/class_1\n\tm\t()Ljava/lang/String;\tdescribe\tmethod_1\nc\tnamed/Child\truntime/class_2\n");
        Path api=temp.resolve("api"), script=temp.resolve("script"); Files.createDirectories(api);Files.createDirectories(script);
        Files.move(classes.resolve("named"),api.resolve("named"));
        try(var files=Files.list(classes)) {for(Path file:files.toList())Files.move(file,script.resolve(file.getFileName()));}
        Path apiJar=temp.resolve("api.jar"),scriptJar=temp.resolve("script.jar");
        // The exact batch remapper used by production resolves Child.describe against its inherited declaration.
        ScriptClasspath.remapBatch(mappings,Map.of(api,apiJar,script,scriptJar),List.of(),"named","intermediary");
        try(var parent=new URLClassLoader(new URL[]{apiJar.toUri().toURL()},ClassLoader.getPlatformClassLoader());
            var loader=new URLClassLoader(new URL[]{scriptJar.toUri().toURL()},parent)) {
            Class<?> type=Class.forName("TestProgram",true,loader);Object instance=type.getConstructor().newInstance();
            assertEquals("mapped",type.getMethod("run").invoke(instance));
            assertSame(parent.loadClass("runtime.class_2"),loader.loadClass("runtime.class_2"));
            assertThrows(ClassNotFoundException.class,()->loader.loadClass("named.Child"));
        }
    }
}
