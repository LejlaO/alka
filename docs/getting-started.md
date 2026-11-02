# Getting started: Java, Maven, a terminal and IntelliJ

For someone who has never used Maven. It says what to install, where to type each command and what you should see. It was written and tried on Windows; Linux and macOS are described too, but not tried yet.

If you already have Java 21 and Maven 3.9 and know your way around a terminal, go straight to [Your first plugin](your-first-plugin.md).

## What you need

| Thing | What it is | Needed for |
|---|---|---|
| **Java 21 or newer** (a JDK) | The program that runs Alka and compiles your plugin. | Everything. |
| **Maven 3.9 or newer** | The tool that builds a Java project (`mvn`). It also downloads what the project needs. | Making your own plugin. Not needed just to run the demo. |
| **A terminal** | A window where you type commands. | Maven. |
| **IntelliJ IDEA** (optional) | An editor for Java. The free *Community* edition is enough. | Only if you want to edit the code comfortably. It brings its own Maven. |

To only run the demo you need nothing but Java: unzip the package and start `studio.bat` (see the [README](../README.md)).

## 1. A terminal

**Windows.** The easiest way to get a terminal in the right folder: open the folder in File Explorer, click the address bar (the path at the top), type `cmd` and press Enter. A black window opens, already in that folder. This is the **Command Prompt**.

Use the Command Prompt (`cmd`) for the commands of Alka, **not Windows PowerShell**. The old Windows PowerShell breaks an option like `-DarchetypeVersion=0.2.0` at the dot, and Maven then fails with a strange error. If you use PowerShell anyway, put every option that starts with `-D` in quotes, for example `"-DarchetypeVersion=0.2.0"`.

**macOS.** Open *Terminal* (Spotlight, then `Terminal`). **Linux:** any terminal.

To go to a folder by hand: `cd C:\Users\you\projects` (Windows) or `cd ~/projects`. To make one first: `mkdir projects`.

## 2. Java 21

In the terminal type:

```
java -version
```

You should see a line like `openjdk version "21.0.5"` (any number from 21 up is good). If you see `'java' is not recognized`, or a version below 21:

1. Go to https://adoptium.net and download **Temurin 21** (LTS) for your system. On Windows take the `.msi` installer.
2. Run it. On the screen with the list of features switch on **Add to PATH** and **Set JAVA_HOME variable**.
3. **Close the terminal and open a new one** (an open terminal does not see the change), and type `java -version` again.

## 3. Maven

```
mvn -version
```

You should see `Apache Maven 3.9.x` and, further down, `Java version: 21...`. If the terminal says `'mvn' is not recognized`, install Maven.

**Windows**

1. Open https://maven.apache.org/download.cgi and download the **Binary zip archive** (`apache-maven-3.9.x-bin.zip`).
2. Unzip it to a short folder without spaces, for example `C:\tools`, so that the file `C:\tools\apache-maven-3.9.x\bin\mvn.cmd` exists.
3. Add the `bin` folder to the PATH: press the Windows key, type `environment variables`, open **Edit the system environment variables**, click **Environment Variables...**. In the upper list (*User variables*) select **Path**, click **Edit...**, **New**, and type `C:\tools\apache-maven-3.9.x\bin`. Confirm with **OK** in all the windows.
4. Open a **new** terminal and type `mvn -version`.

**macOS:** `brew install maven` (needs [Homebrew](https://brew.sh)). **Linux:** do the same as on Windows with the `.tar.gz` from the Maven site (the Maven that comes with the system is often older than 3.9) and add its `bin` folder to the PATH in `~/.bashrc`.

## 4. Make the project

Make a folder for your projects and open a terminal in it (Windows: the address bar trick above). Then:

```
mvn archetype:generate -DarchetypeGroupId=com.alkadev -DarchetypeArtifactId=alka-sample-archetype -DarchetypeVersion=0.2.0
```

The first time Maven downloads a lot of files; that takes a minute and prints a lot of lines. Then it asks four questions. You type the first two and press Enter for the rest:

| Question | What to type |
|---|---|
| `Define value for property 'groupId':` | `com.example` (the name of your company or yourself, written backwards like a web address) |
| `Define value for property 'artifactId':` | `myalka` (the name of your project, and of its folder) |
| `Define value for property 'version' 1.0-SNAPSHOT:` | just Enter |
| `Define value for property 'package' com.example:` | just Enter |
| `Confirm properties configuration:` ... `Y: :` | just Enter |

At the end it says `BUILD SUCCESS` and there is a new folder `myalka`. In it: a manager, a plugin that talks to it, and a service that wires them.

## 5. Build it and put it into Alka

You need the unzipped Alka package (the file `alka-0.2.0.zip` from the [release page](https://github.com/LejlaO/alka/releases/latest)). In it is a folder `home`. You need the full path of that folder. On Windows: open it in File Explorer, click the address bar, and copy the path, for example `C:\Users\you\Downloads\alka-0.2.0\home`.

In the terminal, go into the project and build:

```
cd myalka
mvn install
```

`BUILD SUCCESS` again. Then copy the result into the home (put your own path in; Command Prompt shown):

```
mvn package alka:deploy -Dalka.home=C:\Users\you\Downloads\alka-0.2.0\home
```

If the path has a space in it, put the whole option in quotes: `"-Dalka.home=C:\My Files\alka-0.2.0\home"`.

Now start the Studio (`studio.bat` in the package). The service `myalka` is in the list. Select it and press **Start**. The rest is in [Your first plugin](your-first-plugin.md).

## 6. IntelliJ IDEA (optional)

You can type everything above in a terminal and use any text editor, but IntelliJ is easier for editing the Java files. Download the free **Community** edition from https://www.jetbrains.com/idea/download. The screens differ a little between versions.

**Open the project**

1. Make the project with the command above (step 4).
2. In IntelliJ: **File, Open...**, choose the folder `myalka` (the one that has the file `pom.xml`) and click **OK**. Answer **Trust Project**.
3. IntelliJ sees the `pom.xml` and loads it as a Maven project. Wait until the bar at the bottom stops showing progress. If it asks, click **Load Maven Project**.

**Java 21 in IntelliJ**

**File, Project Structure...** (Ctrl+Alt+Shift+S), tab **Project**, field **SDK**: choose a 21. If there is none, **Add SDK, Download JDK**, version 21, vendor Eclipse Temurin.

**Build and deploy from IntelliJ** (no terminal, and no problem with PowerShell)

1. Open the Maven panel: **View, Tool Windows, Maven** (or the small `m` on the right edge).
2. Expand **myalka, Lifecycle** and double-click **install**. The window at the bottom shows the build and ends with `BUILD SUCCESS`.
3. To copy it into Alka, press **Ctrl** twice quickly (the *Run Anything* box) and type the command, with your own path:

```
mvn package alka:deploy -Dalka.home=C:\Users\you\Downloads\alka-0.2.0\home
```

Press Enter. The same for every later change: edit the code, run `install`, run the deploy command (stop the service in the Studio first).

**Which Maven does IntelliJ use?** **File, Settings, Build, Execution, Deployment, Build Tools, Maven**, field **Maven home path**. The default *Bundled Maven* of a recent IntelliJ is 3.9 and is fine. If it shows an older one, click the field and choose the folder of the Maven you installed in step 3.

**Which Java does Maven use in IntelliJ?** Not always the one you expect. IntelliJ starts Maven with its own setting, and that can be an older Java (17, for example) even when the project uses 21. Then the build fails with `invalid flag: -proc:full`. Set it in **File, Settings, Build, Execution, Deployment, Build Tools, Maven, Runner**, field **JRE**: choose a 21 (Temurin 21; add it with **File, Project Structure, SDKs, +, Download JDK** if it is not in the list). Do the same in **Maven, Importing**, field **JDK for importer**, and set **File, Project Structure, Project, SDK** to the same 21.

**Make the project inside IntelliJ instead of the terminal:** **File, New, Project...**, on the left **Maven Archetype**. Next to *Archetype* click **Add...** and type group id `com.alkadev`, artifact id `alka-sample-archetype`, version `0.2.0`. Give the project a name (it becomes the artifact id, for example `myalka`) and click **Create**.

## When something goes wrong

| What you see | Cause and cure |
|---|---|
| `'mvn' is not recognized` / `command not found` | Maven is not installed or not on the PATH. Step 3, and open a **new** terminal. |
| `'java' is not recognized` | Step 2. |
| `The JAVA_HOME environment variable is not defined correctly` | Reinstall Java with *Set JAVA_HOME variable* switched on, or set `JAVA_HOME` to the folder of the JDK (the one that has a `bin` folder) as in step 3, point 3. |
| `The goal you specified requires a project to execute but there is no POM in this directory` (right after `archetype:generate`), or `Unknown lifecycle phase` | You typed the command in Windows PowerShell, which breaks `-D...=0.2.0` at the dot. Use the Command Prompt, or put each `-D...` option in quotes. |
| `Could not transfer artifact ... status code: 502` or `Connection timed out` | The internet or Maven Central had a hiccup. Run the same command again. |
| `Unsupported class file major version` / `invalid target release` | Your Java is older than 21. Step 2. |
| `invalid flag: -proc:full` / `release version 21 not supported` | Maven runs on a Java older than 21. In IntelliJ it is the **JRE** of the Maven *Runner* setting, see step 6. In a terminal, check `mvn -version`. |
| `alka:deploy` says it cannot replace a file | The service is running and has the jar open (Windows). Stop it in the Studio, deploy, start again. |
| The service `myalka` is not in the Studio | The path in `-Dalka.home=` is not the folder `home` of the package that the Studio shows. Check it, and press the refresh button of the Studio. |

Still stuck? Open an [issue](https://github.com/LejlaO/alka/issues) and paste the text of the error.
