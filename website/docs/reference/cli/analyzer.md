# Analyzer CLI Reference

The *ORT Analyzer* is a Software Composition Analysis (SCA) tool that determines the dependencies of software projects inside the specified version-controlled input directory (`-i`).
It is the only mandatory tool to run from ORT as its output is the input for all other tools.
Analysis works by querying the detected package managers; **no modifications** to your existing project source code, like applying build system plugins, are necessary for that to work if the following preconditions are met:

* All projects use one of the package managers listed below in a reasonably recent version, and they are configured according to common best practices.
* All projects can be built in a single step out-of-the-box, without any custom configuration being set, like build system properties or environment variables.

The tree of transitive dependencies per project is written out as part of an [OrtResult](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/OrtResult.kt) in YAML (or JSON, see `-f`) format to a file named `analyzer-result.yml` in the specified output directory (`-o`).
The output file exactly documents the status quo of all package-related metadata.
It can be further processed or manually edited before passing it to one of the other tools.

## Usage

```
ort analyze [<options>]
```

## Input Options

* `-i`, `--input-dir=<value>` - The project directory to analyze. May point to a definition file, but only if just a single package manager is enabled, and the definition file does not depend on any further definition files.

## Output Options

* `-o`, `--output-dir=<value>`: The directory to write the ORT result file with analyzer results to.
* `-f`, `--output-formats=(JSON|YAML)`: The list of output formats to be used for the ORT result file(s). (default: YAML)

## Configuration Options

* `--repository-configuration-file=<value>`: A file containing the repository configuration. If set, overrides any repository configuration contained in a '.ort.yml' file in the repository.
* `--resolutions-file=<value>`: A file containing issue and rule violation resolutions. (default: ~/.ort/config/resolutions.yml)

## Options

* `-l`, `--label=<value>`: Set a label in the ORT result, overwriting any existing label of the same name. Can be used multiple times. For example: `--label distribution=external`.
* `--dry-run`: Do not actually run the project analysis but only show the package managers that would be used.
* `-h`, `--help`: Show this message and exit.

## Supported package managers

Currently, the following package managers (grouped by the programming language they are most commonly used with) are supported:

* C / C++
  * [Bazel](https://bazel.build/) (limitations: see [open tasks](https://github.com/oss-review-toolkit/ort/issues/264))
  * [Conan 1.x and 2.x](https://conan.io/)
  * Also see: [SPDX documents](#spdx-as-fallback-package-manager)
* Dart / Flutter
  * [Pub](https://pub.dev/)
* Gleam
  * [Gleam](https://gleam.run/)
* Go
  * [GoMod](https://github.com/golang/go/wiki/Modules)
* Haskell
  * [Stack](https://haskellstack.org/)
* Java
  * [Gradle](https://gradle.org/)
  * [Maven](https://maven.apache.org/) (limitations: [default profile only](https://github.com/oss-review-toolkit/ort/issues/1774))
    * Including the [Tycho](https://tycho.eclipseprojects.io/doc/main/index.html) extension for building OSGi bundles and Eclipse IDE plug-ins.
* JavaScript / Node.js
  * [Bower](https://bower.io/)
  * [NPM](https://www.npmjs.com/)
  * [PNPM](https://pnpm.io/)
  * [Yarn 1](https://classic.yarnpkg.com/)
  * [Yarn 2+](https://v2.yarnpkg.com/)
* .NET
  * [DotNet](https://docs.microsoft.com/en-us/dotnet/core/tools/) (limitations: [no floating versions / ranges](https://github.com/oss-review-toolkit/ort/pull/1303#issue-253860146), [no target framework](https://github.com/oss-review-toolkit/ort/issues/4083))
  * [NuGet](https://www.nuget.org/) (limitations: [no floating versions / ranges](https://github.com/oss-review-toolkit/ort/pull/1303#issue-253860146), [no target framework](https://github.com/oss-review-toolkit/ort/issues/4083))
* Objective-C / Swift
  * [Carthage](https://github.com/Carthage/Carthage) (limitation: [no `cartfile.private`](https://github.com/oss-review-toolkit/ort/issues/3774))
  * [CocoaPods](https://github.com/CocoaPods/CocoaPods) (limitations: [no custom source repositories](https://github.com/oss-review-toolkit/ort/issues/4188))
  * [SwiftPM](https://www.swift.org/package-manager)
* PHP
  * [Composer](https://getcomposer.org/)
* Python
  * [PIP](https://pip.pypa.io/)
  * [Pipenv](https://pipenv.pypa.io/en/latest/)
  * [Poetry](https://python-poetry.org/)
* Ruby
  * [Bundler](https://bundler.io/) (limitations: [restricted to the version available on the host](https://github.com/oss-review-toolkit/ort/issues/1308))
* Rust
  * [Cargo](https://doc.rust-lang.org/cargo/)
* Scala
  * [SBT](https://www.scala-sbt.org/)
* Unmanaged
  * This is a special "package manager" that manages all files that cannot be associated with any of the other package managers.

### SPDX as fallback package manager

If another package manager that is not part of the list above is used (or no package manager at all), the generic fallback to [SPDX documents](https://spdx.dev/specifications/) can be leveraged to describe [projects](https://github.com/oss-review-toolkit/ort/blob/main/plugins/package-managers/spdx/src/funTest/assets/projects/synthetic/inline-packages/project-xyz.spdx.yml) or [packages](https://github.com/oss-review-toolkit/ort/blob/main/plugins/package-managers/spdx/src/funTest/assets/projects/synthetic/libs/curl/package.spdx.yml).

## Related resources

* Code
  * [plugins/commands/analyzer/src/main/kotlin/AnalyzeCommand.kt](https://github.com/oss-review-toolkit/ort/blob/main/plugins/commands/analyzer/src/main/kotlin/AnalyzeCommand.kt)
* How-to guides
  * [How to exclude dirs, files or scopes](../../how-to-guides/how-to-exclude-dirs-files-or-scopes.md)
  * [How to include dirs and files](../../how-to-guides/how-to-include-dirs-and-files.md)
  * [How to add non-detected or supported packages](../../how-to-guides/how-to-add-non-detected-or-supported-packages.md)
  * [How to address tool issues](../../how-to-guides/how-to-address-tool-issues.md)
* Tutorials
  * [Analyzing a project for dependencies](../../tutorials/walkthrough/analyzing-a-project-for-dependencies.md)
