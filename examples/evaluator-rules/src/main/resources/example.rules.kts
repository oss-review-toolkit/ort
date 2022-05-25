/*
 * Copyright (C) 2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

/*******************************************************
 * Example OSS Review Toolkit (ORT) .rules.kts file    *
 *                                                     *
 * Note this file only contains example how to write   *
 * rules. It's recommended you consult your own legal  *
 * when writing your own rules.                        *
 *******************************************************/

/**
 * Import the license classifications from license-classifications.yml.
 */

val permissiveLicenses = licenseClassifications.licensesByCategory["permissive"].orEmpty()

val copyleftLicenses = licenseClassifications.licensesByCategory["copyleft"].orEmpty()

val copyleftLimitedLicenses = licenseClassifications.licensesByCategory["copyleft-limited"].orEmpty()

val publicDomainLicenses = licenseClassifications.licensesByCategory["public-domain"].orEmpty()

// The complete set of licenses covered by policy rules.
val handledLicenses = listOf(
    permissiveLicenses,
    publicDomainLicenses,
    copyleftLicenses,
    copyleftLimitedLicenses
).flatten().let {
    it.getDuplicates().let { duplicates ->
        require(duplicates.isEmpty()) {
            "The classifications for the following licenses overlap: $duplicates"
        }
    }

    it.toSet()
}

/**
 * Function to return Markdown-formatted text to aid users with resolving violations.
 */

fun PackageRule.howToFixDefault() = """
        A text written in MarkDown to help users resolve policy violations
        which may link to additional resources.
    """.trimIndent()

/**
 * Set of matchers to help keep policy rules easy to understand
 */

fun PackageRule.LicenseRule.isHandled() =
    object : RuleMatcher {
        override val description = "isHandled($license)"

        override fun matches() =
            license in handledLicenses && ("-exception" !in license.toString() || " WITH " in license.toString())
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

/**
 * Example policy rules
 */

// Define the set of policy rules.
val ruleSet = ruleSet(ortResult, licenseInfoResolver, resolutionProvider) {
    // Define a rule that is executed for each package.
    packageRule("UNHANDLED_LICENSE") {
        // Do not trigger this rule on packages that have been excluded in the .ort.yml.
        require {
            -isExcluded()
        }

        // Define a rule that is executed for each license of the package.
        licenseRule("UNHANDLED_LICENSE", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
            require {
                -isExcluded()
                -isHandled()
            }

            // Throw an error message including guidance how to fix the issue.
            error(
                "The license $license is currently not covered by policy rules. " +
                        "The license was ${licenseSource.name.lowercase()} in package " +
                        "${pkg.id.toCoordinates()}",
                howToFixDefault()
            )
        }
    }

    packageRule("UNMAPPED_DECLARED_LICENSE") {
        require {
            -isExcluded()
        }

        resolvedLicenseInfo.licenseInfo.declaredLicenseInfo.processed.unmapped.forEach { unmappedLicense ->
            warning(
                "The declared license '$unmappedLicense' could not be mapped to a valid license or parsed as an SPDX " +
                        "expression. The license was found in package ${pkg.id.toCoordinates()}.",
                howToFixDefault()
            )
        }
    }

    packageRule("COPYLEFT_IN_SOURCE") {
        require {
            -isExcluded()
        }

        licenseRule("COPYLEFT_IN_SOURCE", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
            require {
                -isExcluded()
                +isCopyleft()
            }

            val message = if (licenseSource == LicenseSource.DETECTED) {
                "The ScanCode copyleft categorized license $license was ${licenseSource.name.lowercase()} " +
                        "in package ${pkg.id.toCoordinates()}."
            } else {
                "The package ${pkg.id.toCoordinates()} has the ${licenseSource.name.lowercase()} ScanCode copyleft " +
                        "catalogized license $license."
            }

            error(message, howToFixDefault())
        }

        licenseRule("COPYLEFT_LIMITED_IN_SOURCE", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
            require {
                -isExcluded()
                +isCopyleftLimited()
            }

            val licenseSourceName = licenseSource.name.lowercase()
            val message = if (licenseSource == LicenseSource.DETECTED) {
                if (pkg.id.type == "Unmanaged") {
                    "The ScanCode copyleft-limited categorized license $license was $licenseSourceName in package " +
                            "${pkg.id.toCoordinates()}."
                } else {
                    "The ScanCode copyleft-limited categorized license $license was $licenseSourceName in package " +
                            "${pkg.id.toCoordinates()}."
                }
            } else {
                "The package ${pkg.id.toCoordinates()} has the $licenseSourceName ScanCode copyleft-limited " +
                        "categorized license $license."
            }

            error(message, howToFixDefault())
        }
    }

    packageRule("VULNERABILITY_IN_PACKAGE") {
        require {
            -isExcluded()
            +hasVulnerability()
        }

        issue(
            Severity.WARNING,
            "The package ${pkg.id.toCoordinates()} has a vulnerability",
            howToFixDefault()
        )
    }

    packageRule("HIGH_SEVERITY_VULNERABILITY_IN_PACKAGE") {
        val maxAcceptedSeverity = "5.0"
        val scoringSystem = "CVSS2"

        require {
            -isExcluded()
            +hasVulnerability(maxAcceptedSeverity, scoringSystem) { value, threshold ->
                value.toFloat() >= threshold.toFloat()
            }
        }

        issue(
            Severity.ERROR,
            "The package ${pkg.id.toCoordinates()} has a vulnerability with $scoringSystem severity > " +
                    "$maxAcceptedSeverity",
            howToFixDefault()
        )
    }

    // Define a rule that is executed for each dependency of a project.
    dependencyRule("COPYLEFT_IN_DEPENDENCY") {
        licenseRule("COPYLEFT_IN_DEPENDENCY", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
            require {
                +isCopyleft()
            }

            issue(
                Severity.ERROR,
                "The project ${project.id.toCoordinates()} has a dependency licensed under the ScanCode " +
                        "copyleft categorized license $license.",
                howToFixDefault()
            )
        }
    }

    dependencyRule("COPYLEFT_LIMITED_STATIC_LINK_IN_DIRECT_DEPENDENCY") {
        require {
            +isAtTreeLevel(0)
            +isStaticallyLinked()
        }

        licenseRule("LINKED_WEAK_COPYLEFT", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
            require {
                +isCopyleftLimited()
            }

            // Use issue() instead of error() if you want to set the severity.
            issue(
                Severity.WARNING,
                "The project ${project.id.toCoordinates()} has a statically linked direct dependency licensed " +
                        "under the ScanCode copyleft-left categorized license $license.",
                howToFixDefault()
            )
        }
    }

    ortResultRule("DEPRECATED_SCOPE_EXCLUDE_REASON_IN_ORT_YML") {
        val reasons = ortResult.repository.config.excludes.scopes.mapTo(mutableSetOf()) { it.reason }

        @Suppress("DEPRECATION")
        val deprecatedReasons = setOf(ScopeExcludeReason.TEST_TOOL_OF)

        reasons.intersect(deprecatedReasons).forEach { offendingReason ->
            warning(
                "The repository configuration is using the deprecated scope exclude reason '$offendingReason'.",
                "Please use only non-deprecated scope exclude reasons, see " +
                        "https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/" +
                        "kotlin/config/ScopeExcludeReason.kt."
            )
        }
    }
}

// Populate the list of policy rule violations to return.
ruleViolations += ruleSet.violations
