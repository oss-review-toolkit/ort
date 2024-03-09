---
sidebar_position: 6
---

# Development

ORT is written in [Kotlin](https://kotlinlang.org/) and uses [Gradle](https://gradle.org/) as the build system, with [Kotlin script](https://docs.gradle.org/current/userguide/kotlin_dsl.html) instead of Groovy as the DSL.
Please ensure to have Gradle's incubating [configuration on demand](https://docs.gradle.org/current/userguide/multi_project_configuration_and_execution.html#sec:configuration_on_demand) feature disabled as it is currently [incompatible with ORT](https://github.com/gradle/gradle/issues/4823).

When developing on the command line, use the committed [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) to bootstrap Gradle in the configured version and execute any given tasks.
The most important tasks for this project are:

| Task        | Purpose                                                           |
|-------------|-------------------------------------------------------------------|
| assemble    | Build the JAR artifacts for all projects                          |
| detekt      | Run static code analysis on all projects                          |
| test        | Run unit tests for all projects                                   |
| funTest     | Run functional tests for all projects                             |
| installDist | Build all projects and install the start scripts for distribution |

All contributions need to pass the `detekt`, `test` and `funTest` checks before they can be merged.

For IDE development, we recommend the [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/) which can directly import the Gradle build files.
After cloning the project's source code recursively, run IDEA and use the following steps to import the project.

1. From the *Welcome* dialog: Select `Open`.

   From a running IDEA instance: Select `File` > `New` > `Project from Existing Sources...`

2. Browse to ORT's source code directory and select either the `build.gradle.kts` or the `settings.gradle.kts` file.

3. In the *Open Project* dialog select `Open as Project`.

## Debugging

To set up a basic run configuration for debugging, navigate to `OrtMain.kt` in the `cli` module and look for the `fun main(args: Array<String>)` function.
In the gutter next to it, a green "Play" icon should be displayed.
Click on it and select `Run 'OrtMainKt'` to run the entry point, which implicitly creates a run configuration.
Double-check that running ORT without any arguments will simply show the command line help in IDEA's *Run* tool window.
Finally, edit the created run configuration to your needs, e.g. by adding an argument and options to run a specific ORT sub-command.

## Testing

ORT uses [Kotest](https://github.com/kotest/kotest) as the test framework.
For running tests and individual test cases from the IDE, the [Kotest plugin](https://plugins.jetbrains.com/plugin/14080-kotest) needs to be installed.
Afterward, tests can be run via the green "Play" icon from the gutter as described above.

When running functional tests (for package managers) from the command line, ORT supports the special value "unified" for Kotest's `kotest.assertions.multi-line-diff` system property.
When set like

```shell
./gradlew -Dkotest.assertions.multi-line-diff=unified -p plugins/package-managers funTest
```

any failing tests will show the deviation from the expected result in a unified diff format that is compatible with `git apply`.
If the actual result should be taken as the new expected result, copy the diff from the console to the clipboard and run

* `wl-paste | patch -p1` (Linux with Wayland)
* `xsel -b | patch -p1` (Linux with X)
* `cat /dev/clipboard | patch -p1` (Windows with Git Bash)
* `pbpaste | patch -p1` (macOS)

to apply the diff to the local Git working tree (this does not create a commit yet).
After reviewing the changes, create a commit to accept the new expected result.

## Want to Help or have Questions?

All contributions are welcome.
If you are interested in contributing, please read our [contributing guide](https://github.com/oss-review-toolkit/.github/blob/main/CONTRIBUTING.md), and to get quick answers to any of your questions, we recommend you [join our Slack community](http://slack.oss-review-toolkit.org).
