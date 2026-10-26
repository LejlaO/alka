# Alka

Alka is a service engine for Java 25. A service is a set of **plugins** and **managers**, each one a jar, that the engine loads and runs from configuration files. This repository holds what a developer builds on:

| Module | Group id | What it is |
|---|---|---|
| `annotations` | `com.alkadev` | `@AlkaSettings` and the annotation processor that writes the settings template of a jar |
| `utils/common` | `com.alkadev.utils` | the base of every component (`AlkaComponent`), the loader of plugin and manager jars |
| `utils/objectmanager` | `com.alkadev.utils` | the base class of a manager (`ObjectManager`) |
| `utils/objectplugin` | `com.alkadev.utils` | the base class of a plugin (`ObjectPlugin`) |
| `samples/sampleplugin` | `com.alkadev` | a small plugin to start from |
| `samples/samplemanager` | `com.alkadev` | a small manager to start from |

The engine itself and its web tool, Alka Studio, are not in this repository. They are given as binaries.

**State:** version 0.1.0, an early version. The API can still change before 1.0.0. It is not on Maven Central yet.

## Build

Java 25 and Maven are needed.

```
mvn install
```

This builds all modules and installs them in the local Maven repository. The jar of a plugin or manager is then in `samples/<name>/target/<name>.jar`; it is copied into the plugin folder of an Alka home to use it.

## Write your own

Copy `samples/sampleplugin` (a plugin) or `samples/samplemanager` (a manager) and change the class, the keys and the names. Each setting is declared in the `@AlkaSettings` annotation of the class; the build writes the template of the settings into the jar, and the Studio shows it as a form. The manifest of the jar names the class (`classname`) and the API version it was built for (`alka-api-version`, now `1`).

## License

Apache License 2.0, see `LICENSE`. Copyright 2026 Lejla Omerovic.
