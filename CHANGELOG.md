# Changes

## 0.2.0

**Breaking: the artifact ids changed.** If you depend on the API of 0.1.0, change the artifact ids in your pom. The group ids, the Java packages and the classes are the same.

| 0.1.0 | 0.2.0 |
|---|---|
| `com.alkadev:annotations` | `com.alkadev:alka-annotations` |
| `com.alkadev.utils:common` | `com.alkadev.utils:alka-common` |
| `com.alkadev.utils:objectmanager` | `com.alkadev.utils:alka-objectmanager` |
| `com.alkadev.utils:objectplugin` | `com.alkadev.utils:alka-objectplugin` |
| `com.alkadev:utils` | `com.alkadev:alka-utils` |
| `com.alkadev:samples` | `com.alkadev:alka-samples` |

A plugin that uses `alka-plugin-parent` needs only the new version of the parent.

**Java 21 is the minimum** (it was 25). The code was not using anything newer. The API jars, the Maven plugin and the package are built for Java 21 and all the tests were run on it.

## 0.1.0

The first public version: the API, the parent pom, the Maven plugin, the project generator, two samples, and the package with the engine and Alka Studio.
