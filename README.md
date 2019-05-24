![OSS Review Toolkit Logo](./logos/ort.png)

&nbsp;

| Linux (OpenJDK 10)             | Windows (Oracle JDK 9)          |
| :----------------------------- | :------------------------------ |
| [![Linux build status][1]][2]  | [![Windows build status][3]][4] |
| [![Linux code coverage][5]][6] |                                 |

| Interact with us!              |
| :----------------------------- |
| [![ort-talk][7]][8]            |

[1]: https://travis-ci.com/heremaps/oss-review-toolkit.svg?branch=master
[2]: https://travis-ci.com/heremaps/oss-review-toolkit
[3]: https://ci.appveyor.com/api/projects/status/hbc1mn5hpo9a4hcq/branch/master?svg=true
[4]: https://ci.appveyor.com/project/heremaps/oss-review-toolkit/branch/master
[5]: https://codecov.io/gh/heremaps/oss-review-toolkit/branch/master/graph/badge.svg
[6]: https://codecov.io/gh/heremaps/oss-review-toolkit/
[7]: https://img.shields.io/badge/slack-ort--talk-blue.svg?longCache=true&logo=slack
[8]: https://join.slack.com/t/ort-talk/shared_invite/enQtMzk3MDU5Njk0Njc1LWQwMDU3NDBjYmEzNGJkM2JiYTE2MmI0MzdhZDRiZjI0MWM3YjZlZGU2ODFhNjgwOTAyZTc5ZGRhZGEyNjMwYTc

# Introduction

The goal of the OSS Review Toolkit (ORT) is to verify Free and Open Source Software license compliance by checking
project source code and dependencies.

At a high level, it works by analyzing the source code for dependencies, downloading the
source code of the dependencies, scanning all source code for license information, and summarizing the results.

The different tools that make up ORT are designed as libraries (for programmatic use) with a minimal command line
interface (for scripted use).

The toolkit is envisioned to consist of the following libraries:

* [Analyzer](#analyzer) - determines dependencies of a software project even if multiple package managers are used. No
  changes to the software project are required.
* [Downloader](#downloader) - fetches the source code based on the Analyzer's output.
* [Scanner](#scanner) - wraps existing copyright / license scanners to detect findings in local source code directories.
* [Evaluator](#evaluator) - evaluates results as OK or NOT OK against user-specified rules.
* *Advisor* * - retrieves security advisories based on Analyzer results.
* [Reporter](#reporter) - presents results in various formats (incl. `NOTICE` files), making it easy to identify
  dependencies, licenses, copyrights and policy violations.
* *Documenter* * - generates the final outcome of the review process, e.g. annotated [SPDX](https://spdx.org/) files
  that can be included into your distribution.

\* Libraries to be implemented, see our [roadmap](https://github.com/heremaps/oss-review-toolkit/projects/1) for details.

## Installation

Follow these steps to run the OSS Review Toolkit from source code:

1. Install the following basic prerequisites:

   * Git (any recent version will do).
   * OpenJDK 8 or Oracle JDK 8u161 or later (not the JRE as you need the `javac` compiler); also remember to the
    `JAVA_HOME` environment variable accordingly.
   * [Node.js](https://nodejs.org) 8.* (for the Web App reporter).
   * [Yarn](https://yarnpkg.com) 1.9.* - 1.12.* (for the Web App reporter).

2. Clone this repository with submodules by running `git clone --recurse-submodules`. If you have already cloned
   non-recursively, you can initialize submodules afterwards by running `git submodule update --init --recursive`. Note
   that submodules are only required if you intend to run tests, though.

3. Change into the created directory and run `./gradlew installDist` to build / install the start script for ORT. On
   the first run, this will also bootstrap Gradle and download required dependencies. The start script can then be run
   as:

   * `./cli/build/install/ort/bin/ort --help`

   Alternatively, ORT can be directly run by Gradle like:

   * `./gradlew cli:run --args="--help"`

   Note that in this case the working directory used by ORT is that of the `cli` project, not directory `gradlew` is
   located in (see https://github.com/gradle/gradle/issues/6074).

4. Make sure that the locale of your system is set to `en_US.UTF-8`, using other locales might lead to issues with parsing
   the output of external tools.

5. Install any missing external command line tools as listed by

   * `./cli/build/install/ort/bin/ort requirements`

   or

   * `./gradlew cli:run --args="requirements"`

Alternatively, you can also run the OSS Review Toolkit by building its Docker image:

1. Ensure you have Docker installed and its daemon running.

2. Clone this repository with submodules by running `git clone --recurse-submodules`. If you have already cloned
   non-recursively, you can initialize submodules afterwards by running `git submodule update --init --recursive`. Note
   that submodules are only required if you intend to run tests, though.

3. Change into the created directory and run `./gradlew cli:dockerBuildImage` to build the Docker image and send it to
   the locally running daemon.

4. Execute `docker run ort requirements` to verify all required command line tools are available in the container.

## Tools

<a name="analyzer"></a>

[![Analyzer](./logos/analyzer.png)](./analyzer/src/main/kotlin)

The Analyzer determines the dependencies of software projects inside the specified input directory (`-i`). It does so by
querying whatever [supported package manager](#supported-package-managers) is found. No modifications to your existing
project source code, or especially to the build system, are necessary for that to work. The tree of transitive
dependencies per project is written out as part of an
[OrtResult](https://github.com/heremaps/oss-review-toolkit/blob/master/model/src/main/kotlin/OrtResult.kt) in YAML (or
JSON, see `-f`) format to a file named `analyzer-result.yml` to the specified output directory (`-o`). The output file
exactly documents the status quo of all package-related meta-data. It can be further processed or manually edited before
passing it to one of the other tools.

<a name="downloader">&nbsp;</a>

[![Downloader](./logos/downloader.png)](./downloader/src/main/kotlin)

Taking an ORT result file with an analyzer result as the input (`-a`), the Downloader retrieves the source code of all
contained packages to the specified output directory (`-o`). The Downloader takes care of things like normalizing URLs
and using the [appropriate VCS tool](./downloader/src/main/kotlin/vcs) to checkout source code from version control.

<a name="scanner">&nbsp;</a>

[![Scanner](./logos/scanner.png)](./scanner/src/main/kotlin)

This tool wraps underlying license / copyright scanners with a common API so all supported scanners can be used in the
same way to easily run them and compare their results. If passed an ORT result file with an analyzer result (`-a`), the
Scanner will automatically download the sources of the dependencies via the Downloader and scan them afterwards. In
order to not download or scan any previously scanned sources, the Scanner can be configured (`-c`) to use a remote
storage hosted e.g. on [Artifactory](./scanner/src/main/kotlin/ArtifactoryStorage.kt) or S3 (not yet implemented, see
[#752](https://github.com/heremaps/oss-review-toolkit/issues/752)). Using the example of configuring an Artifactory
storage, the YAML-based configuration file would look like:

```yaml
artifactory_storage:
  url: "https://artifactory.domain.com/artifactory"
  repository: "generic-repository-name"
  apiToken: $ARTIFACTORY_API_KEY
```

<a name="evaluator">&nbsp;</a>

[![Evaluator](./logos/evaluator.png)](./evaluator/src/main/kotlin)

The evalutor is used to perform custom license policy checks on scan results. The rules to check against are implemented
via scripting. Currently, Kotlin script with a dedicated DSL is used for that, but support for other scripting languages
can be added as well. See [no_gpl_declared.kts](./evaluator/src/main/resources/rules/no_gpl_declared.kts) for a very
simple example of a rule written in Kotlin script which verifies that no dependencies that declare the GPL are used.

<a name="reporter">&nbsp;</a>

[![Reporter](./logos/reporter.png)](./reporter/src/main/kotlin)

The reporter generates human-readable reports from the scan result file generated by the scanner (`-s`). It is designed
to support multiple output formats. Currently the following report formats are supported:

* Excel sheet (`-f Excel`)
* NOTICE file (`-f Notice`)
* Static HTML (`-f StaticHtml`)
* Web App (`-f WebApp`)

## Getting Started

Please see [GettingStarted.md](./docs/GettingStarted.md) for an introduction to the individual tools.

## Configuration

Please see [Configuration.md](./docs/Configuration.md) for details about the ORT configuration.

## Supported package managers

Currently, the following package managers / build systems can be detected and queried for their managed dependencies:

* [Bower](http://bower.io/) (JavaScript)
* [Bundler](http://bundler.io/) (Ruby)
* [dep](https://golang.github.io/dep/) (Go)
* [DotNet](https://docs.microsoft.com/en-us/dotnet/core/tools/) (.NET, with currently some [limitations](https://github.com/heremaps/oss-review-toolkit/pull/1303#issue-253860146))
* [Glide](https://glide.sh/) (Go)
* [Godep](https://github.com/tools/godep) (Go)
* [Gradle](https://gradle.org/) (Java)
* [Maven](http://maven.apache.org/) (Java)
* [NPM](https://www.npmjs.com/) (Node.js)
* [NuGet](https://www.nuget.org/) (.NET, with currently some [limitations](https://github.com/heremaps/oss-review-toolkit/pull/1303#issue-253860146))
* [Composer](https://getcomposer.org/) (PHP)
* [PIP](https://pip.pypa.io/) (Python)
* [SBT](http://www.scala-sbt.org/) (Scala)
* [Stack](http://haskellstack.org/) (Haskell)
* [Yarn](https://yarnpkg.com/) (Node.js)

## Supported license scanners

ORT comes with some example implementations for wrappers around license / copyright scanners:

* [Askalono](https://github.com/amzn/askalono)
* [lc](https://github.com/boyter/lc)
* [Licensee](https://github.com/benbalter/licensee)
* [ScanCode](https://github.com/nexB/scancode-toolkit)

## Supported remote storages

For reusing already known scan results, ORT can currently use one of the following backends as a remote storage:

* [Artifactory](https://jfrog.com/artifactory/)

## Development

The toolkit is written in [Kotlin](https://kotlinlang.org/) and uses [Gradle](https://gradle.org/) as the build system.
We recommend the [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/) as the IDE which can
directly import the Gradle build files.

The most important root project Gradle tasks are listed in the table below.

| Task        | Purpose                                                           |
| ----------- | ----------------------------------------------------------------- |
| assemble    | Build the JAR artifacts for all projects                          |
| detekt      | Run static code analysis on all projects                          |
| test        | Run unit tests for all projects                                   |
| funTest     | Run functional tests for all projects                             |
| installDist | Build all projects and install the start scripts for distribution |

## License

Copyright (C) 2017-2019 HERE Europe B.V.

See the [LICENSE](./LICENSE) file in the root of this project for license details.
