# How Alka works

One page. Read it before the code.

## The pieces

- A **service** is one running engine: a Java process with a name, for example `alkaprocessor`. It is described by one file, `config/services/<name>Config.properties`, and it writes its log to `logs/<name>/`.
- A **plugin** does the work of a service: it reads a queue, answers a request, calls a web service. A plugin receives events from other plugins and sends events on.
- A **manager** owns something that several plugins use together: a connection, a cache, a device. A plugin asks for a manager by its **type** and its **name**, and calls it.
- Each plugin and each manager is **one jar**. The jar names its class in the manifest (`classname`), and the class extends `ObjectPlugin` or `ObjectManager`.
- An **instance** is a jar under a name. One jar can be started many times under different names (`greeter`, `greeter2`), and each instance is a separate object with its own settings.

```
  service "myalka"  (one JVM)
  ├── manager  greeter   ← from myalka-manager.jar
  ├── plugin   hello     ← from myalka-plugin.jar   ── uses ──▶ greeter
  └── plugin   hello2    ← from myalka-plugin.jar   ◀── receives from ── hello
```

## The wiring

It is all in the service config (and the Studio edits the same file in its Diagram):

```properties
# a manager instance and two plugin instances (name = jar)
alkaprocessor.manager.greeter = myalka-manager.jar
alkaprocessor.plugin.hello    = myalka-plugin.jar
alkaprocessor.plugin.hello2   = myalka-plugin.jar
# hello may use the manager greeter
alkaprocessor.addmanager.hello = greeter
# what hello sends on goes to hello2
alkaprocessor.map.hello = hello2
```

(A comment in a properties file is a whole line that starts with `#`. A `#` after a value is part of the value.)

The jars are looked up in the plugin folder of the service, `libs/override/<service>/plugin/`. A plugin that wants a manager asks the engine for it in `init()`:

```java
manager = getManager(ObjectManagerType.of("GREETER"), properties.getProperty("hello.managerName"));
```

The type is only a name. The plugin does not need the class of the manager, and the service decides which manager it gets.

## The life of an instance

`init()`, then `run()`, then `stop()`. Each returns `true` when it went well. The engine starts all managers before the plugins, runs `init()` of every instance first and then `run()` of every instance, and on stop it stops the plugins together and then the managers. An instance whose `init()` returns `false` (or throws) is marked failed and skipped; the others go on. A call that takes longer than `alkaprocessor.lifecycleTimeoutSeconds` (30 by default) is interrupted.

## Events

- A plugin sends to the plugins it is wired to with `onAsyncEvent(object)` (queued, nobody waits) or `onSyncEvent(object, target)` (the sender waits for the answer of the receiver's `accept()`).
- A plugin calls a manager with `manager.processSync(object)` (waits for the answer) or `manager.processAsync(object)`; the manager implements `process(object)`.
- `accept()` and `process()` are called by several threads at once: keep them thread-safe.

## Settings

Every instance gets a `java.util.Properties` object (`properties`) before `init()`. It is made of three layers, each on top of the one before:

1. the **service config** (itself on top of `config/general/CommonConfig.properties`),
2. the **common file** of the instance: `config/plugins/common/<plugins|managers>/<name>.properties`,
3. the **own file** of the instance in this service: `config/plugins/<service>/<plugins|managers>/<name>.properties`.

You declare the settings of a class in the annotation `@AlkaSettings`, with a key, a description, a type (`STRING`, `NUMBER`, `LIST`, `BOOLEAN`), a default, and optionally bounds, the allowed values, or `required`. The build writes this as a **template** into the jar, and the Studio shows it as a form and checks the values.

## The API version

The manifest of a jar says which version of the Alka API it was built for (`alka-api-version`). The engine refuses a jar of another version; the Studio marks it in the palette. The parent pom `alka-plugin-parent` writes the entry for you.

## Class loading

Each jar gets its own class loader that looks in the jar first. Only `java.`, `javax.`, `jdk.`, `sun.`, `com.alkadev.utils.` and Log4j come from the engine. The libraries of a plugin go into a folder next to its jar, `<jar name without .jar>.lib/` (the Maven plugin makes it for you).

## The folders of a home

```
home/
  runnable/alkaprocessor.jar        the engine
  libs/jar/                         the libraries that every service shares
  libs/plugin/                      the pool: every plugin and manager jar that can be added (the Studio palette lists it)
  libs/override/<service>/plugin/   the jars that a service really uses
  config/services/                  one config file per service
  config/plugins/                   the settings of the instances (common/ and one folder per service)
  config/log4j/                     the logging of each service
  config/general/                   what every service shares
  logs/<service>/                   the log, the console and the record of each run
  run/                              pid and status files of the running services
  scripts/                          start scripts (alka.bat, alka.sh)
```

## Alka Studio

The Studio opens on a home. In the browser you see the services and, for one, a **Diagram** of its plugins and managers (drag a jar from the palette to add one, drag a line to wire them), the **settings** as forms, **Validate** (checks of the config, the jars and the wiring), the **log** and the console, the **runs** of the service, and the **History** of every config file, from which an older version can be restored. It starts and stops services, and a service goes on running when the Studio is closed.
