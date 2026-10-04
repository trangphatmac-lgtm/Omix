package cn.omix.util.script;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class ScriptDependenciesTest {
    @TempDir Path temp;

    @Test void declarationsAreDeduplicatedAndOnlyReadFromLeadingLineComments() throws Exception {
        assertEquals(Set.of("sodium", "iris", "extra_mod"), ScriptDependencies.parse(
                "/* license: // @depends ignored */\r\n// @depends sodium, iris\r\n\t// @depends sodium extra_mod\r\nimport java.util.List;"));
        assertEquals(Set.of(), ScriptDependencies.parse("String text = \"\"\"\n// @depends fake_mod\n\"\"\";"));
        assertEquals(Set.of(), ScriptDependencies.parse("/*\n// @depends ignored\n*/\nvoid onLoad() {}\n// @depends also_ignored"));
        assertEquals(Set.of(), ScriptDependencies.parse("// @dependsOn not_a_directive\nint answer = 42;"));
    }

    @Test void invalidDeclarationsReportTheOriginalLine() {
        for (String value : List.of("", ",", "../sodium", "Sodium", "sodium,", "https://example.org/mod.jar")) {
            var error = assertThrows(IOException.class, () -> ScriptDependencies.parse("// license\n// @depends " + value));
            assertTrue(error.getMessage().contains("line 2"), error.getMessage());
        }
    }

    @Test void onlyRequiredAndBundledModsAreVisitedByDefault() throws Exception {
        var nested = mod("bundled_library", List.of(), List.of());
        var mods = Map.of("omix", mod("omix", List.of(dependency("fabric-api", ModDependency.Kind.DEPENDS),
                        dependency("optional_mod", ModDependency.Kind.RECOMMENDS)), List.of(nested)),
                "fabric-api", mod("fabric-api", List.of(dependency("java", ModDependency.Kind.DEPENDS)), List.of()),
                "bundled_library", nested);
        Set<String> visited = new HashSet<>();
        var selected = ScriptDependencies.selectMods(List.of("omix"), Set.of(), id -> {
            visited.add(id);
            if (!mods.containsKey(id)) fail("Unrelated mod must not be inspected: " + id);
            return Optional.of(mods.get(id));
        });
        assertEquals(Set.of("omix", "fabric-api", "bundled_library"), visited);
        assertEquals(3, selected.size());
    }

    @Test void explicitDependenciesFollowRequiredClosureAndHandleAliasesAndCycles() throws Exception {
        var extra = mod("extra_mod", List.of(dependency("extra_api", ModDependency.Kind.DEPENDS)), List.of());
        var mods = Map.of("omix", mod("omix", List.of(), List.of()), "extra_mod", extra, "alias_mod", extra,
                "extra_api", mod("extra_api", List.of(dependency("extra_mod", ModDependency.Kind.DEPENDS)), List.of()));
        var selected = ScriptDependencies.selectMods(List.of("omix"), Set.of("alias_mod", "extra_mod"), id -> Optional.ofNullable(mods.get(id)));
        assertEquals(Set.of("omix", "extra_mod", "extra_api"),
                new HashSet<>(selected.stream().map(mod -> mod.getMetadata().getId()).toList()));
        assertEquals(3, selected.size());
    }

    @Test void missingDependenciesFailWithoutFallingBackToAllMods() {
        var error = assertThrows(IOException.class,
                () -> ScriptDependencies.selectMods(List.of(), Set.of("missing_mod"), id -> Optional.empty()));
        assertTrue(error.getMessage().contains("missing_mod"));
    }

    @Test void absentDevelopmentDefaultsAreSkippedButRequiredDependenciesAreNot() throws Exception {
        var core = mod("omix", List.of(), List.of());
        assertEquals(List.of(core), ScriptDependencies.selectMods(List.of("omix", "development_only"), Set.of(),
                id -> Optional.ofNullable(id.equals("omix") ? core : null)));
        var broken = mod("omix", List.of(dependency("required_library", ModDependency.Kind.DEPENDS)), List.of());
        var error = assertThrows(IOException.class, () -> ScriptDependencies.selectMods(List.of("omix"), Set.of(),
                id -> Optional.ofNullable(id.equals("omix") ? broken : null)));
        assertTrue(error.getMessage().contains("required_library"));
    }

    @Test void launcherClasspathCannotReintroduceUnrelatedModsOrDirectories() throws Exception {
        Path library = Files.writeString(temp.resolve("core-library.jar"), "selected by build index");
        Path unwanted = Files.writeString(temp.resolve("[钠] broken-unrelated.jar"), "not a valid jar");
        Path directory = Files.createDirectories(temp.resolve("unrelated classes"));
        String cp = String.join(File.pathSeparator, unwanted.toString(), library.toString(), directory.toString(), library.toString());
        assertEquals(List.of(library.toRealPath()), ScriptDependencies.selectLibraries(cp, Set.of("core-library.jar")));
    }

    @Test void bundledDefaultsDescribeBuildDependenciesWithoutUnrelatedMods() throws Exception {
        var defaults = ScriptDependencies.defaults();
        assertTrue(defaults.mods().containsAll(List.of("omix", "minecraft", "fabricloader", "fabric-api", "mcef")));
        assertFalse(defaults.mods().contains("sodium"));
        assertFalse(defaults.mods().contains("iris"));
        assertTrue(defaults.libraries().contains("gson-2.13.2.jar"));
        assertFalse(baselineClasspath().isEmpty());
    }

    /** Build a real, restricted development classpath for compiler/example integration tests. */
    static List<Path> baselineClasspath() throws IOException {
        var defaults = ScriptDependencies.defaults();
        String raw = System.getProperty("omix.test.classpath");
        Set<Path> entries = new LinkedHashSet<>(ScriptDependencies.selectLibraries(raw, defaults.libraries()));
        for (String value : raw.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path path = Path.of(value);
            if (Files.isDirectory(path)) { entries.add(path); continue; }
            if (!Files.isRegularFile(path) || !value.endsWith(".jar")) continue;
            try (var jar = new JarFile(path.toFile())) {
                var metadata = jar.getJarEntry("fabric.mod.json");
                if (metadata != null) {
                    try (var reader = new InputStreamReader(jar.getInputStream(metadata), java.nio.charset.StandardCharsets.UTF_8)) {
                        String id = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().get("id").getAsString();
                        if (defaults.mods().contains(id)) entries.add(path);
                    }
                }
                if (jar.getJarEntry("net/minecraft/client/MinecraftClient.class") != null) entries.add(path);
            }
        }
        return List.copyOf(entries);
    }

    private static ModContainer mod(String id, List<ModDependency> dependencies, List<ModContainer> children) {
        var metadata = (ModMetadata) Proxy.newProxyInstance(ModMetadata.class.getClassLoader(), new Class<?>[]{ModMetadata.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getDependencies" -> dependencies;
                    default -> throw new AssertionError("Unexpected metadata access: " + method.getName());
                });
        return (ModContainer) Proxy.newProxyInstance(ModContainer.class.getClassLoader(), new Class<?>[]{ModContainer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMetadata" -> metadata;
                    case "getContainedMods" -> children;
                    default -> throw new AssertionError("Selecting mods must not open their archives: " + method.getName());
                });
    }

    private static ModDependency dependency(String id, ModDependency.Kind kind) {
        return (ModDependency) Proxy.newProxyInstance(ModDependency.class.getClassLoader(), new Class<?>[]{ModDependency.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getModId" -> id;
                    case "getKind" -> kind;
                    default -> throw new AssertionError("Unexpected dependency access: " + method.getName());
                });
    }
}
