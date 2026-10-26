package com.alkadev.utils.common;

import com.alkadev.annotations.Names;
import com.alkadev.annotations.SettingsTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Loads a plugin or manager from its jar: the checks, the settings layers, the class loader and the instance. One place for both kinds. */
public final class PluginLoader {
    private static final Logger logger = LogManager.getLogger(PluginLoader.class);

    // one class loader per jar file name, shared by the instances made from that jar
    private static final ConcurrentHashMap<String, PluginClassLoader> shared = new ConcurrentHashMap<>();

    private PluginLoader() {
    }

    /**
     * @param base            ObjectPlugin or ObjectManager: what the class in the jar must extend
     * @param kind            "Plugin" or "Manager" (log lines)
     * @param subfolder       "plugins" or "managers": where the settings files of this kind are
     * @param serviceSettings the service config on top of the common settings; the instance's files are layered over it
     * @return the instance, or null when it could not be loaded (the reason is in the log)
     */
    public static <T extends AlkaComponent> T load(Class<T> base, String kind, String subfolder, Properties serviceSettings, String configFolder,
                                                   String commonConfigFolder, String pluginFolder, String name, String jarName) {
        String lower = kind.toLowerCase();
        if (!Names.isName(name)) {
            logger.fatal(kind + " loading failed [" + name + "]: an instance name may only contain letters, digits, '_' and '-'.");
            return null;
        }
        if (!Names.isJar(jarName)) {
            logger.fatal(kind + " loading failed [" + name + "]: '" + jarName + "' is not a plain jar file name.");
            return null;
        }

        try {
            logger.info("Loading jar file : " + jarName);
            File file = new File(pluginFolder + "/" + jarName);
            if (!file.exists()) {
                logger.warn("Jar file not loaded : " + jarName + ". Skipping " + lower + ".");
                return null;
            }

            logger.info("Loading " + lower + ": " + name);
            // settings precedence, low to high: service config, common/<kind>s/<name>, <service>/<kind>s/<name>
            String commonFile = commonConfigFolder + "/" + subfolder + "/" + name + ".properties";
            String serviceFile = configFolder + "/" + subfolder + "/" + name + ".properties";
            Properties settings = ConfigLayers.mergeFile(ConfigLayers.mergeFile(serviceSettings, commonFile), serviceFile);
            logger.info(kind + " [" + name + "] settings: " + ConfigLayers.describeFile(commonFile) + "; " + ConfigLayers.describeFile(serviceFile));
            settings.setProperty("sourceName", name);

            // the settings the jar says are required must have a value, or the instance is not started; a value that does not fit its setting is reported
            SettingsTemplate.Template template = templateOf(file, jarName);
            if (template != null) {
                List<String> missing = SettingsTemplate.missingRequired(template, settings::getProperty);
                if (!missing.isEmpty()) {
                    logger.fatal(kind + " loading failed [" + name + "]. Required setting" + (missing.size() > 1 ? "s" : "") + " not set: " + String.join(", ", missing)
                            + " (set " + (missing.size() > 1 ? "them" : "it") + " in " + serviceFile + " or " + commonFile + ").");
                    return null;
                }
                for (SettingsTemplate.Param p : template.params()) {
                    String value = settings.getProperty(p.key());
                    String problem = value == null || value.isBlank() ? null : SettingsTemplate.validate(p, value);
                    if (problem != null) logger.warn(kind + " [" + name + "] setting " + problem);
                }
            }

            PluginClassLoader loader = shared.get(jarName);
            if (loader == null) {
                loader = PluginJars.open(file, base.getClassLoader());
                shared.put(jarName, loader);
            }

            Manifest manifest = loader.getManifest();
            if (manifest == null) {
                logger.fatal(kind + " loading failed [" + name + "]. No META-INF/MANIFEST.MF found in " + jarName + ".");
                return null;
            }
            Attributes attributes = manifest.getMainAttributes();
            String className = attributes.getValue("classname");
            if (className == null) {
                logger.fatal(kind + " loading failed [" + name + "]. The manifest of " + jarName + " has no classname entry.");
                return null;
            }
            String apiProblem = PluginJars.apiProblem(attributes);
            if (apiProblem != null) {
                logger.fatal(kind + " loading failed [" + name + "]. " + jarName + " is " + apiProblem + ".");
                return null;
            }
            // the API version is in the manifest, and in the settings template that the jar carries; either one is enough
            String templateApi = template == null ? "" : template.apiVersion();
            if (attributes.getValue("alka-api-version") == null && templateApi.isBlank())
                logger.warn(kind + " [" + name + "]: " + jarName + " declares no alka-api-version, loading anyway.");
            else if (attributes.getValue("alka-api-version") == null && !templateApi.trim().equals(String.valueOf(Names.API_VERSION))) {
                logger.fatal(kind + " loading failed [" + name + "]. " + jarName + " is built for Alka API '" + templateApi + "' (its settings template says so), this engine is API " + Names.API_VERSION + ".");
                return null;
            }

            Class<?> clas = loader.loadClass(className);
            if (clas.getClassLoader() != loader) {
                logger.fatal(kind + " loading failed [" + name + "]. " + className + " is not part of " + jarName + " itself.");
                return null;
            }
            if (!base.isAssignableFrom(clas)) {
                logger.fatal(kind + " loading failed [" + name + "]. " + className + " does not extend " + base.getSimpleName() + ".");
                return null;
            }

            T instance = base.cast(clas.getDeclaredConstructor().newInstance());
            instance.attach(settings, name);
            if (template != null) instance.declare(template.managerType(), template.usesManagerTypes());
            logger.info("Loaded " + lower + " [" + name + "] from " + jarName + " (" + PluginJars.describe(attributes) + ").");
            return instance;
        } catch (IOException | ReflectiveOperationException | RuntimeException | LinkageError e) {
            // LinkageError is what a jar built for a newer Java, a missing class or a failing static initializer throws: one bad jar must not end the service
            logger.fatal(kind + " loading failed [" + name + "]." + e.getClass().getSimpleName() + " encountered.", e);
            return null;
        }
    }

    // the template that the jar carries (read from the jar itself: the class loader would also find the one of the engine)
    private static SettingsTemplate.Template templateOf(File jar, String jarName) {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry(SettingsTemplate.EMBEDDED);
            if (entry == null) return null;
            try (InputStream in = zip.getInputStream(entry)) {
                return SettingsTemplate.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "inside " + jarName);
            }
        } catch (IOException e) {
            return null;
        }
    }

    /** Closes every shared class loader (when the service stops). */
    public static void closeAll() {
        for (PluginClassLoader loader : shared.values()) {
            try {
                loader.close();
            } catch (IOException ignored) {
            }
        }
        shared.clear();
    }
}
