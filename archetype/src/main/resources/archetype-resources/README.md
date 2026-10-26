# ${artifactId}

A small project of Alka: a manager, a plugin that talks to it, and a service that wires the two.

| Module | What it is |
|---|---|
| `${artifactId}-manager` | `GreeterManager`: owns a counter and a greeting, and answers requests |
| `${artifactId}-plugin` | `HelloPlugin`: greets the manager every few seconds and logs the answer |
| `${artifactId}-service` | no code: the config files of the service `${artifactId}` and its two jars |

## Build

```
mvn install
```

Each module writes a folder `target/alka` with the layout of an Alka home. The manager and the plugin each give their jar (with its template of settings); the service gives its config files and the two jars in its own plugin folder.

## Use

Copy the folders into an Alka home, or let Maven do it (stop the service first if it runs: Windows does not let a running service's jar be replaced):

```
mvn package alka:deploy -Dalka.home=C:\path\to\alka-home
```

Open the Studio on that home. The manager and the plugin are in the Components palette, and the service `${artifactId}` is in the list of services: start it and watch the log. The plugin greets the manager, and the manager answers.

## Change it

- A setting: add a `@Setting` in the annotation `@AlkaSettings` of the class, read it with `properties.getProperty(...)`, rebuild. The Studio shows it as a form.
- A library: add a normal dependency to the pom of the module. It goes into the folder `<jar>.lib` next to the jar.
- Another plugin or manager: copy a module, change `alka.className` in its pom and add it to `modules` in the pom of this project.
