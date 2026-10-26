package com.alkadev.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Copies the drop folder (see alka:package) into an Alka home, for example: mvn package alka:deploy -Dalka.home=C:\alka\home
 * Files of the same name are replaced. A jar that a running service has open cannot be replaced on Windows: stop that service first.
 */
@Mojo(name = "deploy", threadSafe = true)
public class DeployMojo extends AbstractMojo {
    @Parameter(property = "alka.home", required = true)
    private File home;

    @Parameter(defaultValue = "${project.build.directory}/alka")
    private File dropFolder;

    @Override
    public void execute() throws MojoExecutionException {
        Path drop = dropFolder.toPath();
        if (!Files.isDirectory(drop))
            throw new MojoExecutionException("There is no drop folder (" + drop + "): run it together with the package phase, for example mvn package alka:deploy -Dalka.home=...");
        Path target = home.toPath();
        if (!Files.isDirectory(target))
            throw new MojoExecutionException("The Alka home " + target + " does not exist or is not a folder (set it with -Dalka.home=...).");
        if (!Files.isDirectory(target.resolve("config")) && !Files.isDirectory(target.resolve("libs")))
            throw new MojoExecutionException(target + " is not an Alka home: it has no config and no libs folder.");
        int copied = 0;
        try (Stream<Path> walk = Files.walk(drop)) {
            for (Path source : (Iterable<Path>) walk::iterator) {
                Path to = target.resolve(drop.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(to);
                } else {
                    Files.createDirectories(to.getParent());
                    Files.copy(source, to, StandardCopyOption.REPLACE_EXISTING);
                    getLog().info("  " + target.relativize(to).toString().replace(File.separatorChar, '/'));
                    copied++;
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not copy the drop folder into " + target + ": " + e.getMessage()
                    + " (a jar that a running service has open cannot be replaced: stop the service first)", e);
        }
        getLog().info("Alka deploy: " + copied + " files copied into " + target + ".");
    }
}
