# GradleInspector

The [GradleInspector] is an alternative analyzer for projects that use the Gradle package manager. It is supposed to
address [several][] [shortcomings][] of the "legacy" [Gradle] analyzer, but in order to not interfere with it, the
[GradleInspector] is disabled by default.

## Usage

As the [GradleInspector] is disabled by default, it needs to be enabled explicitly (along with any other package
managers that should be enabled):

    ort analyze -P ort.analyzer.enabledPackageManagers=GradleInspector[,...]

It is recommended to *not* also enable the "legacy" [Gradle] analyzer at the same time, as both analyzers would find the
same definition files.

## Implementation

In contrast to the "legacy" [Gradle] analyzer which is fully implemented as an [initialization script], the
[GradleInspector] only uses a minimal [init.gradle] to apply a [Gradle plugin], which in turn does nothing else than
registering the [OrtModelBuilder] for the ORT-specific [data model for Gradle projects]. The [GradleInspector] then
injects both the [init.gradle] and a fat-JAR for the [Gradle plugin] into the project to analyze.

## Limitations

The retrieval of the checksum values for remote artifacts is currently done via plain OkHttp calls, which means it will
not work out of the box for private repositories. To worka round this, crednetials need to be configured in `.netrc`
additionally to in Gradle. This is similar to how the "legacy" [Gradle] analyzer required to additionally configure
credentials in Maven.

Also, the `isModified` check which compares with artifacts of the same name in Maven Central is not implemented yet.

## Building

Due to some optimizations IntelliJ IDEA performs when building Gradle projects, it might be that bundling the fat-JAR
for the [Gradle plugin] as a resource into the [GradleInspector] does not always work reliably. In that case ensure that
IntelliJ IDEA has "Gradle" configured for the "Build and run using" and "Run tests using" settings, and / or try to
run the `:plugins:package-managers:gradle-plugin:fatJar` and `:plugins:package-managers:gradle-inspector:jar` tasks
manually once.

## Debugging

Due to this setup of the [GradleInspector], the [OrtModelBuilder] can actually be debugged. To do so, create a run
configuration in IntelliJ IDEA that runs `ort analyze` and sets the *VM options* to `-Dorg.gradle.debug=true`. Also,
create another *Remote JVm Debug* run configuration with default settings. Now, when debugging the first run
configuration, it will block execution of the Gradle plugin until the remote debugger is attached by debugging the
second run configuration, and any breakpoints in the [OrtModelBuilder] will be hit.

[GradleInspector]: ./src/main/kotlin/GradleInspector.kt
[several]: https://github.com/oss-review-toolkit/ort/issues/4694
[shortcomings]: https://github.com/oss-review-toolkit/ort/issues/5782
[Gradle]: ../gradle/src/main/kotlin/Gradle.kt
[initialization script]: https://docs.gradle.org/current/userguide/init_scripts.html
[init.gradle]: ./src/main/resources/init.gradle.template
[Gradle plugin]: ../gradle-plugin/src/main/kotlin/OrtModelPlugin.kt
[OrtModelBuilder]: ../gradle-plugin/src/main/kotlin/OrtModelBuilder.kt
[data model for Gradle projects]: ../gradle-model/src/main/kotlin/GradleModel.kt
