# How to exclude dirs, files, or scopes

Use excludes to mark parts of your project that are not distributed to third parties, such as build tools, tests, or documentation.

## Excluding paths in the project being analyzed

Add [path excludes][ort-yml-path-excludes] to your `.ort.yml` file in the root of your project:

```yaml
excludes:
  paths:
    - pattern: ".github/**"
      reason: "BUILD_TOOL_OF"
      comment: "GitHub Actions workflows and configuration"
    - pattern: "test/**"
      reason: "TEST_OF"
      comment: "Test files"
    - pattern: "README.md"
      reason: "DOCUMENTATION_OF"
      comment: "Project readme"
```

## Excluding paths in packages (dependencies)

To exclude paths in a dependency's source code (e.g., build or test files that aren't part of the released artifact), create a [package configuration][package-configurations] file:

```yaml
id: "NPM::lodash:4.0.0"
path_excludes:
  - pattern: "test/**"
    reason: "TEST_OF"
    comment: "Test files not included in released artifacts."
  - pattern: "doc/**"
    reason: "DOCUMENTATION_OF"
    comment: "Documentation not included in released artifacts."
```

## Excluding scopes

Many package managers group dependencies by use (e.g., production vs. development) in scopes.
To mark entire dependency scopes as not part of the released artifact,
add [scope excludes][ort-yml-scope-excludes] to your `.ort.yml` file:

```yaml
excludes:
  scopes:
    - pattern: "devDependencies"
      reason: "DEV_DEPENDENCY_OF"
      comment: "Development dependencies not included in released artifacts."
```

## Skipping excluded content

To skip excluded files from being analyzed by the [Analyzer] for projects and dependencies (improves performance but may reduce your compliance), enable the [`analyzer.skip_excluded` option][ort-yml-skip-excluded] option in your [.ort.yml][ort-yml] file:

```
analyzer:
  skip_excluded: true
excludes:
  paths:
    - pattern: ".github/**"
      reason: "BUILD_TOOL_OF"
      comment: "GitHub Actions CI/CD workflows and configuration"
```

💡 A better effort-vs-risk balance maybe to keep excluded projects and dependencies visible in ORT reports but avoid scanning their sources. Instead of setting `analyzer.skip_excluded`, run the [Scanner] with `--skip-excluded`. This preserves excluded items in reports while preventing their sources from being scanned for copyrights and licenses.

## Related resources

* Examples
  * [The .ort.yml of the ORT project](https://github.com/oss-review-toolkit/ort/blob/main/.ort.yml)
  * [The .ort.yml of the Elixir project](https://github.com/elixir-lang/elixir/blob/main/.ort.yml)
* Reference
  * [Analyzer CLI][analyzer]
  * [Package configurations][package-configurations]
  * [Repository configuration (.ort.yml)][ort-yml]
  * [Scanner CLI][scanner]

[analyzer]: ../reference/cli/analyzer.md
[ort-yml]: ../reference/configuration/ort-yml.md
[ort-yml-path-excludes]: ../reference/configuration/ort-yml.md#excludes-paths
[ort-yml-scope-excludes]: ../reference/configuration/ort-yml.md#excluding-scopes
[ort-yml-skip-excluded]: ../reference/configuration/ort-yml.md#excludes-and-file-scanning-results
[package-configurations]: ../reference/configuration/package-configurations.md#file-format
[scanner]: ../reference/cli/scanner.md
