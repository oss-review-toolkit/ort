# The `.ort.yml` file

The items below can be configured by adding an `.ort.yml` file to the root of the source code repository.
All configurations in this file apply only to this Project's context. Usually the global context is preferred for an
increased degree of automation and local configurations should only be done if there are good reasons.

* [excludes](#excludes) - Mark [files, directories](#excluding-paths) or [package manager scopes](#excluding-scopes) as
  not included in released artifacts.
* [curations](#curations) - Overwrite package metadata, set a concluded license or correct license findings.
* [resolutions](#resolutions) - Resolve any issues or policy rule violations.
* [license choices](#License-Choices) - Select a license for packages which offer a license choice.

The sections below explain each in further detail. Prefer to learn by example? See the [.ort.yml](../.ort.yml) for the
OSS Review Toolkit itself.

## Excludes

### When to Use Excludes

Excludes are used to define which OSS is distributed to third parties and which code is only used internally, e.g. for
building, documenting or testing the code. 

Exclusions apply to paths (files/directories) or scopes. Examples of currently supported exclusions:

* all dependencies defined in `./test/pom.xml` in Maven-based projects.
* dependencies in scopes `test` or `provided`.

### Excludes Basics

ORT's philosophy is to analyze and scan everything it can find to build a complete picture of a repository and its
dependencies.

However, the users may not be interested in the results for components that are not included in their released
artifacts, for example build files, documentation, examples or test code. To support such use cases, ORT provides a
mechanism to mark files, directories or scopes included in the repository as excluded.

Note that the excluded parts are analyzed and scanned, but are treated differently in the reports ORT generates:

* The issue summary does not show issues in the excluded parts.
* The excluded parts are grayed out.
* The reason for the exclusion is shown next to the result.

To be able to show why a part is excluded, each exclude must include an explanation. The explanation consists of:

* `reason` -- must be selected from a predefined list of options.
* `comment` -- free text that provides an optional explanation.

### Excluding Paths

Path excludes are used to mark a complete path as excluded.

The code below shows the structure of a path exclude in the `.ort.yml` file:

```yaml
excludes:
  paths:
  - pattern: "A glob pattern matching files or paths."
    reason: "One of PathExcludeReason e.g. BUILD_TOOL_OF, DOCUMENTATION_OF or TEST_OF."
    comment: "A comment further explaining why the path is excluded."
```

Where the list of available options for `reason` is defined in
[PathExcludeReason.kt](../model/src/main/kotlin/config/PathExcludeReason.kt).
For how to write a glob pattern, please see this
[tutorial](https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob).

The path exclude above has the following effects:

* All projects found below the `test-data` directory are marked as excluded.
* License findings in files below the `test-data` directory are marked as excluded. This can be used in
  [evaluator rules](getting-started.md#6-running-the-evaluator) to for instance change the severity from error to
  warning.

```yaml
excludes:
  paths:
  - pattern: "test-data/**"
    reason: "TEST_OF"
    comment: "This directory contains test data which are not distributed."
```

### Excluding Scopes

Many package managers support grouping of dependencies by their use. Such groups are called `scopes` in ORT. For
example, Maven provides the scopes `compile`, `provided`, and `test`, while NPM scopes are `dependencies` and
`devDependencies`.

You can use regular expressions for `pattern` to match the scopes to exclude. This can be useful, for example, with
Gradle, which creates a relatively large number of scopes (internally Gradle calls them `configurations`).

Scopes excludes always apply to all found projects in a scan.

```yaml
excludes:
  scopes:
  - pattern: "test.*"
    reason: "TEST_DEPENDENCY_OF"
    comment: "Packages for testing only."
```

The above example excludes all the following scopes for all projects: `testAnnotationProcessor`,`testApi`,
`testCompile`, `testCompileClasspath`, `testCompileOnly`, `testImplementation`, `testRuntime`, `testRuntimeClasspath`,
`testRuntimeOnly`.

Where the list of available options for scopes is defined in
[ScopeExcludeReason.kt](../model/src/main/kotlin/config/ScopeExcludeReason.kt).

See the examples below for typical scope excludes for the supported package managers. Note that you must verify that the
scopes defined in the examples below match the scopes in your project.

* [bower.ort.yml](../examples/bower.ort.yml)
* [bundler.ort.yml](../examples/bundler.ort.yml)
* [cargo.ort.yml](../examples/cargo.ort.yml)
* [composer.ort.yml](../examples/composer.ort.yml)
* [go-dep.ort.yml](../examples/go-dep.ort.yml)
* [go-mod.ort.yml](../examples/go-mod.ort.yml)
* [gradle.ort.yml](../examples/gradle.ort.yml)
* [gradle-android.ort.yml](../examples/gradle-android.ort.yml)
* [maven.ort.yml](../examples/maven.ort.yml)
* [npm.ort.yml](../examples/npm.ort.yml)
* [pip.ort.yml](../examples/pip.ort.yml)
* [sbt.ort.yml](../examples/sbt.ort.yml)
* [stack.ort.yml](../examples/stack.ort.yml)
* [yarn.ort.yml](../examples/yarn.ort.yml)

## Curations

### When to Use Curations

License finding curations should be used when you want to correct the licenses detected in the source code of the
project. To define curations on global level for third-party packages, please use 
[curations](config-file-curations-yml.md) or [package configurations](config-file-package-configuration-yml.md).

### Curating Project License Findings

An `ort scan` result represents the detected licenses as a collection of license findings. A single `LicenseFinding` is
represented as a tuple: `(license id, file path, start line, end line)`. Applying a `LicenseFindingCuration` changes the
license-Id of any `LicenseFinding` or eliminates the `LicenseFinding` in case the license is set to `NONE`.

As an example, the following curation would replace similar findings of `GPL-2.0-only` with `Apache-2.0` in all `.cpp`
files in the `src` directory:

e.g.:
```yaml
curations:
  license_findings:
  - path: "src/**.cpp"
    start_lines: "3"
    line_count: 11
    detected_license: "GPL-2.0-only"
    reason: "CODE"
    comment: "The scanner matches a variable named `gpl`."
    concluded_license: "Apache-2.0"
 ```

To correct identified licenses in a dependency you can use a package configuration to overwrite scanner findings.
Note that this feature requires `enableRepositoryPackageConfigurations` to be enabled in the `ort.conf` see
[reference.conf](../model/src/main/resources/reference.conf).
```yaml
package_configurations:
- id: 'Maven:com.example:package:1.2.3'
  source_artifact_url: "https://repo.maven.apache.org/maven2/com/example/package/1.2.3/package-1.2.3-sources.jar"
  license_finding_curations:
  - path: "path/to/problematic/file.java"
    start_lines: 22
    line_count: 1
    detected_license: "GPL-2.0-only"
    reason: "CODE"
    comment: "The scanner matches a variable named `gpl`."
    concluded_license: "Apache-2.0"
```

For details of the specification, see 
[LicenseFindingCuration.kt](../model/src/main/kotlin/config/LicenseFindingCuration.kt).
The list of available options for `reason` are defined in
[LicenseFindingCurationReason.kt](../model/src/main/kotlin/config/LicenseFindingCurationReason.kt).

### Curating Metadata

Package curations can be added if you want to correct metadata of third-party dependencies.

The following example corrects the source-artifact URL of the package with the id `Maven:com.example:dummy:0.0.1`.
Note that this feature requires `enableRepositoryPackageCurations` to be enabled in the `ort.conf`, see
[reference.conf](../model/src/main/resources/reference.conf).

e.g.:
```yaml
curations:
  packages:
  - id: "Maven:com.example:dummy:0.0.1"
    curations:
      comment: "An explanation why the curation is needed."
      source_artifact:
        url: "https://example.com/sources.zip"
```

For more information about package curations see
[the documentation for the curations.yml file](config-file-curations-yml.md).

## Resolutions

### When to Use Resolutions

Resolutions should be used if you are unable to solve an issue by other means.

If a resolution is not project-specific than add it to [resolutions.yml](./config-file-resolutions-yml.md) so that it is
applied to each scan.

### Resolution Basics

Resolutions allow you to *resolve* issues, policy rule violations or vulnerabilities by marking them as acceptable. A resolution is
applied to specific issues or violations via the regular expression specified in the `message` of a resolution.

To be able to show why a resolution is acceptable, each resolution must include an explanation. The explanation consists
of:

* `reason` -- an identifier selected from a predefined list of options. 
* `comment` -- free text, providing an explanation and optionally a link to further information.

### Resolving Issues

If the ORT results show issues, the best approach is usually to fix them and run the scan again. However, sometimes it
is not possible, for example if an issue occurs in the license scan of a third-party dependency which cannot be fixed or
updated.

In such situations, you can *resolve* the issue in any future scan by adding a resolution to the `.ort.yml` to mark it
as acceptable.

The code below shows the structure of an issue resolution in the `.ort.yml` file:

```yaml
resolutions:
  issues:
  - message: "A regular expression matching the error message."
    reason: "One of IssueResolutionReason e.g BUILD_TOOL_ISSUE,CANT_FIX_ISSUE."
    comment: "A comment further explaining why the reason above is acceptable."
```
Where the list of available options for `reason` is defined in
[IssueResolutionReason.kt](../model/src/main/kotlin/config/IssueResolutionReason.kt)

For example, to ignore an issue related to a build tool problem, your `.ort.yml` could include:

```yaml
resolutions:
  issues:
  - message: "Does not have X.*"
    reason: "BUILD_TOOL_ISSUE"
    comment: "Error caused by a known issue for which a fix is being implemented, see https://github.com/..."
```

### Resolving Policy Rule Violations

Resolutions should not be used to resolve license policy rule violations as they do not change the generated open
source notices. To resolve a license policy rule violation either add a [license finding curation](#curations) to the
.ort.yml file if the finding is in your code repository or add a curation to the
[curations.yml](config-file-curations-yml.md) if the violation occurs in a third-party dependency.

The code below shows the structure of a policy rule violation resolution in the `.ort.yml` file:

```yaml
resolutions:
  rule_violations:
  - message: "A regular expression matching the policy rule violation message."
    reason: "One of RuleViolationResolutionReason e.g. CANT_FIX_EXCEPTION, DYNAMIC_LINKAGE_EXCEPTION."
    comment: "A comment further explaining why the reason above is applicable."
```

Where the list of available options for `reason` is defined in
[RuleViolationResolutionReason.kt](../model/src/main/kotlin/config/RuleViolationResolutionReason.kt).

For example, to confirm you acquired a commercial Qt license for your project, your `.ort.yml` could include:

```yaml
resolutions:
  rule_violations:
  - message: ".*LicenseRef-scancode-qt-commercial-1.1 found in 'third-party/qt/LICENSE'.*"
    reason: "LICENSE_ACQUIRED_EXCEPTION"
    comment: "Commercial Qt license for the project was purchased, for details see https://jira.example.com/issues/SOURCING-5678"
```

### Resolving Vulnerabilities

The code below shows the structure of a vulnerability resolution in the `.ort.yml` file:

```yaml
resolutions:
  vulnerabilities:
  - id: "A regular expression matching the vulnerability id."
    reason: "One of VulnerabilityResolutionReason e.g. CANT_FIX_VULNERABILITY, INEFFECTIVE_VULNERABILITY."
    comment: "A comment further explaining why the reason above is applicable."
```

Where the list of available options for `reason` is defined in
[VulnerabilityResolutionReason.kt](../model/src/main/kotlin/config/VulnerabilityResolutionReason.kt).

For example, to ignore a vulnerability that is ineffective, because it is not invoked in your project, your `.ort.yml` could include:

```yaml
resolutions:
  vulnerabilities:
  - id: "CVE-9999-9999"
    reason: "INEFFECTIVE_VULNERABILITY"
    comment: "CVE-9999-9999 is a false positive"
```

## License Choices

### When to Use License Choices

For multi-licensed dependencies a specific license can be selected.
The license choice can be applied to a package or globally to an SPDX expression in the project.
A choice is only valid for licenses combined with the SPDX operator `OR`.
The choices are applied in the evaluator, and the reporter to the effective license of a package, which is calculated
by the chosen [LicenseView](../model/src/main/kotlin/licenses/LicenseView.kt).

### License Choice by Package

To select a license from a multi-licensed dependency, specified by its `packageId`, an SPDX expression for a `choice`
must be provided.
The `choice` is either applied to the whole effective SPDX expression of the package or to an optional `given` SPDX 
expression that can represent only a sub-expression of the whole effective SPDX expression. 

e.g.
```yaml
license_choices:
  package_license_choices:
  - package_id: "Maven:com.example:first:0.0.1"
    license_choices:
    # The input of the calculated effective license would be: (A OR B) AND ((C OR D) AND E)
    - given: A OR B
      choice: A
    # The result would be: A AND ((C OR D) AND E)
    # The input of the current effective license would be: A AND ((C OR D) AND E)
    - given: (C OR D) AND E
      choice: C AND E
    # The result would be: A AND C AND E
  - package_id: "Maven:com.example:second:2.3.4"
    license_choices:
    # Without a 'given', the 'choice' is applied to the effective license expression if it is a valid choice.
    # The input from the calculated effective license would be: (C OR D) AND E
    - choice: C AND E
    # The result would be: C AND E
```

### License Choice for the Project

To globally select a license from an SPDX expression, that offers a choice, an SPDX expression for a `given` and a
`choice` must be provided.
The `choice` is applied to the whole `given` SPDX expression.
With a repository license choice, the license choice is applied to each package that offers this license as a choice. 
Not allowing `given` to be null helps only applying the choice to a wanted `given` as opposed to all licenses with that
choice, which could lead to unwanted choices.
The license choices for a project can be overwritten by applying a 
[license choice to a package](#license-choice-by-package).

e.g.
```yaml
license_choices:
  repository_license_choices:
  - given: "A OR B"
    choice: "B"
```

---
**NOTE**

The choice will be applied to the WHOLE `given` license.
If the choice does not provide a valid result, an exception will be thrown upon deserialization.

e.g. invalid configuration:
```yaml
# This is invalid, as 'E' must be in the resulting license.
- given: (C OR D) AND E
  choice: C
```

---
