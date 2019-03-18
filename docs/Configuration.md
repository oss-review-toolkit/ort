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

* All projects found below the `test-data` directory are marked as excluded.
* License findings in files below the `test-data` directory are marked as excluded. This can be used to ignore such
  license findings when writing [evaluator rules](GettingStarted.md#6-running-the-evaluator).

For the available exclude reasons for paths, see
[PathExcludeReason.kt](../model/src/main/kotlin/config/PathExcludeReason.kt).

#### Excluding Projects

ORT defines projects by searching for project definition files in the repositories. For example a Gradle project is
created for each `build.gradle` file, or an NPM project for each `package.json` file. To exclude such projects, provide
the path to the definition file relative to the root of the repository in the file, a `reason` and a `comment` in
`.ort.yml` on this pattern:

```yaml
excludes:
  projects:
  - path: "integrationTests/build.gradle"
    reason: "TEST_TOOL_OF"
    comment: "The project contains integration tests which are not distributed."
```

This configuration marks a whole project and all its dependencies as excluded. If the scan of a dependency of this
project finds an error, that error will not appear in the error summary in the generated reports.

Note that `path` is a regular expression, which makes it possible to exclude all Gradle projects in a repository using a
single rule:

```yaml
excludes:
  projects:
  - path: ".*build\\.gradle"
    reason: "EXAMPLE_OF"
    comment: "The project contains example code which is not distributed."
```

For the available exclude reasons for projects, see
[ProjectExcludeReason.kt](../model/src/main/kotlin/config/ProjectExcludeReason.kt).

#### Excluding Scopes

Many package managers support grouping of dependencies by their use. Such groups are called `scopes` in ORT. For
example, Maven provides the scopes `compile`, `provided`, and `test`, while NPM scopes are `dependencies` and
`devDependencies`.

You can use regular expressions to select the scopes to exclude. This can be useful, for example, with Gradle, which
creates a relatively large number of scopes (internally Gradle calls them `configurations`).

Scopes can be excluded on different levels: globally for all projects, and locally for a single project. Global scope
excludes are useful for repositories that contain many similar projects:

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

To exclude the same scopes for a single project:

```yaml
excludes:
  projects:
  - path: "app/build.gradle"
    scopes:
    - name: "test.*"
      reason: "TEST_TOOL_OF"
      comment: "Test dependencies which are not distributed."
```

Note that in this case only the defined scopes within the project are excluded, not the whole project. Therefore
`reason` and `comment` do not need to be set for the project itself.

It is possible to mix global and local scope excludes in a single `.ort.yml` file.

For the available exclude reasons for scopes, see
[ScopeExcludeReason.kt](../model/src/main/kotlin/config/ScopeExcludeReason.kt).

See the examples below for typical scope excludes for the supported package managers. Note that you must verify that
these examples match the configuration of your project.

* [bower.yml](examples/bower.yml)
* [bundler.yml](examples/bundler.yml)
* [go-dep.yml](examples/go-dep.yml)
* [gradle.yml](examples/gradle.yml)
* [gradle-android.yml](examples/gradle-android.yml)
* [maven.yml](examples/maven.yml)
* [npm.yml](examples/npm.yml)
* [php-composer.yml](examples/php-composer.yml)
* [pip.yml](examples/pip.yml)
* [sbt.yml](examples/sbt.yml)
* [stack.yml](examples/stack.yml)
* [yarn.yml](examples/yarn.yml)

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
