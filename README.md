# Alka

Alka is a service engine for Java 25. A service is a set of **plugins** and **managers**, each one a jar, that the engine loads and runs from configuration files. This repository holds what a developer builds on:

| Module | Group id | What it is |
|---|---|---|
| `annotations` | `com.alkadev` | `@AlkaSettings` and the annotation processor that writes the settings template of a jar |
| `utils/common` | `com.alkadev.utils` | the base of every component (`AlkaComponent`), the loader of plugin and manager jars |
| `utils/objectmanager` | `com.alkadev.utils` | the base class of a manager (`ObjectManager`) |
| `utils/objectplugin` | `com.alkadev.utils` | the base class of a plugin (`ObjectPlugin`) |
| `plugin-parent` | `com.alkadev` | `alka-plugin-parent`: the parent pom of a plugin or a manager |
| `maven-plugin` | `com.alkadev` | `alka-maven-plugin`: the goals `alka:package` and `alka:deploy` |
| `samples/sampleplugin` | `com.alkadev` | a small plugin to start from |
| `samples/samplemanager` | `com.alkadev` | a small manager to start from |

The engine itself and its web tool, Alka Studio, are not in this repository. They are given as binaries.

**State:** version 0.1.0, an early version. The API can still change before 1.0.0. It is not on Maven Central yet, so build this repository first (below).

## Build

Java 25 and Maven are needed.

```
mvn install
```

This builds all modules and installs them in the local Maven repository.

## Write your own plugin or manager

The pom of a plugin is the parent, the name of the class, and the libraries the plugin needs. Look at `samples/sampleplugin/pom.xml`:

```xml
<parent>
    <groupId>com.alkadev</groupId>
    <artifactId>alka-plugin-parent</artifactId>
    <version>0.1.0</version>
</parent>

<artifactId>myplugin</artifactId>

<properties>
    <alka.className>com.example.MyPlugin</alka.className>
</properties>
```

The class extends `ObjectPlugin` (a plugin) or `ObjectManager` (a manager). Each setting is declared in the `@AlkaSettings` annotation of the class; the build writes the template of the settings into the jar, and the Studio shows it as a form. The parent pom adds the API as provided dependencies, runs the annotation processor, writes the manifest entries that the engine reads (`classname` and `alka-api-version`) and names the jar without a version, because a service refers to a jar by its name.

`mvn package` also writes the **drop folder** under `target/alka`. It has the layout of an Alka home:

```
target/alka/libs/plugin/myplugin.jar            the plugin
target/alka/libs/plugin/myplugin.lib/*.jar      its own libraries (not the API and not log4j: the engine has those)
target/alka/config/templates/myplugin.properties  the settings template, for reading
```

Copy it into a home by hand, or let Maven do it:

```
mvn package alka:deploy -Dalka.home=C:\path\to\alka-home
```

The plugin is then in the palette of the Studio and can be added to a service. A jar that a running service has open cannot be replaced on Windows: stop the service first.

## License

Apache License 2.0, see `LICENSE`. Copyright 2026 Lejla Omerovic.
