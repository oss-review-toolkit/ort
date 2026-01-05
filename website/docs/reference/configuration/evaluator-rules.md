# Evaluator Rules

The `evaluator.rules.kts` file allows you to define custom policy rules that automatically apply to ORT results.

## File format

The `evaluator.rules.kts` file uses a Kotlin-based DSL. See the [Automate your Policy Checks tutorial](../../tutorials/automating-policy-checks.md) for a step-by-step guide to writing rules.

## Script context

When a rules script is executed, ORT provides several variables that give access to the scan results and configuration:

| Variable | Type | Description |
| -------- | ---- | ----------- |
| `ortResult` | [`OrtResult`][OrtResult] | The complete ORT result containing analyzer, scanner, and advisor results |
| `licenseInfoResolver` | [`LicenseInfoResolver`][LicenseInfoResolver] | Resolves license information for packages, combining declared, concluded, and detected licenses |
| `resolutionProvider` | [`ResolutionProvider`][ResolutionProvider] | Provides resolutions for issues and rule violations from configuration |
| `licenseClassifications` | [`LicenseClassifications`][LicenseClassifications] | License categories loaded from `license-classifications.yml` |
| `time` | `Instant` | The timestamp when the evaluation started |
| `ruleViolations` | `MutableList<`[`RuleViolation`][RuleViolation]`>` | Output list where your rules add violations |

## Default imports

The following packages are automatically imported and available in rules scripts:

* `org.ossreviewtoolkit.evaluator.*` - Rule DSL classes and functions
* `org.ossreviewtoolkit.model.*` - ORT data model classes
* `org.ossreviewtoolkit.model.config.*` - Configuration classes
* `org.ossreviewtoolkit.model.licenses.*` - License-related classes
* `org.ossreviewtoolkit.model.utils.*` - Utility classes
* `org.ossreviewtoolkit.utils.spdx.*` - SPDX license expression utilities

## Helper functions

### ruleSet()

The [`ruleSet()`][RuleSet] function creates a container for all your policy rules:

```kotlin
val ruleSet = ruleSet(ortResult, licenseInfoResolver, resolutionProvider) {
    // Define rules here
}

ruleViolations += ruleSet.violations
```

| Parameter | Description |
| --------- | ----------- |
| `ortResult` | The ORT result to evaluate |
| `licenseInfoResolver` | Resolver for license information (optional, defaults to one created from ortResult) |
| `resolutionProvider` | Provider for resolutions (optional, defaults to empty) |
| `projectSourceResolver` | Resolver for project source code access (optional, for `projectSourceRule`) |

## Rule types

The DSL supports four rule types:

| Rule Type | Scope | Use Case |
| --------- | ----- | -------- |
| [`packageRule`][PackageRule] | Each package and project | License checks, vulnerability checks |
| [`dependencyRule`][DependencyRule] | Each dependency in the tree | Dependency-level license checks with context |
| [`projectSourceRule`][ProjectSourceRule] | Once, with source access | Repository structure checks (README, CI config) |
| [`ortResultRule`][OrtResultRule] | Once | Global checks on the entire result |

### Nested license rules

Inside `packageRule` and `dependencyRule`, you can nest `licenseRule` to evaluate each license:

```kotlin
packageRule("RULE_NAME") {
    licenseRule("LICENSE_RULE_NAME", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
        // Access: license, licenseSource, pkg
    }
}
```

### LicenseView options

[`LicenseView`][LicenseView] determines which license sources to consider:

| View | Description |
| ---- | ----------- |
| `ALL` | All licenses from all sources |
| `ONLY_CONCLUDED` | Only concluded licenses |
| `ONLY_DECLARED` | Only declared licenses from package metadata |
| `ONLY_DETECTED` | Only licenses detected by scanners |
| `CONCLUDED_OR_DECLARED_OR_DETECTED` | Concluded if available, else declared, else detected |
| `CONCLUDED_OR_DECLARED_AND_DETECTED` | Concluded, or both declared and detected |

## Matchers

Matchers are conditions implementing the [`RuleMatcher`][RuleMatcher] interface, used in `require {}` blocks. Use `+` for positive match and `-` for negation:

```kotlin
require {
    -isExcluded()      // Package must NOT be excluded
    +hasLicense()      // Package must have a license
}
```

### Package rule matchers

| Matcher | Description |
| ------- | ----------- |
| `isExcluded()` | Package is excluded via .ort.yml |
| `hasLicense()` | Package has any license |
| `hasConcludedLicense()` | Package has a concluded license |
| `hasVulnerability()` | Package has any vulnerability |
| `hasVulnerability(threshold, system)` | Package has vulnerability above threshold (e.g., 7.0, "CVSS:3.1") |
| `isFromOrg(vararg names)` | Package belongs to specified organization(s) |
| `isMetadataOnly()` | Package is metadata-only |
| `isProject()` | Package was created from a project definition |
| `isType(type)` | Package has specified type (Maven, NPM, etc.) |

### Dependency rule matchers

In addition to package rule matchers:

| Matcher | Description |
| ------- | ----------- |
| `isAtTreeLevel(level)` | Dependency is at specified tree depth (0 = direct) |
| `isStaticallyLinked()` | Dependency uses static linkage |
| `isProjectFromOrg(vararg names)` | Parent project belongs to specified organization(s) |

### License rule matchers

| Matcher | Description |
| ------- | ----------- |
| `isExcluded()` | License finding is excluded |
| `isSpdxLicense()` | License is a valid SPDX license (not a LicenseRef) |

### Project source rule matchers

| Matcher | Description |
| ------- | ----------- |
| `projectSourceHasFile(vararg patterns)` | Project has files matching glob patterns |
| `projectSourceHasDirectory(vararg patterns)` | Project has directories matching patterns |
| `projectSourceHasFileWithContent(regex, vararg patterns)` | Files matching patterns contain content matching regex |
| `projectSourceHasVcsType(vararg types)` | Project uses specified VCS type(s) |

### Combining matchers

Use `AllOf`, `AnyOf`, and `NoneOf` to combine matchers:

```kotlin
require {
    +AnyOf(
        isFromOrg("com.example"),
        isType("Maven")
    )
}
```

## Triggering policy violations

Rules can trigger policy violations with different severities:

```kotlin
error("Message describing the violation", "How to fix this issue")
warning("Warning message", "Suggested action")
hint("Informational hint", "Optional guidance")

// Or with explicit severity:
issue(Severity.ERROR, "Message", "How to fix")
```

See [`Severity`][Severity] for available severity levels.

## Command line

To use a `*.rules.kts` file, put it to `$ORT_CONFIG_DIR` directory or pass it via the `--rules-file` option to the [ORT Evaluator](../cli/evaluator.md).

```shell
cli/build/install/ort/bin/ort evaluate \
  -i <scanner-output-dir>/scan-result.yml \
  -o <evaluator-output-dir> \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-dir $ORT_CONFIG_DIR/curations  \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

Alternatively, you can also use the ORT docker image.

```shell
docker run ghcr.io/oss-review-toolkit/ort evaluate \
  -i <scanner-output-dir>/scan-result.yml \
  -o <evaluator-output-dir> \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-dir $ORT_CONFIG_DIR/curations  \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

## Command line

To use a `*.rules.kts` file, put it to `$ORT_CONFIG_DIR` directory or pass it via the `--rules-file` option to the [ORT Evaluator](../cli/evaluator.md).

```shell
cli/build/install/ort/bin/ort evaluate \
  -i <scanner-output-dir>/scan-result.yml \
  -o <evaluator-output-dir> \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-dir $ORT_CONFIG_DIR/curations  \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

Alternatively, you can also use the ORT docker image.

```shell
docker run ghcr.io/oss-review-toolkit/ort evaluate \
  -i <scanner-output-dir>/scan-result.yml \
  -o <evaluator-output-dir> \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-dir $ORT_CONFIG_DIR/curations  \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

## Related resources

* Code
  * Evaluator DSL
    * [RuleSet.kt][RuleSet] - Rule set container and `ruleSet()` function
    * [PackageRule.kt][PackageRule] - Package rule implementation and matchers
    * [DependencyRule.kt][DependencyRule] - Dependency rule implementation and matchers
    * [ProjectSourceRule.kt][ProjectSourceRule] - Project source rule implementation and matchers
    * [OrtResultRule.kt][OrtResultRule] - ORT result rule implementation
    * [RuleMatcher.kt][RuleMatcher] - Matcher interface and combinators
    * [RulesScriptTemplate.kt][RulesScriptTemplate] - Script context and imports
  * Model classes
    * [OrtResult.kt][OrtResult] - ORT result data model
    * [LicenseInfoResolver.kt][LicenseInfoResolver] - License information resolver
    * [LicenseClassifications.kt][LicenseClassifications] - License classifications model
    * [LicenseView.kt][LicenseView] - License view filtering
    * [ResolutionProvider.kt][ResolutionProvider] - Resolution provider interface
    * [RuleViolation.kt][RuleViolation] - Rule violation model
    * [Severity.kt][Severity] - Severity levels
* Examples
  * [examples/example.rules.kts](https://github.com/oss-review-toolkit/ort/blob/main/examples/example.rules.kts)
  * [evaluator/src/main/resources/rules/osadl.rules.kts](https://github.com/oss-review-toolkit/ort/blob/main/evaluator/src/main/resources/rules/osadl.rules.kts)
  * [evaluator.rules.kts within the ort-config repository](https://github.com/oss-review-toolkit/ort-config/blob/main/evaluator.rules.kts)
* Reference
  * [Evaluator CLI](../cli/evaluator.md)
  * [License classifications](license-classifications.md)
* Tutorials
  * [Automating policy checks](../../tutorials/automating-policy-checks.md) - Step-by-step guide for writing evaluator policy rules

[RuleSet]: https://github.com/oss-review-toolkit/ort/blob/main/evaluator/src/main/kotlin/RuleSet.kt
[PackageRule]: https://github.com/oss-review-toolkit/ort/blob/main/evaluator/src/main/kotlin/PackageRule.kt
[DependencyRule]: https://github.com/oss-review-toolkit/ort/blob/main/evaluator/src/main/kotlin/DependencyRule.kt
[ProjectSourceRule]: https://github.com/oss-review-toolkit/ort/blob/main/evaluator/src/main/kotlin/ProjectSourceRule.kt
[OrtResultRule]: https://github.com/oss-review-toolkit/ort/blob/main/evaluator/src/main/kotlin/OrtResultRule.kt
[RuleMatcher]: https://github.com/oss-review-toolkit/ort/blob/main/evaluator/src/main/kotlin/RuleMatcher.kt
[RulesScriptTemplate]: https://github.com/oss-review-toolkit/ort/blob/main/evaluator/src/main/kotlin/RulesScriptTemplate.kt
[OrtResult]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/OrtResult.kt
[LicenseInfoResolver]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/licenses/LicenseInfoResolver.kt
[LicenseClassifications]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/licenses/LicenseClassifications.kt
[LicenseView]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/licenses/LicenseView.kt
[ResolutionProvider]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/utils/ResolutionProvider.kt
[RuleViolation]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/RuleViolation.kt
[Severity]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/Severity.kt
