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
