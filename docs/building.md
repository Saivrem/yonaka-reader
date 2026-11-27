# Build and test Yonaka Reader

[Yonaka Reader](../README.md)

## Requirements

- JDK 25

The Gradle wrapper is included, and the build uses a Java 25 toolchain. A separate Gradle installation is not required.

## Build and test

Run the full verification and build an executable fat JAR:

```bash
./gradlew clean test fatJar
```

The resulting application is written to:

```text
build/libs/yonaka-reader-0.23.1.jar
```

## Run

Open the application without an initial file:

```bash
java -jar build/libs/yonaka-reader-0.23.1.jar
```

For development, use `./gradlew run` or the shared **Yonaka Reader** run configuration
in IntelliJ IDEA. Both enable native access required by SQLite on Java 25. A manually
created classpath/Main configuration must include `--enable-native-access=ALL-UNNAMED`
in its VM options; executable JAR and native-package launches already enable it.
`./gradlew launchSmoke` verifies SQLite access with unapproved native calls denied.

Open an image, a directory of images, a PDF volume, or a ZIP/CBZ archive immediately:

```bash
java -jar build/libs/yonaka-reader-0.23.1.jar /path/to/page.jpg
java -jar build/libs/yonaka-reader-0.23.1.jar /path/to/chapter
java -jar build/libs/yonaka-reader-0.23.1.jar /path/to/volume.zip
java -jar build/libs/yonaka-reader-0.23.1.jar /path/to/volume.pdf
```

## Project structure

- `src/main/java/org/dustyroom/be` — file discovery, ordering, models, and image iterators
- `src/main/java/org/dustyroom/ui` — Swing window, actions, components, navigation, and rendering
- `src/test/java` — automated tests

The main entry point is `org.dustyroom.Main`.

## Continuous integration

GitHub Actions runs tests, builds the runnable JAR and checks packaged SQLite on
Linux, macOS and Windows using Java 25. CI runs on pushes to `main` and when a
pull request targeting `main` is opened, reopened or updated. Each platform runs
its tests once and builds its native packages in the same job. Test reports,
native packages and one runnable JAR are available as artifacts for 14 days.
The packaged SQLite check can also be run locally:

```shell
./gradlew clean test fatJar packagedStoreSmoke
```

These checks cover headless behavior; native menus, drag-and-drop and display
scaling still need manual desktop verification on each platform.
