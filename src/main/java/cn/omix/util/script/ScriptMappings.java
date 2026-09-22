package cn.omix.util.script;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Reflection strings need explicit conversion; ordinary bytecode references are remapped by the compiler. */
public final class ScriptMappings {
    private final MemoryMappingTree tree;
    private final int named, runtime;
    public ScriptMappings(Reader mappings, String namespace) throws IOException {
        tree = new MemoryMappingTree(); MappingReader.read(mappings, tree);
        named = tree.getNamespaceId("named"); runtime = tree.getNamespaceId(namespace);
        if (named == -2 || runtime == -2) throw new IOException("Mapping namespace is missing: " + namespace);
    }
    private static final class Holder {
        static final ScriptMappings INSTANCE = load();
        private static ScriptMappings load() {
            try (InputStream input = ScriptMappings.class.getResourceAsStream("/assets/omix/script/mappings/mappings.tiny")) {
                if (input == null) throw new IOException("Bundled Yarn mappings missing");
                return new ScriptMappings(new InputStreamReader(input, StandardCharsets.UTF_8), FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace());
            } catch (IOException error) { throw new IllegalStateException(error); }
        }
    }
    public static ScriptMappings current() { return Holder.INSTANCE; }
    public String className(String name) { return tree.mapClassName(name.replace('.', '/'), named, runtime).replace('/', '.'); }
    public String descriptor(String namedDescriptor) { return tree.mapDesc(namedDescriptor, named, runtime); }
    /** Specify the class that declares the member and its Yarn JVM descriptor, including return type. */
    public String methodName(String declaringClass, String name, String descriptor) {
        var method = tree.getMethod(declaringClass.replace('.', '/'), name, descriptor, named);
        return method == null || method.getName(runtime) == null ? name : method.getName(runtime);
    }
    public String fieldName(String declaringClass, String name, String descriptor) {
        var field = tree.getField(declaringClass.replace('.', '/'), name, descriptor, named);
        return field == null || field.getName(runtime) == null ? name : field.getName(runtime);
    }
}
