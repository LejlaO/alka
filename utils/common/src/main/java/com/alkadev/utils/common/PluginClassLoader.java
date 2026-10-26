package com.alkadev.utils.common;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** Loads a plugin jar and its own libraries before the application classpath; the engine API stays shared. */
public class PluginClassLoader extends URLClassLoader {
    private static final String[] SHARED = {"java.", "javax.", "jdk.", "sun.", "com.alkadev.utils.", "org.apache.logging.log4j."};

    private final Path mainJar;

    PluginClassLoader(URL[] urls, ClassLoader parent, Path mainJar) {
        super(urls, parent);
        this.mainJar = mainJar;
    }

    public Manifest getManifest() throws IOException {
        try (JarFile jar = new JarFile(mainJar.toFile())) {
            return jar.getManifest();
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (isShared(name)) return super.loadClass(name, resolve);
                try {
                    c = findClass(name);
                } catch (ClassNotFoundException e) {
                    return super.loadClass(name, resolve);
                }
            }
            if (resolve) resolveClass(c);
            return c;
        }
    }

    private static boolean isShared(String name) {
        for (String prefix : SHARED)
            if (name.startsWith(prefix)) return true;
        return false;
    }
}
