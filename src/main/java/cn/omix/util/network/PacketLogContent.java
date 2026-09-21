package cn.omix.util.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded read-only field snapshots, including packet types without a hand-written summary. */
public final class PacketLogContent {
    public static final int MAX_CHARS = 2048;
    private static final int MAX_DEPTH = 4;
    private static final int MAX_ITEMS = 8;
    private static final int MAX_FIELDS = 16;
    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> result = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) result.add(field);
                    // One extra field lets the renderer explicitly mark truncation.
                    if (result.size() > MAX_FIELDS) return List.copyOf(result);
                }
            }
            return List.copyOf(result);
        }
    };

    private final StringBuilder out = new StringBuilder();
    private final IdentityHashMap<Object, Boolean> path = new IdentityHashMap<>();

    private PacketLogContent() {}

    public static String snapshot(Object value) {
        PacketLogContent writer = new PacketLogContent();
        writer.write(value, 0);
        String result = writer.out.toString();
        return result.length() == MAX_CHARS ? result.substring(0, MAX_CHARS - 1) + "…" : result;
    }

    private void append(String text) {
        if (out.length() >= MAX_CHARS) return;
        int length = Math.min(text.length(), MAX_CHARS - out.length());
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            out.append(Character.isISOControl(c) || c == '§' ? ' ' : c);
        }
    }

    private void string(String value) {
        append("\"");
        append(value.substring(0, Math.min(value.length(), 256)));
        if (value.length() > 256) append("…");
        append("\"");
    }

    private void write(Object value, int depth) {
        if (out.length() >= MAX_CHARS) return;
        if (value == null) { append("null"); return; }
        if (value instanceof String s) { string(s); return; }
        if (value instanceof Text text) { string(text.getString()); return; }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character
                || value instanceof UUID || value instanceof Identifier) { append(value.toString()); return; }
        if (value instanceof Enum<?> e) { append(e.name()); return; }
        if (depth >= MAX_DEPTH) { append("<max depth>"); return; }
        if (path.put(value, Boolean.TRUE) != null) { append("<cycle>"); return; }
        try {
            if (value instanceof ByteBuf bytes) {
                append("bytes[" + bytes.readableBytes() + "]:");
                for (int i = 0; i < Math.min(bytes.readableBytes(), 32); i++) {
                    int b = bytes.getUnsignedByte(bytes.readerIndex() + i);
                    append("0123456789abcdef".substring(b >>> 4, (b >>> 4) + 1));
                    append("0123456789abcdef".substring(b & 15, (b & 15) + 1));
                }
                if (bytes.readableBytes() > 32) append("…");
            } else if (value instanceof Optional<?> optional) {
                write(optional.orElse(null), depth + 1);
            } else if (value instanceof Map<?, ?> map) {
                append("{");
                int count = 0;
                for (var entry : map.entrySet()) {
                    if (count > 0) append(", ");
                    if (count++ == MAX_ITEMS || out.length() >= MAX_CHARS) { append("…"); break; }
                    write(entry.getKey(), depth + 1);
                    append("=");
                    write(entry.getValue(), depth + 1);
                }
                append("}");
            } else if (value instanceof Iterable<?> iterable) {
                append("[");
                int count = 0;
                for (Object item : iterable) {
                    if (count > 0) append(", ");
                    if (count++ == MAX_ITEMS || out.length() >= MAX_CHARS) { append("…"); break; }
                    write(item, depth + 1);
                }
                append("]");
            } else if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                append("[");
                for (int i = 0; i < Math.min(length, MAX_ITEMS); i++) {
                    if (i > 0) append(", ");
                    write(Array.get(value, i), depth + 1);
                }
                if (length > MAX_ITEMS) append(", … (" + length + " items)");
                append("]");
            } else {
                append("{");
                List<Field> fields = FIELDS.get(value.getClass());
                for (int i = 0; i < Math.min(fields.size(), MAX_FIELDS) && out.length() < MAX_CHARS; i++) {
                    if (i > 0) append(", ");
                    Field field = fields.get(i);
                    append(field.getName() + "=");
                    try {
                        if (field.trySetAccessible()) write(field.get(value), depth + 1);
                        else append("<unavailable>");
                    } catch (ReflectiveOperationException | RuntimeException exception) {
                        append("<unavailable>");
                    }
                }
                if (fields.size() > MAX_FIELDS) append(", …");
                append("}");
            }
        } catch (RuntimeException exception) {
            append("<unavailable>");
        } finally {
            path.remove(value);
        }
    }
}
