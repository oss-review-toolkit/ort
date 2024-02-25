![OSS Review Toolkit Logo](./logos/ort.png)

&nbsp;

[![Slack][1]][2]

[![Wrapper Validation][3]][4] [![Static Analysis][5]][6]

[![Build and Test][7]][8] [![JitPack build status][9]][10] [![Code coverage][11]][12]

[![TODOs][13]][14] [![REUSE status][15]][16] [![CII][17]][18]

[1]: https://img.shields.io/badge/Join_us_on_Slack!-ort--talk-blue.svg?longCache=true&logo=slack
[2]: http://slack.oss-review-toolkit.org
[3]: https://github.com/oss-review-toolkit/ort/actions/workflows/wrapper-validation.yml/badge.svg
[4]: https://github.com/oss-review-toolkit/ort/actions/workflows/wrapper-validation.yml
[5]: https://github.com/oss-review-toolkit/ort/actions/workflows/static-analysis.yml/badge.svg
[6]: https://github.com/oss-review-toolkit/ort/actions/workflows/static-analysis.yml
[7]: https://github.com/oss-review-toolkit/ort/actions/workflows/build-and-test.yml/badge.svg
[8]: https://github.com/oss-review-toolkit/ort/actions/workflows/build-and-test.yml
[9]: https://jitpack.io/v/oss-review-toolkit/ort.svg
[10]: https://jitpack.io/#oss-review-toolkit/ort
[11]: https://codecov.io/gh/oss-review-toolkit/ort/branch/main/graph/badge.svg?token=QD2tCSUTVN
[12]: https://app.codecov.io/gh/oss-review-toolkit/ort
[13]: https://badgen.net/https/api.tickgit.com/badgen/github.com/oss-review-toolkit/ort
[14]: https://www.tickgit.com/browse?repo=github.com/oss-review-toolkit/ort
[15]: https://api.reuse.software/badge/github.com/oss-review-toolkit/ort
[16]: https://api.reuse.software/info/github.com/oss-review-toolkit/ort
[17]: https://bestpractices.coreinfrastructure.org/projects/4618/badge
[18]: https://bestpractices.coreinfrastructure.org/projects/4618

# Introduction

The OSS Review Toolkit (ORT) is a FOSS policy automation and orchestration toolkit that you can use to manage your (open source) software dependencies in a strategic, safe and efficient manner.

You can use it to:

* Generate CycloneDX, SPDX SBOMs, or custom FOSS attribution documentation for your software project
* Automate your FOSS policy using risk-based Policy as Code to do licensing, security vulnerability, InnerSource and engineering standards checks for your software project and its dependencies
* Create a source code archive for your software project and its dependencies to comply with certain licenses or have your own copy as nothing on the internet is forever
* Correct package metadata or licensing findings yourself, using InnerSource or with the help of the FOSS community

ORT can be used as a library (for programmatic use), via a command line interface (for scripted use), or via its CI integrations.
It consists of the following tools which can be combined into a *highly customizable* pipeline:

* [*Analyzer*](https://oss-review-toolkit.org/ort/docs/tools/analyzer):
  Determines the dependencies of projects and their metadata, abstracting which package managers or build systems are actually being used.
* [*Downloader*](https://oss-review-toolkit.org/ort/docs/tools/downloader):
  Fetches all source code of the projects and their dependencies, abstracting which Version Control System (VCS) or other means are used to retrieve the source code.
* [*Scanner*](https://oss-review-toolkit.org/ort/docs/tools/scanner):
  Uses configured source code scanners to detect license / copyright findings, abstracting the type of scanner.
* [*Advisor*](https://oss-review-toolkit.org/ort/docs/tools/advisor):
  Retrieves security advisories for used dependencies from configured vulnerability data services.
* [*Evaluator*](https://oss-review-toolkit.org/ort/docs/tools/evaluator):
  Evaluates custom policy rules along with custom license classifications against the data gathered in preceding stages and returns a list of policy violations, e.g. to flag license findings.
* [*Reporter*](https://oss-review-toolkit.org/ort/docs/tools/reporter):
  Presents results in various formats such as visual reports, Open Source notices or Bill-Of-Materials (BOMs) to easily identify dependencies, licenses, copyrights or policy rule violations.
* *Notifier*:
  Sends result notifications via different channels (like [emails](./examples/example.notifications.kts) and / or JIRA tickets).

Also see the [list of related tools](https://oss-review-toolkit.org/ort/docs/related-tools) that help with running ORT.

## Documentation

For detailed information, see the documentation on the [ORT Website](https://oss-review-toolkit.org/ort/).

# Installation

## System requirements

ORT is being continuously used on Linux, Windows and macOS by the [core development team](https://github.com/orgs/oss-review-toolkit/people), so these operating systems are considered to be well-supported.

To run the ORT binaries (also see [Installation from binaries](#from-binaries)) at least Java 11 is required.
Memory and CPU requirements vary depending on the size and type of project(s) to analyze / scan, but the general recommendation is to configure Java with 8 GiB of memory and to use a CPU with at least 4 cores.

```shell
# This will give the Java Virtual Machine 8GB Memory.
export JAVA_OPTS="$JAVA_OPTS -Xmx8g"
```

If ORT requires external tools to analyze a project, these tools are listed by the `ort requirements` command.
If a package manager is not list listed there, support for it is integrated directly into ORT and does not require any external tools to be installed.

## From binaries

Preliminary binary artifacts for ORT are currently available via [JitPack](https://jitpack.io/#oss-review-toolkit/ort).
Please note that due to limitations with the JitPack build environment, the reporter is not able to create the Web App report.

## From sources

Install the following basic prerequisites:

* Git (any recent version will do).

Then clone this repository.

```shell
git clone https://github.com/oss-review-toolkit/ort
# If you intend to run tests, you have to clone the submodules too.
cd ort
git submodule update --init --recursive
```

### Build using Docker

Install the following basic prerequisites:

* Docker 18.09 or later (and ensure its daemon is running).
* Enable [BuildKit](https://docs.docker.com/develop/develop-images/build_enhancements/#to-enable-buildkit-builds) for Docker.

Change into the directory with ORT's source code and run `docker build -t ort .`.
Alternatively, use the script at `scripts/docker_build.sh` which also sets the ORT version from the Git revision.

### Build natively

Install these additional prerequisites:

* Java Development Kit (JDK) version 11 or later; also remember to set the `JAVA_HOME` environment variable accordingly.

Change into the directory with ORT's source code and run `./gradlew installDist` (on the first run this will bootstrap Gradle and download all required dependencies).

## Basic usage

Depending on how ORT was installed, it can be run in the following ways:

* If the Docker image was built, use

  ```shell
  docker run ort --help
  ```

  You can find further hints for using ORT with Docker in the [documentation](./website/docs/guides/docker.md).

* If the ORT distribution was built from sources, use

  ```shell
  ./cli/build/install/ort/bin/ort --help
  ```

* If running directly from sources via Gradle, use

  ```shell
  ./gradlew cli:run --args="--help"
  ```

  Note that in this case the working directory used by ORT is that of the `cli` project, not the directory `gradlew` is located in (see https://github.com/gradle/gradle/issues/6074).

# Want to Help or have Questions?

All contributions are welcome.
If you are interested in contributing, please read our [contributing guide](https://github.com/oss-review-toolkit/.github/blob/main/CONTRIBUTING.md).
To get quick answers to any of your questions, we recommend you [join our Slack community][2].

# License

Copyright (C) 2017-2023 [The ORT Project Authors](./NOTICE).

See the [LICENSE](./LICENSE) file in the root of this project for license details.

OSS Review Toolkit (ORT) is a [Linux Foundation project](https://www.linuxfoundation.org) and part of [ACT](https://automatecompliance.org/).
