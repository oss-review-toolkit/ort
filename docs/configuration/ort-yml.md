# Repository Configuration `(.ort.yml)`

The items below can be configured by adding an `.ort.yml` file to the root of the source code repository.
All configurations in this file apply only to this Project's context.
Usually, the global context is preferred for an increased degree of automation, and local configurations should only be done if there are good reasons.

* [includes](#includes) - Mark [files, directories](#including-paths) as included in released artifacts.
* [excludes](#excludes) - Mark [files, directories](#excluding-paths) or [package manager scopes](#excluding-scopes) as not included in released artifacts.
* [resolutions](#resolutions) - Resolve any issues or policy rule violations.
* [curations](#curations) - Overwrite package metadata, set a concluded license, or correct license findings in the project.
* [package configurations](#package-configurations) - Define path excludes or correct license findings in dependencies.
* [license choices](#license-choices) - Select a license for packages which offer a license choice.

The sections below explain each in further detail.
Prefer to learn by example?
See the [.ort.yml](https://github.com/oss-review-toolkit/ort/blob/main/.ort.yml) for the OSS Review Toolkit itself.

## Includes

### When to Use Includes

Includes are used to define which OSS is distributed to third parties and which code is only used internally.

Inclusions apply to paths (files/directories) only.
Examples of currently supported inclusions:

* all dependencies defined in `./src/pom.xml` in Maven-based projects.

### Includes Basics

ORT's default philosophy is to analyze and scan everything it can find to build a complete picture of a repository and its dependencies.

However, in some cases, only a subset of the repository is relevant for the analysis, for example, if the repository is a monorepo containing multiple projects.
While [excludes](#excludes) could be used to ignore the irrelevant parts, it is often more convenient to explicitly include only the relevant parts.

To be able to show why a part is included, each include must have an explanation.
The explanation consists of:

* `reason` -- must be selected from a predefined list of options.
* `comment` -- free text that provides an optional explanation.

### Including Paths

Path includes are used to mark a complete path as included.

The code below shows the structure of a path include in the `.ort.yml` file:

```yaml
includes:
  paths:
  - pattern: "A glob pattern matching files or paths."
    reason: "One of PathIncludeReason e.g. SOURCE_OF."
    comment: "A comment further explaining why the path is included."
```

Where the list of available options for `reason` is defined in [PathIncludeReason.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/PathIncludeReason.kt).
For how to write a glob pattern, please see the [AntPathMatcher documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html).

The path include below has the following effects:

* All projects found below the `src` directory are marked as included.
* License findings in files below the `src` directory are marked as included.

```yaml
excludes:
  paths:
  - pattern: "src/**"
    reason: "SOURCE_OF"
    comment: "This directory contains the source code of binaries that are distributed."
```

## Excludes

### When to Use Excludes

Excludes are used to define which OSS is distributed to third parties and which code is only used internally, e.g., for building, documenting or testing the code.

Exclusions apply to paths (files/directories) or scopes.
Examples of currently supported exclusions:

* all dependencies defined in `./test/pom.xml` in Maven-based projects.
* dependencies in scopes `test` or `provided`.

### Excludes Basics

ORT's default philosophy is to analyze and scan everything it can find to build a complete picture of a repository and its dependencies.

However, users may not be interested in the results for components that are not included in their released artifacts, for example, build files, documentation, examples or test code.
To support such use cases, ORT provides a mechanism to mark files, directories or scopes included in the repository as excluded.

Note that by default, the excluded parts are analyzed and scanned, but are treated differently in the reports ORT generates:

* The issue summary does not show issues in the excluded parts.
* The excluded parts are grayed out.
* The reason for the exclusion is shown next to the result.

This is a rather safe option, since the reports still display elements marked as excluded and thus allow the user to verify the correctness of the declared exclusions.
If it is clear that the excluded projects or scopes are irrelevant from a compliance point of view, ORT can be configured to skip them completely during the analysis phase.
The affected elements are then not processed any further and do not occur in generated reports.
Especially for larger projects with many excluded elements, this can significantly reduce resource usage and analysis time.
To enable this mode, add the following declaration to the `.ort.yml` file:

```yaml
analyzer:
  skip_excluded: true
excludes:
  ...
```

To be able to show why a part is excluded, each exclude must include an explanation.
The explanation consists of:

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

Where the list of available options for `reason` is defined in [PathExcludeReason.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/PathExcludeReason.kt).
For how to write a glob pattern, please see the [AntPathMatcher documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html).

The path exclude below has the following effects:

* All projects found below the `test-data` directory are marked as excluded.
* License findings in files below the `test-data` directory are marked as excluded.
  This can be used in [evaluator rules](../getting-started/tutorial.md#6-running-the-evaluator) to for instance change the severity from error to warning.

```yaml
excludes:
  paths:
  - pattern: "test-data/**"
    reason: "TEST_OF"
    comment: "This directory contains test data which are not distributed."
```

### Excluding Scopes

Many package managers support grouping of dependencies by their use.
Such groups are called `scopes` in ORT.
For example, Maven provides the scopes `compile`, `provided`, and `test`, while NPM scopes are `dependencies` and `devDependencies`.

You can use regular expressions for `pattern` to match the scopes to exclude.
This can be useful, for example, with Gradle, which creates a relatively large number of scopes (internally Gradle calls them `configurations`).

Scopes excludes always apply to all found projects in a scan.

```yaml
excludes:
  scopes:
  - pattern: "test.*"
    reason: "TEST_DEPENDENCY_OF"
    comment: "Packages for testing only."
```

The above example excludes all the following scopes for all projects:
`testAnnotationProcessor`,`testApi`, `testCompile`, `testCompileClasspath`, `testCompileOnly`, `testImplementation`, `testRuntime`, `testRuntimeClasspath`, `testRuntimeOnly`.

Where the list of available options for scopes is defined in [ScopeExcludeReason.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/ScopeExcludeReason.kt).

See the examples below for typical scope excludes for the supported package managers.
Note that you must verify that the scopes defined in the examples below match the scopes in your project.

### Examples

```mdx-code-block
import CodeBlock from '@theme/CodeBlock';
import Bower from '!!raw-loader!@site/../examples/bower.ort.yml'
import Bundler from '!!raw-loader!@site/../examples/bundler.ort.yml'
import Cargo from '!!raw-loader!@site/../examples/cargo.ort.yml'
import Composer from '!!raw-loader!@site/../examples/composer.ort.yml'
import GoMod from '!!raw-loader!@site/../examples/go-mod.ort.yml'
import Gradle from '!!raw-loader!@site/../examples/gradle.ort.yml'
import GradleAndroid from '!!raw-loader!@site/../examples/gradle-android.ort.yml'
import Maven from '!!raw-loader!@site/../examples/maven.ort.yml'
import Npm from '!!raw-loader!@site/../examples/npm.ort.yml'
import Pip from '!!raw-loader!@site/../examples/pip.ort.yml'
import Sbt from '!!raw-loader!@site/../examples/sbt.ort.yml'
import Stack from '!!raw-loader!@site/../examples/stack.ort.yml'
import Yarn from '!!raw-loader!@site/../examples/yarn.ort.yml'

<CodeBlock language="yml" title="Bower">{Bower}</CodeBlock>
<CodeBlock language="yml" title="Bundler">{Bundler}</CodeBlock>
<CodeBlock language="yml" title="Cargo">{Cargo}</CodeBlock>
<CodeBlock language="yml" title="Composer">{Composer}</CodeBlock>
<CodeBlock language="yml" title="Go Mod">{GoMod}</CodeBlock>
<CodeBlock language="yml" title="Gradle">{Gradle}</CodeBlock>
<CodeBlock language="yml" title="Gradle Android">{GradleAndroid}</CodeBlock>
<CodeBlock language="yml" title="Maven">{Maven}</CodeBlock>
<CodeBlock language="yml" title="npm">{Npm}</CodeBlock>
<CodeBlock language="yml" title="pip">{Pip}</CodeBlock>
<CodeBlock language="yml" title="sbt">{Sbt}</CodeBlock>
<CodeBlock language="yml" title="Stack">{Stack}</CodeBlock>
<CodeBlock language="yml" title="Yarn">{Yarn}</CodeBlock>
```

## Interaction between Includes and Excludes

There is no priority when using both includes and excludes.
The includes control what is included and excludes everything else.
Excludes add extra exclusions.
If includes and excludes overlap, excludes are stronger.
This means that if a file is matched by both includes and excludes, it will be excluded.

## Resolutions

### When to Use Resolutions

Resolutions should be used if you are unable to solve an issue by other means.

If a resolution is not project-specific, add it to [resolutions.yml](resolutions.md) so that it is applied to each scan.

### Resolution Basics

Resolutions allow you to *resolve* issues, policy rule violations or vulnerabilities by marking them as acceptable.
A resolution is applied to specific issues or violations via the regular expression specified in the `message` of a resolution.

To be able to show why a resolution is acceptable, each resolution must include an explanation.
The explanation consists of:

* `reason` -- an identifier selected from a predefined list of options.
* `comment` -- free text, providing an explanation and optionally a link to further information.

### Resolving Issues

If the ORT results show issues, the best approach is usually to fix them and run the scan again.
However, sometimes it is not possible, for example, if an issue occurs in the license scan of a third-party dependency which cannot be fixed or updated.

In such situations, you can *resolve* the issue in any future scan by adding a resolution to the `.ort.yml` to mark it as acceptable.

The code below shows the structure of an issue resolution in the `.ort.yml` file:

```yaml
resolutions:
  issues:
  - message: "A regular expression matching the error message."
    reason: "One of IssueResolutionReason e.g BUILD_TOOL_ISSUE,CANT_FIX_ISSUE."
    comment: "A comment further explaining why the reason above is acceptable."
```

Where the list of available options for `reason` is defined in [IssueResolutionReason.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/IssueResolutionReason.kt)

For example, to ignore an issue related to a build tool problem, your `.ort.yml` could include:

```yaml
resolutions:
  issues:
  - message: "Does not have X.*"
    reason: "BUILD_TOOL_ISSUE"
    comment: "Error caused by a known issue for which a fix is being implemented, see https://github.com/..."
```

### Resolving Policy Rule Violations

Resolutions should not be used to resolve license policy rule violations as they do not change the generated open source notices.
To resolve a license policy rule violation, either add a [license finding curation](#curations) to the .ort.yml file if the finding is in your code repository or add a curation to the [curations.yml](package-curations.md) if the violation occurs in a third-party dependency.

The code below shows the structure of a policy rule violation resolution in the `.ort.yml` file:

```yaml
resolutions:
  rule_violations:
  - message: "A regular expression matching the policy rule violation message."
    reason: "One of RuleViolationResolutionReason e.g. CANT_FIX_EXCEPTION, DYNAMIC_LINKAGE_EXCEPTION."
    comment: "A comment further explaining why the reason above is applicable."
```

Where the list of available options for `reason` is defined in [RuleViolationResolutionReason.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/RuleViolationResolutionReason.kt).

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

Where the list of available options for `reason` is defined in [VulnerabilityResolutionReason.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/VulnerabilityResolutionReason.kt).

For example, to ignore a vulnerability that is ineffective, because it is not invoked in your project, your `.ort.yml` could include:

```yaml
resolutions:
  vulnerabilities:
  - id: "CVE-9999-9999"
    reason: "INEFFECTIVE_VULNERABILITY"
    comment: "CVE-9999-9999 is a false positive"
```

### Example

```mdx-code-block
import Resolutions from '!!raw-loader!@site/../examples/resolutions.ort.yml'

<CodeBlock language="yml" title="resolutions.ort.yml">{Resolutions}</CodeBlock>
```

## Curations

:::note

This feature requires `enableRepositoryPackageCurations` to be enabled in the [config.yml](../getting-started/usage.md#ort-configuration-file).

:::

### When to Use Curations

Similar to global [package curations](package-curations.md), curations can be used to correct metadata of dependencies specific to the projects in the repository.
Additionally, license findings curations may be used to correct the licenses detected in the source code of the project.
To correct license findings detected in dependencies, use global [package configurations](package-configurations.md) instead.

### Curating Package Metadata

The following example corrects the source-artifact URL of the package with the id `Maven:com.example:dummy:0.0.1`:

```yaml
curations:
  packages:
  - id: "Maven:com.example:dummy:0.0.1"
    curations:
      comment: "An explanation why the curation is needed."
      source_artifact:
        url: "https://example.com/sources.zip"
```

### Curating Project License Findings

An `ort scan` result represents the detected licenses as a collection of license findings.
A single `LicenseFinding` is represented as a tuple:
`(license id, file path, start line, end line)`.
Applying a `LicenseFindingCuration` changes the license-Id of any `LicenseFinding` or eliminates the `LicenseFinding` in case the license is set to `NONE`.

As an example, the following curation would replace similar findings of `GPL-2.0-only` with `Apache-2.0` in all `.cpp` files in the `src` directory:

```yaml
curations:
  license_findings:
  - path: "src/**/*.cpp"
    start_lines: "3"
    line_count: 11
    detected_license: "GPL-2.0-only"
    reason: "CODE"
    comment: "The scanner matches a variable named `gpl`."
    concluded_license: "Apache-2.0"
 ```

For details of the specification, see [LicenseFindingCuration.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/LicenseFindingCuration.kt).
The list of available options for `reason` are defined in [LicenseFindingCurationReason.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/LicenseFindingCurationReason.kt).

### Full Example

```mdx-code-block
import Curations from '!!raw-loader!@site/../examples/curations.ort.yml'

<CodeBlock language="yml" title="curations.ort.yml">{Curations}</CodeBlock>
```

## Package Configurations

:::note

This feature requires `enableRepositoryPackageConfigurations` to be enabled in the [config.yml](../getting-started/usage.md#ort-configuration-file).

:::

### When to Use Package Configurations

You can use a package configuration to set path excludes or correct detected licenses in a dependency.
The following overwrites a license finding that a scanner found in a source artifact:

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

For details of the specification, see [LicenseFindingCuration.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/LicenseFindingCuration.kt).
The list of available options for `reason` are defined in [LicenseFindingCurationReason.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/LicenseFindingCurationReason.kt).

## License Choices

### When to Use License Choices

For multi-licensed dependencies, a specific license can be selected.
The license choice can be applied to a package or globally to an SPDX expression in the project.
A choice is only valid for licenses combined with the SPDX operator `OR`.
The choices are applied in the evaluator, and the reporter to the effective license of a package, which is calculated by the chosen [LicenseView](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/licenses/LicenseView.kt).

### License Choice by Package

To select a license from a multi-licensed dependency, specified by its `packageId`, an SPDX expression for a `choice` must be provided.
The `choice` is either applied to the whole effective SPDX expression of the package or to an optional `given` SPDX expression that can represent only a sub-expression of the whole effective SPDX expression.

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

To globally select a license from an SPDX expression, that offers a choice, an SPDX expression for a `given` and a `choice` must be provided.
The `choice` is applied to the whole `given` SPDX expression.
With a repository license choice, the license choice is applied to each project or package that offers this specific license choice.
Not allowing `given` to be null helps only applying the choice to a wanted `given` as opposed to all licenses with that choice, which could lead to unwanted choices.
The license choices for a project can be overwritten by applying a [license choice to a package](#license-choice-by-package).

```yaml
license_choices:
  repository_license_choices:
  - given: "A OR B"
    choice: "B"
```

### Invalid License Choice

The choice will be applied to the WHOLE `given` license.
If the choice does not provide a valid result, an exception will be thrown upon deserialization.

Example for an invalid configuration:

```yaml
# This is invalid, as 'E' must be in the resulting license.
- given: (C OR D) AND E
  choice: C
```
