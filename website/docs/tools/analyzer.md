---
sidebar_position: 1
---

# Analyzer

The *analyzer* is a Software Composition Analysis (SCA) tool that determines the dependencies of software projects inside the specified version-controlled input directory (`-i`).
It is the only mandatory tool to run from ORT as its output is the input for all other tools.
Analysis works by querying the detected package managers; **no modifications** to your existing project source code, like applying build system plugins, are necessary for that to work if the following preconditions are met:

* All projects use one of the package managers listed below in a reasonably recent version, and they are configured according to common best practices.
* All projects can be built in a single step out-of-the-box, without any custom configuration being set, like build system properties or environment variables.

The tree of transitive dependencies per project is written out as part of an [OrtResult](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/OrtResult.kt) in YAML (or JSON, see `-f`) format to a file named `analyzer-result.yml` in the specified output directory (`-o`).
The output file exactly documents the status quo of all package-related metadata.
It can be further processed or manually edited before passing it to one of the other tools.

Currently, the following package managers (grouped by the programming language they are most commonly used with) are supported:

* C / C++
  * [Bazel](https://bazel.build/) (limitations: see [open tasks](https://github.com/oss-review-toolkit/ort/issues/264))
  * [Conan 1.x and 2.x](https://conan.io/)
  * Also see: [SPDX documents](#spdx-as-fallback-package-manager)
* Dart / Flutter
  * [Pub](https://pub.dev/)
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

## SPDX as Fallback Package Manager

If another package manager that is not part of the list above is used (or no package manager at all), the generic fallback to [SPDX documents](https://spdx.dev/specifications/) can be leveraged to describe [projects](https://github.com/oss-review-toolkit/ort/blob/main/plugins/package-managers/spdx/src/funTest/assets/projects/synthetic/inline-packages/project-xyz.spdx.yml) or [packages](https://github.com/oss-review-toolkit/ort/blob/main/plugins/package-managers/spdx/src/funTest/assets/projects/synthetic/libs/curl/package.spdx.yml).
