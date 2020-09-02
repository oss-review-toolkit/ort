![OSS Review Toolkit Logo](./logos/ort.png)

&nbsp;

| Linux (OpenJDK 11)             | Windows (Oracle JDK 11)         | JitPack (OpenJDK 8)             |
| :----------------------------- | :------------------------------ | :------------------------------ |
| [![Linux build status][1]][2]  | [![Windows build status][3]][4] | [![JitPack build status][5]][6] |
| [![Linux code coverage][7]][8] |                                 |                                 |

| License status           | Code quality      | TODOs              | Interact with us!              |
| :----------------------- | :---------------- | :----------------- | :----------------------------- |
| [![REUSE status][9]][10] | [![LGTM][11]][12] | [![TODOs][13]][14] | [![ort-talk][15]][16]          |

[1]: https://travis-ci.com/oss-review-toolkit/ort.svg?branch=master
[2]: https://travis-ci.com/oss-review-toolkit/ort
[3]: https://ci.appveyor.com/api/projects/status/8oh5ld40c8h19jr5/branch/master?svg=true
[4]: https://ci.appveyor.com/project/oss-review-toolkit/ort/branch/master
[5]: https://jitpack.io/v/oss-review-toolkit/ort.svg
[6]: https://jitpack.io/#oss-review-toolkit/ort
[7]: https://codecov.io/gh/oss-review-toolkit/ort/branch/master/graph/badge.svg
[8]: https://codecov.io/gh/oss-review-toolkit/ort/
[9]: https://api.reuse.software/badge/github.com/oss-review-toolkit/ort
[10]: https://api.reuse.software/info/github.com/oss-review-toolkit/ort
[11]: https://img.shields.io/lgtm/alerts/g/oss-review-toolkit/ort.svg?logo=lgtm&logoWidth=18
[12]: https://lgtm.com/projects/g/oss-review-toolkit/ort/alerts/
[13]: https://badgen.net/https/api.tickgit.com/badgen/github.com/oss-review-toolkit/ort
[14]: https://www.tickgit.com/browse?repo=github.com/oss-review-toolkit/ort
[15]: https://img.shields.io/badge/slack-ort--talk-blue.svg?longCache=true&logo=slack
[16]: https://join.slack.com/t/ort-talk/shared_invite/enQtMzk3MDU5Njk0Njc1LThiNmJmMjc5YWUxZTU4OGI5NmY3YTFlZWM5YTliZmY5ODc0MGMyOWIwYmRiZWFmNGMzOWY2NzVhYTI0NTJkNmY

# Introduction

The OSS Review Toolkit (ORT) aims to assist with the tasks that commonly need to be performed in the context of license
compliance checks, especially for (but not limited to) Free and Open Source Software dependencies.

It does so by orchestrating a _highly customizable_ pipeline of tools that _abstract away_ the underlying services.
These tools are implemented as libraries (for programmatic use) and exposed via a command line interface (for scripted
use):

* [_Analyzer_](#analyzer) - determines the dependencies of projects and their meta-data, abstracting which package
  managers or build systems are actually being used.
* [_Downloader_](#downloader) - fetches all source code of the projects and their dependencies, abstracting which
  Version Control System (VCS) or other means are used to retrieve the source code.
* [_Scanner_](#scanner) - uses configured source code scanners to detect license / copyright findings, abstracting
  the type of scanner.
* [_Evaluator_](#evaluator) - evaluates license / copyright findings against customizable policy rules and license
  classifications.
* [_Reporter_](#reporter) - presents results in various formats such as visual reports, Open Source notices or
  Bill-Of-Materials (BOMs) to easily identify dependencies, licenses, copyrights or policy rule violations.

The following tools are [planned](https://github.com/oss-review-toolkit/ort/projects/1) but not yet available:

* _Advisor_ - retrieves security advisories based on the Analyzer result.
* _Documenter_ - generates the final outcome of the review process incl. legal conclusions, e.g. annotated
  [SPDX](https://spdx.dev/) files that can be included into the distribution.

# Installation

## From binaries

Preliminary binary artifacts for ORT are currently available via
[JitPack](https://jitpack.io/#oss-review-toolkit/ort). Please note that due to limitations with the JitPack build
environment, the reporter is not able to create the Web App report.

## From sources

Install the following basic prerequisites:

* Git (any recent version will do).

Then clone this repository. If you intend to run tests, you need to clone with submodules by running
`git clone --recurse-submodules`. If you have already cloned non-recursively, you can initialize submodules afterwards
by running `git submodule update --init --recursive`.

### Build using Docker

Install the following basic prerequisites:

* Docker (and ensure its daemon is running).

Change into the directory with ORT's source code and run `docker build -t ort .`.

### Build natively

Install these additional prerequisites:

* OpenJDK 8 or Oracle JDK 8u161 or later (not the JRE as you need the `javac` compiler); also remember to set the
  `JAVA_HOME` environment variable accordingly.

Change into the directory with ORT's source code and run `./gradlew installDist` (on the first run this will bootstrap
Gradle and download all required dependencies).

## Basic usage

ORT can now be run using

    ./cli/build/install/ort/bin/ort --help

Note that if you make any changes to ORT's source code, you would have to regenerate the distribution using the steps
above.

To avoid that, you can also build and run ORT in one go (if you have the prerequisites from the
[Build natively](#build-natively) section installed):

    ./gradlew cli:run --args="--help"

Note that in this case the working directory used by ORT is that of the `cli` project, not the directory `gradlew` is
located in (see https://github.com/gradle/gradle/issues/6074).

# Running the tools

Like for building ORT from sources you have the option to run ORT from a Docker image (which comes with all runtime
dependencies) or to run ORT natively (in which case some additional requirements need to be fulfilled).

## Run using Docker

After you have built the image as [described above](#build-using-docker), simply run
`docker run <DOCKER_ARGS> ort <ORT_ARGS>`. You typically use `<DOCKER_ARGS>` to mount the project directory to analyze
into the container for ORT to access it, like:

    docker run -v /workspace:/project ort --info analyze -f JSON -i /project -o /project/ort/analyzer

## Run natively

First of all, make sure that the locale of your system is set to `en_US.UTF-8` as using other locales might lead to
issues with parsing the output of some external tools.

Then install any missing external command line tools as listed by

    ./cli/build/install/ort/bin/ort requirements

or

    ./gradlew cli:run --args="requirements"

Then run ORT like

    ./cli/build/install/ort/bin/ort --info analyze -f JSON -i /project -o /project/ort/analyzer

or

    ./gradlew cli:run --args="--info analyze -f JSON -i /project -o /project/ort/analyzer"

## Running on CI

A basic ORT pipeline (using the _analyzer_, _scanner_ and _reporter_) can easily be run on
[Jenkins CI](https://jenkins.io/) by using the [Jenkinsfile](./Jenkinsfile) in a (declarative)
[pipeline](https://jenkins.io/doc/book/pipeline/) job. Please see the [Jenkinsfile](./Jenkinsfile) itself for
documentation of the required Jenkins plugins. The job accepts various parameters that are translated to ORT command
line arguments. Additionally, one can trigger a downstream job which e.g. further processes scan results. Note that it
is the downstream job's responsibility to copy any artifacts it needs from the upstream job.

## Getting started

Please see [Getting Started](./docs/getting-started.md) for an introduction to the individual tools.

## Configuration

Please see the documentation below for details about the ORT configuration.

* The [ORT configuration](./model/src/main/resources/reference.conf) file - the main configuration file for the
  operation of ORT. This configuration is maintained by an administrator who manages the ORT instance. In contrast to
  the configuration files in the following, this file rarely changes once ORT is operational.
* The [.ort.yml](./docs/config-file-ort-yml.md) file - project-specific license finding curations, exclusions
  and resolutions to address issues found within a project's code repository.
* The [package configuration](./docs/config-file-package-configuration-yml.md) file - package (dependency) and provenance
  specific license finding curations and exclusions to address issues found within a scan result for a package.
* The [curations.yml](./docs/config-file-curations-yml.md) file - curations correct invalid or missing package metadata
  and set the concluded license for packages.
* The [resolutions.yml](./docs/config-file-resolutions-yml.md) file - resolutions allow *resolving* any issues
  or policy rule violations by providing a reason why they are acceptable and can be ignored.

# Details on the tools

<a name="analyzer"></a>

[![Analyzer](./logos/analyzer.png)](./analyzer/src/main/kotlin)

The _analyzer_ is a Software Composition Analysis (SCA) tool that determines the dependencies of software projects
inside the specified input directory (`-i`). It does so by querying the detected package managers; **no modifications**
to your existing project source code, like applying build system plugins, are necessary for that to work. The tree of
transitive dependencies per project is written out as part of an
[OrtResult](https://github.com/oss-review-toolkit/ort/blob/master/model/src/main/kotlin/OrtResult.kt) in YAML (or
JSON, see `-f`) format to a file named `analyzer-result.yml` in the specified output directory (`-o`). The output file
exactly documents the status quo of all package-related meta-data. It can be further processed or manually edited before
passing it to one of the other tools.

Currently, the following package managers are supported:

* [Bower](http://bower.io/) (JavaScript)
* [Bundler](http://bundler.io/) (Ruby)
* [Cargo](https://doc.rust-lang.org/cargo/) (Rust)
* [Conan](https://conan.io/) (C / C++, *experimental* as the VCS locations often times do not contain the actual source
  code, see [issue #2037](https://github.com/oss-review-toolkit/ort/issues/2037))
* [dep](https://golang.github.io/dep/) (Go)
* [DotNet](https://docs.microsoft.com/en-us/dotnet/core/tools/) (.NET, with currently some
  [limitations](https://github.com/oss-review-toolkit/ort/pull/1303#issue-253860146))
* [Glide](https://glide.sh/) (Go)
* [Godep](https://github.com/tools/godep) (Go)
* [GoMod](https://github.com/golang/go/wiki/Modules) (Go, *experimental* as only proxy-based source artifacts but no VCS
  locations are supported)
* [Gradle](https://gradle.org/) (Java)
* [Maven](http://maven.apache.org/) (Java)
* [NPM](https://www.npmjs.com/) (Node.js)
* [NuGet](https://www.nuget.org/) (.NET, with currently some
  [limitations](https://github.com/oss-review-toolkit/ort/pull/1303#issue-253860146))
* [Composer](https://getcomposer.org/) (PHP)
* [PIP](https://pip.pypa.io/) (Python)
* [Pipenv](https://pipenv.readthedocs.io/) (Python)
* [Pub](https://pub.dev/) (Dart / Flutter)
* [SBT](http://www.scala-sbt.org/) (Scala)
* [SPDX](https://spdx.dev/specifications/) (SPDX documents used to describe
  [projects](./analyzer/src/funTest/assets/projects/synthetic/spdx/project/project.spdx.yml) or
  [packages](./analyzer/src/funTest/assets/projects/synthetic/spdx/package/libs/curl/package.spdx.yml))
* [Stack](http://haskellstack.org/) (Haskell)
* [Yarn](https://yarnpkg.com/) (Node.js)

<a name="downloader">&nbsp;</a>

[![Downloader](./logos/downloader.png)](./downloader/src/main/kotlin)

Taking an ORT result file with an _analyzer_ result as the input (`-i`), the _downloader_ retrieves the source code of
all contained packages to the specified output directory (`-o`). The _downloader_ takes care of things like normalizing
URLs and using the [appropriate VCS tool](./downloader/src/main/kotlin/vcs) to checkout source code from version
control.

Currently, the following Version Control Systems (VCS) are supported:

* [CVS](https://en.wikipedia.org/wiki/Concurrent_Versions_System)
* [Git](https://git-scm.com/)
* [Git-Repo](https://source.android.com/setup/develop/repo)
* [Mercurial](https://www.mercurial-scm.org/)
* [Subversion](https://subversion.apache.org/)

<a name="scanner">&nbsp;</a>

[![Scanner](./logos/scanner.png)](./scanner/src/main/kotlin)

This tool wraps underlying license / copyright scanners with a common API so all supported scanners can be used in the
same way to easily run them and compare their results. If passed an ORT result file with an analyzer result (`-i`), the
_scanner_ will automatically download the sources of the dependencies via the _downloader_ and scan them afterwards.

Currently, the following license scanners are supported:

* [Askalono](https://github.com/amzn/askalono)
* [lc](https://github.com/boyter/lc)
* [Licensee](https://github.com/benbalter/licensee)
* [ScanCode](https://github.com/nexB/scancode-toolkit)

For a comparison of some of these, see this
[Bachelor Thesis](https://osr.cs.fau.de/2019/08/07/final-thesis-a-comparison-study-of-open-source-license-crawler/).

## Storage Backends

In order to not download or scan any previously scanned sources again, the _scanner_ can use a storage backend to store
scan results for later reuse.

### Local File Storage

By default the _scanner_ stores scan results on the local file system in the current user's home directory (i.e.
`~/.ort/scanner/scan-results`) for later reuse. The storage directory can be customized by passing an ORT configuration
file (`-c`) that contains a respective local file storage configuration:

```hocon
ort {
  scanner {
    fileBasedStorage {
      backend {
        localFileStorage {
          directory = "/tmp/ort/scan-results"
          compression = false
        }
      }
    }
  }
}
```

### HTTP Storage

Any HTTP file server can be used to store scan results. Custom headers can be configured to provide authentication
credentials. For example, to use Artifactory to store scan results, use the following configuration:

```hocon
ort {
  scanner {
    fileBasedStorage {
      backend {
        httpFileStorage {
          url = "https://artifactory.domain.com/artifactory/repository/scan-results"
          headers {
            X-JFrog-Art-Api = "api-token"
          }
        }
      }
    }
  }
}
```

### PostgreSQL Storage

To use PostgreSQL for storing scan results you need at least version 9.4, create a database with the `client_encoding`
set to `UTF8`, and a configuration like the following:

```hocon
ort {
  scanner {
    postgresStorage {
      url = "jdbc:postgresql://example.com:5444/database"
      schema = "schema"
      username = "username"
      password = "password"
      sslmode = "verify-full"
    }
  }
}
```

While the specified schema already needs to exist, the _scanner_ will itself create a table called `scan_results` and
store the data in a [jsonb](https://www.postgresql.org/docs/current/datatype-json.html) column.

If you do not want to use SSL set the `sslmode` to `disable`, other possible values are explained in the
[documentation](https://jdbc.postgresql.org/documentation/head/ssl-client.html). For other supported configuration
options see [PostgresStorageConfiguration.kt](./model/src/main/kotlin/config/PostgresStorageConfiguration.kt).

<a name="evaluator">&nbsp;</a>

[![Evaluator](./logos/evaluator.png)](./evaluator/src/main/kotlin)

The _evaluator_ is used to perform custom license policy checks on scan results. The rules to check against are
implemented as scripts (currently Kotlin scripts, with a dedicated DSL, but support for other scripting can be added as
well. See [rules.kts](./examples/rules.kts) for an example file.

<a name="reporter">&nbsp;</a>

[![Reporter](./logos/reporter.png)](./reporter/src/main/kotlin)

The _reporter_ generates human-readable reports from the scan result file generated by the _scanner_ (`-s`). It is
designed to support multiple output formats.

Currently, the following report formats are supported (reporter names are case-insensitive):

* [Amazon OSS Attribution Builder](https://github.com/amzn/oss-attribution-builder) document (*experimental*, `-f AmazonOssAttributionBuilder`)
* [Antenna Attribution Document (PDF)](./docs/reporters/AntennaAttributionDocumentReporter.md) (`-f AntennaAttributionDocument`)
* [CycloneDX](https://cyclonedx.org/) BOM (`-f CycloneDx`)
* [Excel](https://products.office.com/excel) sheet (`-f Excel`)
* [GitLabLicenseModel](https://docs.gitlab.com/ee/ci/pipelines/job_artifacts.html#artifactsreportslicense_scanning-ultimate) (`-f GitLabLicenseModel`)
* [NOTICE](http://www.apache.org/dev/licensing-howto.html) file in two variants
  * List license texts and copyrights by package (`-f NoticeByPackage`)
  * Summarize all license texts and copyrights (`-f NoticeSummary`)
* [SPDX Document](https://spdx.dev/specifications/), version 2.2 (`-f SpdxDocument`)
* Static HTML (`-f StaticHtml`)
* Web App (`-f WebApp`)

# System requirements

ORT is being continuously used on Linux, Windows and macOS by the
[core development team](https://github.com/orgs/oss-review-toolkit/teams/core-devs), so these operating systems are
considered to be well supported.

To run the ORT binaries (also see [Installation from binaries](#from-binaries))) at least a Java Runtime Environment
(JRE) version 8 is required, but using version 11 is recommended. Memory and CPU requirements vary depending on the size
and type of project(s) to analyze / scan, but the general recommendation is to configure the JRE with 8 GiB of memory
(`-Xmx=8g`) and to use a CPU with at least 4 cores.

If ORT requires external tools in order to analyze a project, these tools are listed by the `ort requirements` command.
If a package manager is not list listed there, support for it is integrated directly into ORT and does not require any
external tools to be installed.

# Development

ORT is written in [Kotlin](https://kotlinlang.org/) and uses [Gradle](https://gradle.org/) as the build system, with
[Kotlin script](https://docs.gradle.org/current/userguide/kotlin_dsl.html) instead of Groovy as the DSL.

When developing on the command line, use the committed
[Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) to bootstrap Gradle in the configured
version and execute any given tasks. The most important tasks for this project are:

| Task        | Purpose                                                           |
| ----------- | ----------------------------------------------------------------- |
| assemble    | Build the JAR artifacts for all projects                          |
| detekt      | Run static code analysis on all projects                          |
| test        | Run unit tests for all projects                                   |
| funTest     | Run functional tests for all projects                             |
| installDist | Build all projects and install the start scripts for distribution |

All contributions need to pass the `detekt`, `test` and `funTest` checks before they can be merged.

For IDE development we recommend the [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/) which
can directly import the Gradle build files. After cloning the project's source code recursively, simply run IDEA and use
the following steps to import the project.

1. From the wizard dialog: Select *Import Project*.

   From a running IDEA instance: Select *File* -> *New* -> *Project from Existing Sources...*

2. Browse to ORT's source code directory and select either the `build.gradle.kts` or the `settings.gradle.kts` file.

3. In the *Import Project from Gradle* dialog select *Use auto-import* and leave all other settings at their defaults.

To set up a basic run configuration for debugging, navigate to `Main.kt` in the `cli` module and look for the
`fun main(args: Array<String>)` function. In the gutter next to it, a green "Play" icon should be displayed. Click on it
and select `Run 'org.ossreviewtoolkit.Main'` to run the entry point, which implicitly creates a run configuration.
Double-check that running ORT without any arguments will simply show the command line help in IDEA's *Run* tool window.
Finally, edit the created run configuration to your needs, e.g. by adding an argument and options to run a specific ORT
sub-command.

# License

Copyright (C) 2017-2020 HERE Europe B.V.

See the [LICENSE](./LICENSE) file in the root of this project for license details.

OSS Review Toolkit (ORT) is a [Linux Foundation project](https://www.linuxfoundation.org) and part of [ACT](https://automatecompliance.org/).
