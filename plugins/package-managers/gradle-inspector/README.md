# GradleInspector

The [GradleInspector] is the default analyzer for projects that use the Gradle package manager.
It is supposed to address [several] [shortcomings] of the "legacy" [Gradle] analyzer, which is disabled by default.

> [!NOTE]
> It is *not* recommended to also enable the "legacy" [Gradle] analyzer at the same time, as both analyzers would find the same definition files.

## Implementation

In contrast to the "legacy" [Gradle] analyzer which is fully implemented as an [initialization script], the [GradleInspector] only uses a minimal [init.gradle] to apply a [Gradle plugin], which in turn does nothing else than registering the [OrtModelBuilder] for the ORT-specific [data model for Gradle projects].
The [GradleInspector] then injects both the [init.gradle] and a fat-JAR for the [Gradle plugin] into the project to analyze.

## Debugging

Due to the implementation of the [GradleInspector], the [OrtModelBuilder] can actually be debugged.
To do so, create the following run configuration in IntelliJ IDEA:

1. A *Kotlin* configuration that runs `ort analyze` and sets the *VM options* to `-Dorg.gradle.debug=true`.
2. A *Remote JVM Debug* configuration with default settings.

Now, when debugging the first run configuration, wait until the ORT analyzer reaches the point of resolving Gradle dependencies.
Execution of the Gradle plugin will be blocked until the remote debugger is attached by debugging the second run configuration, and any breakpoints in the [OrtModelBuilder] will be hit.

## Limitations

Currently, the [GradleInspector] has the following known limitations:

* The retrieval of the checksum values for remote artifacts is currently done via plain OkHttp calls, which means it will not work out of the box for private repositories.
  To work around this, credentials need to be configured in a `.netrc` file in addition to the Gradle build.
* The `isModified` check which compares build artifacts with artifacts of the same name in Maven Central is not implemented yet.
* The implementation [cannot deal with classifiers and / or non-JAR artifacts].

[GradleInspector]: ./src/main/kotlin/GradleInspector.kt
[several]: https://github.com/oss-review-toolkit/ort/issues/4694
[shortcomings]: https://github.com/oss-review-toolkit/ort/issues/5782
[Gradle]: ../gradle/src/main/kotlin/Gradle.kt
[initialization script]: https://docs.gradle.org/current/userguide/init_scripts.html
[init.gradle]: ./src/main/resources/template.init.gradle
[Gradle plugin]: ../gradle-plugin/src/main/kotlin/OrtModelPlugin.kt
[OrtModelBuilder]: ../gradle-plugin/src/main/kotlin/OrtModelBuilder.kt
[data model for Gradle projects]: ../gradle-model/src/main/kotlin/GradleModel.kt
[cannot deal with classifiers and / or non-JAR artifacts]: https://github.com/oss-review-toolkit/ort/issues/7995
