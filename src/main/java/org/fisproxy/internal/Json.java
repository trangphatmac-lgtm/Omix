package org.fisproxy.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compact UTF-8 JSON matching Python {@code json.dumps(..., separators=(',', ':'), ensure_ascii=False)}. */
public final class Json {
    private static final int MAX_DEPTH = 64;

    private Json() {
    }

    public static byte[] dumps(Object value) {
        return stringify(value).getBytes(StandardCharsets.UTF_8);
    }

    public static String stringify(Object value) {
        StringBuilder result = new StringBuilder();
        write(value, result, 0);
        return result.toString();
    }

    public static Object parse(String json) {
        return new Parser(json).parse();
    }

    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("JSON response must be an object");
        }
        return asObject(map);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("JSON object key must be a string");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    public static Map<String, Object> freeze(Map<String, ?> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    public static List<Object> asList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return List.copyOf(list);
    }

    private static void write(Object value, StringBuilder output, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("JSON value is too deeply nested");
        }
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            quote(text, output);
        } else if (value instanceof Boolean bool) {
            output.append(bool ? "true" : "false");
        } else if (value instanceof BigDecimal number) {
            output.append(number.toPlainString());
        } else if (value instanceof Double number) {
            if (number.isNaN() || number.isInfinite()) {
                throw new IllegalArgumentException("JSON number must be finite");
            }
            output.append(new BigDecimal(number.toString()).toPlainString());
        } else if (value instanceof Float number) {
            if (number.isNaN() || number.isInfinite()) {
                throw new IllegalArgumentException("JSON number must be finite");
            }
            output.append(new BigDecimal(number.toString()).toPlainString());
        } else if (value instanceof Number) {
            output.append(value);
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                if (!first) {
                    output.append(',');
                }
                first = false;
                quote(key, output);
                output.append(':');
                write(entry.getValue(), output, depth + 1);
            }
            output.append('}');
        } else if (value instanceof Collection<?> collection) {
            output.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                write(item, output, depth + 1);
            }
            output.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
        }
    }

    private static void quote(String value, StringBuilder output) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            this.input = input;
        }

        private Object parse() {
            skipWhitespace();
            Object result = value(0);
            skipWhitespace();
            if (position != input.length()) {
                throw error("Unexpected trailing data");
            }
            return result;
        }

        private Object value(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("JSON nesting is too deep");
            }
            if (position >= input.length()) {
                throw error("Unexpected end of JSON");
            }
            return switch (input.charAt(position)) {
                case '{' -> object(depth + 1);
                case '[' -> array(depth + 1);
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object(int depth) {
            position++;
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (consume('}')) {
                return result;
            }
            while (true) {
                if (position >= input.length() || input.charAt(position) != '"') {
                    throw error("Expected object key");
                }
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                if (result.containsKey(key)) {
                    throw error("Duplicate object key: " + key);
                }
                result.put(key, value(depth));
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private List<Object> array(int depth) {
            position++;
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(value(depth));
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                char character = input.charAt(position++);
                if (character == '"') {
                    return result.toString();
                }
                if (character != '\\') {
                    if (character < 0x20) {
                        throw error("Control character in string");
                    }
                    result.append(character);
                    continue;
                }
                if (position >= input.length()) {
                    throw error("Unfinished escape sequence");
                }
                char escape = input.charAt(position++);
                switch (escape) {
                    case '"', '\\', '/' -> result.append(escape);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(unicodeEscape());
                    default -> throw error("Invalid escape sequence");
                }
            }
            throw error("Unterminated string");
        }

        private char unicodeEscape() {
            if (position + 4 > input.length()) {
                throw error("Invalid unicode escape");
            }
            try {
                char result = (char) Integer.parseInt(input.substring(position, position + 4), 16);
                position += 4;
                return result;
            } catch (NumberFormatException exception) {
                throw error("Invalid unicode escape");
            }
        }

        private Object number() {
            int start = position;
            if (consume('-') && position >= input.length()) {
                throw error("Invalid number");
            }
            if (consume('0')) {
                if (position < input.length() && Character.isDigit(input.charAt(position))) {
                    throw error("Leading zero in number");
                }
            } else {
                digits();
            }
            boolean fractional = false;
            if (consume('.')) {
                fractional = true;
                digits();
            }
            if (position < input.length() && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
                fractional = true;
                position++;
                if (position < input.length() && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                    position++;
                }
                digits();
            }
            String text = input.substring(start, position);
            try {
                if (!fractional) {
                    try {
                        return Long.parseLong(text);
                    } catch (NumberFormatException ignored) {
                        return new BigDecimal(text);
                    }
                }
                return new BigDecimal(text);
            } catch (NumberFormatException exception) {
                throw error("Invalid number");
            }
        }

        private void digits() {
            int start = position;
            while (position < input.length() && Character.isDigit(input.charAt(position))) {
                position++;
            }
            if (start == position) {
                throw error("Expected digit");
            }
        }

        private Object literal(String text, Object value) {
            if (!input.startsWith(text, position)) {
                throw error("Invalid JSON literal");
            }
            position += text.length();
            return value;
        }

        private boolean consume(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private void skipWhitespace() {
            while (position < input.length()) {
                char character = input.charAt(position);
                if (character != ' ' && character != '\n' && character != '\r' && character != '\t') {
                    return;
                }
                position++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at JSON offset " + position);
        }
    }
}

