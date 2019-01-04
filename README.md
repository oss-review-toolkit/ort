# OSS Review Toolkit

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

The goal of the OSS Review Toolkit (ORT) is to verify Free and Open Source Software licence compliance by checking
project source code and dependencies.

At a high level, it works by analyzing the source code for dependencies, downloading the
source code of the dependencies, scanning all source code for license information, and summarizing the results.

The different tools that make up ORT are designed as libraries (for programmatic use) with a minimal command line
interface (for scripted use).

The toolkit is envisioned to consist of the following libraries:

* *Analyzer* - determines dependencies of a software project even if multiple package managers are used. No changes to
  the software project are required.
* *Downloader* - fetches the source code based on the Analyzer's output.
* *Scanner* - wraps existing copyright / license scanners to detect findings in local source code directories.
* *Evaluator* - evaluates results as OK or NOT OK against user-specified rules.
* *Advisor* * - retrieves security advisories based on Analyzer results.
* *Reporter* - presents results in various formats (incl. `NOTICE` files), making it easy to identify dependencies,
  licenses, copyrights and policy violations.
* *Documenter* * - generates the final outcome of the review process, e.g. annotated [SPDX](https://spdx.org/) files
  that can be included into your distribution.

\* Libraries to be implemented, see our [roadmap](https://github.com/heremaps/oss-review-toolkit/projects/1) for details.

## Installation

Follow these steps to run the OSS Review Toolkit from source code:

1. Ensure OpenJDK 8 or Oracle JDK 8u161 or later (not the JRE as you need the `javac` compiler) is installed and the
   `JAVA_HOME` environment variable set.

   In addition to Java (version >= 8) the following tools are required:

   * Git (any recent version will do)
   * [Node.js](https://nodejs.org) 8.*
   * [NPM](https://www.npmjs.com) 5.5.* - 6.4.*
   * [Yarn](https://yarnpkg.com) 1.9.* - 1.12.*

2. Clone this repository with submodules by running `git clone --recurse-submodules`. If you have already cloned
   non-recursively, you can initialize submodules afterwards by running `git submodule update --init --recursive`.

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

## Tools

### [analyzer](./analyzer/src/main/kotlin)

The Analyzer determines the dependencies of software projects inside the specified input directory (`-i`). It does so by
querying whatever [supported package manager](./analyzer/src/main/kotlin/managers) is found. No modifications to your
existing project source code, or especially to the build system, are necessary for that to work. The tree of transitive
dependencies per project is written out as [ABCD](https://github.com/nexB/aboutcode/tree/master/aboutcode-data)-style
YAML (or JSON, see `-f`) file named `analyzer-result.yml` to the specified output directory (`-o`). The output file
exactly documents the status quo of all package-related meta-data. It can be further processed or manually edited before
passing it to one of the other tools.

### [downloader](./downloader/src/main/kotlin)

Taking the ABCD-syle dependencies file as the input (`-d`), the Downloader retrieves the source code of all contained
packages to the specified output directory (`-o`). The Downloader takes care of things like normalizing URLs and using
the [appropriate VCS tool](./downloader/src/main/kotlin/vcs) to checkout source code from version control.

### [scanner](./scanner/src/main/kotlin)

This tool wraps underlying license / copyright scanners with a common API. This way all supported scanners can be used
in the same way to easily run them and compare their results. If passed a dependencies analysis file (`-d`), the Scanner
will automatically download the sources of the dependencies via the Downloader and scan them afterwards. In order to not
download or scan any previously scanned sources, the Scanner can be configured (`-c`) to use a remote cache, hosted
e.g. on [Artifactory](./scanner/src/main/kotlin/ArtifactoryCache.kt) or S3 (not yet implemented, see
[#752](https://github.com/heremaps/oss-review-toolkit/issues/752)). Using the example of configuring an Artifactory
cache, the YAML-based configuration file would look like:

```yaml
artifactory_cache:
  url: "https://artifactory.domain.com/artifactory/generic-repository-name"
  apiToken: $ARTIFACTORY_API_KEY
```

### [reporter](./reporter/src/main/kotlin)

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
* [Glide](https://glide.sh/) (Go)
* [Godep](https://github.com/tools/godep) (Go)
* [Gradle](https://gradle.org/) (Java)
* [Maven](http://maven.apache.org/) (Java)
* [NPM](https://www.npmjs.com/) (Node.js)
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

## Supported remote caches

For reusing already known scan results, ORT can currently use one of the following backends as a remote cache:

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

Copyright (C) 2017-2018 HERE Europe B.V.

See the [LICENSE](./LICENSE) file in the root of this project for license details.
