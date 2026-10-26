package com.alkadev.annotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The generated, documented settings file of a plugin, manager or service: the format the {@link SettingsProcessor} writes at compile time,
 * and the rules about it that the engine and the Studio both apply (what is required, what a value may be).
 */
public final class SettingsTemplate {
    /** Where a jar carries its template. */
    public static final String EMBEDDED = "META-INF/alka/settings.properties";

    public record Param(String key, String description, String type, String defaultValue, List<String> options, String min, String max, boolean required) {
    }

    /** apiVersion, managerType (a manager) and usesManagerTypes (a plugin) are "" or empty when the template does not say. */
    public record Template(String component, String kind, String description, String source, List<Param> params, String apiVersion, String managerType, List<String> usesManagerTypes) {
    }

    private SettingsTemplate() {
    }

    public static Template parse(String text, String source) {
        Map<String, String> header = new LinkedHashMap<>();
        List<Param> params = new ArrayList<>();
        Map<String, String> current = null;
        String currentKey = null;

        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("## @param ")) {
                add(params, currentKey, current);
                currentKey = line.substring("## @param ".length()).trim();
                current = new LinkedHashMap<>();
            } else if (line.startsWith("## ") && current != null) {
                int colon = line.indexOf(':');
                if (colon > 3) current.put(line.substring(3, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
            } else if (line.isEmpty() && current != null) {
                add(params, currentKey, current);
                current = null;
                currentKey = null;
            } else if (current == null && line.startsWith("# ") && !line.startsWith("## ")) {
                int colon = line.indexOf(':');
                if (colon > 2) header.put(line.substring(2, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
            }
        }
        add(params, currentKey, current);
        List<String> uses = new ArrayList<>();
        for (String type : header.getOrDefault("uses-manager-types", "").split(",")) if (!type.isBlank()) uses.add(type.trim().toUpperCase());
        return new Template(header.getOrDefault("component", ""), header.getOrDefault("kind", ""), header.getOrDefault("description", ""), source, params,
                header.getOrDefault("api-version", ""), header.getOrDefault("manager-type", "").trim().toUpperCase(), uses);
    }

    private static void add(List<Param> params, String key, Map<String, String> attrs) {
        if (key == null || attrs == null || key.isEmpty()) return;
        String type = attrs.getOrDefault("type", "string");
        if (!List.of("string", "number", "list", "boolean").contains(type)) type = "string";
        List<String> options = new ArrayList<>();
        String listed = attrs.get("options");
        if (listed != null) for (String o : Arrays.asList(listed.split(","))) if (!o.trim().isEmpty()) options.add(o.trim());
        params.add(new Param(key, attrs.getOrDefault("description", ""), type, attrs.getOrDefault("default", ""), options, attrs.getOrDefault("min", ""), attrs.getOrDefault("max", ""), attrs.getOrDefault("required", "").equalsIgnoreCase("true")));
    }

    /** The setting with this key, or null. */
    public static Param find(Template template, String key) {
        if (template != null) for (Param p : template.params()) if (p.key().equals(key)) return p;
        return null;
    }

    /** The whole number the value says when it is one inside the range of the setting, otherwise the default of the setting. */
    public static int intOrDefault(Param p, String value) {
        try {
            int number = Integer.parseInt(value.trim());
            if ((p.min().isBlank() || number >= Integer.parseInt(p.min())) && (p.max().isBlank() || number <= Integer.parseInt(p.max()))) return number;
        } catch (NumberFormatException ignored) {
        }
        return Integer.parseInt(p.defaultValue().isBlank() ? "0" : p.defaultValue().trim());
    }

    /**
     * The required settings that have no value.
     *
     * @param valueOf the value a key has, or null when it has none; a blank value counts as none
     */
    public static List<String> missingRequired(Template template, Function<String, String> valueOf) {
        List<String> missing = new ArrayList<>();
        if (template == null) return missing;
        for (Param p : template.params()) {
            if (!p.required()) continue;
            String value = valueOf.apply(p.key());
            if (value == null || value.isBlank()) missing.add(p.key());
        }
        return missing;
    }

    /** What is wrong with a value for a setting (not a number, outside its range, not one of the options, ...), or null when it is fine. */
    public static String validate(Param p, String value) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) return p.key() + ": a value cannot contain a line break.";
        if (value.length() > 4000) return p.key() + ": the value is too long.";
        switch (p.type()) {
            case "number" -> {
                double number;
                try {
                    number = Double.parseDouble(value.trim());
                } catch (NumberFormatException e) {
                    return p.key() + ": '" + value + "' is not a number.";
                }
                if (Double.isNaN(number) || Double.isInfinite(number)) return p.key() + ": '" + value + "' is not a usable number.";
                if (!p.min().isBlank() && number < Double.parseDouble(p.min())) return p.key() + ": must be at least " + p.min() + ".";
                if (!p.max().isBlank() && number > Double.parseDouble(p.max())) return p.key() + ": must be at most " + p.max() + ".";
            }
            case "list" -> {
                if (!p.options().contains(value)) return p.key() + ": '" + value + "' is not one of " + String.join(", ", p.options()) + ".";
            }
            case "boolean" -> {
                if (!value.equals("true") && !value.equals("false")) return p.key() + ": must be true or false.";
            }
            default -> {
            }
        }
        return null;
    }
}
