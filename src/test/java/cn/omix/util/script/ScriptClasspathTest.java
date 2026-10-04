package cn.omix.util.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptClasspathTest {
    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(strings = {"plain/dependency.jar", "Release 2.2.11/dependency.jar", "100%/dependency.jar",
            "literal%20space/dependency.jar", "mods#1/dependency.jar", "中文目录/dependency.jar", "mods+extra/dependency.jar",
            "[mods]/dependency.jar", "mods/[钠] sodium-fabric-0.8.12+mc1.21.11.jar",
            "mods/[钠 · 扩展] sodium-extra-fabric-0.8.3+mc1.21.11.jar",
            "[mods space]/dependency.jar", "[%20 #+]/[%25 #+] dependency.jar",
            "Release 2.2.11/.minecraft/versions/1.21.11-Fabric/mods/[钠] sodium-fabric-0.8.12+mc1.21.11.jar",
            "[目录] 100%20 #+!/mods/[钠] 100%25 #+!.jar"})
    void archiveRootsReusePhysicalJarsWithoutDecodingTheirUriTwice(String relativePath) throws Exception {
        Path archive = temp.resolve(relativePath);
        Files.createDirectories(archive.getParent());
        try (var jar = new JarOutputStream(Files.newOutputStream(archive))) {
            jar.putNextEntry(new JarEntry("sample/Value.class")); jar.closeEntry();
        }
        Path cache = temp.resolve("cache");
        var classpath = new ScriptClasspath(cache);
        try (var zip = FileSystems.newFileSystem(archive)) {
            assertEquals(archive.toRealPath(), classpath.materialize(zip.getPath("/")));
        }
        assertFalse(Files.exists(cache), "Physical JARs should not need an extracted copy");
    }

    @Test void archiveSubdirectoriesStillExtractOnlyTheirOwnClassesAndManifest() throws Exception {
        Path archive = temp.resolve("dependency with spaces.jar");
        Map<String, String> entries = Map.of("classes/sample/Value.class", "bytecode",
                "classes/META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n\n",
                "classes/assets/ignored.txt", "resource", "other/Outside.class", "outside");
        try (var jar = new JarOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)); jar.closeEntry();
            }
        }
        Path cache = Files.createDirectories(temp.resolve("cache"));
        var classpath = new ScriptClasspath(cache);
        Path materialized;
        try (var zip = FileSystems.newFileSystem(archive)) {
            materialized = classpath.materialize(zip.getPath("/classes"));
            assertEquals(materialized, classpath.materialize(zip.getPath("/classes")));
        }
        assertNotEquals(archive, materialized);
        try (var jar = new JarFile(materialized.toFile())) {
            assertEquals(Set.of("sample/Value.class", "META-INF/MANIFEST.MF"),
                    new HashSet<>(jar.stream().map(JarEntry::getName).toList()));
            assertArrayEquals("bytecode".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    jar.getInputStream(jar.getJarEntry("sample/Value.class")).readAllBytes());
        }
    }

    @Test void nestedArchiveRootsAreExtractedInsteadOfUsedAsPhysicalFiles() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry("sample/Value.class")); jar.write(new byte[]{1, 2, 3}); jar.closeEntry();
        }
        Path outer = temp.resolve("[外层] 100%20 #+!.jar");
        try (var zip = FileSystems.newFileSystem(outer, Map.of("create", "true"))) {
            Files.write(zip.getPath("/[内层] dependency.jar"), bytes.toByteArray());
        }
        Path cache = Files.createDirectories(temp.resolve("cache"));
        try (var zip = FileSystems.newFileSystem(outer);
             var nested = FileSystems.newFileSystem(zip.getPath("/[内层] dependency.jar"))) {
            Path extracted = new ScriptClasspath(cache).materialize(nested.getPath("/"));
            assertEquals(cache, extracted.getParent());
            try (var jar = new JarFile(extracted.toFile())) {
                assertArrayEquals(new byte[]{1, 2, 3}, jar.getInputStream(jar.getJarEntry("sample/Value.class")).readAllBytes());
            }
        }
    }
}
