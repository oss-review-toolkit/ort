# Getting Started

This tutorial gives a brief introduction to how the tools work together at the example of the
[mime-types](https://www.npmjs.com/package/mime-types) NPM package. It will guide through the main steps for running
ORT:

* Install ORT.
* Analyze the dependencies of `mime-types` using the `analyzer`.
* Scan the source code of `mime-types` and its dependencies using the `scanner`.

## 1. Prerequisites

ORT is tested to run on Linux, macOS, and Windows. This tutorial assumes that you are running on Linux, but it should be
easy to adapt the commands to macOS or Windows.

In addition to Java (version >= 8), for some of the supported package managers and Version Control Systems additional
tools need to be installed. In the context of this tutorial the following tools are required:

* Git (any recent version will do)
* [Node.js](https://nodejs.org) 8.*
* [NPM](https://www.npmjs.com) 5.5.* - 6.4.*
* [Yarn](https://yarnpkg.com) 1.9.* - 1.16.*

For the full list of supported package managers and Version Control Systems see the [README](../README.md).

## 2. Download & Install ORT

In future we will provide binaries of the ORT tools, but currently you have to build the tools on your own. First
download the source code (including Git submodules) from GitHub:

```bash
git clone --recurse-submodules https://github.com/heremaps/oss-review-toolkit.git
```

To build the command line interface run:

```bash
cd oss-review-toolkit
./gradlew installDist
```

This will create the script to run ORT at `cli/build/install/ort/bin/ort`. To get the general command line help run it
with the `--help` option:

```bash
cli/build/install/ort/bin/ort --help
```

## 3. Download the `mime-types` source code

Before scanning `mime-types` its source code has to be downloaded. For reliable results we use version 2.1.18 (replace
`[mime-types-path]` with the path you want to clone `mime-types` to):

```bash
git clone https://github.com/jshttp/mime-types.git [mime-types-path]
cd [mime-types-path]
git checkout 2.1.18
```

## 4. Run the analyzer on `mime-types`

The next step is to run the `analyzer`. It will create a JSON or YAML output file containing the full dependency tree of
`mime-types` including the meta-data of `mime-types` and its dependencies.

```bash
# Command line help specific to the analyzer.
cli/build/install/ort/bin/ort analyze --help

# The easiest way to run the analyzer. Be aware that the [analyzer-output-path] directory must not exist.
cli/build/install/ort/bin/ort analyze -i [mime-types-path] -o [analyzer-output-path]

# The command above will create the default YAML output. If you prefer JSON run:
cli/build/install/ort/bin/ort analyze -i [mime-types-path] -o [analyzer-output-path] -f JSON

# To get the maximum log output run:
cli/build/install/ort/bin/ort --debug --stacktrace analyze -i [mime-types-path] -o [analyzer-output-path]
```

The `analyzer` will search for build files of all supported package managers. In case of `mime-types` it will find the
`package.json` file and write the results of the dependency analysis to the output file `analyzer-result.yml`. On the
first attempt of running the analyzer on the `mime-types` package it will fail with an error message:

```bash
The following package managers are activated:
        Gradle, Maven, SBT, NPM, Yarn, GoDep, PIP, Bundler, PhpComposer, Stack
Analyzing project path:
        [mime-types-path]
ERROR - Resolving dependencies for 'package.json' failed with: No lockfile found in '[mime-types-path]'. This potentially results in unstable versions of dependencies. To allow this, enable support for dynamic versions.
Writing analyzer result to '[analyzer-output-path]/analyzer-result.yml'.
```

This happens because `mime-types` does not have `package-lock.json` file. Without this file the versions of (transitive)
dependencies that are defined with version ranges could change at any time, leading to different results of the
analyzer. To override this check use the `--allow-dynamic-versions` option:

```bash
$ cli/build/install/ort/bin/ort analyze -i [mime-types-path] -o [analyzer-output-path] --allow-dynamic-versions
The following package managers are activated:
        Gradle, Maven, SBT, NPM, Yarn, GoDep, PIP, Bundler, PhpComposer, Stack
Analyzing project path:
        [mime-types-path]
Writing analyzer result to '[analyzer-output-path]/analyzer-result.yml'.
```

The result file will contain information about the `mime-types` package itself, the dependency tree for each scope, and
information about each dependency. The scope names come from the package managers, for NPM packages these are usually
`dependencies` and `devDependencies`, for Maven package it would be `compile`, `runtime`, `test`, and so on.

The structure of the results file is:

```yaml
# VCS information about the input directory.
repository:
  vcs:
    type: "Git"
    url: "https://github.com/jshttp/mime-types.git"
    revision: "7c4ce23d7354fbf64c69d7b7be8413c4ba2add78"
    path: ""
  vcs_processed:
    type: "git"
    url: "https://github.com/jshttp/mime-types.git"
    revision: "7c4ce23d7354fbf64c69d7b7be8413c4ba2add78"
    path: ""
  config: {}
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
    ignore_tool_versions: false
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
        type: "git"
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
            # If an error occured during the dependency analysis of this package there would be an additional "errors"
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
          type: "git"
          url: "git+ssh://git@github.com/isaacs/abbrev-js.git"
          revision: "c386cd9dbb1d8d7581718c54d4ba944cc9298d6f"
          path: ""
        vcs_processed:
          type: "git"
          url: "ssh://git@github.com/isaacs/abbrev-js.git"
          revision: "c386cd9dbb1d8d7581718c54d4ba944cc9298d6f"
          path: ""
      curations: []
# ...
# Finally a list of project related errors that happened during dependency analysis. Fortunately empty in this case.
    errors: {}
# A field to quickly check if the analyzer result contains any errors.
    has_errors: false
```

## 5. Run the scanner

To scan the source code of `mime-types` and its dependencies the source code of `mime-types` and all its dependencies
needs to be downloaded. The `downloader` tool could be used for this, but it is also integrated in the `scanner` tool,
so the scanner will automatically download the source code if the required VCS metadata could be obtained.

ORT is designed to integrate lots of different scanners and is not limited to license scanners, technically any tool
that explores the source code of a software package could be integrated. The actual scanner does not have to run on the
same machine, for example we will soon integrate the [ClearlyDefined](https://clearlydefined.io/) scanner backend which
will perform the actual scanning remotely.

For this tutorial we will use `ScanCode`. You do not have to install the tool manually, it will automatically be
bootstrapped by the `scanner`.

As for the `analyzer` you can get the command line options for the `scanner` using the `--help` option:

```bash
cli/build/install/ort/bin/ort scan --help
```

The `mime-types` package has only one dependency in the `depenencies` scope, but a lot of dependencies in the
`devDependencies` scope. Scanning all of the `devDependencies` would take a lot of time, so we will only run the
scanner on the `dependencies` scope in this tutorial. If you also want to scan the `devDependencies` it is strongly
advised to configure a cache for the scan results as documented in the [README](../README.md) to speed up repeated scans.

```bash
$ cli/build/install/ort/bin/ort scan --ort-file [analyzer-output-path]/analyzer-result.yml -o [scanner-output-path] --scopes dependencies
Using scanner 'ScanCode'.
Limiting scan to scopes: [dependencies]
Bootstrapping scanner 'ScanCode' as required version 2.9.2 was not found in PATH.
Using processed VcsInfo(type=git, url=https://github.com/jshttp/mime-db.git, revision=482cd6a25bbd6177de04a686d0e2a0c2465bf445, resolvedRevision=null, path=).
Original was VcsInfo(type=git, url=git+https://github.com/jshttp/mime-db.git, revision=482cd6a25bbd6177de04a686d0e2a0c2465bf445, resolvedRevision=null, path=).
Running ScanCode version 2.9.2 on directory '[scanner-output-path]/downloads/NPM/unknown/mime-db/1.35.0'.
Using processed VcsInfo(type=git, url=https://github.com/jshttp/mime-types.git, revision=7c4ce23d7354fbf64c69d7b7be8413c4ba2add78, resolvedRevision=null, path=).
Original was VcsInfo(type=, url=https://github.com/jshttp/mime-types.git, revision=, resolvedRevision=null, path=).
Running ScanCode version 2.9.2 on directory '[scanner-output-path]/downloads/NPM/unknown/mime-types/2.1.18'.
Writing scan result to '[scanner-output-path]/scan-result.yml'.
```

As you can see from when you check the results file the licenses detected by `ScanCode` match the licenses declared by
the packages. This is because we scanned a small and well-maintained package in this example, but if you run the scan on
a bigger project you will see that `ScanCode` often finds more licenses than are declared by the packages.

The `scanner` writes a new ORT result file to `[scanner-output-path]/scan-result.yml` containing the scan results in
addition to the analyzer result from the input. This way belonging results are stored in the same place for traceability.
If the input file already contained scan results they are replaced by the new scan results in the output.

## 6. Running the evaluator

The evaluator can apply a set of rules against the scan result created above. ORT provides a
[sample rules file](../evaluator/src/main/resources/rules/no_gpl_declared.kts) on the classpath that can be used for
testing the evaluator. The sample rule creates an error if any package contained in the result declares a GPL license.
To run the sample rules use:

```bash
cli/build/install/ort/bin/ort evaluate --rules-resource rules/no_gpl_declared.kts \
    -i [scanner-output-path]/scan-result.yml -o [evaluator-output-path]/mime-types
```

Because neither mime-types nor any of its dependencies declares a GPL license this finishes without an error.

It is possible to write your own evaluator rules as Kotlin script and pass it to the evaluator using `--rules-file`.
Note that detailed documentation for writing custom rules is not yet available.

## 7. Generate a report

The `evaluation-result.yml` file can now be used as input for the reporter to generate human-readable reports. For
example, to generate both a static HTML report and an Excel report use:

```bash
cli/build/install/ort/bin/ort report -f StaticHtml,Excel -i [evaluator-output-path]/evaluation-result.yml -o [reporter-output-path]
Writing static HTML report to '[reporter-output-path]/scan-report.html'.
Writing Excel report to '[reporter-output-path]/scan-report.xlsx'.
```

If you do not want to run the evaluator you can pass the scan result `[scanner-output-path/scan-result.yml` to the
reporter instead.

## 8. Curating the metadata

In the example above everything went well because the VCS information provided by the packages was correct, but this is
not always the case. Often the metadata of packages has no VCS information, points to outdated repositories, or the
repositories are not correctly tagged. Because this information can not always be fixed in remote packages ORT provides
a mechanism to curate metadata of packages. For details, see
[Configuration.md](./Configuration.md#curating-metadata-of-packages).
