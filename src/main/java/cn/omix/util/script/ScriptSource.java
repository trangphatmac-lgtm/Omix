package cn.omix.util.script;

import java.util.*;
import java.util.regex.Pattern;

/** Wraps a Java class body, preserving a mapping for every original source line. */
public record ScriptSource(String className, String source, int[] originalLines) {
    private static final String IMPORTS = "import java.util.*;\nimport java.util.List;\nimport java.awt.Color;\n"
            + "import cn.omix.script.api.*;\nimport cn.omix.module.Category;\nimport cn.omix.module.value.impl.*;\n"
            + "import cn.omix.event.impl.*;\nimport net.minecraft.util.math.*;\n"
            + "import cn.omix.management.rotation.RotationRequest;\nimport cn.omix.management.movement.MovementCorrection;\n";
    public static ScriptSource wrap(String className, String body) {
        if (!className.matches("[A-Za-z_$][A-Za-z0-9_$]*")) throw new IllegalArgumentException("Invalid generated class name");
        body = body.replace("\r\n", "\n").replace('\r', '\n');
        String masked = mask(body);
        if (Pattern.compile("(?m)^\\s*package\\s").matcher(masked).find())
            throw new IllegalArgumentException("Scripts are Java class bodies; omit the package and outer class.");
        var imports = Pattern.compile("(?m)^[ \\t]*import\\s+(?:static\\s+)?[\\w.$*\\s]+;").matcher(masked);
        StringBuilder header = new StringBuilder(IMPORTS);
        List<Integer> map = new ArrayList<>();
        for (int i = 0; i < IMPORTS.lines().count(); i++) map.add(0);
        char[] content = body.toCharArray();
        while (imports.find()) {
            int line = 1 + (int) body.substring(0, imports.start()).chars().filter(c -> c == '\n').count();
            String declaration = body.substring(imports.start(), imports.end());
            header.append(declaration).append('\n');
            for (int i = 0; i <= declaration.chars().filter(c -> c == '\n').count(); i++) map.add(line + i);
            for (int i = imports.start(); i < imports.end(); i++) if (content[i] != '\n') content[i] = ' ';
        }
        header.append("public final class ").append(className).append(" extends ScriptApi {\n")
                .append("public ").append(className).append("(ScriptContext context) { super(context); }\n");
        map.add(0); map.add(0);
        header.append(content).append("\n}\n");
        for (int i = 1; i <= body.split("\n", -1).length; i++) map.add(i);
        map.add(0);
        return new ScriptSource(className, header.toString(), map.stream().mapToInt(Integer::intValue).toArray());
    }
    public int line(long generated) { return generated > 0 && generated <= originalLines.length ? originalLines[(int) generated - 1] : 0; }
    /** Blanks comments/literals without changing offsets. Text blocks and escapes are significant. */
    static String mask(String text) {
        char[] out = text.toCharArray();
        int state = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i), n = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (state == 0) {
                if (c == '/' && n == '/') { out[i] = out[++i] = ' '; state = 1; }
                else if (c == '/' && n == '*') { out[i] = out[++i] = ' '; state = 2; }
                else if (text.startsWith("\"\"\"", i)) { out[i] = out[++i] = out[++i] = ' '; state = 5; }
                else if (c == '"' || c == '\'') { state = c == '"' ? 3 : 4; out[i] = ' '; }
            } else {
                if (c != '\n') out[i] = ' ';
                if (state == 1 && c == '\n') state = 0;
                else if (state == 2 && c == '*' && n == '/') { out[++i] = ' '; state = 0; }
                else if (state >= 3 && c == '\\' && i + 1 < out.length) { if (out[++i] != '\n') out[i] = ' '; }
                else if (state == 5 && text.startsWith("\"\"\"", i)) { out[++i] = out[++i] = ' '; state = 0; }
                else if ((state == 3 && c == '"') || (state == 4 && c == '\'')) state = 0;
            }
        }
        return new String(out);
    }
}
