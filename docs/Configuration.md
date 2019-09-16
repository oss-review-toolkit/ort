# OSS Review Toolkit Configuration

This page describes the different configuration options of ORT.

## Repository Configuration

ORT's behavior can be customized for a specific repository by adding a `.ort.yml` file to the root of the repository.
Currently, this file can only be used to configure the excludes described below, but more features are planned for the
future.

Note that for Git-Repo repositories, the default location of the configuration file is next to the manifest file. For
example, if the manifest is `manifest.xml` the configuration should be `manifest.xml.ort.yml`.

### Excludes

ORT's philosophy is to analyze and scan everything it can find to build a complete picture of a repository and its
dependencies. However, the users may not be interested in the results for components that are not distributed, for
example CI configuration or test code. To support such cases, ORT provides a mechanism to mark parts of the repository
as excluded.

Note that the excluded parts are analyzed and scanned, but are treated differently in the reports ORT generates:

* The error summary does not show errors in the excluded parts.
* The excluded parts are grayed out.
* The reason for the exclusion is shown next to the result.

To be able to show why a part is excluded, ORT requires the user to provide an explanation the file `.ort.yml`. The
explanation consists of:

* `reason` -- must be selected from a predefined list of options
* `comment` -- free text that provides an additional explanation

The sections below contain links to the lists of available exclude reasons for each type of exclude.

#### Excluding Paths

Path excludes are used to mark a complete path as excluded. They are defined using a
[glob pattern](https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob), a `reason` and a `comment`:

```yaml
excludes:
  paths:
  - pattern: "test-data/**"
    reason: "TEST_TOOL_OF"
    comment: "This folder contains examples which are not distributed."
```

The path exclude above has the following effects:

* All projects found below the `test-data` directory are marked as excluded. This overlaps with
  [project excludes](#excluding-projects), with the difference that project excludes can also be used to exclude
  specific [dependency scopes](#excluding-scopes).
* License findings in files below the `test-data` directory are marked as excluded. This can be used to ignore such
  license findings when writing [evaluator rules](GettingStarted.md#6-running-the-evaluator) by checking if the are
  excluded.

For the available exclude reasons for paths, see
[PathExcludeReason.kt](../model/src/main/kotlin/config/PathExcludeReason.kt).

#### Excluding Scopes

Many package managers support grouping of dependencies by their use. Such groups are called `scopes` in ORT. For
example, Maven provides the scopes `compile`, `provided`, and `test`, while NPM scopes are `dependencies` and
`devDependencies`.

You can use regular expressions to select the scopes to exclude. This can be useful, for example, with Gradle, which
creates a relatively large number of scopes (internally Gradle calls them `configurations`).

Scopes are always excluded globally that is they apply to all projects.

```yaml
excludes:
  scopes:
  - name: "test.*"
    reason: "TEST_TOOL_OF"
    comment: "Test dependencies which are not distributed."
```

The above example excludes all of the following scopes for all projects: `testAnnotationProcessor`,`testApi`,
`testCompile`, `testCompileClasspath`, `testCompileOnly`, `testImplementation`, `testRuntime`, `testRuntimeClasspath`,
`testRuntimeOnly`.

For the available exclude reasons for scopes, see
[ScopeExcludeReason.kt](../model/src/main/kotlin/config/ScopeExcludeReason.kt).

See the examples below for typical scope excludes for the supported package managers. Note that you must verify that
these examples match the configuration of your project.

* [bower.ort.yml](examples/bower.ort.yml)
* [bundler.ort.yml](examples/bundler.ort.yml)
* [cargo.ort.yml](examples/cargo.ort.yml)
* [go-dep.ort.yml](examples/go-dep.ort.yml)
* [gradle.ort.yml](examples/gradle.ort.yml)
* [gradle-android.ort.yml](examples/gradle-android.ort.yml)
* [maven.ort.yml](examples/maven.ort.yml)
* [npm.ort.yml](examples/npm.ort.yml)
* [php-composer.ort.yml](examples/php-composer.ort.yml)
* [pip.ort.yml](examples/pip.ort.yml)
* [sbt.ort.yml](examples/sbt.ort.yml)
* [stack.ort.yml](examples/stack.ort.yml)
* [yarn.ort.yml](examples/yarn.ort.yml)

### Resolving errors

If the ORT results show errors, the best approach is usually to fix them and run the scan again. However, sometimes it
is not possible or necessary to fix an error, for example:

* The error occurs in the license scan of a third-party dependency which cannot be fixed or updated.
* The error is a false positive.
* The error occurs in a part of the repository which is not distributed (in this case it might be better to use the
  excludes mechanism described above).

In such situations, *resolve* the error by indicating in `.ort.yml` that it can be ignored. This requires a
an entry consisting of `message`, `reason` and `comment`. Here is the pattern to follow:

```yaml
resolutions:
  errors:
  - message: "A regular expression matching the error message."
    reason: "One of: BUILD_TOOL_ISSUE|CANT_FIX_ISSUE|SCANNER_ISSUE"
    comment: "A comment further explaining why the reason above is applicable here."
```

For detailed information about the available reasons, see
[ErrorResolutionReason.kt](../model/src/main/kotlin/config/ErrorResolutionReason.kt).

For example, to ignore an error related to a build tool problem, your `.ort.yml` could include:

```yaml
resolutions:
  errors:
  - message: "Does not have X.*"
    reason: "BUILD_TOOL_ISSUE"
    comment: "Error caused by a known problem in the build tool."
```

If there are other errors to ignore, but no existing `message` captures them, create further entries under
`resolutions`/`errors` in `.ort.yml` as appropriate.

Error resolutions are taken into account only by the `reporter`, while the `analyzer` and `scan` results ignore them and
show the errors. In the generated reports, however, resolved errors do not appear in the error summary and are marked as
resolved next to the items in which they were found.

Note that if an error resolution is not specific to the project, it should not be configured in `.ort.yml`, but in a
global resolutions file. This makes such resolutions reusable for different projects. For details, see
[below](#resolving-errors-and-rule-violations).

### Resolving rule violations

Like errors (see [Resolving errors](#resolving-errors)), rule violations can be resolved by providing a regular
expression matching the rule violation message along with a `reason` and an explanatory `comment` in `.ort.yml` on this
pattern:

```yaml
resolutions:
  rule_violations:
  - message: "A regular expression matching the rule violation message."
    reason: "One of: CANT_FIX_EXCEPTION|DYNAMIC_LINKAGE_EXCEPTION|EXAMPLE_OF_EXCEPTION|LICENSE_ACQUIRED_EXCEPTION|PATENT_GRANT_EXCEPTION"
    comment: "A comment further explaining why the reason above is applicable."
```

For details of the available reasons, see
[RuleViolationResolutionReason.kt](../model/src/main/kotlin/config/RuleViolationResolutionReason.kt)

## Global configuration

This section describes repository-independent configuration options for ORT.

### Curating metadata of packages

In order to discover the source code of the dependencies of a package, ORT relies on the package metadata. Often the
metadata contains information on how to locate the source code, but not always. In many cases, the metadata of packages
has no VCS information, it points to outdated repositories or the repositories are not correctly tagged.  Because this
information can not always be fixed in upstream packages, ORT provides a mechanism to curate metadata of packages.

These curations can be configured in a YAML file that is passed to the `analyzer`. The data from the curations file
amends the metadata provided by the packages themselves. This way, it is possible to fix broken VCS URLs or provide the
location of source artifacts. The structure of the curations file is:

```yaml
# Example for a complete curation object:
#- id: "Maven:org.hamcrest:hamcrest-core:1.3"
#  curations:
#    concluded_license: "Apache-2.0 OR MIT" # Has to be a valid SPDX license expression.
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

- id: "Maven:asm:asm" # No version means the curation will be applied to all versions of the package.
  curations:
    comment: "Repository moved to https://gitlab.ow2.org."
    vcs:
      type: "git"
      url: "https://gitlab.ow2.org/asm/asm.git"

- id: "NPM::ast-traverse:0.1.0"
  curations:
    comment: "Revision found by comparing NPM packages with the sources from https://github.com/olov/ast-traverse."
    vcs:
      revision: "f864d24ba07cde4b79f16999b1c99bfb240a441e"
- id: "NPM::ast-traverse:0.1.1"
  curations:
    comment: "Revision found by comparing NPM packages with the sources from https://github.com/olov/ast-traverse."
    vcs:
      revision: "73f2b3c319af82fd8e490d40dd89a15951069b0d"

- id: "NPM::ramda:[0.21.0,0.25.0]" # Ivy-style version matchers are supported.
  curations:
    comment: "The package is licensed under MIT per `LICENSE` and `dist/ramda.js`. The project logo is CC-BY-NC-SA-3.0 \
      but it is not part of the distributed .tar.gz package, see the `README.md` which says: \
      Ramda logo artwork © 2014 J. C. Phillipps. Licensed Creative Commons CC BY-NC-SA 3.0"
    concluded_license: "MIT"
```

To use the curations file pass it to the `--package-curations-file` option of the `analyzer`:

```
cli/build/install/ort/bin/ort analyze -i [input-path] -o [analyzer-output-path] --package-curations-file [curations-file-path]
```

In the future we will integrate [ClearlyDefined](https://clearlydefined.io/) as a source for curated metadata. Until
then, and also for curations for internal packages that cannot be published, the curations file can be used.

### Resolving errors and rule violations

Global error resolutions work the same way as the project-specific error resolutions described in [Resolving
errors](#resolving-errors) and in [Resolving rule violations](#resolving-rule-violations), but instead of the `.ort.yml`
file of the project, they are added to a separate configuration file, which makes them reusable for different
projects. Global error resolutions should only be used for generic errors that can always be ignored, for example scan
errors in third-party package repositories which relate to files not built into the package, such as documentation or
test files.

The structure of the resolutions file is:

```yaml
errors:
- message: "A regular expression matching the error message."
  reason: "One of: BUILD_TOOL_ISSUE|CANT_FIX_ISSUE|SCANNER_ISSUE"
  comment: "A comment further explaining why the reason above is applicable here."
rule_violations:
- message: "A regular expression matching the rule violation message."
  reason: "One of: CANT_FIX_EXCEPTION|DYNAMIC_LINKAGE_EXCEPTION|EXAMPLE_OF_EXCEPTION|LICENSE_ACQUIRED_EXCEPTION|PATENT_GRANT_EXCEPTION"
  comment: "A comment further explaining why the reason above is applicable here."
```

The resolutions file can be passed to the `reporter` using the `--resolutions-file` option:

```
cli/build/install/ort/bin/ort report -i [ort-result-path] -o [reporter-output-path] --resolutions-file [resolutions-file-path]
```
