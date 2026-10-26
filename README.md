# Alka

Web page: https://alkadev.com

Alka is a small service engine for Java 25. You write the pieces of a service as **plugins** and **managers** (one jar each), and the engine loads them and runs them from configuration files. **Alka Studio**, a tool in your browser, shows how the pieces are wired, edits their settings, starts and stops the service and shows its log.

```
 plugin ──sends events──▶ plugin          a plugin does the work and talks to other plugins
   │
   └──asks──▶ manager                     a manager owns what plugins share: a connection, a cache, a device
```

**State:** version 0.x, the first public versions. The API can still change before 1.0.0. It was tried on Windows only; Linux and macOS are expected to work but are not tried yet.

## Download and run the demo

1. You need **Java 25 or newer** (`java -version`), for example from https://adoptium.net.
2. Download `alka-<version>.zip` from the [latest release](https://github.com/LejlaO/alka/releases/latest) and unzip it anywhere.
3. Start `studio.bat` (Windows) or `./studio.sh` (Linux, macOS). The Studio opens in your browser.
4. Select the service **alkaprocessor** (the demo), press **Start**, and watch the log: one plugin greets a manager every few seconds, and a second plugin receives what the first sends on.

The zip holds the Studio, the engine and the demo service. The checksums are in `SHA256SUMS.txt` on the release page. `alka-<version>-home.zip` is only the home folder with the engine, for someone who has a Studio already.

## Make your own plugin or manager

You need Java 25 and [Maven](https://maven.apache.org) 3.9 or newer. One command makes a small project:

```
mvn archetype:generate -DarchetypeGroupId=com.alkadev -DarchetypeArtifactId=alka-sample-archetype -DarchetypeVersion=0.1.0
```

Maven asks for a group id, an artifact id (say `myalka`), a version and a package, and makes three modules: a manager, a plugin that talks to it, and a service that wires the two. Then:

```
cd myalka
mvn install
mvn package alka:deploy -Dalka.home=<the folder "home" of the unzipped package>
```

Open the Studio again: the service `myalka` is in the list, and its manager and plugin are in the Components palette. Start it and watch the log. The guide [Your first plugin](docs/your-first-plugin.md) goes through it step by step.

## Read more

- [How Alka works](docs/how-it-works.md): services, plugins, managers, wiring, events, settings, the folders of a home. One page, read it before the code.
- [Your first plugin](docs/your-first-plugin.md): from the generated project to a plugin of your own.
- [Things to know](docs/gotchas.md): the places where a plugin surprises its author (threads, settings, shared state).
- [Releasing](RELEASING.md): how a release of this repository is made.

## What is in this repository

| Module | Group id | What it is |
|---|---|---|
| `annotations` | `com.alkadev` | `@AlkaSettings` and the annotation processor that writes the settings template of a jar |
| `utils/common` | `com.alkadev.utils` | the base of every component (`AlkaComponent`), the loader of plugin and manager jars |
| `utils/objectmanager` | `com.alkadev.utils` | the base class of a manager (`ObjectManager`) |
| `utils/objectplugin` | `com.alkadev.utils` | the base class of a plugin (`ObjectPlugin`) |
| `plugin-parent` | `com.alkadev` | `alka-plugin-parent`: the parent pom of a plugin or a manager |
| `maven-plugin` | `com.alkadev` | `alka-maven-plugin`: the goals `alka:package` and `alka:deploy` |
| `archetype` | `com.alkadev` | `alka-sample-archetype`: makes a small project with a manager, a plugin and a service |
| `samples/sampleplugin` | `com.alkadev` | a reference plugin with every kind of setting |
| `samples/samplemanager` | `com.alkadev` | a reference manager |

The engine and Alka Studio are not in this repository: they are given as binaries in the package.

To build this repository yourself you need Java 25 and Maven: `mvn install`.

## License

- **This repository** (the API, the Maven parent and plugin, the archetype and the samples): [Apache License 2.0](LICENSE).
- **The package** (the engine and Alka Studio, as binaries): free for **noncommercial** use under the PolyForm Noncommercial License 1.0.0 with a few added terms; commercial use needs written permission, which you can ask for by [opening an issue](https://github.com/LejlaO/alka/issues). The text is `LICENSE-binaries.txt` in the package. The libraries of Apache Log4j that come with it are under the Apache License 2.0.

Copyright 2026 Lejla Omerovic.
