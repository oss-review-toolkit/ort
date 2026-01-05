# Repository Configuration `(.ort.yml)`

The items below can be configured by adding an `.ort.yml` file to the root of a project's source code repository.
All configurations in this file apply only to the context of this project.
Usually, the global context is preferred for an increased degree of automation, and local configurations should only be done if there are good reasons.

* [excludes](#excludes) - Mark [files, directories](#excludes-paths) or [package manager scopes](#excluding-scopes) as not included in released artifacts.
* [includes](#includes) - Mark [files, directories](#path-includes) as included in released artifacts.
* [curations](#curations) - Overwrite package metadata, set a concluded license, or correct license findings in the project.
* [package configurations](#package-configurations) - Define path excludes or correct license findings in dependencies.
* [license choices](#license-choices) - Select a license for packages which offer a license choice.
* [snippet choices](#snippet-choices) - Select which snippet findings to include in ORT results.
* [resolutions](#resolutions) - Resolve any issues or policy rule violations, use only after applying any of the above points.

The sections below explain each in further detail.
Prefer to learn by example?
See the [.ort.yml](https://github.com/oss-review-toolkit/ort/blob/main/.ort.yml) for the OSS Review Toolkit itself.

## Excludes

### When to use excludes

Excludes can be used to define which parts of the software project are distributed to third parties and which code is only used for internal use, such as building, documenting, or testing the code. Using excludes you can those part of projects that are *not distributed*, ensuring that only applicable policy checks are applied and enabling the generation of high-quality SBOMs that containing only ORT results for files actually present in the release artifacts.

Exclusions can apply to paths (files/directories) or scopes. Here are a few examples of currently supported exclusions:

* All files and directories in `.github/` or `.gitlab/`, which are used solely for CI/CD.
* All dependencies defined in `./test/pom.xml` in Maven-based projects.
* Dependencies in the `test` or `provided` scopes.

### Excludes and file scanning results

ORT's default philosophy is to analyze and scan everything it can find to build a complete picture of a project's code repository and its dependencies

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

### Excludes file format

To be able to mark why a part of project is excluded, each exclude must include an explanation.
The explanation consists of:

* `reason` - must be selected from a predefined list of options.
* `comment` - free text that provides an optional explanation.

#### Excludes paths

Path excludes are used to mark files or direcotories as excluded.

The code below shows the structure of a path exclude in the `.ort.yml` file.

```yaml
excludes:
  paths:
  - pattern: "A glob pattern matching file or directory paths."
    reason: >
      One of PathExcludeReason e.g.
      BUILD_TOOL_OF,
      DATA_FILE_OF,
      DOCUMENTATION_OF,
      EXAMPLE_OF,
      OPTIONAL_COMPONENT_OF,
      OTHER,
      PROVIDED_BY,
      TEST_OF or
      TEST_TOOL_OF.
    comment: "A comment further explaining why the path is excluded."
```

Check out [PathExcludeReason.kt][PathExcludeReason] for all the `reason` options available, along with descriptions on when to use each one.
To learn how to write glob patterns, consult the [AntPathMatcher documentation][AntPathMatcher].

The path exclude below has the following effects:

* All projects found below the `test-data` directory are marked as excluded.
* License findings in files below the `test-data` directory are marked as excluded.
  This can be used in [evaluator rules](evaluator-rules.md) to for instance change the severity from error to warning.

```yaml
excludes:
  paths:
  - pattern: "test-data/**"
    reason: "TEST_OF"
    comment: "This directory contains test data which are not distributed."
```

#### Excluding scopes

Many package managers support grouping of dependencies by their use.
Such groups are called `scopes` in ORT.

The code below shows the structure of a scope exclude in the `.ort.yml` file.

```yaml
excludes:
  scope:
  - pattern: "A glob pattern matching scope name(s)."
    reason: >
      One of ScopeExcludeReason e.g.
      BUILD_DEPENDENCY_OF,
      DEV_DEPENDENCY_OF,
      DOCUMENTATION_OF,
      DOCUMENTATION_DEPENDENCY_OF,
      PROVIDED_DEPENDENCY_OF,
      TEST_DEPENDENCY_OF or,
      RUNTIME_DEPENDENCY_OF.
    comment: "A comment further explaining why the scope is excluded."
```

Check out [ScopeExcludeReason.kt][ScopeExcludeReason] for all the `reason` options available, along with descriptions on when to use each one.

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

## Includes

### When to use includes

ORT's default philosophy is to analyze and scan everything it can find to build a complete picture of a project's code repository and its dependencies.

Includes can be used to define which parts of the software project are distributed to third parties and which code is only used for internal use, such as building, documenting, or testing the code. Using includes you can mark those parts of projects that are *distributed*, ensuring that only applicable policy checks are applied and enabling the generation of high-quality SBOMs containing only ORT results for files actually present in the release artifacts.

### Includes file format

To be able to show why a part is included, each include must have an explanation.
The explanation consists of:

* `reason` - must be selected from a predefined list of options.
* `comment` - free text that provides an optional explanation.

#### Path includes

Unlike [excludes](#excludes), includes can only can be used to mark paths (files/directories) as included.

The code below shows the structure of a path include in the `.ort.yml` file:

```yaml
includes:
  paths:
  - pattern: "A glob pattern matching files or paths."
    reason: >
      One of PathIncludeReason e.g.
      SOURCE_OF or,
      OTHER.
    comment: "A comment further explaining why the path is included."
```

Check out [PathIncludeReason.kt][PathIncludeReason] for all the `reason` options available, along with descriptions on when to use each one.
For how to write a glob pattern, please see the [AntPathMatcher documentation][AntPathMatcher].

The path include below has the following effects:

* All projects found below the `src` directory are marked as included.
* License findings in files below the `src` directory are marked as included.

```yaml
includes:
  paths:
  - pattern: "src/**"
    reason: "SOURCE_OF"
    comment: "This directory contains the source code of binaries that are distributed."
```

## Interaction between includes and excludes

There is no priority when using both includes and excludes.
The includes control what is included and excludes everything else.
Excludes add extra exclusions.
If includes and excludes overlap, excludes are stronger.
This means that if a file is matched by both includes and excludes, it will be excluded.

## Curations

⚠️ This feature requires `enableRepositoryPackageCurations` to be enabled in your [config.yml](index.md#ort-configuration-file) file.

### When to use curations

Use a package curations to:

* Correct package metadata such as declared license or the location of the source code repository or sources artifact.
* Overwrite scanner findings to correct identified licenses for specific project file(s).

To correct license findings detected in dependencies, use global [package configurations](package-configurations.md) instead.

### Curations file format

```yaml
curations:
  packages:
  - id: "An ORT package identifier e.g. Maven:com.example.app:example:0.0.1"
    curations:
      comment: |
        An explanation why the curation is needed or why it is set to specific value.
        It’s recommended to include links to the relevant code or ticket to support your explanation.
      purl: "A package URL e.g. pkg:Maven/com.example.app/example@0.0.1?arch=arm64-v8a#src/main."
      authors:
      - "Name of one author"
      - "Name of another author"
      cpe: "cpe:2.3:a:example-org:example-package:0.0.1:*:*:*:*:*:*:*"
      concluded_license: "Valid SPDX license expression to override the license findings."
      declared_license_mapping:
        "license a": "Apache-2.0"
      description: "Curated description."
      homepage_url: "http://example.com"
      binary_artifact:
        url: "http://example.com/binary.zip"
        hash:
          value: "ddce269a1e3d054cae349621c198dd52"
          algorithm: "MD5"
      source_artifact:
        url: "http://example.com/sources.zip"
        hash:
          value: "ddce269a1e3d054cae349621c198dd52"
          algorithm: "MD5"
      vcs:
        type: "Git"
        url: "http://example.com/repo.git"
        revision: "1234abc"
        path: "subdirectory"
      is_metadata_only: true
      is_modified: true
      source_code_origins: [ARTIFACT, VCS]
      labels:
        my-key: "my-value"
  license_findings:
  - path: "A glob pattern matching files or paths."
    start_lines: "3"
    line_count: 11
    detected_license: "SPDX license expression"
    reason: >
      One of LicenseFindingCurationReason e.g.
      CODE,
      DATA_OF,
      DOCUMENTATION_OF,
      INCORRECT,
      NOT_DETECTED or
      REFERENCE.
    comment: |
      An explanation why the license finding curation is needed or why it is set to specific value.
      It’s recommended to include links to the relevant code or ticket to support your explanation.
    concluded_license: "SPDX license expression"
```

Where the list of available options for curations is defined in [PackageCurationData.kt][PackageCurationData].

To learn how to write glob patterns, consult the [AntPathMatcher documentation][AntPathMatcher]. Also, check out [LicenseFindingCurationReason.kt][LicenseFindingCurationReason] for all the `reason` options available, along with descriptions on when to use each one.

### Correcting package metadata

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

### Correcting project license findings

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

## Package configurations

⚠️ This feature requires `enableRepositoryPackageConfigurations` to be enabled in your [config.yml](index.md#ort-configuration-file) file.

### When to use package configurations

Use a package configuration to:

* Mark files and directories as not included in released artifacts.
  Use it to make clear that license findings in documentation or tests in a package sources do not apply to the release (binary) artifact which is a dependency in your project.
* Overwrite scanner findings to correct identified licenses for a specific file(s) present in a dependency sources or code repository.

### Package configurations file format

```yaml
package_configurations:
- id: 'An ORT package identifier e.g. Pip::example-package:0.0.1'
  path_excludes:
  - pattern: "A glob pattern matching files or paths."
    reason: >
      One of PathExcludeReason e.g.
      BUILD_TOOL_OF,
      DATA_FILE_OF,
      DOCUMENTATION_OF,
      EXAMPLE_OF,
      OPTIONAL_COMPONENT_OF,
      OTHER,
      PROVIDED_BY,
      TEST_OF or
      TEST_TOOL_OF.
    comment: "A comment further explaining why the path is excluded."
  license_finding_curations:
  - path: "A glob pattern matching files or paths."
    start_lines: "3"
    line_count: 11
    detected_license: "SPDX license expression"
    reason: >
      One of LicenseFindingCurationReason e.g.
      CODE,
      DATA_OF,
      DOCUMENTATION_OF,
      INCORRECT,
      NOT_DETECTED or
      REFERENCE.
    comment: |
      An explanation why the license finding curation is needed or why it is set to specific value.
      It’s recommended to include links to the relevant code or ticket to support your explanation.
    concluded_license: "SPDX license expression"
```

Refer to [PackageConfiguration.kt][PackageConfiguration] for the package configuration specification.

To learn how to write glob patterns, consult the [AntPathMatcher documentation][AntPathMatcher]. Also, check [PathExcludeReason.kt][PathExcludeReason] and [LicenseFindingCurationReason.kt][LicenseFindingCurationReason] for available `reason` options and their usage.

### Correcting dependency license findings

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

## License choices

### When to use license choices

For multi-licensed dependencies, a specific license can be selected.
The license choice can be applied to a package or globally to an SPDX expression in the project.
A choice is only valid for licenses combined with the SPDX operator `OR`.
The choices are applied in the evaluator, and the reporter to the effective license of a package, which is calculated by the chosen [LicenseView][LicenseView].

⚠️ If the choice does not provide a valid result, an exception will be thrown upon deserialization.

Example for an invalid configuration:

```yaml
# This is invalid, as 'E' must be in the resulting license.
- given: (C OR D) AND E
  choice: C
```

### License choice file format

#### License choice by package

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

#### License choice for the project

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

## Snippet choices

### When to use snippet choices

Use a snippet choice to:

* Select a snippet as the origin of a code snippet found in the project source code, disregarding all other snippets for that location.
  * [ORT Scanner](../cli/scanner.md) supported snippet scanners, like [FossID] and [ScanOSS], may return multiple matches for the same piece of code within your project, so it’s necessary to choose the correct finding.
* Mark all snippets for a given source location in the project code repository as false positives.

### Snippet choice file format

The code below shows the structure of `snippet_choices` in the `.ort.yml` file:

```yaml
snippet_choices:
- provenance:
    url: "Url of snippet related code repository e.g. https://github.com/vdurmont/semver4j.git"
  choices:
  - given:
      source_location:
        path: "The path (with invariant separators) of the file that contains the snippet."
        start_line: 3 # The line number the snippet is starting at.
        end_line: 17 # The line number the snippet is ending at.
    choice:
      purl: "A package URL e.g. pkg:github/RS2007/dotfiles@0384a21038fd2e5befb429d0ca52384172607a6d."
      reason: >
        One of SnippetChoiceReason e.g.
        NO_RELEVANT_FINDING,
        ORIGINAL_FINDING or
        OTHER.
      comment: |
        An explanation why this why this snippet choice was made.
        It’s recommended to include links to the relevant code or ticket to support your explanation.
```

Check out [SnippetChoiceReason.kt][SnippetChoiceReason] for all the `reason` options available, along with descriptions on when to use each one.

## Resolutions

### When to use resolutions

Resolutions allow you to *resolve* issues, policy rule violations or vulnerabilities by providing a reason why they are acceptable and can be ignored.

Use a resolution to:

* Mark tool issues as resolved, typically in cases of:
  * license scanner detection timeouts
  * unavailable package sources
* Mark policy rule violations as resolved when the policy requires:
  * confirmation that a dependency was not modified or is dynamically linked
  * verification that a license was acquired for proprietary software
* Mark detected vulnerabilities as resolved for false positives, such as:
  * unreachable or non-executable code linked to a known vulnerability
  * invalid matched vulnerabilities
  * orphaned packages or those declared end-of-life that will not be fixed

Resolutions are only taken into account by the [ORT Reporter](../cli/reporter.md), while the [ORT Analyzer](../cli/analyzer.md) and [ORT Scanner](../cli/scanner.md) ignore them. If a resolution is not project-specific, add it to [resolutions.yml](resolutions.md) so that it is applied to each scan.

⚠️ **Resolutions should not be used to resolve license policy rule violations** as they do not the change generated open source notices (e.g. NOTICE files).
To resolve a license policy rule violation, either add a local `license_findings` curation to the [.ort.yml file](./ort-yml.md) if the finding is in your code repository or create a [package configuration](package-curations.md) with a `license_finding_curations` if the violation occurs in a dependency.

### Resolutions file format

A resolution addresses specific issues, violations, or vulnerabilities through the regular expression specified in the `message`. Each resolution must include an explanation to clarify its acceptability, comprising:

* `reason` - an identifier selected from a predefined list of options either a
  * [IssueResolutionReason][issueResolutionReason] for tool issue resolutions,
  * [RuleViolationResolutionReason][RuleViolationResolutionReason] for policy violation resolutions or,
  * [VulnerabilityResolutionReason][VulnerabilityResolutionReason] for security vulnerability resolutions.
* `comment` - free text, providing an explanation and optionally a link to further information.

The code below shows the structure of `resolutions` in the `.ort.yml` file:

```yaml
resolutions:
  issues:
  - message: "A regular expression matching the error message."
    reason: >
      One of IssueResolutionReason e.g.
      BUILD_TOOL_ISSUE,
      CANT_FIX_ISSUE or
      SCANNER_ISSUE.
    comment: |
      An explanation why the resolution is acceptable.
      It’s recommended to include links to the relevant code or ticket to support your explanation.
  rule_violations:
  - message: "A regular expression matching the policy violation message."
    reason: >
      One of RuleViolationResolutionReason e.g.
      CANT_FIX_EXCEPTION,
      DYNAMIC_LINKAGE_EXCEPTION,
      EXAMPLE_OF_EXCEPTION,
      LICENSE_ACQUIRED_EXCEPTION,
      NOT_MODIFIED_EXCEPTION or
      PATENT_GRANT_EXCEPTION.
    comment: |
      An explanation why the resolution is acceptable.
      It’s recommended to include links to the relevant code or ticket to support your explanation.
  vulnerabilities:
  - id: "A regular expression matching the vulnerability id."
    reason: >
      One of VulnerabilityResolutionReason e.g.
      CANT_FIX_VULNERABILITY,
      INEFFECTIVE_VULNERABILITY,
      INVALID_MATCH_VULNERABILITY,
      MITIGATED_VULNERABILITY,
      NOT_A_VULNERABILITY,
      WILL_NOT_FIX_VULNERABILITY or
      WORKAROUND_FOR_VULNERABILITY.
    comment: |
      An explanation why the resolution is acceptable.
      It’s recommended to include links to the relevant code or ticket to support your explanation.
```

## Related resources

* Code
  * [model/src/main/kotlin/config/IssueResolutionReason.kt][IssueResolutionReason]
  * [model/src/main/kotlin/config/LicenseFindingCuration.kt][LicenseFindingCuration]
  * [model/src/main/kotlin/config/LicenseFindingCurationReason.kt][LicenseFindingCurationReason]
  * [model/src/main/kotlin/config/PathExcludeReason.kt][PathExcludeReason]
  * [model/src/main/kotlin/config/PathIncludeReason.kt][PathIncludeReason]
  * [model/src/main/kotlin/config/ScopeExcludeReason.kt][ScopeExcludeReason]
  * [model/src/main/kotlin/config/SnippetChoices.kt][SnippetChoices]
  * [model/src/main/kotlin/config/snippet/SnippetChoice.kt][SnippetChoice]
  * [model/src/main/kotlin/config/snippet/SnippetChoiceReason.kt][SnippetChoiceReason]
  * [model/src/main/kotlin/config/RuleViolationResolutionReason.kt][RuleViolationResolutionReason]
  * [model/src/main/kotlin/config/VulnerabilityResolutionReason.kt][VulnerabilityResolutionReason]
  * [model/src/main/kotlin/config/PackageConfiguration.kt][PackageConfiguration]
  * [model/src/main/kotlin/PackageCurationData.kt][PackageCurationData]
  * [src/main/kotlin/config/RepositoryConfiguration.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/RepositoryConfiguration.kt)
* Examples
  * [examples/*.ort.yml](https://github.com/oss-review-toolkit/ort/blob/main/examples/)
* How-to guides
  * [How to address tool issues](../../how-to-guides/how-to-address-tool-issues.md)
  * [How to correct licenses](../../how-to-guides/how-to-correct-licenses.md)
  * [How to address a license policy violation](../../how-to-guides/how-to-address-a-license-policy-violation.md)
  * [How to check and remediate vulnerabilities in dependencies](../../how-to-guides/how-to-check-and-remediate-vulnerabilities-in-dependencies.md)
  * [How to make license choices](../../how-to-guides/how-to-make-a-license-choice.md)
  * [How to make snippet choices](../../how-to-guides/how-to-make-snippet-choices.md)
* JSON schema
  * [integrations/schemas/repository-configuration-schema.json](https://github.com/oss-review-toolkit/ort/blob/main/integrations/schemas/repository-configuration-schema.json)
* Reference
  * [Analyzer CLI --repository-configuration-file option](../cli/analyzer.md#configuration-options)
  * [Evaluator CLI --repository-configuration-file option](../cli/evaluator.md#configuration-options)
  * [Helper CLI --repository-configuration command](../cli/orth.md#commands)
  * [Reporter CLI --custom-license-texts-dir, --repository-configuration-file and --package-configurations-dir options](../cli/reporter.md#configuration-options)

[AntPathMatcher]: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html
[FossID]: https://fossid.com
[IssueResolutionReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/IssueResolutionReason.kt
[LicenseFindingCuration]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/LicenseFindingCuration.kt
[LicenseFindingCurationReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/LicenseFindingCurationReason.kt
[LicenseView]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/licenses/LicenseView.kt
[PackageConfiguration]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/PackageConfiguration.kt
[PackageCurationData]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/PackageCurationData.kt
[PathExcludeReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/PathExcludeReason.kt
[PathIncludeReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/PathIncludeReason.kt
[ScanOSS]: https://www.scanoss.com
[ScopeExcludeReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/ScopeExcludeReason.kt
[SnippetChoice]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/snippet/SnippetChoice.kt
[SnippetChoices]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/SnippetChoices.kt
[SnippetChoiceReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/snippet/SnippetChoiceReason.kt
[RuleViolationResolutionReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/RuleViolationResolutionReason.kt
[VulnerabilityResolutionReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/VulnerabilityResolutionReason.kt
