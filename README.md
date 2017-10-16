# OSS Review Toolkit

The OSS Review Toolkit (ORT for short) is a suite of tools to assist with reviewing Open Source Software dependencies in
your software deliverables. The different tools in the suite are designed as libraries (for programmatic use) with
minimal command line interface (for scripted use,
[doing one thing and doing it well](https://en.wikipedia.org/wiki/Unix_philosophy#Do_One_Thing_and_Do_It_Well)).

## Installation

After cloning the Git repository, on the command line do execute

```
./gradlew installDist
```

to build the tools and install the start scripts for distribution. The individual start scripts can then be run directly
from their respective locations at

* `./analyzer/build/install/analyzer/bin/analyzer`
* `./graph/build/install/graph/bin/graph`
* `./downloader/build/install/downloader/bin/downloader`
* `./scanner/build/install/scanner/bin/scanner`

## Usage

### [analyzer](./analyzer/src/main/kotlin)

The Analyzer determines the dependencies of software projects inside the specified input directory (`-i`). It does so by
querying whatever [supported package manager](./analyzer/src/main/kotlin/managers) is found. No modifications to your
existing project source code, or especially to the build system, are necessary for that to work. The tree of transitive
dependencies per project is written out as [ABCD](https://github.com/nexB/aboutcode/tree/master/aboutcode-data)-style
YAML (or JSON, see `-f`) files to the specified output directory (`-o`) whose inner structure mirrors the one from the
input directory. The output files exactly document the status quo of all package-related meta-data. They can and
probably need to be further processed or manually edited before passing them to one of the other tools.

The `analyzer` command line tool takes the following arguments:

```
Usage: analyzer [options]
  Options:
    --ignore-versions
      Ignore versions of required tools. NOTE: This may lead to erroneous
      results.
      Default: false
    --debug
      Enable debug logging and keep any temporary files.
      Default: false
    --stacktrace
      Print out the stacktrace for all exceptions.
      Default: false
  * --input-dir, -i
      The project directory to scan.
    --info
      Enable info logging.
      Default: false
  * --output-dir, -o
      The directory to write dependency information to.
    --allow-dynamic-versions
      Allow dynamic versions of dependencies. This can result in unstable
      results when dependencies use version ranges. This option only affects
      package managers that support lock files, like NPM.
      Default: false
    --package-managers, -m
      A list of package managers to activate.
      Default: [Gradle, NPM, PIP]
    --output-format, -f
      The data format used for dependency information.
      Default: YAML
      Possible Values: [JSON, YAML]
    --help, -h
      Display the command line help.
```

### [graph](./graph/src/main/kotlin)

In order to quickly visualize dependency information from an analysis the Graph tool can be used. Given a dependencies
file (`-d`) it diplays a simple representation of the dependency graph in a GUI. The graph is interactive in the sense
that nodes can be dragged & dropped with the mouse to rearrange them for a better overview.

The `graph` command line tool takes the following arguments:

```
Usage: graph [options]
  Options:
    --info
      Enable info logging.
      Default: false
    --debug
      Enable debug logging and keep any temporary files.
      Default: false
    --stacktrace
      Print out the stacktrace for all exceptions.
      Default: false
  * --dependencies-file, -d
      The dependencies analysis file to use.
    --help, -h
      Display the command line help.
```

### [downloader](./downloader/src/main/kotlin)

Taking a single ABCD-syle dependencies file as the input (`-d`), the Downloader retrieves the source code of all contained
packages to the specified output directory (`-o`). The Downloader takes care of things like normalizing URLs and using
the [appropriate VCS tool](./downloader/src/main/kotlin/vcs) to checkout source code from version control.

The `downloader` command line tool takes the following arguments:

```
Usage: downloader [options]
  Options:
  * --dependencies-file, -d
      The dependencies analysis file to use.
    --debug
      Enable debug logging and keep any temporary files.
      Default: false
  * --output-dir, -o
      The output directory to download the source code to.
    --info
      Enable info logging.
      Default: false
    --stacktrace
      Print out the stacktrace for all exceptions.
      Default: false
    --entities, -e
      The data entities from the dependencies analysis file to download.
      Default: [PACKAGES, PROJECT]
    --help, -h
      Display the command line help.
```

### [scanner](./scanner/src/main/kotlin)

This tool wraps underlying license / copyright scanners with a common API. This way all supported scanners can be used
in the same way to easily run them and compare their results. If passed a dependencies analysis file (`-d`), the Scanner
will automatically download the sources of the dependencies via the Downloader and scan them afterwards. In order to not
download or scan any previously scanned sources, the Scanner can be configured (`-c`) to use a (remote) cache, hosted
e.g. on [Artifactory](./scanner/src/main/kotlin/ArtifactoryCache.kt) or S3 (not yet implemented). Using the example of
configuring an Artifactory cache, the YAML-based configuration file would look like:

```
scanner:
  cache:
    type: Artifactory
    url: "https://artifactory.domain.com/artifactory/generic-repository-name"
    apiToken: $ARTIFACTORY_API_KEY
```

The `scanner` command line tool takes the following arguments:

```
Usage: scanner [options]
  Options:
  * --output-dir, -o
      The output directory to store the scan results and source code of
      packages.
    --summary-format, -f
      The list of file formats for the summary files.
      Default: [YAML]
    --info
      Enable info logging.
      Default: false
    --dependencies-file, -d
      The dependencies analysis file to use. Source code will be downloaded
      automatically if needed. This parameter and --input-path are mutually
      exclusive.
    --debug
      Enable debug logging and keep any temporary files.
      Default: false
    --input-path, -i
      The input directory or file to scan. This parameter and
      --dependencies-file are mutually exclusive.
    --stacktrace
      Print out the stacktrace for all exceptions.
      Default: false
    --scanner, -s
      The scanner to use.
      Default: ScanCode
    --config, -c
      The path to the configuration file.
    --help, -h
      Display the command line help.
```

## Development

The toolkit is written in [Kotlin](https://kotlinlang.org/) and uses [Gradle](https://gradle.org/) as the build system.
We recommend the [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/) as the IDE which can
directly import the Gradle build files.

The most important root project Gradle tasks are listed in the table below.

| Task        | Purpose                                                           |
| ----------- | ----------------------------------------------------------------- |
| assemble    | Build the JAR artifacts for all projects                          |
| detektCheck | Run static code analysis on all projects                          |
| test        | Run unit tests for all projects                                   |
| funTest     | Run functional tests for all projects                             |
| installDist | Build all projects and install the start scripts for distribution |

## License

Copyright (c) 2017 HERE Europe B.V.

See the [LICENSE](./LICENSE) file in the root of this project for license details.
