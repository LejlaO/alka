package com.alkadev.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Writes the drop folder of a plugin or a manager under target/alka. It has the layout of an Alka home, so it can be copied into one:
 * <pre>
 * libs/plugin/x.jar              the jar
 * libs/plugin/x.lib/*.jar        the libraries of the plugin that the engine does not have (only when there are any)
 * config/templates/x.properties  the settings template that is inside the jar (only when the jar has one)
 * </pre>
 */
@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class PackageMojo extends AbstractMojo {
    /** where the annotation processor of Alka writes the template inside the jar */
    static final String EMBEDDED_TEMPLATE = "META-INF/alka/settings.properties";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/alka")
    private File dropFolder;

    /** libraries of these groups are never put in the .lib folder: the engine has them (the API, log4j) */
    @Parameter
    private List<String> excludeGroupIds;

    @Parameter(property = "alka.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Alka drop folder skipped (alka.skip).");
            return;
        }
        if (!"jar".equals(project.getPackaging())) {
            getLog().debug("Not a jar project (" + project.getPackaging() + "): no drop folder.");
            return;
        }
        File jar = builtJar();
        String jarName = jar.getName();
        String base = jarName.endsWith(".jar") ? jarName.substring(0, jarName.length() - 4) : jarName;
        Path drop = dropFolder.toPath();
        try {
            deleteTree(drop);
            Path pluginFolder = Files.createDirectories(drop.resolve("libs").resolve("plugin"));
            Files.copy(jar.toPath(), pluginFolder.resolve(jarName), StandardCopyOption.REPLACE_EXISTING);

            List<String> libs = new ArrayList<>();
            Path libFolder = pluginFolder.resolve(base + ".lib");
            for (Artifact artifact : sorted(project.getArtifacts())) {
                if (!wanted(artifact)) continue;
                Files.createDirectories(libFolder);
                Files.copy(artifact.getFile().toPath(), libFolder.resolve(artifact.getFile().getName()), StandardCopyOption.REPLACE_EXISTING);
                libs.add(artifact.getFile().getName());
            }

            boolean template = copyTemplate(jar, drop.resolve("config").resolve("templates").resolve(base + ".properties"));
            getLog().info("Alka drop folder: " + drop + " (" + jarName + ", " + (libs.isEmpty() ? "no libraries" : libs.size() + " libraries in " + base + ".lib") + ", "
                    + (template ? "settings template" : "no settings template") + ")");
            if (!template)
                getLog().warn(jarName + " has no settings template: the class has no @AlkaSettings, so the Studio shows no form for it.");
        } catch (IOException e) {
            throw new MojoExecutionException("Could not write the drop folder " + drop + ": " + e.getMessage(), e);
        }
    }

    private File builtJar() throws MojoExecutionException {
        File jar = project.getArtifact() == null ? null : project.getArtifact().getFile();
        if (jar == null || !jar.isFile()) jar = new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".jar");
        if (!jar.isFile()) throw new MojoExecutionException("There is no jar to put in the drop folder (" + jar + "): run it together with the package phase, for example mvn package.");
        return jar;
    }

    private boolean wanted(Artifact artifact) {
        if (artifact.getFile() == null || !artifact.getFile().isFile() || !"jar".equals(artifact.getType())) return false;
        String scope = artifact.getScope();
        if (!Artifact.SCOPE_COMPILE.equals(scope) && !Artifact.SCOPE_RUNTIME.equals(scope)) return false;
        List<String> excluded = excludeGroupIds == null || excludeGroupIds.isEmpty() ? List.of("com.alkadev", "org.apache.logging.log4j") : excludeGroupIds;
        for (String group : excluded)
            if (artifact.getGroupId().equals(group) || artifact.getGroupId().startsWith(group + ".")) return false;
        return true;
    }

    private static List<Artifact> sorted(Set<Artifact> artifacts) {
        List<Artifact> list = new ArrayList<>(artifacts);
        list.sort(Comparator.comparing(Artifact::getGroupId).thenComparing(Artifact::getArtifactId));
        return list;
    }

    private static boolean copyTemplate(File jar, Path target) throws IOException {
        try (JarFile file = new JarFile(jar)) {
            ZipEntry entry = file.getEntry(EMBEDDED_TEMPLATE);
            if (entry == null) return false;
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream(entry)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }
    }

    static void deleteTree(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        try (var walk = Files.walk(folder)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
        }
    }
}
