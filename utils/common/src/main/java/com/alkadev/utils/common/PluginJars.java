package com.alkadev.utils.common;

import com.alkadev.annotations.Names;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;

public final class PluginJars {
    private PluginJars() {
    }

    // the jar and the libraries of the plugin (the jars in <jar name without .jar>.lib/) are loaded where they are, not from a copy:
    // a jar that a running service uses cannot be replaced until the service stops (Windows locks it)
    public static PluginClassLoader open(File jar, ClassLoader parent) throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(jar.toURI().toURL());

        File libFolder = new File(jar.getParentFile(), jar.getName().substring(0, jar.getName().length() - 4) + ".lib");
        File[] libs = libFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (libs != null) {
            Arrays.sort(libs);
            for (File lib : libs)
                urls.add(lib.toURI().toURL());
        }
        return new PluginClassLoader(urls.toArray(new URL[0]), parent, jar.toPath());
    }

    // null = compatible; a jar without the attribute is accepted and the caller warns
    public static String apiProblem(Attributes attributes) {
        String declared = attributes.getValue("alka-api-version");
        if (declared == null) return null;
        try {
            if (Integer.parseInt(declared.trim()) == Names.API_VERSION) return null;
        } catch (NumberFormatException ignored) {
        }
        return "built for Alka API '" + declared + "', this engine is API " + Names.API_VERSION;
    }

    public static String describe(Attributes attributes) {
        StringBuilder text = new StringBuilder();
        for (String key : new String[]{"Implementation-Title", "Implementation-Version", "Implementation-Vendor"}) {
            String value = attributes.getValue(key);
            if (value != null) text.append(text.length() == 0 ? "" : ", ").append(value);
        }
        return text.length() == 0 ? "no metadata" : text.toString();
    }
}
