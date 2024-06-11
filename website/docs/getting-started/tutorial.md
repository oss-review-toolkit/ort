---
sidebar_position: 4
---

# Tutorial

This tutorial gives a brief introduction to how the tools work together at the example of the [mime-types](https://www.npmjs.com/package/mime-types) NPM package.
It will guide through the main steps for running ORT:

* Install ORT.
* Analyze the dependencies of `mime-types` using the *analyzer*.
* Scan the source code of `mime-types` and its dependencies using the `scanner`.
* Run the evaluator to find any rule violations.
* Generate reports to show the results.

## 1. Prerequisites

ORT is tested to run on Linux, macOS, and Windows.
This tutorial assumes that you are running on Linux, but it should be easy to adapt the commands to macOS or Windows.

In addition to Java (version >= 11), for some supported package managers and Version Control Systems, additional tools need to be installed.
In the context of this tutorial, the following tools are required:

* Git (any recent version will do)
* [Node.js](https://nodejs.org) 10.* or higher.
* [NPM](https://www.npmjs.com) 6.4.* or higher.
* [Yarn](https://yarnpkg.com) 1.9.* or higher.

For the full list of supported package managers and Version Control Systems, see the [README](../tools/analyzer.md).

## 2. Download & Install ORT

In the future, we will provide binaries of the ORT tools, but currently you have to build the tools on your own.
First download the source code (including Git submodules) from GitHub:

```shell
git clone --recurse-submodules https://github.com/oss-review-toolkit/ort.git
```

To build the command line interface run:

```shell
cd ort
./gradlew installDist
```

This will create the script to run ORT at `cli/build/install/ort/bin/ort`.
To get the general command line help run it with the `--help` option:

```shell
cli/build/install/ort/bin/ort --help
```

## 3. Download the `mime-types` source code

Before scanning `mime-types` its source code has to be downloaded.
For reliable results we use version 2.1.18 (replace `[mime-types-dir]` with the directory you want to clone `mime-types` to):

```shell
git clone https://github.com/jshttp/mime-types.git [mime-types-dir]
cd [mime-types-dir]
git checkout 2.1.35
```

## 4. Run the analyzer on `mime-types`

The next step is to run the *analyzer*.
It will create a JSON or YAML output file containing the full dependency tree of `mime-types` including the metadata of `mime-types` and its dependencies.

```shell
# Command line help specific to the analyzer.
cli/build/install/ort/bin/ort analyze --help

# The easiest way to run the analyzer. Be aware that the [analyzer-output-dir] directory must not exist.
cli/build/install/ort/bin/ort analyze -i [mime-types-dir] -o [analyzer-output-dir]

# The command above will create the default YAML output. If you prefer JSON run:
cli/build/install/ort/bin/ort analyze -i [mime-types-dir] -o [analyzer-output-dir] -f JSON

# To get the maximum log output run:
cli/build/install/ort/bin/ort --debug --stacktrace analyze -i [mime-types-dir] -o [analyzer-output-dir]
```

The *analyzer* will search for build files of all supported package managers.
In case of `mime-types` it will find the `package.json` file and write the results of the dependency analysis to the output file `analyzer-result.yml`.
On the first attempt of running the analyzer on the `mime-types` package it will fail with an error message:

```shell
The following 25 package manager(s) are enabled:
        Bazel, Bower, Bundler, Cargo, Carthage, CocoaPods, Composer, Conan, GoMod, Gradle, Maven, NPM, NuGet, PIP, Pipenv, PNPM, Poetry, Pub, SBT, SpdxDocumentFile, Stack, SwiftPM, Unmanaged, Yarn, Yarn2
The following 2 package curation provider(s) are enabled:
        DefaultDir, DefaultFile
Analyzing project path:
        [workdir]/mime-types
00:31:07.985 [main] WARN  org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection - Any of [NPM, PNPM, YARN, YARN2] could be the package manager for '[workdir]/mime-types/package.json'. Assuming it is an NPM project.
00:31:07.987 [main] WARN  org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection - Any of [NPM, PNPM, YARN, YARN2] could be the package manager for '[workdir]/mime-types/package.json'. Assuming it is an NPM project.
00:31:07.988 [main] WARN  org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection - Any of [NPM, PNPM, YARN, YARN2] could be the package manager for '[workdir]/mime-types/package.json'. Assuming it is an NPM project.
00:31:07.990 [main] WARN  org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection - Any of [NPM, PNPM, YARN, YARN2] could be the package manager for '[workdir]/mime-types/package.json'. Assuming it is an NPM project.
Found 1 NPM definition file(s) at:
        package.json
Found in total 1 definition file(s) from the following 1 package manager(s):
        NPM
00:31:08.572 [DefaultDispatcher-worker-1] ERROR org.ossreviewtoolkit.analyzer.PackageManager - NPM failed to resolve dependencies for path 'package.json': IllegalArgumentException: No lockfile found in '.'. This potentially results in unstable versions of dependencies. To support this, enable the 'allowDynamicVersions' option in 'config.yml'.
Wrote analyzer result to '[analyzer-output-dir]/analyzer-result.yml' (0.00 MiB) in 449.630951ms.
```

This happens because `mime-types` does not have `package-lock.json` file.
Without this file, the versions of (transitive) dependencies that are defined with version ranges could change at any time, leading to different results of the analyzer.
To override this check, use the global `-P ort.analyzer.allowDynamicVersions=true` option:

```shell
$ rm [analyzer-output-dir/analyzer-result.yml
$ cli/build/install/ort/bin/ort -P ort.analyzer.allowDynamicVersions=true analyze -i [mime-types-dir] -o [analyzer-output-dir]
...
The following 25 package manager(s) are enabled:
        Bazel, Bower, Bundler, Cargo, Carthage, CocoaPods, Composer, Conan, GoMod, Gradle, Maven, NPM, NuGet, PIP, Pipenv, PNPM, Poetry, Pub, SBT, SpdxDocumentFile, Stack, SwiftPM, Unmanaged, Yarn, Yarn2
The following 2 package curation provider(s) are enabled:
        DefaultDir, DefaultFile
Analyzing project path:
        [workdir]/mime-types
00:33:15.573 [main] WARN  org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection - Any of [NPM, PNPM, YARN, YARN2] could be the package manager for '[workdir]/mime-types/package.json'. Assuming it is an NPM project.
00:33:15.575 [main] WARN  org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection - Any of [NPM, PNPM, YARN, YARN2] could be the package manager for '[workdir]/mime-types/package.json'. Assuming it is an NPM project.
00:33:15.576 [main] WARN  org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection - Any of [NPM, PNPM, YARN, YARN2] could be the package manager for '[workdir]/mime-types/package.json'. Assuming it is an NPM project.
00:33:15.579 [main] WARN  org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection - Any of [NPM, PNPM, YARN, YARN2] could be the package manager for '[workdir]/mime-types/package.json'. Assuming it is an NPM project.
Found 1 NPM definition file(s) at:
        package.json
Found in total 1 definition file(s) from the following 1 package manager(s):
        NPM
Wrote analyzer result to '[analyzer-output-dir]/analyzer-result.yml' (0.24 MiB) in 339.135518ms.
...
```

The result file will contain information about the `mime-types` package itself, the dependency tree for each scope, and information about each dependency.
The scope names come from the package managers, for NPM packages these are usually `dependencies` and `devDependencies`, for Maven package it would be `compile`, `runtime`, `test`, and so on.

Note that the `analyzer-result.yml` is supposed to capture all known information about a project, which can then be "filtered" in later steps.
For example, scopes which are not relevant for the distribution will still be listed, but can be configured to get excluded so that they e.g. do not get downloaded and scanned by the *scanner* step.
To specify which scopes should be excluded, add an `.ort.yml` configuration file to the input directory of the *analyzer*.
For more details see [Configuration File](../configuration/ort-yml.md).

For this guide, `[mime-types-dir]/.ort.yml` can be created with following content:

```yaml
excludes:
  scopes:
  - pattern: "devDependencies"
    reason: "DEV_DEPENDENCY_OF"
    comment: "Packages for development only."
```

Following is an overview of the structure of the `analyzer-result.yml` file (comments were added for clarity and are not part of a real result file):

```yaml
---
# VCS information about the input directory.
repository:
  vcs:
    type: "Git"
    url: "https://github.com/jshttp/mime-types.git"
    revision: "ef932231c20e716ec27ea159c082322c3c485b66"
    path: ""
  vcs_processed:
    type: "Git"
    url: "https://github.com/jshttp/mime-types.git"
    revision: "ef932231c20e716ec27ea159c082322c3c485b66"
    path: ""
  # Will only be present if an '.ort.yml' configuration file with scope excludes was provided. Otherwise, this is an empty object.
  config:
    excludes:
      scopes:
        - pattern: "devDependencies"
          reason: "DEV_DEPENDENCY_OF"
          comment: "Packages for development only."
# The analyzer result.
analyzer:
  # The time when the analyzer was executed.
  start_time: "2024-06-07T23:05:21.827379368Z"
  end_time: "2024-06-07T23:05:39.100655684Z"
  environment:
    ort_version: "22.7.1-006.sha.56240bf"
    build_jdk: "11.0.22+7"
    java_version: "17.0.11"
    os: "Linux"
    processors: 24
    max_memory: 25300041728
    variables:
      HOME: "/home/[your user]"
      SHELL: "/bin/bash"
      TERM: "xterm-256color"
      GOPATH: "[Path to the Go binary]"
    tool_versions:
      NPM: "9.2.0"
  # Configuration options of the analyzer.
  config:
    allow_dynamic_versions: true
    skip_excluded: false
  # The result of the dependency analysis.
  result:
    # Metadata about all found projects, in this case only the mime-types package defined by the package.json file.
    projects:
      - id: "NPM::mime-types:2.1.35"
        definition_file_path: "package.json"
        declared_licenses:
          - "MIT"
        declared_licenses_processed:
          spdx_expression: "MIT"
        vcs:
          type: ""
          url: "https://github.com/jshttp/mime-types.git"
          revision: ""
          path: ""
        vcs_processed:
          type: "Git"
          url: "https://github.com/jshttp/mime-types.git"
          revision: "ef932231c20e716ec27ea159c082322c3c485b66"
          path: ""
        homepage_url: ""
        # The dependency trees by scope.
        scope_names:
          - "dependencies"
          - "devDependencies"
    # Detailed metadata about each package from the dependency trees.
    packages:
      - id: "NPM::acorn:7.4.1"
        purl: "pkg:npm/acorn@7.4.1"
        declared_licenses:
          - "MIT"
        declared_licenses_processed:
          spdx_expression: "MIT"
        description: "ECMAScript parser"
        homepage_url: "https://github.com/acornjs/acorn"
        binary_artifact:
          url: ""
          hash:
            value: ""
            algorithm: ""
        source_artifact:
          url: "https://registry.npmjs.org/acorn/-/acorn-7.4.1.tgz"
          hash:
            value: "feaed255973d2e77555b83dbc08851a6c63520fa"
            algorithm: "SHA-1"
        vcs:
          type: "Git"
          url: "https://github.com/acornjs/acorn.git"
          revision: ""
          path: ""
        vcs_processed:
          type: "Git"
          url: "https://github.com/acornjs/acorn.git"
          revision: ""
          path: ""
    # ...
    # A list of project-related issues that happened during dependency analysis.
    issues:
      NPM::mime-types:2.1.35:
        - timestamp: "2024-06-07T23:05:25.472509460Z"
          source: "NPM"
          message: "deprecated inflight@1.0.6: This module is not supported, and leaks\
          \ memory. Do not use it. Check out lru-cache if you want a good and tested\
          \ way to coalesce async requests by a key value, which is much more comprehensive\
          \ and powerful."
          severity: "HINT"
        - timestamp: "2024-06-07T23:05:25.472524097Z"
          source: "NPM"
          message: "deprecated rimraf@3.0.2: Rimraf versions prior to v4 are no longer\
          \ supported"
          severity: "HINT"
        - timestamp: "2024-06-07T23:05:25.472525680Z"
          source: "NPM"
          message: "deprecated glob@7.2.0: Glob versions prior to v9 are no longer supported"
          severity: "HINT"
    dependency_graphs:
      NPM:
        packages:
          - "NPM::acorn-jsx:5.3.2"
          - "NPM::acorn:7.4.1"
          - "NPM::aggregate-error:3.1.0"
          - "NPM::ajv:6.12.6"
          - "NPM::ajv:8.16.0"
        # ...
        scopes:
          :mime-types:2.1.35:dependencies:
            - root: 216
          :mime-types:2.1.35:devDependencies:
            - root: 81
            - root: 85
            - root: 86
            - root: 87
            - root: 88
            - root: 89
            - root: 94
            - root: 220
            - root: 229
        nodes:
          - pkg: 216
          - pkg: 81
          - pkg: 72
          - pkg: 117
        # ...
        edges:
          - from: 6
            to: 3
          - from: 7
            to: 2
# ...
scanner: null
advisor: null
evaluator: null
resolved_configuration:
  package_curations:
    - provider:
        id: "DefaultDir"
      curations: []
    - provider:
        id: "DefaultFile"
      curations: []
```

## 5. Run the scanner

To scan the source code of `mime-types` and its dependencies the source code of `mime-types` and all its dependencies needs to be downloaded.
The *downloader* tool could be used for this, but it is also integrated in the `scanner` tool, so the scanner will automatically download the source code if the required VCS metadata could be obtained.

Note that if the *downloader* is unable to download the source code, for example, because the package medata provides no source code location, you can use [curations](../configuration/package-curations.md) to fix up the package's metadata.

ORT is designed to integrate lots of different scanners and is not limited to license scanners, technically any tool that explores the source code of a software package could be integrated.
The actual scanner does not have to run on the same computer.
For example, the [FossID](https://github.com/oss-review-toolkit/ort/blob/main/plugins/scanners/fossid/src/main/kotlin/FossId.kt) uses a remote service to execute the scan.

For this tutorial [ScanCode](https://github.com/nexB/scancode-toolkit) is used as a scanner.
Please install it according to [these instructions](https://github.com/nexB/scancode-toolkit/#installation) first.

As for the *analyzer* you can get the command line options for the `scanner` using the `--help` option:

```shell
cli/build/install/ort/bin/ort scan --help
```

The `mime-types` package has only one dependency in the `dependencies` scope, but a lot of dependencies in the `devDependencies` scope.
Scanning all of the `devDependencies` would take a lot of time, so we will only run the scanner on the `dependencies` scope in this tutorial.
If you also want to scan the `devDependencies` it is strongly advised to configure a [scan storage](../tools/scanner.md#storage-backends) for the scan results to speed up repeated scans.

As during the *analyzer* step an `.ort.yml` configuration file was provided to exclude `devDependencies`, the `-P ort.scanner.skipExcluded=true` option can be used to avoid the download and scanning of that scope.

```shell
$ cli/build/install/ort/bin/ort -P ort.scanner.skipExcluded=true scan -i [analyzer-output-dir]/analyzer-result.yml -o [scanner-output-dir]
Scanning projects with:
        ScanCode (version 32.1.0)
Scanning packages with:
        ScanCode (version 32.1.0)
Wrote scan result to '[scanner-output-dir]/scan-result.yml' (0.42 MiB) in 209.344576ms.
The scan took 3.591540482s.
Resolved issues: 0 errors, 0 warnings, 0 hints.
Unresolved issues: 0 errors, 0 warnings, 0 hints.
```

The `scanner` writes a new ORT result file to `[scanner-output-dir]/scan-result.yml` containing the scan results in addition to the analyzer result from the input.
This way belonging results are stored in the same place for traceability.
If the input file already contained scan results, they are replaced by the new scan results in the output.

As you can see when checking the `scan-result.yml` file, the licenses detected by `ScanCode` match the licenses declared by the packages.
This is because we scanned a small and well-maintained package in this example.
If you run the scan on a bigger project, you will see that `ScanCode` often finds more licenses than are declared by the packages.

## 6. Running the evaluator

The evaluator can apply a set of rules against the scan result created above.
ORT provides examples for the policy rules file ([example.rules.kts](../configuration/evaluator-rules.md#example)), user-defined categorization of licenses ([license-classifications.yml](../configuration/license-classifications.md)) and user-defined package curations ([curations.yml](../configuration/package-curations.md)) that can be used for testing the *evaluator*.

To run the example rules use:

```shell
cli/build/install/ort/bin/ort evaluate
  --package-curations-file curations.yml
  --rules-file evaluator.rules.kts
  --license-classifications-file license-classifications.yml
  -i [scanner-output-dir]/scan-result.yml
  -o [evaluator-output-dir]
```

Example output:

```shell
The following 1 rule violations have been found:
ERROR: MISSING_CONTRIBUTING_FILE - The project's code repository does not contain the file 'CONTRIBUTING.md'.
The evaluation of 1 script(s) took 2.277363920s.
Wrote evaluation result to '[evaluator-output-dir]/evaluation-result.yml' (0.42 MiB) in 225.231183ms.
Resolved rule violations: 0 errors, 0 warnings, 0 hints.
Unresolved rule violations: 1 error, 0 warnings, 0 hints.
There is 1 unresolved rule violation with a severity equal to or greater than the WARNING threshold.
```

See the [curations.yml documentation](../configuration/package-curations.md) to learn more about using curations to correct invalid or missing package metadata and the [license-classifications.yml documentation](../configuration/license-classifications.md) on how you can classify licenses to simplify writing the policy rules.

It is possible to write your own evaluator rules as a Kotlin script and pass it to the *evaluator* using `--rules-file`.
Note that detailed documentation for writing custom rules is not yet available.

## 7. Generate a report

The `evaluation-result.yml` file can now be used as input for the reporter to generate human-readable reports and open source notices.

For example, to generate a static HTML report, WebApp report, and an open source notice by package, use:

```shell
cli/build/install/ort/bin/ort report
  -f PlainTextTemplate,StaticHtml,WebApp
  -i [evaluator-output-dir]/evaluation-result.yml
  -o [reporter-output-dir]
...
Generating the 'PlainTextTemplate' report in thread 'DefaultDispatcher-worker-2'...
Generating the 'WebApp' report in thread 'DefaultDispatcher-worker-4'...
Generating the 'StaticHtml' report in thread 'DefaultDispatcher-worker-3'...
Successfully created 'PlainTextTemplate' report(s) at '[reporter-output-dir]/NOTICE_DEFAULT' in 289.407999ms.
Successfully created 'StaticHtml' report(s) at '[reporter-output-dir]/scan-report.html' in 593.313833ms.
Successfully created 'WebApp' report(s) at '[reporter-output-dir]/scan-report-web-app.html' in 475.395069ms.
Created 3 of 3 report(s) in 618.614701ms.
```

If you do not want to run the *evaluator* you can pass the *scanner* result e.g. `[scanner-output-dir]/scan-result.yml` to the `reporter` instead.
To learn how you can customize the generated notices, see [Reporter Templates](../configuration/reporter-templates.md#plain-text-templates).
To learn how to customize the how-to-fix texts for scanner and analyzer issues see [how-to-fix-text-provider-kts.md](../configuration/how-to-fix-text-provider.md).

## 8. Curating Package Metadata or License Findings

In the example above, everything went well because the VCS information provided by the packages was correct, but this is not always the case.
Often the metadata of packages has no VCS information, points to outdated repositories, or the repositories are not correctly tagged.

ORT provides a variety of mechanisms to fix a variety of issues, for details see:

* [The .ort.yml file](../configuration/ort-yml.md) - project-specific license finding curations, exclusions and resolutions to address issues found within a project's code repository.
* [The package configuration file](../configuration/package-configurations.md) - package (dependency) and provenance-specific license finding curations and exclusions to address issues found within a scan result for a package.
* [The curations.yml file](../configuration/package-curations.md) - curations correct invalid or missing package metadata and set the concluded license for packages.
* [The resolutions.yml file](../configuration/resolutions.md) - resolutions allow *resolving* any issues or policy rule violations by providing a reason why they are acceptable and can be ignored.
