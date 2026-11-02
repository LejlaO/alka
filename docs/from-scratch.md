# A manager, a plugin and a service from scratch

No archetype, no parent pom, no `alka:deploy`. You write every file yourself and copy them into the Alka home by hand. It takes longer than [Your first plugin](your-first-plugin.md), but afterwards nothing is a black box.

Every file on this page was built and run: the jars below start in a real Alka 0.2.0 package and print the log shown in step 6.

## What you will build

A small service called `demo` with three parts:

| Part | What it is | In this example |
|---|---|---|
| **Manager** | Owns something that several plugins share. One jar. | `counter-manager.jar`: a counter. You ask it for a number and it answers with the next one. |
| **Plugin** | Does the work and talks to managers and to other plugins. One jar. | `ticker-plugin.jar`: every 2 seconds it asks the manager for a number and sends it on to another plugin. |
| **Service** (the processor) | No code. Config files that say which jars to start and how they are connected. | `demo`: one manager, and two instances of the plugin (`ticker` sends, `printer` receives). |

The engine that runs a service is called the *processor* (`alkaprocessor`), and that is why every key in a service config starts with `alkaprocessor.`.

You make two Maven projects (the manager and the plugin) and one folder of config files:

```
alka-from-scratch/
  counter-manager/
    pom.xml
    src/main/java/com/example/counter/CounterManager.java
  ticker-plugin/
    pom.xml
    src/main/java/com/example/ticker/TickerPlugin.java
  service/
    demoConfig.properties
    counter.properties
    ticker.properties
    printer.properties
    log4j2demo.xml
```

## 0. Before you start

You need Java 21 or newer, Maven 3.9 and the unzipped Alka package (the folder `home` in it is "the home"). If one of these is missing, [Getting started](getting-started.md) shows how to get it. On Windows type the commands in the Command Prompt, not in PowerShell.

Make the folder `alka-from-scratch` somewhere and the folders from the tree above inside it. The jars are looked up by file name, so the names matter: `counter-manager.jar` and `ticker-plugin.jar`.

## 1. The manager

A manager owns a resource and answers requests of plugins. This one owns a number.

### The pom

`counter-manager/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>counter-manager</artifactId>
    <version>1.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <!-- the class that the engine creates: it extends ObjectManager -->
        <alka.className>com.example.counter.CounterManager</alka.className>
    </properties>

    <!-- provided: the engine already has these, so they must not go into the jar -->
    <dependencies>
        <dependency>
            <groupId>com.alkadev.utils</groupId>
            <artifactId>alka-objectmanager</artifactId>
            <version>0.2.0</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.alkadev</groupId>
            <artifactId>alka-annotations</artifactId>
            <version>0.2.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <!-- no version in the name of the jar: a service refers to the jar by its file name -->
        <finalName>${project.artifactId}</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <compilerArgs>
                        <!-- runs the annotation processor of Alka: it writes the settings template into the jar -->
                        <arg>-proc:full</arg>
                    </compilerArgs>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.2</version>
                <configuration>
                    <archive>
                        <manifestEntries>
                            <!-- the engine reads these two entries of the manifest -->
                            <classname>${alka.className}</classname>
                            <alka-api-version>1</alka-api-version>
                        </manifestEntries>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

What is in it that is specific to Alka:

- **The API as `provided` dependencies.** `alka-objectmanager` has the class `ObjectManager` (and brings `alka-common` and Log4j with it). `alka-annotations` has `@AlkaSettings`. They are on Maven Central. `provided` means "needed to compile, but the engine has it at runtime", so it stays out of your jar.
- **`finalName` without a version.** A service names a jar by its file name (`counter-manager.jar`). With a version in the name, every new version would break the config.
- **`classname` in the manifest.** The engine finds your class here. It must be the full name of a class that extends `ObjectManager` (or `ObjectPlugin` in a plugin) and is inside this jar. Without this entry the engine says `The manifest ... has no classname entry`.
- **`alka-api-version` in the manifest.** The version of the Alka API the jar was built for. It is `1` in Alka 0.2.0. The engine refuses a jar that says another number.
- **`-proc:full`.** It makes the compiler run the annotation processor of Alka, which turns your `@AlkaSettings` into a settings template inside the jar (`META-INF/alka/settings.properties`). The Studio uses the template for the form of the settings. Without it the jar still works, but the Studio shows no form. Newer Java versions skip processors found on the class path unless you ask for them, so keep the flag.

### The class

`counter-manager/src/main/java/com/example/counter/CounterManager.java`:

```java
package com.example.counter;

import com.alkadev.annotations.AlkaSettings;
import com.alkadev.annotations.Setting;
import com.alkadev.annotations.SettingType;
import com.alkadev.utils.objectmanager.ObjectManager;
import com.alkadev.utils.objectmanager.ObjectManagerType;

import java.util.concurrent.atomic.AtomicLong;

@AlkaSettings(description = "Counts the requests it gets and answers each one with the next number.", managerType = "COUNTER", value = {
        @Setting(key = "counter.start", type = SettingType.NUMBER, defaultValue = "0", description = "The number the count starts from.")
})
public class CounterManager extends ObjectManager {
    private final AtomicLong count = new AtomicLong();

    // the engine creates the class with this constructor; the type is the name that plugins ask for
    public CounterManager() {
        super(ObjectManagerType.of("COUNTER"));
    }

    @Override
    public boolean init() {
        // the settings are there from init() on, not in the constructor
        try {
            count.set(Long.parseLong(properties.getProperty("counter.start", "0").trim()));
        } catch (NumberFormatException e) {
            logError("counter.start is not a whole number.");
            return false;
        }
        logInfo("initialized, counting from {}.", count.get());
        return true;
    }

    @Override
    public boolean run() {
        logInfo("running.");
        return true;
    }

    @Override
    public boolean stop() {
        logInfo("stopped at {}.", count.get());
        return true;
    }

    // called by several threads at once: AtomicLong is thread-safe
    @Override
    public Object process(Object request) {
        return count.incrementAndGet();
    }
}
```

The rules of a manager:

- **A public constructor without arguments**, and it calls `super(ObjectManagerType.of("..."))`. The engine creates the class by reflection. The type is just a name (case does not matter, `of("counter")` is the same type). A plugin asks for a manager by this name, so the plugin never needs the class of the manager.
- **Settings are not there in the constructor.** `properties` (the settings of this instance) and `managerName` (its name in the service) are set after the constructor and before `init()`. Read them in `init()`.
- **`init()`, then `run()`, then `stop()`.** Each returns `true` when it went well. `init()` of every instance runs before any `run()`. If `init()` returns `false` or throws, the instance is marked failed and skipped, and the rest of the service goes on.
- **`process(Object)` is the one method plugins call.** It gets whatever the plugin passes and returns the answer. Several threads call it at once, so it must be thread-safe.
- **Logging:** use `logInfo`, `logWarn`, `logError`, `logDebug` of the base class. They put the instance name first in brackets (`[counter] ...`), and the Studio files the line under that box. `{}` is replaced by the arguments.
- **`@AlkaSettings`** lists every setting of the class: key, description, type (`STRING`, `NUMBER`, `LIST`, `BOOLEAN`), default, and optionally `min`, `max`, `options` (for `LIST`) and `required`. `managerType` repeats the type, so the Studio can check the wiring.

### Build it

In the folder `counter-manager`:

```
mvn package
```

It ends with `BUILD SUCCESS` and you have `target/counter-manager.jar`. You can open the jar with any zip program: it has `com/example/counter/CounterManager.class`, `META-INF/MANIFEST.MF` (with `classname` and `alka-api-version`) and `META-INF/alka/settings.properties`. If the last one is missing, the annotation processor did not run: check `-proc:full`.

## 2. The plugin

A plugin does the work. This one asks the manager for the next number every 2 seconds and sends it to the plugins it is wired to.

### The pom

`ticker-plugin/pom.xml` is the same as the manager's, with three differences: the artifact id, the class name, and the API dependency, which is `alka-objectplugin`. (It brings `ObjectManager` with it, because a plugin calls managers.)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>ticker-plugin</artifactId>
    <version>1.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <!-- the class that the engine creates: it extends ObjectPlugin -->
        <alka.className>com.example.ticker.TickerPlugin</alka.className>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.alkadev.utils</groupId>
            <artifactId>alka-objectplugin</artifactId>
            <version>0.2.0</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.alkadev</groupId>
            <artifactId>alka-annotations</artifactId>
            <version>0.2.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.artifactId}</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <compilerArgs>
                        <arg>-proc:full</arg>
                    </compilerArgs>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.2</version>
                <configuration>
                    <archive>
                        <manifestEntries>
                            <classname>${alka.className}</classname>
                            <alka-api-version>1</alka-api-version>
                        </manifestEntries>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

The plugin does **not** depend on the manager jar. It never sees the class `CounterManager`; it only knows the type name `COUNTER`.

### The class

`ticker-plugin/src/main/java/com/example/ticker/TickerPlugin.java`:

```java
package com.example.ticker;

import com.alkadev.annotations.AlkaSettings;
import com.alkadev.annotations.Setting;
import com.alkadev.annotations.SettingType;
import com.alkadev.utils.objectmanager.ObjectManager;
import com.alkadev.utils.objectmanager.ObjectManagerType;
import com.alkadev.utils.objectplugin.ObjectPlugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@AlkaSettings(description = "Asks its manager for the next number every few seconds and sends it on to the plugins it is wired to. With the interval 0 it only receives.",
        usesManagerTypes = "COUNTER", value = {
        @Setting(key = "ticker.managerName", description = "The name of the manager instance to ask (it must be in alkaprocessor.addmanager of the service)."),
        @Setting(key = "ticker.intervalSeconds", type = SettingType.NUMBER, defaultValue = "2", min = "0", max = "3600",
                description = "Seconds between two ticks. 0 = this instance does not tick, it only receives.")
})
public class TickerPlugin extends ObjectPlugin {
    private int seconds;
    private volatile ObjectManager manager;
    private ScheduledExecutorService scheduler;

    @Override
    public boolean init() {
        try {
            seconds = Integer.parseInt(properties.getProperty("ticker.intervalSeconds", "2").trim());
        } catch (NumberFormatException e) {
            logError("ticker.intervalSeconds is not a whole number.");
            return false;
        }
        if (seconds > 0) {
            // a plugin asks for a manager by its type and by the name that its own settings give
            manager = getManager(ObjectManagerType.of("COUNTER"), properties.getProperty("ticker.managerName"));
            if (manager == null) {
                logError("no manager: check ticker.managerName and alkaprocessor.addmanager.{} in the config of the service.", pluginName);
                return false;
            }
        }
        logInfo("initialized, interval {} s.", seconds);
        return true;
    }

    @Override
    public boolean run() {
        if (seconds > 0) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::tick, 1, seconds, TimeUnit.SECONDS);
        }
        logInfo("running.");
        return true;
    }

    // an exception that escapes a scheduled task ends it for good, so everything is caught here
    private void tick() {
        try {
            Object number = manager.processSync("tick");
            logInfo("tick {}", number);
            onAsyncEvent("tick number " + number);
        } catch (Exception e) {
            logError("tick failed, will try again.", e);
        }
    }

    @Override
    public boolean stop() {
        // a thread that a plugin starts must be stopped here
        if (scheduler != null) scheduler.shutdownNow();
        logInfo("stopped.");
        return true;
    }

    // called by several threads at once; the sender gets the return value when it sends with onSyncEvent
    @Override
    public Object accept(Object event) {
        logInfo("received: {}", event);
        return null;
    }
}
```

The rules of a plugin are the same as of a manager (constructor, settings from `init()`, `init` then `run` then `stop`, thread safety), and these:

- **Asking for a manager:** `getManager(type, name)` in `init()`. The type is the name the manager gave itself. The name is the *instance* name, and here the plugin reads it from its own settings (`ticker.managerName`), so the service decides which manager it gets. `getManager` returns `null` for a manager that the service did not give to this plugin (see `addmanager` in step 3). A plugin that cannot work without it returns `false`.
- **Calling a manager:** `manager.processSync(object)` waits for the answer of the manager's `process()`. `manager.processAsync(object)` queues the call and does not wait.
- **Sending to other plugins:** `onAsyncEvent(object)` queues the event for every plugin this one is wired to (`alkaprocessor.map`) and does not wait. `onAsyncEvent(object, "name")` sends to one of them. `onSyncEvent(object, "name")` calls that plugin's `accept()` right here and returns what it returns. `onSyncEvent(object)` does this for every receiver in name order.
- **Receiving:** the engine calls `accept(Object)` for every event sent to this plugin. The return value matters only to a sender that used `onSyncEvent`.
- **Threads:** a thread that you start in `init()` or `run()` is a daemon thread and does not keep the service alive. Stop your own threads in `stop()`. In a scheduled task catch everything, because the JDK cancels a task that throws, for good.
- **`pluginName`** is the name of this instance in the service.
- Read [Things to know](gotchas.md) once: it is short and lists what surprises authors.

### Build it

In the folder `ticker-plugin`: `mvn package`. You get `target/ticker-plugin.jar`.

## 3. The service

A service is not code. It is the config files that tell the engine what to start. Make them in the folder `service`.

The name of the service is `demo`. It may contain letters, digits, `_` and `-`. The name is part of the file names below, and the engine finds the files by them, so they must be spelled exactly like this.

### The service config

`service/demoConfig.properties`:

```properties
# The service demo: one manager (counter) and two plugin instances of the same jar.
alkaprocessor.manager.counter = counter-manager.jar
alkaprocessor.plugin.ticker = ticker-plugin.jar
alkaprocessor.plugin.printer = ticker-plugin.jar
alkaprocessor.addmanager.ticker = counter
alkaprocessor.map.ticker = printer
```

| Key | Means |
|---|---|
| `alkaprocessor.manager.<name> = <jar>` | A manager instance: the name you give it, and the jar it is made from. |
| `alkaprocessor.plugin.<name> = <jar>` | A plugin instance. The same jar can be started under many names (`ticker` and `printer` above), and each is a separate object with its own settings. |
| `alkaprocessor.addmanager.<plugin> = <manager>,<manager>` | The managers that this plugin may use. `getManager` finds only these. |
| `alkaprocessor.map.<plugin> = <plugin>,<plugin>` | The plugins that receive what this plugin sends with `onAsyncEvent` / `onSyncEvent`. |

A comment is a whole line that starts with `#`. A `#` after a value is part of the value.

Two more optional keys: `alkaprocessor.lifecycleTimeoutSeconds` (how long `init`, `run` or `stop` of one instance may take, 30 by default) and `alkaprocessor.javaOptions` (for example `-Xmx512m`, used when the Studio or the script starts the service).

### The settings of each instance

The settings files are optional (a setting that is not there gets the default of its annotation), but this is where you change the values. One file per instance, in a folder named after the service and the kind:

`service/counter.properties`:

```properties
counter.start=100
```

`service/ticker.properties`:

```properties
ticker.managerName=counter
ticker.intervalSeconds=2
```

`service/printer.properties`:

```properties
ticker.intervalSeconds=0
```

`printer` is made from the same jar as `ticker`, but with the interval 0 it does not tick. It only receives what `ticker` sends. The keys are the ones you declared with `@Setting`. If you built the jars, you can also see them in the template inside the jar (`META-INF/alka/settings.properties`), where every setting is a commented line you can copy.

The settings of an instance are three layers, each on top of the one before: the service config (and the file `config/general/CommonConfig.properties` under it), the optional shared file `config/plugins/common/<plugins|managers>/<name>.properties`, and the file of the instance in this service.

### The log config

`service/log4j2demo.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss} %-5level %logger{36} - %msg%n"/>
        </Console>
        <RollingFile name="File"
                     fileName="${sys:alka.logDir}/demo.log"
                     filePattern="${sys:alka.logDir}/demo-%d{yyyy-MM-dd}-%i.log.gz">
            <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{36} - %msg%n"/>
            <Policies>
                <TimeBasedTriggeringPolicy interval="1"/>
                <SizeBasedTriggeringPolicy size="100 MB"/>
            </Policies>
            <DefaultRolloverStrategy max="20"/>
        </RollingFile>
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="File"/>
        </Root>
    </Loggers>
</Configuration>
```

This is an ordinary Log4j 2 file. The engine sets `alka.logDir` to `logs/demo` in the home, so the log goes to `logs/demo/demo.log`. Without this file the service still starts, but it prints only errors to the console and writes no log file.

## 4. Put it into the home by hand

You need the full path of the `home` folder of the unzipped package. This is the whole deploy: copy files to the places in this table. `demo` is the name of the service. Create the folders that do not exist yet.

| What | Where in the home |
|---|---|
| `counter-manager.jar`, `ticker-plugin.jar` | `libs/override/demo/plugin/` |
| `demoConfig.properties` | `config/services/` |
| `counter.properties` | `config/plugins/demo/managers/` |
| `ticker.properties`, `printer.properties` | `config/plugins/demo/plugins/` |
| `log4j2demo.xml` | `config/log4j/` |

`libs/override/<service>/plugin/` is the folder from which a service loads its jars. It is the one that matters for starting. The jars should also be copied into `libs/plugin/`: that is the *pool* that the Studio lists in its palette of components, so you can drag a jar onto a diagram. It is not needed to run a service.

On Windows, Command Prompt (put your own path in the first line; make the folders with `mkdir`, and ignore the message if one already exists):

```
set ALKA_HOME=C:\Users\you\Downloads\alka-0.2.0\home
mkdir "%ALKA_HOME%\libs\override\demo\plugin"
mkdir "%ALKA_HOME%\config\plugins\demo\managers"
mkdir "%ALKA_HOME%\config\plugins\demo\plugins"

copy counter-manager\target\counter-manager.jar "%ALKA_HOME%\libs\override\demo\plugin\"
copy ticker-plugin\target\ticker-plugin.jar "%ALKA_HOME%\libs\override\demo\plugin\"
copy counter-manager\target\counter-manager.jar "%ALKA_HOME%\libs\plugin\"
copy ticker-plugin\target\ticker-plugin.jar "%ALKA_HOME%\libs\plugin\"

copy service\demoConfig.properties "%ALKA_HOME%\config\services\"
copy service\counter.properties "%ALKA_HOME%\config\plugins\demo\managers\"
copy service\ticker.properties "%ALKA_HOME%\config\plugins\demo\plugins\"
copy service\printer.properties "%ALKA_HOME%\config\plugins\demo\plugins\"
copy service\log4j2demo.xml "%ALKA_HOME%\config\log4j\"
```

On Linux and macOS:

```
ALKA_HOME=~/alka-0.2.0/home
mkdir -p $ALKA_HOME/libs/override/demo/plugin $ALKA_HOME/config/plugins/demo/managers $ALKA_HOME/config/plugins/demo/plugins

cp counter-manager/target/counter-manager.jar ticker-plugin/target/ticker-plugin.jar $ALKA_HOME/libs/override/demo/plugin/
cp counter-manager/target/counter-manager.jar ticker-plugin/target/ticker-plugin.jar $ALKA_HOME/libs/plugin/

cp service/demoConfig.properties $ALKA_HOME/config/services/
cp service/counter.properties $ALKA_HOME/config/plugins/demo/managers/
cp service/ticker.properties service/printer.properties $ALKA_HOME/config/plugins/demo/plugins/
cp service/log4j2demo.xml $ALKA_HOME/config/log4j/
```

You can do the same in the file manager: it is only copying files.

## 5. Start it

**In the Studio:** start `studio.bat` (`./studio.sh`), press the refresh button, select the service `demo` and press **Start**. The Diagram shows `counter`, `ticker` and `printer` with the lines between them. **Validate** checks the config, the jars and the wiring and says in words what is wrong.

**Without the Studio:** in the `scripts` folder of the package, `alka.bat demo` (`./alka.sh demo`). It needs `java` on the PATH.

In the log you should see lines like these (shortened):

```
INFO  Loaded manager [counter] from counter-manager.jar (no metadata).
INFO  Loaded plugin [printer] from ticker-plugin.jar (no metadata).
INFO  Loaded plugin [ticker] from ticker-plugin.jar (no metadata).
INFO  [counter] initialized, counting from 100.
INFO  [printer] initialized, interval 0 s.
INFO  [ticker] initialized, interval 2 s.
INFO  AlkaProcessor processor started.
INFO  [ticker] tick 101
INFO  [printer] received: tick number 101
INFO  [ticker] tick 102
INFO  [printer] received: tick number 102
```

(`no metadata` only says that the jar has no `Implementation-Title` in its manifest. It is harmless.)

That is the whole chain: the processor loaded two jars, made three instances from them, wired them, and `ticker` asks `counter` for a number and sends it to `printer`.

## 6. Instead of copying: the Maven plugin

Once you have done step 4 by hand, you may want Maven to copy the jar for you. Add the Alka Maven plugin to the `<plugins>` of the pom of a plugin or manager:

```xml
<plugin>
    <groupId>com.alkadev</groupId>
    <artifactId>alka-maven-plugin</artifactId>
    <version>0.2.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>package</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

`mvn package` then also makes a **drop folder**, `target/alka`, with the layout of a home: `libs/plugin/<jar>`, the libraries of the jar in `libs/plugin/<jar name without .jar>.lib/` (if it has any), and `config/templates/<jar name>.properties` (the settings template). Then:

```
mvn package alka:deploy -Dalka.home=C:\Users\you\Downloads\alka-0.2.0\home
```

copies the drop folder into the home. (In PowerShell put the whole option in quotes: `"-Dalka.home=..."`.)

Two things to know. The deploy puts the jar into the **pool** (`libs/plugin/`), not into the folder of a service. A service uses `libs/override/<service>/plugin/`; the Studio copies a jar there when you drag it onto the Diagram, and by hand you copy it yourself, as in step 4. And the service config files are never made by this plugin: they are yours.

## 7. Change it later

- **A new setting:** add a `@Setting` to the annotation, read it in `init()` with `properties.getProperty("key", "default")`, put a line for it in the settings file of the instance, rebuild.
- **A library** (a database driver, an HTTP client): add it as a normal dependency in the pom (without `provided`). The engine does not have it, so it must be next to your jar: in a folder named like the jar without `.jar`, here `ticker-plugin.lib/`, in the same folder as the jar. Copy there the library jars, but not the ones of Alka and Log4j (the engine has those). `mvn dependency:copy-dependencies -DoutputDirectory=target/lib -DincludeScope=runtime` copies them to `target/lib`, and the Maven plugin of step 6 makes the `.lib` folder for you.
- **Another instance of the same jar:** a new pair of lines in the service config (`alkaprocessor.plugin.printer2 = ticker-plugin.jar`), and a file of settings if it needs other values.
- **A new version of a jar:** stop the service, copy the new jar over the old one, start the service. On Windows a running service has its jars open, so the copy fails while it runs.
- **A jar for a different API version:** the engine refuses it. Rebuild against the version of the engine.

## 8. When something is wrong

The log of the service has a line for every step of loading a jar. The usual ones:

| In the log | Cause |
|---|---|
| `Jar file not loaded : x.jar. Skipping` | The jar is not in `libs/override/<service>/plugin/`. |
| `The manifest of x.jar has no classname entry` | The manifest has no `classname`: check the `maven-jar-plugin` part of the pom. |
| `... is not part of x.jar itself` | `classname` names a class that is not in this jar (a typo, or a class of the engine). |
| `... does not extend ObjectPlugin` / `ObjectManager` | The plugin has to extend `ObjectPlugin`, the manager `ObjectManager`. A jar in the `manager` line of the config must hold a manager, not a plugin. |
| `built for Alka API '...'` | `alka-api-version` in the manifest is not the version of the engine. |
| `ClassNotFoundException` or `NoClassDefFoundError` | The jar needs a library that is not there: the `.lib` folder (step 7). |
| `UnsupportedClassVersionError` | The jar was built with a newer Java than the one that runs the service. |
| `Required setting not set: ...` | A setting with `required = true` has no value in any of the files. |
| `no manager` (your own message) | The plugin is not given the manager: check `alkaprocessor.addmanager.<plugin>` and the name in its settings. |
| `... an instance name may only contain letters, digits, '_' and '-'` | A name in the config has a space or another character. |
| `Service config not found or unreadable` | The file is not `config/services/<service>Config.properties`, or the service name given to the script is spelled differently. |
| `log4j config not found ... using log4j defaults` | There is no `config/log4j/log4j2<service>.xml`. |

**Validate** in the Studio finds most of these before you start. After that, read the log from the top: the first `FATAL`, `ERROR` or `WARN` line is almost always the cause, and everything below it is a result.
