# Local verification

The source tree and all JSON resources were syntax-checked while assembling this workspace.

A full NeoForge compilation requires Gradle to download the Minecraft/NeoForge development dependencies. If the first Gradle import fails because of a transient download/cache problem, try:

```bash
./gradlew --refresh-dependencies
./gradlew clean
./gradlew runClient
```

For a compile-only check:

```bash
./gradlew compileJava
```

When reporting a problem, the most useful files are:

```text
run/logs/latest.log
run/crash-reports/<latest crash>.txt
```

For compile errors, copy the complete `compileJava` error section, including the first error in the list.
