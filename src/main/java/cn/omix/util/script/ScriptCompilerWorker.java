package cn.omix.util.script;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Standalone Java entry point: no game initialization or script execution in this process. */
public final class ScriptCompilerWorker {
    record Request(String cache, ScriptClasspath.Environment environment, String id, long generation, String body, int leadingLines) {}
    record Result(ScriptSource source, String jar, List<ScriptCompiler.Problem> diagnostics, String error) {}
    private ScriptCompilerWorker() {}

    public static void main(String[] args) throws Exception {
        Gson gson = new Gson();
        Path request = Path.of(args[0]), response = Path.of(args[1]), progress = Path.of(args[2]);
        Result result;
        try {
            Request data = gson.fromJson(Files.readString(request), Request.class);
            Path cache = Path.of(data.cache());
            var compiler = new ScriptCompiler(cache, new ScriptClasspath(cache.resolve("classpath"), data.environment()));
            var compiled = compiler.compileLocal(data.id(), data.generation(), data.body(), data.leadingLines(), phase -> {
                try { ScriptFiles.atomicWrite(progress, phase); }
                catch (java.io.IOException ignored) {
                    // Progress is advisory. Windows can deny replacement while the
                    // parent reads phase.txt; this must not abort the compilation.
                }
            });
            result = new Result(compiled.source(), compiled.jar().toString(), compiled.diagnostics(), null);
        } catch (Throwable error) {
            error.printStackTrace();
            var diagnostics = error instanceof ScriptCompiler.CompileFailure failure ? failure.diagnostics : List.<ScriptCompiler.Problem>of();
            result = new Result(null, null, diagnostics, error.toString());
        }
        ScriptFiles.atomicWrite(response, gson.toJson(result));
    }
}
