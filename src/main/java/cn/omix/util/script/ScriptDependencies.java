package cn.omix.util.script;

import com.google.gson.Gson;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** Per-script dependency declarations and the build's baseline compilation dependencies. */
final class ScriptDependencies {
    record Defaults(List<String> mods, Set<String> libraries) {}
    private ScriptDependencies() {}

    static Defaults defaults() throws IOException {
        try (var input = ScriptDependencies.class.getResourceAsStream("/assets/omix/script/dependencies.json")) {
            if (input == null) throw new IOException("Bundled script dependency index missing; rebuild the client.");
            return new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Defaults.class);
        }
    }

    /** Only leading Java comments are directives; strings, method bodies and text blocks are never parsed. */
    static Set<String> parse(String source) throws IOException {
        source = source.replace("\r\n", "\n").replace('\r', '\n');
        Set<String> requested = new TreeSet<>();
        int cursor = 0;
        while (cursor < source.length()) {
            if (Character.isWhitespace(source.charAt(cursor))) { cursor++; continue; }
            if (source.startsWith("/*", cursor)) {
                int end = source.indexOf("*/", cursor + 2);
                if (end < 0) break; // ECJ reports the unfinished comment.
                cursor = end + 2;
            } else if (source.startsWith("//", cursor)) {
                int end = source.indexOf('\n', cursor);
                if (end < 0) end = source.length();
                String comment = source.substring(cursor + 2, end).strip();
                if (comment.equals("@depends") || comment.startsWith("@depends ") || comment.startsWith("@depends\t")) {
                    String ids = comment.substring(8).strip();
                    int line = 1 + (int) source.substring(0, cursor).chars().filter(c -> c == '\n').count();
                    if (ids.isEmpty()) throw new IOException("Empty // @depends declaration at line " + line);
                    for (String id : ids.split("[,\\s]+", -1)) {
                        if (!id.matches("[a-z][a-z0-9_-]{1,63}"))
                            throw new IOException("Invalid Mod ID '" + id + "' in // @depends at line " + line);
                        requested.add(id);
                    }
                }
                cursor = end;
            } else break;
        }
        return Set.copyOf(requested);
    }

    static List<ModContainer> selectMods(Collection<String> baseline, Set<String> requested,
                                          Function<String, Optional<ModContainer>> lookup) throws IOException {
        Deque<String> pending = new ArrayDeque<>();
        // Development-only build dependencies (for example Fabric GameTest) may be absent in a release.
        for (String id : baseline) if (lookup.apply(id).isPresent()) pending.addLast(id);
        pending.addAll(new TreeSet<>(requested));
        Map<String, ModContainer> selected = new LinkedHashMap<>();
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (id.equals("java")) continue; // The JVM provides these classes, not a mod archive.
            ModContainer mod = lookup.apply(id).orElseThrow(() -> new IOException("Script dependency Mod is not loaded: " + id));
            if (selected.putIfAbsent(mod.getMetadata().getId(), mod) != null) continue;
            for (var dependency : mod.getMetadata().getDependencies()) {
                if (dependency.getKind() == ModDependency.Kind.DEPENDS) pending.addLast(dependency.getModId());
            }
            for (var child : mod.getContainedMods()) pending.addLast(child.getMetadata().getId());
        }
        return List.copyOf(selected.values());
    }

    /** Match the build's library filenames before touching files; unrelated launcher entries are ignored. */
    static List<Path> selectLibraries(String runtimeClasspath, Set<String> libraries) throws IOException {
        Set<Path> paths = new LinkedHashSet<>();
        for (String entry : runtimeClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (entry.isBlank()) continue;
            Path path = Path.of(entry);
            if (path.getFileName() != null && libraries.contains(path.getFileName().toString()) && Files.isRegularFile(path))
                paths.add(path.toRealPath());
        }
        return List.copyOf(paths);
    }
}
