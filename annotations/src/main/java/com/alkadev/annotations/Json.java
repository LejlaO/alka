package com.alkadev.annotations;

import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public final class Json {
    private Json() {
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case String s -> quote(out, s);
            case Path p -> quote(out, p.toString());
            case Enum<?> e -> quote(out, e.name());
            case Boolean b -> out.append(b);
            case Double d when d == Math.rint(d) && Math.abs(d) < 1e15 -> out.append((long) d.doubleValue());
            case Number n -> out.append(n);
            case Optional<?> o -> append(out, o.orElse(null));
            case Map<?, ?> m -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) out.append(',');
                    first = false;
                    quote(out, String.valueOf(e.getKey()));
                    out.append(':');
                    append(out, e.getValue());
                }
                out.append('}');
            }
            case Collection<?> c -> {
                out.append('[');
                boolean first = true;
                for (Object item : c) {
                    if (!first) out.append(',');
                    first = false;
                    append(out, item);
                }
                out.append(']');
            }
            case Object[] array -> {
                out.append('[');
                for (int k = 0; k < array.length; k++) {
                    if (k > 0) out.append(',');
                    append(out, array[k]);
                }
                out.append(']');
            }
            default -> {
                if (value.getClass().isRecord()) appendRecord(out, value);
                else quote(out, value.toString());
            }
        }
    }

    private static void appendRecord(StringBuilder out, Object record) {
        out.append('{');
        boolean first = true;
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (!first) out.append(',');
            first = false;
            quote(out, component.getName());
            out.append(':');
            try {
                append(out, component.getAccessor().invoke(record));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        out.append('}');
    }

    private static void quote(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
