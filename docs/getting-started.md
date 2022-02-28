# Getting Started

This tutorial gives a brief introduction to how the tools work together at the example of the
[mime-types](https://www.npmjs.com/package/mime-types) NPM package. It will guide through the main steps for running
ORT:

* Install ORT.
* Analyze the dependencies of `mime-types` using the _analyzer_.
* Scan the source code of `mime-types` and its dependencies using the `scanner`.
* Run the evaluator to find any rule violations.
* Generate reports to show the results.

## 1. Prerequisites

ORT is tested to run on Linux, macOS, and Windows. This tutorial assumes that you are running on Linux, but it should be
easy to adapt the commands to macOS or Windows.

In addition to Java (version >= 11), for some supported package managers and Version Control Systems additional
tools need to be installed. In the context of this tutorial the following tools are required:

* Git (any recent version will do)
* [Node.js](https://nodejs.org) 10.* or higher.
* [NPM](https://www.npmjs.com) 6.4.* or higher.
* [Yarn](https://yarnpkg.com) 1.9.* or higher.

For the full list of supported package managers and Version Control Systems see the [README](../README.md).

## 2. Download & Install ORT

In the future, we will provide binaries of the ORT tools, but currently you have to build the tools on your own. First
download the source code (including Git submodules) from GitHub:

```bash
git clone --recurse-submodules https://github.com/oss-review-toolkit/ort.git
```

To build the command line interface run:

```bash
cd ort
./gradlew installDist
```

This will create the script to run ORT at `cli/build/install/ort/bin/ort`. To get the general command line help run it
with the `--help` option:

```bash
cli/build/install/ort/bin/ort --help
```

## 3. Download the `mime-types` source code

Before scanning `mime-types` its source code has to be downloaded. For reliable results we use version 2.1.18 (replace
`[mime-types-dir]` with the directory you want to clone `mime-types` to):

```bash
git clone https://github.com/jshttp/mime-types.git [mime-types-dir]
cd [mime-types-dir]
git checkout 2.1.18
```

## 4. Run the analyzer on `mime-types`

The next step is to run the _analyzer_. It will create a JSON or YAML output file containing the full dependency tree of
`mime-types` including the metadata of `mime-types` and its dependencies.

```bash
# Command line help specific to the analyzer.
cli/build/install/ort/bin/ort analyze --help

# The easiest way to run the analyzer. Be aware that the [analyzer-output-dir] directory must not exist.
cli/build/install/ort/bin/ort analyze -i [mime-types-dir] -o [analyzer-output-dir]

# The command above will create the default YAML output. If you prefer JSON run:
cli/build/install/ort/bin/ort analyze -i [mime-types-dir] -o [analyzer-output-dir] -f JSON

# To get the maximum log output run:
cli/build/install/ort/bin/ort --debug --stacktrace analyze -i [mime-types-dir] -o [analyzer-output-dir]
```

The _analyzer_ will search for build files of all supported package managers. In case of `mime-types` it will find the
`package.json` file and write the results of the dependency analysis to the output file `analyzer-result.yml`. On the
first attempt of running the analyzer on the `mime-types` package it will fail with an error message:

```bash
The following package managers are activated:
        Bower, Bundler, Cargo, Composer, DotNet, GoDep, Gradle, Maven, NPM, NuGet, PIP, SBT, Stack, Yarn
Analyzing project path:
        [mime-types-dir]
ERROR - Resolving dependencies for 'package.json' failed with: No lockfile found in '[mime-types-dir]'. This potentially results in unstable versions of dependencies. To support this, enable the 'allowDynamicVersions' option in 'ort.conf'.
Writing analyzer result to '[analyzer-output-dir]/analyzer-result.yml'.
```

This happens because `mime-types` does not have `package-lock.json` file. Without this file the versions of (transitive)
dependencies that are defined with version ranges could change at any time, leading to different results of the
analyzer. To override this check, use the global `-P ort.analyzer.allowDynamicVersions=true` option:

```bash
$ cli/build/install/ort/bin/ort -P ort.analyzer.allowDynamicVersions=true analyze -i [mime-types-dir] -o [analyzer-output-dir]
The following package managers are activated:
        Bundler, Composer, GoDep, Gradle, Maven, NPM, PIP, SBT, Stack, Yarn
Analyzing project path:
        [mime-types-dir]
Writing analyzer result to '[analyzer-output-dir]/analyzer-result.yml'.
```

The result file will contain information about the `mime-types` package itself, the dependency tree for each scope, and
information about each dependency. The scope names come from the package managers, for NPM packages these are usually
`dependencies` and `devDependencies`, for Maven package it would be `compile`, `runtime`, `test`, and so on.

Note that the `analyzer-result.yml` is supposed to capture all known information about a project, which can then be
"filtered" in later steps. For example, scopes which are not relevant for the distribution will still be listed,
but can be configured to get excluded so that they e.g. do not get downloaded and scanned by the _scanner_ step.
To specify which scopes should be excluded, add an `.ort.yml` configuration file to the input directory of the
_analyzer_. For more details see [Configuration File](config-file-ort-yml.md).

For this guide, `[mime-types-dir]/.ort.yml` can be created with following content:

```yaml
excludes:
  scopes:
  - pattern: "devDependencies"
    reason: "DEV_DEPENDENCY_OF"
    comment: "Packages for development only."
```

Following is an overview of the structure of the `analyzer-result.yml` file (comments were added for clarity and are not
part of a real result file):

```yaml
# VCS information about the input directory.
repository:
  vcs:
    type: "Git"
    url: "https://github.com/jshttp/mime-types.git"
    revision: "7c4ce23d7354fbf64c69d7b7be8413c4ba2add78"
    path: ""
  vcs_processed:
    type: "Git"
    url: "https://github.com/jshttp/mime-types.git"
    revision: "7c4ce23d7354fbf64c69d7b7be8413c4ba2add78"
    path: ""
  # Will only be present if an '.ort.yml' configuration file with scope excludes was provided. Otherwise this is an empty object.
  config:
    excludes:
      scopes:
      - pattern: "devDependencies"
        reason: "DEV_DEPENDENCY_OF"
        comment: "Packages for development only."
# The analyzer result.
analyzer:
  # The time when the analyzer was executed.
  start_time: "2019-02-19T10:03:07.269Z"
  end_time: "2019-02-19T10:03:19.932Z"
  # Information about the environment the analyzer was run in.
  environment:
    ort_version: "331c32d"
    os: "Linux"
    variables:
      SHELL: "/bin/bash"
      TERM: "xterm-256color"
      JAVA_HOME: "/usr/lib/jvm/java-8-oracle"
    tool_versions: {}
  # Configuration options of the analyzer.
  config:
    allow_dynamic_versions: true
  # The result of the dependency analysis.
  result:
    # Metadata about all found projects, in this case only the mime-types package defined by the package.json file.
    projects:
    - id: "NPM::mime-types:2.1.18"
      purl: "pkg://NPM//mime-types@2.1.18"
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
        revision: "076f7902e3a730970ea96cd0b9c09bb6110f1127"
        path: ""
      homepage_url: ""
      # The dependency trees by scope.
      scopes:
      - name: "dependencies"
        dependencies:
        - id: "NPM::mime-db:1.33.0"
      - name: "devDependencies"
        dependencies:
        - id: "NPM::eslint-config-standard:10.2.1"
        - id: "NPM::eslint-plugin-import:2.8.0"
          dependencies:
          - id: "NPM::builtin-modules:1.1.1"
          - id: "NPM::contains-path:0.1.0"
            # If an issue occurred during the dependency analysis of this package there would be an additional "issues"
            # array.
# ...
# Detailed metadata about each package from the dependency trees.
    packages:
    - package:
        id: "NPM::abbrev:1.0.9"
        purl: "pkg://NPM//abbrev@1.0.9"
        declared_licenses:
        - "ISC"
        declared_licenses_processed:
          spdx_expression: "ISC"
        description: "Like ruby's abbrev module, but in js"
        homepage_url: "https://github.com/isaacs/abbrev-js#readme"
        binary_artifact:
          url: ""
          hash: ""
          hash_algorithm: ""
        source_artifact:
          url: "https://registry.npmjs.org/abbrev/-/abbrev-1.0.9.tgz"
          hash: "91b4792588a7738c25f35dd6f63752a2f8776135"
          hash_algorithm: "SHA-1"
        vcs:
          type: "Git"
          url: "git+ssh://git@github.com/isaacs/abbrev-js.git"
          revision: "c386cd9dbb1d8d7581718c54d4ba944cc9298d6f"
          path: ""
        vcs_processed:
          type: "Git"
          url: "ssh://git@github.com/isaacs/abbrev-js.git"
          revision: "c386cd9dbb1d8d7581718c54d4ba944cc9298d6f"
          path: ""
      curations: []
# ...
# Finally a list of project related issues that happened during dependency analysis. Fortunately empty in this case.
    issues: {}
# A field to quickly check if the analyzer result contains any issues.
    has_issues: false
```

## 5. Run the scanner

To scan the source code of `mime-types` and its dependencies the source code of `mime-types` and all its dependencies
needs to be downloaded. The _downloader_ tool could be used for this, but it is also integrated in the `scanner` tool,
so the scanner will automatically download the source code if the required VCS metadata could be obtained.

Note that if _downloader_ is unable to download the source code due to say a missing source code location in the package
metadata then you can use curations as a workaround.

To use curations, create a [curations.yml](config-file-curations-yml.md)
and pass it to the `--package-curations-file` option of the _analyzer_:

```
cli/build/install/ort/bin/ort analyze
  -i [mime-types-dir]
  -o [analyzer-output-dir]
  --package-curations-file $ORT_CONFIG_DIR/curations.yml
```

ORT is designed to integrate lots of different scanners and is not limited to license scanners, technically any tool
that explores the source code of a software package could be integrated. The actual scanner does not have to run on the
same machine, for example we will soon integrate the [ClearlyDefined](https://clearlydefined.io/) scanner backend which
will perform the actual scanning remotely.

For this tutorial we will use `ScanCode`. You do not have to install the tool manually, it will automatically be
bootstrapped by the `scanner`.

As for the _analyzer_ you can get the command line options for the `scanner` using the `--help` option:

```bash
cli/build/install/ort/bin/ort scan --help
```

The `mime-types` package has only one dependency in the `dependencies` scope, but a lot of dependencies in the
`devDependencies` scope. Scanning all of the `devDependencies` would take a lot of time, so we will only run the
scanner on the `dependencies` scope in this tutorial. If you also want to scan the `devDependencies` it is strongly
advised to configure a [scan storage](../README.md#storage-backends) for the scan results to speed up repeated scans.

As during the _analyzer_ step an `.ort.yml` configuration file was provided to exclude `devDependencies`,
the `--skip-excluded` option can be used to avoid the download and scanning of that scope.

```bash
$ cli/build/install/ort/bin/ort scan -i [analyzer-output-dir]/analyzer-result.yml -o [scanner-output-dir] --skip-excluded
Using scanner 'ScanCode'.
Limiting scan to scopes: [dependencies]
Bootstrapping scanner 'ScanCode' as required version 2.9.2 was not found in PATH.
Using processed VcsInfo(type=git, url=https://github.com/jshttp/mime-db.git, revision=482cd6a25bbd6177de04a686d0e2a0c2465bf445, resolvedRevision=null, path=).
Original was VcsInfo(type=git, url=git+https://github.com/jshttp/mime-db.git, revision=482cd6a25bbd6177de04a686d0e2a0c2465bf445, resolvedRevision=null, path=).
Running ScanCode version 2.9.2 on directory '[scanner-output-dir]/downloads/NPM/unknown/mime-db/1.35.0'.
Using processed VcsInfo(type=git, url=https://github.com/jshttp/mime-types.git, revision=7c4ce23d7354fbf64c69d7b7be8413c4ba2add78, resolvedRevision=null, path=).
Original was VcsInfo(type=, url=https://github.com/jshttp/mime-types.git, revision=, resolvedRevision=null, path=).
Running ScanCode version 2.9.2 on directory '[scanner-output-dir]/downloads/NPM/unknown/mime-types/2.1.18'.
Writing scan result to '[scanner-output-dir]/scan-result.yml'.
```

The `scanner` writes a new ORT result file to `[scanner-output-dir]/scan-result.yml` containing the scan results in
addition to the analyzer result from the input. This way belonging results are stored in the same place for
traceability. If the input file already contained scan results, they are replaced by the new scan results in the output.

As you can see when checking the `scan-result.yml` file, the licenses detected by `ScanCode` match the licenses declared
by the packages. This is because we scanned a small and well-maintained package in this example, but if you run the scan
on a bigger project you will see that `ScanCode` often finds more licenses than are declared by the packages.

## 6. Running the evaluator

The evaluator can apply a set of rules against the scan result created above.
ORT provides examples for the policy rules file ([example.rules.kts](../examples/evaluator-rules/src/main/resources/example.rules.kts)),
user-defined categorization of licenses ([license-classifications.yml](../examples/license-classifications.yml)) and
user-defined package curations ([curations.yml](../examples/curations.yml)) that can be used for testing the
_evaluator_. 

To run the example rules use:

```bash
cli/build/install/ort/bin/ort evaluate
  --package-curations-file curations.yml
  --rules-file evaluator.rules.kts
  --license-classifications-file license-classifications.yml
  -i [scanner-output-dir]/scan-result.yml
  -o [evaluator-output-dir]/mime-types
```

See the [curations.yml documentation](config-file-curations-yml.md) to learn more about using curations to correct
invalid or missing package metadata and the [license-classifications.yml documentation](config-file-license-classifications-yml.md) on
how you can classify licenses to simplify writing the policy rules.

It is possible to write your own evaluator rules as a Kotlin script and pass it to the _evaluator_ using `--rules-file`.
Note that detailed documentation for writing custom rules is not yet available.

## 7. Generate a report

The `evaluation-result.yml` file can now be used as input for the reporter to generate human-readable reports and open
source notices. 

For example, to generate a static HTML report, WebApp report, and an open source notice by package, use:

```bash
cli/build/install/ort/bin/ort report
  -f NoticeTemplate,StaticHtml,WebApp
  -i [evaluator-output-dir]/evaluation-result.yml
  -o [reporter-output-dir]
Created 'StaticHtml' report: [reporter-output-dir]/scan-report.html
Created 'WebApp' report: [reporter-output-dir]/scan-report-web-app.html
Created 'NoticeTemplate' report: [reporter-output-dir]/NOTICE_default
```

If you do not want to run the _evaluator_ you can pass the _scanner_ result e.g. `[scanner-output-dir]/scan-result.yml`
to the `reporter` instead. To learn how you can customize generated notices see
[notice-templates.md](notice-templates.md). To learn how to customize the how-to-fix texts for scanner and analyzer
issues see [how-to-fix-text-provider-kts.md](how-to-fix-text-provider-kts.md).

## 8. Curating Package Metadata or License Findings

In the example above everything went well because the VCS information provided by the packages was correct, but this is
not always the case. Often the metadata of packages has no VCS information, points to outdated repositories, or the
repositories are not correctly tagged.

ORT provides a variety of mechanisms to fix a variety of issues, for details see:

* [The .ort.yml file](config-file-ort-yml.md) - project-specific license finding curations, exclusions and resolutions
  to address issues found within a project's code repository.
* [The package configuration file](config-file-package-configuration-yml.md) - package (dependency) and provenance
  specific license finding curations and exclusions to address issues found within a scan result for a package.
* [The curations.yml file](config-file-curations-yml.md) - curations correct invalid or missing package metadata and set
  the concluded license for packages.
* [The resolutions.yml file](config-file-resolutions-yml.md) - resolutions allow *resolving* any issues or policy rule
  violations by providing a reason why they are acceptable and can be ignored.
