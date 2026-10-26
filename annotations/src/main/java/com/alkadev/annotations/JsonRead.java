package com.alkadev.annotations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads JSON text into Map, List, String, Double, Boolean and null: enough for the files the engine writes. */
public final class JsonRead {
    private static final int MAX_DEPTH = 64;
    private final String s;
    private int i;
    private int depth;

    private JsonRead(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        JsonRead r = new JsonRead(text);
        try {
            r.space();
            Object value = r.value();
            r.space();
            if (r.i != r.s.length()) throw new IllegalArgumentException("JSON: text after the value at " + r.i);
            return value;
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("JSON: the text ends too early");
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        Object value = parse(text);
        if (value instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new IllegalArgumentException("JSON: an object was expected");
    }

    public static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }

    public static String string(Object value) {
        return value instanceof String text ? text : null;
    }

    private void space() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        if (i >= s.length()) throw new IllegalArgumentException("JSON: the text ends too early");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        if (s.startsWith("null", i)) {
            i += 4;
            return null;
        }
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw new IllegalArgumentException("JSON: unexpected character '" + c + "' at " + i);
        return Double.valueOf(s.substring(start, i));
    }

    private void deeper() {
        if (++depth > MAX_DEPTH) throw new IllegalArgumentException("JSON: nested more than " + MAX_DEPTH + " levels");
    }

    private Map<String, Object> object() {
        Map<String, Object> out = new LinkedHashMap<>();
        deeper();
        i++;
        space();
        if (s.charAt(i) == '}') {
            i++;
            depth--;
            return out;
        }
        while (true) {
            space();
            String key = string();
            space();
            expect(':');
            space();
            out.put(key, value());
            space();
            if (s.charAt(i) == ',') {
                i++;
                continue;
            }
            expect('}');
            depth--;
            return out;
        }
    }

    private List<Object> array() {
        List<Object> out = new ArrayList<>();
        deeper();
        i++;
        space();
        if (s.charAt(i) == ']') {
            i++;
            depth--;
            return out;
        }
        while (true) {
            space();
            out.add(value());
            space();
            if (s.charAt(i) == ',') {
                i++;
                continue;
            }
            expect(']');
            depth--;
            return out;
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw new IllegalArgumentException("JSON: a string is not closed");
            char c = s.charAt(i++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = s.charAt(i++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> out.append(e);
            }
        }
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c) throw new IllegalArgumentException("JSON: '" + c + "' was expected at " + i);
        i++;
    }
}
