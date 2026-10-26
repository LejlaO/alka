package com.alkadev.annotations;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** The names the engine and the Studio both go by: the keys of a service config, what a name may be made of, and the lists written with commas. */
public final class Names {
    public static final String PREFIX = "alkaprocessor.";
    public static final String MANAGER = PREFIX + "manager.";
    public static final String PLUGIN = PREFIX + "plugin.";
    public static final String MAP = PREFIX + "map.";
    public static final String ADDMANAGER = PREFIX + "addmanager.";
    /** The version of the plugin and manager API: a jar says which one it was built for (alka-api-version in its manifest) and the engine refuses another. */
    public static final int API_VERSION = 1;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern JAR = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*\\.jar");

    private Names() {
    }

    /** A service, a plugin or a manager: letters, digits, underscore and hyphen. */
    public static boolean isName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** A plain jar file name, not a path. */
    public static boolean isJar(String name) {
        return name != null && JAR.matcher(name).matches() && !name.contains("..");
    }

    /** "a, b,,c" gives a, b, c. */
    public static List<String> split(String commaSeparated) {
        List<String> names = new ArrayList<>();
        if (commaSeparated == null) return names;
        for (String part : commaSeparated.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    public static String join(List<String> names) {
        return String.join(",", names);
    }
}
