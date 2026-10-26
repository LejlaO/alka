# Your first plugin

From nothing to a plugin of your own, running in a service. About 20 minutes.

You need: the Alka package unzipped (see the [README](../README.md)), **Java 25** and **Maven 3.9**.

## 1. Make the project

```
mvn archetype:generate -DarchetypeGroupId=com.alkadev -DarchetypeArtifactId=alka-sample-archetype -DarchetypeVersion=0.1.0
```

Answer the questions: group id `com.example`, artifact id `myalka`, version `0.1.0`, package `com.example.myalka`. You get:

```
myalka/
  pom.xml
  myalka-manager/   GreeterManager.java    a manager: a counter and a greeting
  myalka-plugin/    HelloPlugin.java       a plugin: greets the manager every few seconds
  myalka-service/   src/alka/config/...    no code: the config files of the service "myalka"
```

Open the two Java files. They are short and have comments that explain the rules.

## 2. Build it and put it into the home

```
cd myalka
mvn install
mvn package alka:deploy -Dalka.home=C:\path\to\alka-0.1.0\home
```

`mvn install` makes the jars, and for each module a **drop folder** `target/alka` with the layout of a home: the jar in `libs/plugin`, its libraries (if any) in `<jar>.lib`, and the settings template in `config/templates`. `alka:deploy` copies the drop folders into your home. (A jar that a running service has open cannot be replaced on Windows: stop that service first.)

## 3. Run it

Start the Studio. The service `myalka` is in the list, and `myalka-manager.jar` and `myalka-plugin.jar` are in the **Components** palette. Select the service, press **Start**, and watch the log: the plugin greets the manager, and the manager answers.

Click the box of the plugin in the **Diagram**: its settings are a form, made from the `@AlkaSettings` of your class. Change `hello.intervalSeconds`, save, restart the service.

## 4. Change the plugin

In `HelloPlugin.java`:

- **A new setting:** add a `@Setting(key = "hello.shout", type = SettingType.BOOLEAN, defaultValue = "false", description = "...")` to the annotation, read it in `init()` with `properties.getProperty("hello.shout")`, and use it.
- **A library:** add a normal dependency to `myalka-plugin/pom.xml`. After `mvn install` it is in `myalka-plugin.lib/` next to the jar, and the engine loads it. The API and Log4j are not copied: the engine has them.
- **Another instance of the same plugin:** in the Studio drag `myalka-plugin.jar` onto the Diagram a second time and give it a name, or add `alkaprocessor.plugin.hello2 = myalka-plugin.jar` to the config of the service.
- **Plugins that talk:** wire `hello` to `hello2` (drag a line in the Diagram, or `alkaprocessor.map.hello = hello2`), and send with `onAsyncEvent(...)` or `onSyncEvent(..., "hello2")`. The receiver implements `accept(Object)`.

Rebuild and deploy again (stop the service first):

```
mvn install
mvn package alka:deploy -Dalka.home=C:\path\to\alka-0.1.0\home
```

## 5. When something is wrong

- **Validate** in the Studio checks the config, the jars, the settings and the wiring, and says what is wrong in words.
- The **log** of the service has a line for every step of loading a jar and every failure. A plugin that did not start has its reason there.
- Read [Things to know](gotchas.md) once.

## 6. Your own project from scratch

You do not need the archetype to start. The pom of a plugin is only this:

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

The parent adds the API as provided dependencies, runs the annotation processor, writes the manifest entries that the engine reads and names the jar without a version. It also carries the license, the developer and the source link of Alka: put your own `<licenses>`, `<developers>` and `<scm>` in your pom if you publish the plugin.
