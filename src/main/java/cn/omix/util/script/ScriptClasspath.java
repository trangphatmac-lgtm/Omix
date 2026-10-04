package cn.omix.util.script;

import cn.omix.Client;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.tinyremapper.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Creates a local named compilation view. No Minecraft binaries are redistributed. */
public final class ScriptClasspath {
    private final Path cache;
    /** Only paths and namespace cross the process boundary; the worker never starts Fabric/Minecraft. */
    public record Environment(String namespace, List<String> entries, String mappings) {}
    @FunctionalInterface interface EnvironmentProvider { Environment get(Set<String> requested) throws IOException; }
    private final EnvironmentProvider environmentProvider;
    private final Map<Set<String>, Environment> environments = new HashMap<>();
    private List<Path> named;
    private Path mappings;
    private String namespace;
    public ScriptClasspath(Path cache) { this.cache = cache; this.environmentProvider = this::collectEnvironment; }
    ScriptClasspath(Path cache, Environment environment) { this(cache, requested -> environment); }
    ScriptClasspath(Path cache, EnvironmentProvider provider) { this.cache = cache; this.environmentProvider = provider; }
    public Environment environment() throws IOException { return environment(Set.of()); }
    public synchronized Environment environment(Set<String> requested) throws IOException {
        Set<String> key = Set.copyOf(requested);
        Environment result = environments.get(key);
        if (result == null) {
            result = environmentProvider.get(key);
            // Bound per-session combinations; disk mapping caches remain reusable.
            if (environments.size() >= 32) environments.clear();
            environments.put(key, result);
        }
        return result;
    }
    private Environment collectEnvironment(Set<String> requested) throws IOException {
        Files.createDirectories(cache);
        namespace = FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
        mappings = cache.resolve("yarn.tiny").toAbsolutePath();
        if (!namespace.equals("named")) {
            try (InputStream input = ScriptClasspath.class.getResourceAsStream("/assets/omix/script/mappings/mappings.tiny")) {
                if (input == null) throw new IOException("Bundled Yarn mappings missing; rebuild the client.");
                byte[] bytes = input.readAllBytes();
                if (!Files.exists(mappings) || !Arrays.equals(Files.readAllBytes(mappings), bytes)) Files.write(mappings, bytes);
            }
        }
        var entries = new LinkedHashSet<Path>();
        var defaults = ScriptDependencies.defaults();
        for (var mod : ScriptDependencies.selectMods(defaults.mods(), requested, FabricLoader.getInstance()::getModContainer)) {
            for (Path root : mod.getRootPaths()) entries.add(materialize(root));
        }
        entries.addAll(ScriptDependencies.selectLibraries(System.getProperty("java.class.path", ""), defaults.libraries()));
        for (Class<?> type : List.of(Client.class, net.minecraft.client.MinecraftClient.class,
                ScriptCompiler.class, org.eclipse.jdt.internal.compiler.tool.EclipseCompiler.class)) {
            try {
                var location = type.getProtectionDomain().getCodeSource();
                if (location != null && location.getLocation().getProtocol().equals("file")) entries.add(Path.of(location.getLocation().toURI()).toRealPath());
            } catch (Exception error) { throw new IOException("Cannot locate compiler dependency " + type.getName(), error); }
        }
        List<Path> originals = new ArrayList<>();
        for (Path entry : entries) if (Files.exists(entry) && isRuntimeMinecraft(entry, namespace)) originals.add(entry);
        return new Environment(namespace, originals.stream().map(path -> path.toAbsolutePath().toString()).toList(), mappings.toString());
    }
    public synchronized List<Path> prepare() throws IOException {
        if (named != null) return named;
        Environment runtime = environment();
        namespace = runtime.namespace(); mappings = Path.of(runtime.mappings());
        Files.createDirectories(cache);
        List<Path> originals = runtime.entries().stream().map(Path::of).toList();
        if (namespace.equals("named")) return named = List.copyOf(originals);
        if (!namespace.equals("intermediary")) throw new IOException("Unsupported runtime namespace: " + namespace);
        // TinyRemapper creates a copy of the WHOLE graph for every multi-release version it sees.
        // Compilation needs only the classes selected by this JVM, never all historical variants.
        List<Path> views = new ArrayList<>();
        for (Path original : originals) views.add(runtimeView(original));
        originals = views;
        String mappingHash = ScriptFiles.hash(Files.readString(mappings));
        String dependencyHash = ScriptFiles.hash(String.join("\n", originals.stream().map(path -> { try { return fingerprint(path); } catch (IOException error) { throw new UncheckedIOException(error); } }).toList()));
        List<Path> result = new ArrayList<>();
        Map<Path, Path> pending = new LinkedHashMap<>();
        for (Path entry : originals) {
            String key = fingerprint(entry) + mappingHash + dependencyHash;
            Path output = cache.resolve("named-v2-" + ScriptFiles.hash(key) + ".jar");
            if (!Files.isRegularFile(output)) pending.put(entry, output);
            result.add(output);
        }
        if (!pending.isEmpty()) remapBatch(mappings, pending, originals, namespace, "named");
        return named = List.copyOf(result);
    }
    Path runtimeView(Path input) throws IOException {
        Path output = cache.resolve("view-v1-" + ScriptFiles.hash(fingerprint(input) + ":" + Runtime.version().feature()) + ".jar");
        if (Files.isRegularFile(output)) return output;
        Files.createDirectories(cache);
        Path temp = Files.createTempFile(cache, "view-", ".tmp");
        try {
            try (var jar = new JarOutputStream(Files.newOutputStream(temp))) {
                if (Files.isDirectory(input)) {
                    try (var files = Files.walk(input)) {
                        for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                            String name = input.relativize(file).toString().replace('\\', '/');
                            if (!classEntry(name)) continue;
                            jar.putNextEntry(new JarEntry(name)); Files.copy(file, jar); jar.closeEntry();
                        }
                    }
                } else {
                    try (var archive = new JarFile(input.toFile(), false, JarFile.OPEN_READ, Runtime.version())) {
                        for (JarEntry entry : archive.versionedStream().filter(value -> classEntry(value.getName())).toList()) {
                            jar.putNextEntry(new JarEntry(entry.getName()));
                            try (var bytes = archive.getInputStream(entry)) { bytes.transferTo(jar); }
                            jar.closeEntry();
                        }
                    }
                }
            }
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        return output;
    }
    private static boolean classEntry(String name) {
        return name.endsWith(".class") && !name.startsWith("META-INF/") && !name.equals("module-info.class");
    }
    /** Launchers retain the official game jar on java.class.path even after Fabric remaps it.
     * Its default-package classes (for example cn.class) shadow script package expressions. */
    public static boolean isRuntimeMinecraft(Path path, String namespace) throws IOException {
        String client = namespace.equals("named") ? "net/minecraft/client/MinecraftClient.class" : "net/minecraft/class_310.class";
        if (Files.isDirectory(path)) return !Files.exists(path.resolve("net/minecraft/client/main/Main.class")) || Files.exists(path.resolve(client));
        if (!path.toString().endsWith(".jar")) return true;
        try (JarFile jar = new JarFile(path.toFile())) {
            return jar.getJarEntry("net/minecraft/client/main/Main.class") == null || jar.getJarEntry(client) != null;
        }
    }
    private static String fingerprint(Path path) throws IOException {
        if (Files.isRegularFile(path)) return path + ":" + Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis();
        StringBuilder value = new StringBuilder(path.toString());
        try (var files = Files.walk(path)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                value.append(file).append(':').append(Files.size(file)).append(':').append(Files.getLastModifiedTime(file).toMillis());
            }
        }
        return value.toString();
    }
    Path materialize(Path root) throws IOException {
        if (root.getFileSystem().equals(FileSystems.getDefault())) return root.toRealPath();
        // Prefer the physical Fabric jar over a duplicate extraction of the same classpath root.
        var uri = root.toUri();
        if (uri.getScheme().equals("jar") && root.toString().equals("/")) {
            // Opaque jar URIs allow [] and ? unescaped, unlike a hierarchical file URI's path.
            // Keep existing escapes: decoding the opaque URI also leaves escapes inside [] intact.
            // Strip only the root suffix: a directory name may itself end with '!'.
            String archiveUri = uri.getRawSchemeSpecificPart();
            if (archiveUri.startsWith("file:") && archiveUri.endsWith("!/")) {
                String fileUri = archiveUri.substring(0, archiveUri.length() - 2)
                        .replace("[", "%5B").replace("]", "%5D").replace("?", "%3F");
                Path archive = Path.of(URI.create(fileUri));
                if (Files.isRegularFile(archive)) return archive.toRealPath();
            }
        }
        // Nested Fabric jars use zipfs roots. Extract classes/resources into a regular jar ECJ can read.
        Path output = cache.resolve("dependency-" + ScriptFiles.hash(fingerprint(root)) + ".jar");
        if (Files.isRegularFile(output)) return output;
        Path temp = Files.createTempFile(cache, "dependency-", ".jar");
        try {
            try (var jar = new JarOutputStream(Files.newOutputStream(temp)); var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    String name = root.relativize(file).toString().replace('\\', '/');
                    if (!name.endsWith(".class") && !name.equals("META-INF/MANIFEST.MF")) continue;
                    JarEntry entry = new JarEntry(name); entry.setTime(0); jar.putNextEntry(entry);
                    Files.copy(file, jar); jar.closeEntry();
                }
            }
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        return output;
    }
    public void remapOutput(Path input, Path output) throws IOException {
        List<Path> classpath = prepare();
        if (namespace.equals("named")) {
            try (var jar = new JarOutputStream(Files.newOutputStream(output)); var files = Files.walk(input)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    jar.putNextEntry(new JarEntry(input.relativize(file).toString().replace('\\', '/')));
                    Files.copy(file, jar); jar.closeEntry();
                }
            }
        } else remap(input, output, classpath, "named", namespace);
    }
    private void remap(Path input, Path output, List<Path> classpath, String from, String to) throws IOException {
        remapBatch(mappings, Map.of(input, output), classpath, from, to);
    }
    /** One graph resolves inheritance across every dependency before emitting individual artifacts. */
    public static void remapBatch(Path mappings, Map<Path, Path> outputs, List<Path> classpath, String from, String to) throws IOException {
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createTinyMappingProvider(mappings, from, to)).threads(2).renameInvalidLocals(true).build();
        Map<Path, InputTag> tags = new LinkedHashMap<>();
        try {
            remapper.readClassPath(classpath.stream().filter(path -> !outputs.containsKey(path)).toArray(Path[]::new));
            for (var entry : outputs.entrySet()) {
                InputTag tag = outputs.size() == 1 ? null : remapper.createInputTag(); tags.put(entry.getValue(), tag); remapper.readInputs(tag, entry.getKey());
            }
            for (var entry : tags.entrySet()) {
                Files.createDirectories(entry.getKey().getParent());
                Path temp = Files.createTempFile(entry.getKey().getParent(), "remap-", ".jar"); Files.delete(temp);
                try {
                    try (var consumer = new OutputConsumerPath.Builder(temp).build()) { remapper.apply(consumer, entry.getValue()); }
                    Files.move(temp, entry.getKey(), StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temp); }
            }
        } catch (RuntimeException error) { throw new IOException("Java script mapping failed", error); }
        finally { remapper.finish(); }
    }
}
