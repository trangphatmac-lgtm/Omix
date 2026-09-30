package cn.omix.util.skeet;

/** Unicode-safe, single-line editing state, also used for sensitive values without exposing their text. */
public final class SkeetTextBuffer {
    private String value = "";
    private int cursor, anchor;
    private final int limit;

    public SkeetTextBuffer(int limit) { this.limit = limit; }
    public String value() { return value; }
    public int cursor() { return cursor; }
    public int anchor() { return anchor; }
    public void set(String text) { value = text == null ? "" : text; cursor = anchor = value.length(); }
    public void selectAll() { anchor = 0; cursor = value.length(); }
    public String selection() { return value.substring(Math.min(cursor, anchor), Math.max(cursor, anchor)); }
    public void move(int direction, boolean selecting) {
        if (!selecting && cursor != anchor) cursor = direction < 0 ? Math.min(cursor, anchor) : Math.max(cursor, anchor);
        else if (direction < 0 && cursor > 0 || direction > 0 && cursor < value.length())
            cursor = value.offsetByCodePoints(cursor, direction);
        if (!selecting) anchor = cursor;
    }
    public void home(boolean selecting) { cursor = 0; if (!selecting) anchor = cursor; }
    public void end(boolean selecting) { cursor = value.length(); if (!selecting) anchor = cursor; }
    public void erase(boolean backwards) {
        if (cursor == anchor) {
            if (backwards && cursor > 0) anchor = value.offsetByCodePoints(cursor, -1);
            if (!backwards && cursor < value.length()) anchor = value.offsetByCodePoints(cursor, 1);
        }
        replace("");
    }
    public void replace(String text) {
        String clean = text.replaceAll("[\\p{Cntrl}§]", "");
        int start = Math.min(cursor, anchor), end = Math.max(cursor, anchor);
        int length = Math.min(clean.length(), Math.max(0, limit - value.length() + end - start));
        if (length > 0 && Character.isHighSurrogate(clean.charAt(length - 1))) length--;
        value = value.substring(0, start) + clean.substring(0, length) + value.substring(end);
        cursor = anchor = start + length;
    }
}
