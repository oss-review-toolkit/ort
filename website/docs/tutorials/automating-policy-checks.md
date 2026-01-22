# Automating Policy Checks

In this tutorial, you'll implement a complete license compliance policy for a fictitious company using ORT's [Evaluator]. By the end, you'll have a working rules file that automatically checks for license violations, security vulnerabilities, and repository best practices.

## The scenario

You're a developer at **Example LLC**, a mid-size software company. Exmaple LLC builds a commercial SaaS product with proprietary backend services and also maintains an open-source SDK. The legal team has defined a license compliance policy, and your task is to automate these checks using ORT.

## Example LLC license policy

Here's what the legal team requires:

1. **Permissive licenses are allowed** - MIT, Apache-2.0, and BSD variants can be used freely
2. **Copyleft licenses are prohibited** - GPL and AGPL are not allowed in proprietary code
3. **Copyleft-limited licenses need review** - LGPL and MPL are allowed but require a warning when statically linked
4. **All packages must have known licenses** - No `NOASSERTION` or unmapped licenses allowed
5. **High-severity vulnerabilities must be addressed** - Any vulnerability with CVSS ≥ 7.0 is an error
6. **Projects must be documented** - Every repository needs a README.md with a License section
7. **No vendored dependencies** - `node_modules` and `vendor` directories must not be committed

Let's implement each requirement as an ORT evaluator rule.

## Prerequisites

* Basic familiarity with ORT (see the [walkthrough tutorial](walkthrough/index.md))
* An ORT result file from running the analyzer and scanner
* Basic knowledge of Kotlin syntax

## Setting up

### Running the Evaluator

The Evaluator checks your scan results against policy rules defined in a `.rules.kts` file:

```shell
ort evaluate \
  -i scan-result.yml \
  -o output-dir \
  --rules-file example-llc-rules.kts \
  --license-classifications-file license-classifications.yml
```

| Option | Description |
| ------ | ----------- |
| `-i` | Input ORT result file (from analyzer, scanner, or advisor) |
| `-o` | Output directory for the evaluation result |
| `--rules-file` | Path to your rules script |
| `--license-classifications-file` | License categories for use in rules |

If no `--rules-file` is specified, ORT looks for `evaluator.rules.kts` in your config directory (`$ORT_CONFIG_DIR` or `~/.ort/config`).

See the [Evaluator CLI reference](../reference/cli/evaluator.md) for all available options.

### Setting up license classifications

Before writing rules, we need to categorize licenses. The [ort-config repository](https://github.com/oss-review-toolkit/ort-config) provides a comprehensive default [license-classifications.yml](https://github.com/oss-review-toolkit/ort-config/blob/main/license-classifications.yml) that covers most common licenses and can be used as-is or as a starting point.

For this tutorial, we'll create a custom `license-classifications.yml` that matches Example LLC's specific policy categories. You typically need a custom file when:

* Your policy uses different category names or groupings
* You need to add custom or proprietary licenses not in the default file
* You want to categorize licenses differently than the defaults

```yaml
categories:
- name: "permissive"
  description: "Licenses with minimal restrictions - allowed at Example LLC"
- name: "copyleft"
  description: "Licenses requiring derivative works to be open source - prohibited"
- name: "copyleft-limited"
  description: "Copyleft licenses with linking exceptions - requires review"
- name: "public-domain"
  description: "Public domain dedications - allowed"

categorizations:
- id: "Apache-2.0"
  categories:
  - "permissive"
- id: "MIT"
  categories:
  - "permissive"
- id: "BSD-2-Clause"
  categories:
  - "permissive"
- id: "BSD-3-Clause"
  categories:
  - "permissive"
- id: "GPL-2.0-only"
  categories:
  - "copyleft"
- id: "GPL-3.0-only"
  categories:
  - "copyleft"
- id: "AGPL-3.0-only"
  categories:
  - "copyleft"
- id: "LGPL-2.1-only"
  categories:
  - "copyleft-limited"
- id: "LGPL-3.0-only"
  categories:
  - "copyleft-limited"
- id: "MPL-2.0"
  categories:
  - "copyleft-limited"
- id: "CC0-1.0"
  categories:
  - "public-domain"
- id: "Unlicense"
  categories:
  - "public-domain"
```

See [How to classify licenses](../how-to-guides/how-to-classify-licenses.md) for detailed guidance on setting up classifications.

### Rules file structure

Create `example-llc-rules.kts` with this basic structure:

```kotlin
// Load license categories from our classifications file
val permissiveLicenses = licenseClassifications.licensesByCategory["permissive"].orEmpty()
val copyleftLicenses = licenseClassifications.licensesByCategory["copyleft"].orEmpty()
val copyleftLimitedLicenses = licenseClassifications.licensesByCategory["copyleft-limited"].orEmpty()
val publicDomainLicenses = licenseClassifications.licensesByCategory["public-domain"].orEmpty()

// Combine all known licenses for validation
val handledLicenses = listOf(
    permissiveLicenses,
    copyleftLicenses,
    copyleftLimitedLicenses,
    publicDomainLicenses
).flatten().toSet()

// Helper function for remediation guidance
fun PackageRule.howToFixDefault() = """
    Please review this violation and take appropriate action:
    - If this is a false positive, add an exclusion to your .ort.yml
    - If this is a valid issue, update your dependencies or contact the legal team
""".trimIndent()

// Create the rule set - we'll add rules here
val ruleSet = ruleSet(ortResult, licenseInfoResolver, resolutionProvider) {
    // Rules will go here
}

// Collect all violations
ruleViolations += ruleSet.violations
```

The script has access to several context variables provided by ORT. See [Script context](../reference/configuration/evaluator-rules.md#script-context) for the complete list.

Now let's implement each policy requirement.

## Policy 1: Allow permissive licenses

The first rule ensures all licenses are categorized in our policy. If we encounter a license that's not in our `handledLicenses` set, we need to review and categorize it.

First, let's create a custom matcher to check if a license is handled:

```kotlin
fun PackageRule.LicenseRule.isHandled() =
    object : RuleMatcher {
        override val description = "isHandled($license)"

        override fun matches() =
            license in handledLicenses &&
            // Handle license exceptions properly
            ("-exception" !in license.toString() || " WITH " in license.toString())
    }
```

Now the rule itself:

```kotlin
fun RuleSet.unhandledLicenseRule() = packageRule("UNHANDLED_LICENSE") {
    // Skip excluded packages
    require {
        -isExcluded()
    }

    // Check each license of the package
    licenseRule("UNHANDLED_LICENSE", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
        require {
            -isExcluded()
            -isHandled()
        }

        error(
            "The license $license is currently not covered by Example LLC's policy. " +
                "The license was ${licenseSource.name.lowercase()} in package " +
                "${pkg.metadata.id.toCoordinates()}.",
            howToFixDefault()
        )
    }
}
```

Key concepts:

* `require {}` defines conditions that must be met for the rule to apply
* `-isExcluded()` means "package must NOT be excluded" (the `-` negates)
* `-isHandled()` means "license must NOT be in our handled set" (triggering the error)
* `licenseRule()` iterates over each license in the package

### Understanding LicenseView

The `LicenseView` parameter determines which licenses to check:

| View | Description |
| ---- | ----------- |
| `CONCLUDED_OR_DECLARED_AND_DETECTED` | Use concluded license if available, otherwise require both declared and detected |
| `CONCLUDED_OR_DECLARED_OR_DETECTED` | Use concluded, then declared, then detected (first available) |
| `ONLY_CONCLUDED` | Only check concluded licenses |
| `ONLY_DECLARED` | Only check declared licenses from package metadata |
| `ONLY_DETECTED` | Only check licenses found by scanners |
| `ALL` | Check all licenses from all sources |

### Checking for unmapped licenses

Some declared licenses can't be mapped to SPDX identifiers. Let's also warn about those:

```kotlin
fun RuleSet.unmappedDeclaredLicenseRule() = packageRule("UNMAPPED_DECLARED_LICENSE") {
    require {
        -isExcluded()
    }

    resolvedLicenseInfo.licenseInfo.declaredLicenseInfo.processed.unmapped.forEach { unmappedLicense ->
        warning(
            "The declared license '$unmappedLicense' could not be mapped to a valid license or parsed as an SPDX " +
                "expression. The license was found in package ${pkg.metadata.id.toCoordinates()}.",
            howToFixDefault()
        )
    }
}
```

## Policy 2: Prohibit copyleft licenses

Example LLC's proprietary code cannot include GPL or AGPL licensed dependencies. Let's create a matcher and rule for this:

```kotlin
fun PackageRule.LicenseRule.isCopyleft() =
    object : RuleMatcher {
        override val description = "isCopyleft($license)"
        override fun matches() = license in copyleftLicenses
    }

fun RuleSet.copyleftInSourceRule() = packageRule("COPYLEFT_IN_SOURCE") {
    require {
        -isExcluded()
    }

    licenseRule("COPYLEFT_IN_SOURCE", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
        require {
            -isExcluded()
            +isCopyleft()
        }

        val message = if (licenseSource == LicenseSource.DETECTED) {
            "The copyleft license $license was ${licenseSource.name.lowercase()} " +
                "in package ${pkg.metadata.id.toCoordinates()}."
        } else {
            "The package ${pkg.metadata.id.toCoordinates()} has the ${licenseSource.name.lowercase()} copyleft " +
                "license $license."
        }

        error(message, howToFixDefault())
    }
}
```

We also need a dependency rule to catch copyleft licenses in the dependency tree:

```kotlin
fun RuleSet.copyleftInDependencyRule() = dependencyRule("COPYLEFT_IN_DEPENDENCY") {
    licenseRule("COPYLEFT_IN_DEPENDENCY", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
        require {
            +isCopyleft()
        }

        issue(
            Severity.ERROR,
            "The project ${project.id.toCoordinates()} has a dependency licensed under the " +
                "copyleft license $license.",
            howToFixDefault()
        )
    }
}
```

## Policy 3: Warn about copyleft-limited licenses

LGPL and MPL are allowed at Example LLC, but static linking can be problematic. Let's warn when a direct dependency is statically linked:

```kotlin
fun PackageRule.LicenseRule.isCopyleftLimited() =
    object : RuleMatcher {
        override val description = "isCopyleftLimited($license)"
        override fun matches() = license in copyleftLimitedLicenses
    }

fun RuleSet.copyleftLimitedStaticLinkRule() = dependencyRule("COPYLEFT_LIMITED_STATIC_LINK") {
    require {
        +isAtTreeLevel(0)      // Only direct dependencies
        +isStaticallyLinked()  // Only statically linked
    }

    licenseRule("COPYLEFT_LIMITED_STATIC_LINK", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
        require {
            +isCopyleftLimited()
        }

        issue(
            Severity.WARNING,
            "The project ${project.id.toCoordinates()} has a statically linked direct dependency " +
                "licensed under the copyleft-limited license $license. " +
                "This may have licensing implications.",
            howToFixDefault()
        )
    }
}
```

| Matcher | Description |
| ------- | ----------- |
| `isAtTreeLevel(0)` | Direct dependency |
| `isAtTreeLevel(1)` | Transitive dependency (one level deep) |
| `isStaticallyLinked()` | Dependency uses static or project-static linkage |

We also want a general rule for copyleft-limited licenses in the source:

```kotlin
fun RuleSet.copyleftLimitedInSourceRule() = packageRule("COPYLEFT_LIMITED_IN_SOURCE") {
    require {
        -isExcluded()
    }

    licenseRule("COPYLEFT_LIMITED_IN_SOURCE", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
        require {
            -isExcluded()
            +isCopyleftLimited()
        }

        val licenseSourceName = licenseSource.name.lowercase()
        val message = if (licenseSource == LicenseSource.DETECTED) {
            "The copyleft-limited license $license was $licenseSourceName in package " +
                "${pkg.metadata.id.toCoordinates()}."
        } else {
            "The package ${pkg.metadata.id.toCoordinates()} has the $licenseSourceName copyleft-limited " +
                "license $license."
        }

        // This is a warning, not an error - needs review but may be acceptable
        warning(message, howToFixDefault())
    }
}
```

## Policy 4: Require known licenses

This is already covered by our `unhandledLicenseRule()` and `unmappedDeclaredLicenseRule()` from Policy 1. Any package with `NOASSERTION` or an unrecognized license will trigger a violation.

## Policy 5: Flag high-severity vulnerabilities

Example LLC requires all high-severity vulnerabilities to be addressed before release:

```kotlin
fun RuleSet.highSeverityVulnerabilityRule() = packageRule("HIGH_SEVERITY_VULNERABILITY") {
    val scoreThreshold = 7.0f
    val scoringSystem = "CVSS:3.1"

    require {
        -isExcluded()
        +hasVulnerability(scoreThreshold, scoringSystem)
    }

    issue(
        Severity.ERROR,
        "The package ${pkg.metadata.id.toCoordinates()} has a vulnerability with $scoringSystem score >= " +
            "$scoreThreshold. This must be addressed before release.",
        howToFixDefault()
    )
}
```

For tracking purposes, let's also add a rule that warns about any vulnerability:

```kotlin
fun RuleSet.vulnerabilityInPackageRule() = packageRule("VULNERABILITY_IN_PACKAGE") {
    require {
        -isExcluded()
        +hasVulnerability()
    }

    issue(
        Severity.WARNING,
        "The package ${pkg.metadata.id.toCoordinates()} has a vulnerability.",
        howToFixDefault()
    )
}
```

## Policy 6: Require documentation

Example LLC wants every repository to have a README with license information:

```kotlin
fun RuleSet.missingReadmeFileRule() = projectSourceRule("MISSING_README_FILE") {
    require {
        -projectSourceHasFile("README.md")
    }

    error("The project's code repository does not contain the file 'README.md'.")
}

fun RuleSet.missingReadmeFileLicenseSectionRule() = projectSourceRule("MISSING_README_LICENSE_SECTION") {
    require {
        +projectSourceHasFile("README.md")
        -projectSourceHasFileWithContent(".*^#{1,2} License$.*", "README.md")
    }

    error(
        message = "The file 'README.md' is missing a \"License\" section.",
        howToFix = "Please add a \"License\" section to the file 'README.md'."
    )
}
```

## Policy 7: No vendored dependencies

Committed `node_modules` or `vendor` directories cause problems with license scanning and should be in `.gitignore`:

```kotlin
fun RuleSet.vendoredDependenciesRule() = projectSourceRule("VENDORED_DEPENDENCIES") {
    val denyDirPatterns = listOf(
        "**/node_modules" to setOf("NPM", "Yarn", "PNPM"),
        "**/vendor" to setOf("GoMod")
    )

    denyDirPatterns.forEach { (pattern, packageManagers) ->
        val offendingDirs = projectSourceFindDirectories(pattern)

        if (offendingDirs.isNotEmpty()) {
            issue(
                Severity.ERROR,
                "The directories ${offendingDirs.joinToString()} belong to the package manager(s) " +
                    "${packageManagers.joinToString()} and must not be committed.",
                "Please delete the directories: ${offendingDirs.joinToString()} and add them to .gitignore."
            )
        }
    }
}
```

## Putting it all together

Here's the complete `example-llc-rules.kts` file:

```kotlin
// =============================================================================
// Example LLC License Policy Rules
// =============================================================================

// Load license categories
val permissiveLicenses = licenseClassifications.licensesByCategory["permissive"].orEmpty()
val copyleftLicenses = licenseClassifications.licensesByCategory["copyleft"].orEmpty()
val copyleftLimitedLicenses = licenseClassifications.licensesByCategory["copyleft-limited"].orEmpty()
val publicDomainLicenses = licenseClassifications.licensesByCategory["public-domain"].orEmpty()

// Validate our license categories don't overlap
val handledLicenses = listOf(
    permissiveLicenses,
    copyleftLicenses,
    copyleftLimitedLicenses,
    publicDomainLicenses
).flatten().let {
    it.getDuplicates().let { duplicates ->
        require(duplicates.isEmpty()) {
            "The classifications for the following licenses overlap: $duplicates"
        }
    }
    it.toSet()
}

// Helper for remediation guidance
fun PackageRule.howToFixDefault() = """
    Please review this violation and take appropriate action:
    - If this is a false positive, add an exclusion to your .ort.yml
    - If this is a valid issue, update your dependencies or contact the legal team
""".trimIndent()

// =============================================================================
// Custom Matchers
// =============================================================================

fun PackageRule.LicenseRule.isHandled() =
    object : RuleMatcher {
        override val description = "isHandled($license)"
        override fun matches() =
            license in handledLicenses &&
            ("-exception" !in license.toString() || " WITH " in license.toString())
    }

fun PackageRule.LicenseRule.isCopyleft() =
    object : RuleMatcher {
        override val description = "isCopyleft($license)"
        override fun matches() = license in copyleftLicenses
    }

fun PackageRule.LicenseRule.isCopyleftLimited() =
    object : RuleMatcher {
        override val description = "isCopyleftLimited($license)"
        override fun matches() = license in copyleftLimitedLicenses
    }

// =============================================================================
// Policy Rules
// =============================================================================

// Policy 1 & 4: All licenses must be known and handled
fun RuleSet.unhandledLicenseRule() = packageRule("UNHANDLED_LICENSE") {
    require {
        -isExcluded()
    }

    licenseRule("UNHANDLED_LICENSE", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
        require {
            -isExcluded()
            -isHandled()
        }

        error(
            "The license $license is currently not covered by policy rules. " +
                "The license was ${licenseSource.name.lowercase()} in package " +
                "${pkg.metadata.id.toCoordinates()}.",
            howToFixDefault()
        )
    }
}

fun RuleSet.unmappedDeclaredLicenseRule() = packageRule("UNMAPPED_DECLARED_LICENSE") {
    require {
        -isExcluded()
    }

    resolvedLicenseInfo.licenseInfo.declaredLicenseInfo.processed.unmapped.forEach { unmappedLicense ->
        warning(
            "The declared license '$unmappedLicense' could not be mapped to a valid license or parsed as an SPDX " +
                "expression. The license was found in package ${pkg.metadata.id.toCoordinates()}.",
            howToFixDefault()
        )
    }
}

// Policy 2: Copyleft licenses are prohibited
fun RuleSet.copyleftInSourceRule() = packageRule("COPYLEFT_IN_SOURCE") {
    require {
        -isExcluded()
    }

    licenseRule("COPYLEFT_IN_SOURCE", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
        require {
            -isExcluded()
            +isCopyleft()
        }

        val message = if (licenseSource == LicenseSource.DETECTED) {
            "The copyleft license $license was ${licenseSource.name.lowercase()} " +
                "in package ${pkg.metadata.id.toCoordinates()}."
        } else {
            "The package ${pkg.metadata.id.toCoordinates()} has the ${licenseSource.name.lowercase()} copyleft " +
                "license $license."
        }

        error(message, howToFixDefault())
    }
}

fun RuleSet.copyleftInDependencyRule() = dependencyRule("COPYLEFT_IN_DEPENDENCY") {
    licenseRule("COPYLEFT_IN_DEPENDENCY", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
        require {
            +isCopyleft()
        }

        issue(
            Severity.ERROR,
            "The project ${project.id.toCoordinates()} has a dependency licensed under the " +
                "copyleft license $license.",
            howToFixDefault()
        )
    }
}

// Policy 3: Copyleft-limited licenses need review (warning for static linking)
fun RuleSet.copyleftLimitedInSourceRule() = packageRule("COPYLEFT_LIMITED_IN_SOURCE") {
    require {
        -isExcluded()
    }

    licenseRule("COPYLEFT_LIMITED_IN_SOURCE", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
        require {
            -isExcluded()
            +isCopyleftLimited()
        }

        val licenseSourceName = licenseSource.name.lowercase()
        val message = if (licenseSource == LicenseSource.DETECTED) {
            "The copyleft-limited license $license was $licenseSourceName in package " +
                "${pkg.metadata.id.toCoordinates()}."
        } else {
            "The package ${pkg.metadata.id.toCoordinates()} has the $licenseSourceName copyleft-limited " +
                "license $license."
        }

        warning(message, howToFixDefault())
    }
}

fun RuleSet.copyleftLimitedStaticLinkRule() = dependencyRule("COPYLEFT_LIMITED_STATIC_LINK") {
    require {
        +isAtTreeLevel(0)
        +isStaticallyLinked()
    }

    licenseRule("COPYLEFT_LIMITED_STATIC_LINK", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
        require {
            +isCopyleftLimited()
        }

        issue(
            Severity.WARNING,
            "The project ${project.id.toCoordinates()} has a statically linked direct dependency licensed " +
                "under the copyleft-limited license $license.",
            howToFixDefault()
        )
    }
}

// Policy 5: High-severity vulnerabilities must be addressed
fun RuleSet.vulnerabilityInPackageRule() = packageRule("VULNERABILITY_IN_PACKAGE") {
    require {
        -isExcluded()
        +hasVulnerability()
    }

    issue(
        Severity.WARNING,
        "The package ${pkg.metadata.id.toCoordinates()} has a vulnerability.",
        howToFixDefault()
    )
}

fun RuleSet.highSeverityVulnerabilityRule() = packageRule("HIGH_SEVERITY_VULNERABILITY") {
    val scoreThreshold = 7.0f
    val scoringSystem = "CVSS:3.1"

    require {
        -isExcluded()
        +hasVulnerability(scoreThreshold, scoringSystem)
    }

    issue(
        Severity.ERROR,
        "The package ${pkg.metadata.id.toCoordinates()} has a vulnerability with $scoringSystem score >= " +
            "$scoreThreshold.",
        howToFixDefault()
    )
}

// Policy 6: Projects must have documentation
fun RuleSet.missingReadmeFileRule() = projectSourceRule("MISSING_README_FILE") {
    require {
        -projectSourceHasFile("README.md")
    }

    error("The project's code repository does not contain the file 'README.md'.")
}

fun RuleSet.missingReadmeFileLicenseSectionRule() = projectSourceRule("MISSING_README_LICENSE_SECTION") {
    require {
        +projectSourceHasFile("README.md")
        -projectSourceHasFileWithContent(".*^#{1,2} License$.*", "README.md")
    }

    error(
        message = "The file 'README.md' is missing a \"License\" section.",
        howToFix = "Please add a \"License\" section to the file 'README.md'."
    )
}

// Policy 7: No vendored dependencies
fun RuleSet.vendoredDependenciesRule() = projectSourceRule("VENDORED_DEPENDENCIES") {
    val denyDirPatterns = listOf(
        "**/node_modules" to setOf("NPM", "Yarn", "PNPM"),
        "**/vendor" to setOf("GoMod")
    )

    denyDirPatterns.forEach { (pattern, packageManagers) ->
        val offendingDirs = projectSourceFindDirectories(pattern)

        if (offendingDirs.isNotEmpty()) {
            issue(
                Severity.ERROR,
                "The directories ${offendingDirs.joinToString()} belong to the package manager(s) " +
                    "${packageManagers.joinToString()} and must not be committed.",
                "Please delete the directories: ${offendingDirs.joinToString()}."
            )
        }
    }
}

// =============================================================================
// Rule Set Registration
// =============================================================================

val ruleSet = ruleSet(ortResult, licenseInfoResolver, resolutionProvider) {
    // License policy rules
    unhandledLicenseRule()
    unmappedDeclaredLicenseRule()
    copyleftInSourceRule()
    copyleftInDependencyRule()
    copyleftLimitedInSourceRule()
    copyleftLimitedStaticLinkRule()

    // Security rules
    vulnerabilityInPackageRule()
    highSeverityVulnerabilityRule()

    // Repository structure rules
    missingReadmeFileRule()
    missingReadmeFileLicenseSectionRule()
    vendoredDependenciesRule()
}

ruleViolations += ruleSet.violations
```

## Additional rule examples

Beyond Example LLC's specific policy, here are some other useful rules you might want to adapt.

### Requiring CI configuration

```kotlin
fun RuleSet.missingCiConfigurationRule() = projectSourceRule("MISSING_CI_CONFIGURATION") {
    require {
        -AnyOf(
            projectSourceHasFile(
                ".appveyor.yml",
                ".bitbucket-pipelines.yml",
                ".gitlab-ci.yml",
                ".travis.yml"
            ),
            projectSourceHasDirectory(
                ".circleci",
                ".github/workflows"
            )
        )
    }

    error(
        message = "This project does not have any known CI configuration files.",
        howToFix = "Please setup a CI. If you already have a CI and the error persists, please contact support."
    )
}
```

### Requiring a CONTRIBUTING file

```kotlin
fun RuleSet.missingContributingFileRule() = projectSourceRule("MISSING_CONTRIBUTING_FILE") {
    require {
        -projectSourceHasFile("CONTRIBUTING.md")
    }

    error("The project's code repository does not contain the file 'CONTRIBUTING.md'.")
}
```

### Validating the LICENSE file content

```kotlin
fun RuleSet.wrongLicenseInLicenseFileRule() = projectSourceRule("WRONG_LICENSE_IN_LICENSE_FILE") {
    require {
        +projectSourceHasFile("LICENSE")
    }

    val allowedRootLicenses = setOf("Apache-2.0", "MIT")
    val detectedRootLicenses = projectSourceGetDetectedLicensesByFilePath("LICENSE").values.flatten().toSet()
    val wrongLicenses = detectedRootLicenses - allowedRootLicenses

    if (wrongLicenses.isNotEmpty()) {
        error(
            message = "The file 'LICENSE' contains the following disallowed licenses ${wrongLicenses.joinToString()}.",
            howToFix = "Please use only the following allowed licenses: ${allowedRootLicenses.joinToString()}."
        )
    } else if (detectedRootLicenses.isEmpty()) {
        error(
            message = "The file 'LICENSE' does not contain any license.",
            howToFix = "Please use one of the following allowed licenses: ${allowedRootLicenses.joinToString()}."
        )
    }
}
```

### Checking for deprecated configuration

```kotlin
fun RuleSet.deprecatedScopeExcludeReasonInOrtYmlRule() = ortResultRule("DEPRECATED_SCOPE_EXCLUDE_REASON") {
    val reasons = ortResult.repository.config.excludes.scopes.mapTo(mutableSetOf()) { it.reason }

    @Suppress("DEPRECATION")
    val deprecatedReasons = setOf(ScopeExcludeReason.TEST_TOOL_OF)

    reasons.intersect(deprecatedReasons).forEach { offendingReason ->
        warning(
            "The repository configuration is using the deprecated scope exclude reason '$offendingReason'.",
            "Please use only non-deprecated scope exclude reasons, see " +
                "https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/ScopeExcludeReason.kt."
        )
    }
}
```

## Advanced techniques

The [ort-config evaluator.rules.kts](https://github.com/oss-review-toolkit/ort-config/blob/main/evaluator.rules.kts) demonstrates several advanced patterns for production rule sets.

### Dynamic how-to-fix messages

Generate context-aware remediation guidance based on whether you're checking a project or dependency:

```kotlin
fun PackageRule.howToFixLicenseViolation(): String {
    return if (pkg.metadata.isProject) {
        if (licenseSource == LicenseSource.DETECTED) {
            "This license was detected in your project's source code. Review the flagged files and " +
                "either remove the code or add a license exception in your .ort.yml file."
        } else {
            "Update your project's declared license in your build configuration."
        }
    } else {
        "This is a dependency issue. Consider:\n" +
            "1. Finding an alternative package with a compatible license\n" +
            "2. Adding a license conclusion via package curations\n" +
            "3. Excluding this dependency if it's not needed"
    }
}
```

### Generating package configuration templates

Help users fix issues by generating ready-to-use configuration snippets:

````kotlin
fun PackageRule.generatePackageConfigurationHint(): String {
    val id = pkg.metadata.id
    val provenance = pkg.provenance

    val vcsMatch = if (provenance is RepositoryProvenance) {
        """
        vcs:
          type: "${provenance.vcsInfo.type}"
          url: "${provenance.vcsInfo.url}"
          revision: "${provenance.resolvedRevision}"
        """.trimIndent()
    } else {
        """
        source_artifact:
          url: "${(provenance as? ArtifactProvenance)?.sourceArtifact?.url}"
        """.trimIndent()
    }

    return """
        Add to package configuration at curations/${id.type}/${id.namespace}/${id.name}.yml:

        ```yaml
        - id: "${id.toCoordinates()}"
          $vcsMatch
          license_findings:
            - ...
        ```
    """.trimIndent()
}
````

### Differentiating projects from dependencies

Apply different rules based on whether a package is your project or a third-party dependency:

```kotlin
fun RuleSet.copyleftInProjectsRule() = packageRule("COPYLEFT_IN_PROJECTS") {
    require {
        -isExcluded()
        +isProject()  // Only check your own projects
    }

    licenseRule("COPYLEFT_IN_PROJECT", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
        require {
            +isCopyleft()
        }

        error(
            "Your project ${pkg.metadata.id.toCoordinates()} contains copyleft-licensed code ($license). " +
                "This may require releasing your source code.",
            "Review the detected files and ensure compliance with $license requirements."
        )
    }
}

fun RuleSet.copyleftInDependenciesOnlyRule() = packageRule("COPYLEFT_IN_DEPENDENCIES") {
    require {
        -isExcluded()
        -isProject()  // Only check dependencies
    }

    licenseRule("COPYLEFT_IN_DEPENDENCY", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
        require {
            +isCopyleft()
        }

        warning(
            "Dependency ${pkg.metadata.id.toCoordinates()} is licensed under $license.",
            "Evaluate whether this dependency's license is compatible with your project."
        )
    }
}
```

### Using labels for conditional rules

ORT supports labels that can be set via CLI (`--label key=value`) to enable conditional rule execution:

```kotlin
fun RuleSet.strictModeRules() = packageRule("STRICT_MODE_CHECK") {
    // Only run this rule if strict mode is enabled via --label strict=true
    require {
        +hasLabel("strict", "true")
    }

    // Stricter checks here...
}
```

## Next steps

You now have a working rules file that implements Example LLC's license policy. From here you can:

* Add the rules file to version control so policy changes are tracked
* Run the evaluator in CI to catch violations before merging
* Adjust thresholds and severity levels as your policy evolves
* Add more license categorizations as you encounter new licenses

## Related resources

* Examples
  * [example.rules.kts](https://github.com/oss-review-toolkit/ort/blob/main/examples/example.rules.kts)
  * [ort-config evaluator.rules.kts](https://github.com/oss-review-toolkit/ort-config/blob/main/evaluator.rules.kts)
* How-to guides
  * [How to classify licenses](../how-to-guides/how-to-classify-licenses.md)
  * [How to address a license policy violation](../how-to-guides/how-to-address-a-license-policy-violation.md)
* Reference
  * [Evaluator rules DSL](../reference/configuration/evaluator-rules.md)
  * [Evaluator CLI][evaluator]
  * [License classifications](../reference/configuration/license-classifications.md)

[evaluator]: ../reference/cli/evaluator.md
