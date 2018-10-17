# OSS Review Toolkit Configuration

This page describes the different configuration options of ORT.

## Repository Configuration

ORT's behavior can be customized for a specific repository by adding an `.ort.yml` file to the root of the repository.
Currently this file can only be used to configure the excludes described below, but more features are planned for the
future.

### Excludes

ORT's philosophy is to always analyze and scan everything it can find to get a complete picture of a repository and its
dependencies. But often the users are not interested in the results for all components, e.g., a repository might contain
CI configuration that is not distributed, or test code that is not distributed either. To support such cases, ORT
provides a mechanism to mark such parts of the repository as excluded.

Excluded parts will still be analyzed and scanned, but the results will be handled differently in the generated reports:

* Errors occurring in excluded parts will not be shown in the error summary.
* Excluded parts will be grayed out.
* The reason for the exclusion will be shown next to the result.

To document why a part was excluded, ORT requires the user to provide an explanation. This explanation is split into two
parts: The first is called `reason` and has to be selected from a predefined list of options. The second part is called
`comment` and is a free text that can be used to provide additional explanation. The sections below contain links to the
lists of available exclude reasons for each type of exclude.

#### Excluding Projects

ORT defines projects by searching for project definition files in the repositories. For example a Gradle project will be
created for each `build.gradle` file, or an NPM project for each `package.json` file. Such projects can be excluded by
providing the path to the definition file relative to the root of the repository:

```yaml
excludes:
  projects:
  - path: "integrationTests/build.gradle"
    reason: "TEST_TOOL_OF"
    comment: "The project contains integration tests which are not distributed."
```

This configuration will mark the whole project and all its dependencies as excluded in the generated reports. For
example, if an error occurs during the scan of a dependency of this project, it will not appear in the error summary.

The path is interpreted as a regular expression, so it is possible to exclude all Gradle projects in a repository using
a single exclude:

```yaml
excludes:
  projects:
  - path: ".*build\.gradle"
    reason: "EXAMPLE_OF"
    comment: "The project contains example code which is not distributed."
```

For the available exclude reasons for projects, see
[ProjectExcludeReason.kt](../model/src/main/kotlin/config/ProjectExcludeReason.kt).

#### Excluding Scopes

Many package managers support grouping of dependencies by their use. These groups are called `scopes` in ORT. For
example Maven provides the scopes `compile`, `provided`, and `test`, or NPM provides the scopes `dependencies` and
`devDependencies`.

Scopes can be excluded based on regular expressions matching their names. This allows to exclude
multiple scopes at once, which is useful for example in Gradle which creates a lot of scopes (internally Gradle calls
them `configurations`).

Scopes can be excluded on different levels: globally for all projects, and locally for a single project. Global scope
excludes are useful for repositories that contain many similar projects:

```yaml
excludes:
  scopes:
  - name: "test.*"
    reason: "TEST_TOOL_OF"
    comment: "Test dependencies which are not distributed."
```

This will exclude all of these scopes for all projects: `testAnnotationProcessor`,`testApi`, `testCompile`,
`testCompileClasspath`, `testCompileOnly`, `testImplementation`, `testRuntime`, `testRuntimeClasspath`,
`testRuntimeOnly`.

The same scopes can also be excluded only for a single project:

```yaml
excludes:
  projects:
  - path: "app/build.gradle"
    scopes:
    - name: "test.*"
      reason: "TEST_TOOL_OF"
      comment: "Test dependencies which are not distributed."
```

Note that in this case not the whole project is excluded, but only the defined scopes. Therefore no `reason` and
`comment` have to be set for the project itself.

It is possible to mix global and local scope excludes in a single `.ort.yml`.

For the available exclude reasons for scopes, see
[ScopeExcludeReason.kt](../model/src/main/kotlin/config/ScopeExcludeReason.kt).

See below examples for typical scope excludes for the supported package managers. Note that you have to verify that
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

If the ORT result contains errors, usually the best approach is to fix them and run the scan again. However, sometimes
it is not possible or necessary to fix an error, for example:

* The error occurs in the license scan of a third-party dependency which cannot be fixed or updated.
* The error is a false positive.
* The error occurs in a part of the repository which is not distributed (in this case it might be better to use the
  excludes mechanism described above).

In such situations it is possible to *resolve* an error by providing a reason why it can be ignored, and a comment to
further explain why the reason is applicable, in the `.ort.yml` file:

```yaml
resolutions:
  errors:
  - message: "A regular expression matching the error message."
    reason: "One of: BUILD_TOOL_ISSUE|CANT_FIX_ISSUE|SCANNER_ISSUE"
    comment: "A comment further explaining why the reason above is applicable here."
```

Note that the `message` property is interpreted as a regular expression, so it can be used to resolve multiple similar
errors at once.

Error resolutions are only taken into account by the reporter, they still appear as-is in the analyzer and scan results.
In the generated reports, however, resolved errors are not shown in the error summary, and are marked as resolved in the
place they occur.

For details about the available reasons, see
[ErrorResolutionReason.kt](../model/src/main/kotlin/config/ErrorResolutionReason.kt).

## Global configuration

This section describes repository-independent configuration options for ORT.

### Curating metadata of packages

In order to discover the source code of dependencies, ORT relies on the metadata provided by those packages. Often the
metadata contains information how to locate the source code, but this is not always the case. In many cases the metadata
of packages has no VCS information, it points to outdated repositories, or the repositories are not correctly tagged.
Because this information can not always be fixed in upstream packages, ORT provides a mechanism to curate metadata of
packages.

These curations can be configured in a YAML file that has to be passed to the `analyzer`. The data from the curations
file will amend the metadata provided by the packages themselves. This way it is possible to fix borken VCS URLs or
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
cli/build/install/ort/bin/ort analyze -i [input-path] -o [analyzer-output-path] --package-curations-file [curations-file-path]
```

In the future we will integrate [ClearlyDefined](https://clearlydefined.io/) as a source for curated metadata. Until
then, and also for curations for internal packages that cannot be published, the curations file can be used.
