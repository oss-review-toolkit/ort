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

To run ORT the following tools are required:

* Java (version >= 8)
* Git

For some of the supported package managers and SCMs additional tools need to be installed:

* CVS
* [dep](https://github.com/golang/dep) 0.4.1
* Go (tested with 1.10)
* Mercurial
* NPM 5.5.1 and Node.js 8.x (required for this tutorial because we scan `mime-types` which is an NPM project)
* PHP Composer >= 1.5
* SBT
* Subversion
* Yarn 1.3.2

## 2. Download & Install ORT

In future we will provide binaries of the ORT tools, but currently you have to build the tools on your own. First
download the source code from GitHub:

```bash
git clone https://github.com/heremaps/oss-review-toolkit.git
```

To build the tools run:

```bash
cd oss-review-toolkit
./gradlew installDist
```

This will create binaries of the tools in their builds folders, for example the analyzer binary can be found in
`analyzer/build/install/analyzer/bin/analyzer`. To get the command line help for tool run it with the `--help` option:

```bash
analyzer/build/install/analyzer/bin/analyzer --help
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
# The easiest way to run the analyzer. Be aware that the [output-path] directory must not exist.
analyzer/build/install/analyzer/bin/analyzer -i [mime-types-path] -o [output-path]

# The command above will create the default YAML output. If you prefer JSON run:
analyzer/build/install/analyzer/bin/analyzer -i [mime-types-path] -o [output-path] -f JSON

# To get the maximum log output run:
analyzer/build/install/analyzer/bin/analyzer -i [mime-types-path] -o [output-path] --debug --stacktrace
```

The `analyzer` will search for build files of all supported package managers. In case of `mime-types` it will find the
`package.json` file and write the results of the dependency analysis to the output file `all-dependencies.yml`:

```bash
$ analyzer/build/install/analyzer/bin/analyzer -i ~/git/mime-types -o ~/analyzer-results/mime-types
The following package managers are activated:
        Gradle, Maven, SBT, NPM, GoDep, PIP, Bundler, PhpComposer
Scanning project path:
        [mime-types-path]
NPM projects found in:
        package.json
Resolving NPM dependencies for '[mime-types-path]/package.json'...
Writing analyzer result
to
        [output-path]/mime-types/all-dependencies.yml
done.
```

The result file will contain information about the `mime-types` package itself, the dependency tree for each scope, and
information about each dependency. The scope names come from the package managers, for NPM packages these are usually
`dependencies` and `devDependencies`, for Maven package it would be `compile`, `runtime`, `test`, and so on.

The structure of the results file is:

```yaml
allowDynamicVersions: false
# VCS information for the input directory.
vcs:
  type: "Git"
  url: "https://github.com/jshttp/mime-types.git"
  revision: "076f7902e3a730970ea96cd0b9c09bb6110f1127"
  path: ""
vcs_processed:
  type: "git"
  url: "https://github.com/jshttp/mime-types.git"
  revision: "076f7902e3a730970ea96cd0b9c09bb6110f1127"
  path: ""
# Metadata about the mime-types package.
projects:
- id: "NPM::mime-types:2.1.18"
  definition_file_path: "package.json"
  declared_licenses:
  - "MIT"
  aliases: []
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
    distributed: true
    dependencies:
    - id: "NPM::mime-db:1.33.0"
      dependencies: []
      errors: []
  - name: "devDependencies"
    distributed: false
    dependencies:
    - id: "NPM::eslint-config-standard:10.2.1"
      dependencies: []
      errors: []
    - id: "NPM::eslint-plugin-import:2.8.0"
      dependencies:
      - id: "NPM::builtin-modules:1.1.1"
        dependencies: []
        errors: [] # If an error occured during the dependency analysis of this package it would be in this array.
# ...
# Detailed metadata about each package from the dependency trees.
packages:
- package:
    id: "NPM::abbrev:1.0.9"
    declared_licenses:
    - "ISC"
    description: "Like ruby's abbrev module, but in js"
    homepage_url: "https://github.com/isaacs/abbrev-js#readme"
    binary_artifact:
      url: "https://registry.npmjs.org/abbrev/-/abbrev-1.0.9.tgz"
      hash: "91b4792588a7738c25f35dd6f63752a2f8776135"
      hash_algorithm: "SHA-1"
    source_artifact:
      url: ""
      hash: ""
      hash_algorithm: ""
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
#  Finally a list of errors that happened during dependency analysis. Fortunately empty in this case.
errors: []
```

If you try the commands above with a different NPM package that does not have a
[package-lock.json](https://docs.npmjs.com/files/package-locks) (or `npm-shrinkwrap.json` or `yarn.lock`) the analyzer
will terminate with an error message like this:

```
ERROR - Analysis for these projects did not complete successfully:
[npm-project-path]/package.json
```

This means that there have been issues with the dependency resolution of these packages. The reasons for these errors
can be found in the log output of the `analyzer` or in the results file:

```
Resolving NPM dependencies for '[npm-project-path]/package.json'...
17:11:16.683 ERROR - Resolving dependencies for 'package.json' failed with: No lockfile found in [npm-project-path], dependency versions are unstable.
```

This happens because without a [lockfile](https://docs.npmjs.com/files/package-locks) the versions of transitive
dependencies could change at any time. Therefore ORT checks for the presence of a lockfile to generate reliable results.
This check can be disabled with the `--allow-dynamic-versions` option.

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
scanner/build/install/scanner/bin/scanner --help
```

The `mime-types` package has only one dependency in the `depenencies` scope, but a lot of dependencies in the
`devDependencies` scope. Scanning all of the `devDependencies` would take a lot of time, so we will only run the
scanner on the `dependencies` scope in this tutorial. If you also want to scan the `devDependencies` it is strongly
advised to configure a cache for the scan results as documented above to speed up repeated scans.

```bash
$ scanner/build/install/scanner/bin/scanner -d [analyzer-output-path]/all-dependencies.yml -o [scanner-output-path] --scopes dependencies
Using scanner 'ScanCode'.
Limiting scan to scopes: [dependencies]
Bootstrapping scanner 'ScanCode' as version 2.9.2 was not found in PATH.
Using processed VcsInfo(type=git, url=https://github.com/jshttp/mime-db.git, revision=e7c849b1c70ff745a4ae456a0cd5e6be8b05c2fb, resolvedRevision=null, path=).
Original was VcsInfo(type=git, url=git+https://github.com/jshttp/mime-db.git, revision=e7c849b1c70ff745a4ae456a0cd5e6be8b05c2fb, resolvedRevision=null, path=).
Running ScanCode version 2.9.2 on directory '[scanner-output-path]/mime-types/downloads/NPM/unknown/mime-db/1.33.0'.
Using processed VcsInfo(type=git, url=https://github.com/jshttp/mime-types.git, revision=076f7902e3a730970ea96cd0b9c09bb6110f1127, resolvedRevision=null, path=).
Original was VcsInfo(type=, url=https://github.com/jshttp/mime-types.git, revision=, resolvedRevision=null, path=).
Running ScanCode version 2.9.2 on directory '[scanner-output-path]/mime-types/downloads/NPM/unknown/mime-types/2.1.18'.
Declared licenses for 'NPM::mime-db:1.33.0': MIT
Detected licenses for 'NPM::mime-db:1.33.0': MIT
Declared licenses for 'NPM::mime-types:2.1.18': MIT
Detected licenses for 'NPM::mime-types:2.1.18': MIT
Writing scan record to [scanner-output-path]/mime-types/scan-record.yml.
```

As you can see from the output the licenses detected by `ScanCode` match the licenses declared by the packages. This is
because we scanned a small and well-maintained package in this example, but if you run the scan on a bigger project you
will see that `ScanCode` often finds more licenses than are declared by the packages.

The `scanner` writes the raw scanner output for each scanned package to a file in the
`[scanner-output-path]/scanResults` directory. Additionally it creates a `[scanner-output-path]/scan-record.yml` file
which contains a summary of all licenses for all packages and some more details like cache statistics and information
about the scanned scopes.

## 6. Generate a report

The `scan-record.yml` file can now be used as input for the reporter to generate human-readable reports. For example to
generate both, the static HTML report and the Excel record, use:

```bash
reporter/build/install/reporter/bin/reporter  -f STATIC_HTML,EXCEL -s [scanner-output-path]/mime-types/scan-record.yml -o [reporter-output-path]/mime-types
Writing static HTML report to '[reporter-output-path]/mime-types/scan-report.html'.
Writing Excel report to '[reporter-output-path]/mime-types/scan-report.xlsx'.
```

## 7. Curating the metadata

In the example above everything went well because the VCS information provided by the packages was correct, but this is
not always the case. Often the metadata of packages has no VCS information, points to outdated repositories, or the
repositories are not correctly tagged. Because this information can not always be fixed in remote packages ORT provides
a mechanism to curate metadata of packages.

These curations can be configured in a YAML file that has to be passed to the `analyzer`. The data from the curations
file will overwrite the metadata provided by the packages themselves. This way it is possible to fix borken VCS URLs or
provide the location of source artifacts. The structure of the curations file is:

```yaml
# Example for a complete curation object:
#- id: "Maven:org.hamcrest:hamcrest-core:1.3"
#  curations:
#    declared_licenses:
#    - "license a"
#    - "license b"
#    description: "curated description"
#    homepage_url: "http://example.com"
#    binary_artifact:
#      url: "http://example.com/binary.zip"
#      hash: "ddce269a1e3d054cae349621c198dd52"
#      hash_algorithm: "MD5"
#    source_artifact:
#      url: "http://example.com/sources.zip"
#      hash: "ddce269a1e3d054cae349621c198dd52"
#      hash_algorithm: "MD5"
#    vcs:
#      type: "git"
#      url: "http://example.com/repo.git"
#      revision: "1234abc"
#      path: "subdirectory"

# A few examples:

# Repository moved to https://gitlab.ow2.org.
- id: "Maven:asm:asm" # No version means the curation will be applied to all versions of the package.
  curations:
    vcs:
      type: "git"
      url: "https://gitlab.ow2.org/asm/asm.git"

# Revisions found by comparing NPM packages with the sources from https://github.com/olov/ast-traverse.
- id: "NPM::ast-traverse:0.1.0"
  curations:
    vcs:
      revision: "f864d24ba07cde4b79f16999b1c99bfb240a441e"
- id: "NPM::ast-traverse:0.1.1"
  curations:
    vcs:
      revision: "73f2b3c319af82fd8e490d40dd89a15951069b0d"
```

To use the curations file pass it to the `--package-curations-file` option of the `analyzer`:

```
analyzer/build/install/analyzer/bin/analyzer -i [input-path] -o [output-path] --package-curations-file [curations-file-path]
```

In future we will integrate [ClearlyDefined](https://clearlydefined.io/) as a source for curated metadata. Until then,
and also for curations for internal packages that cannot be published, the curations file can be used.
