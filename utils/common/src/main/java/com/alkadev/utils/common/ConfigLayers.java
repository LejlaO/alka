package com.alkadev.utils.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Settings are layers: the common file, the service config on top of it, then the settings file of one instance on top of that.
 * A layer is a plain {@link Properties} object that is handed down; nothing is kept in a global place.
 */
public final class ConfigLayers {
    private static final Logger logger = LogManager.getLogger(ConfigLayers.class);

    private ConfigLayers() {
    }

    /** The file as it is; throws when it is missing or cannot be read. */
    public static Properties read(String file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = com.alkadev.annotations.PropertyFiles.open(Path.of(file))) {
            properties.load(in);
        }
        return properties;
    }

    /** A new object with the parent's keys and then the child's, which win. Either may be null. */
    public static Properties merge(Properties parent, Properties child) {
        Properties merged = new Properties();
        if (parent != null) merged.putAll(parent);
        if (child != null) merged.putAll(child);
        return merged;
    }

    /** The parent with the file on top. A missing file adds nothing; a file that exists but cannot be read is reported and adds nothing. */
    public static Properties mergeFile(Properties parent, String file) {
        Properties merged = merge(parent, null);
        if (Files.isRegularFile(Path.of(file))) {
            try {
                merged.putAll(read(file));
            } catch (Exception e) {
                logger.error("Property file '" + file + "' exists but cannot be read, its settings are ignored: " + e);
            }
        }
        return merged;
    }

    public static String describeFile(String file) {
        return (Files.isRegularFile(Path.of(file)) ? "found " : "absent ") + file;
    }

    /** The keys that start with the prefix (it ends in a dot), by the name after it, in name order. A key that is only the prefix is skipped. */
    public static Map<String, String> keysWithPrefix(String prefix, Properties properties) {
        Map<String, String> found = new TreeMap<>();
        for (String key : properties.stringPropertyNames())
            if (key.startsWith(prefix) && key.length() > prefix.length()) found.put(key.substring(prefix.length()), properties.getProperty(key));
        return found;
    }
}
