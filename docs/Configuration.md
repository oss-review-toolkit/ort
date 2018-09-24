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
    reason: "TEST_CASE_OF"
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
    reason: "TEST_CASE_OF"
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
      reason: "TEST_CASE_OF"
      comment: "Test dependencies which are not distributed."
```

Note that in this case not the whole project is excluded, but only the defined scopes. Therefore no `reason` and
`comment` have to be set for the project itself.

It is possible to mix global and local scope excludes in a single `.ort.yml`.

For the available exclude reasons for scopes, see
[ScopeExcludeReason.kt](../model/src/main/kotlin/config/ScopeExcludeReason.kt).
