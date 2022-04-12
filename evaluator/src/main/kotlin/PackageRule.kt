/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.evaluator

import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCurationResult
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedLicense
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseReferenceExpression

/**
 * A [Rule] to check a single [Package].
 */
open class PackageRule(
    ruleSet: RuleSet,
    name: String,

    /**
     * The [Package] to check.
     */
    val pkg: Package,

    /**
     * The list of curations applied to the [package][pkg].
     */
    val curations: List<PackageCurationResult>,

    /**
     * The resolved license info for the [Package].
     */
    val resolvedLicenseInfo: ResolvedLicenseInfo
) : Rule(ruleSet, name) {
    private val licenseRules = mutableListOf<LicenseRule>()

    @Suppress("UNUSED") // This is intended to be used by rule implementations.
    val uncuratedPkg by lazy { CuratedPackage(pkg, curations).toUncuratedPackage() }

    override val description = "Evaluating rule '$name' for package '${pkg.id.toCoordinates()}'."

    override fun issueSource() = "$name - ${pkg.id.toCoordinates()}"

    override fun runInternal() {
        licenseRules.forEach { it.evaluate() }
    }

    /**
     * A [RuleMatcher] that checks whether any vulnerability was found for the [package][pkg].
     */
    fun hasVulnerability(): RuleMatcher {
        return object : RuleMatcher {
            override val description = "hasVulnerability()"

            override fun matches(): Boolean {
                val run = ruleSet.ortResult.advisor ?: return false
                return run.results.getVulnerabilities(pkg.id).isNotEmpty()
            }
        }
    }

    /**
     * A [RuleMatcher] that checks whether any vulnerability for the [package][pkg] has a score that equals or is
     * greater than [threshold] according to the [scoringSystem] and the belonging [severityComparator].
     */
    fun hasVulnerability(threshold: String, scoringSystem: String, severityComparator: (String, String) -> Boolean) =
        object : RuleMatcher {
            override val description = "hasVulnerability($threshold, $scoringSystem)"

            override fun matches(): Boolean {
                val run = ruleSet.ortResult.advisor ?: return false
                return run.results.getVulnerabilities(pkg.id)
                    .flatMap { it.references }
                    .filter { reference -> reference.scoringSystem == scoringSystem }
                    .mapNotNull { reference -> reference.severity }
                    .any { severityComparator(it, threshold) }
            }
        }

    /**
     * A [RuleMatcher] that checks if the [package][pkg] has any concluded, declared, or detected license.
     */
    fun hasLicense() =
        object : RuleMatcher {
            override val description = "hasLicense()"

            override fun matches() = resolvedLicenseInfo.licenses.any { it.license.isPresent() }
        }

    /**
     * A [RuleMatcher] that checks if the [package][pkg] is [excluded][Excludes].
     */
    fun isExcluded() =
        object : RuleMatcher {
            override val description = "isExcluded()"

            override fun matches() = ruleSet.ortResult.isExcluded(pkg.id)
        }

    /**
     * A [RuleMatcher] that checks if the [identifier][Package.id] of the [package][pkg] belongs to one of the provided
     * [orgs][Identifier.isFromOrg].
     */
    fun isFromOrg(vararg names: String) =
        object : RuleMatcher {
            override val description = "isFromOrg(${names.joinToString()})"

            override fun matches() = pkg.id.isFromOrg(*names)
        }

    /**
     * A [RuleMatcher] that checks whether the [package][pkg] is metadata only.
     */
    fun isMetaDataOnly() =
        object : RuleMatcher {
            override val description = "isMetaDataOnly()"

            override fun matches() = pkg.isMetaDataOnly
        }

    /**
     * A [RuleMatcher] that checks if the [package][pkg] was created from a [Project].
     */
    fun isProject() =
        object : RuleMatcher {
            override val description = "isProject()"

            override fun matches() = ruleSet.ortResult.isProject(pkg.id)
        }

    /**
     * A [RuleMatcher] that checks if the [identifier type][Identifier.type] of the [package][pkg] equals [type].
     */
    fun isType(type: String) =
        object : RuleMatcher {
            override val description = "isType($type)"

            override fun matches() = pkg.id.type == type
        }

    /**
     * A DSL function to configure a [LicenseRule] and add it to this rule.
     */
    fun licenseRule(name: String, licenseView: LicenseView, block: LicenseRule.() -> Unit) {
        resolvedLicenseInfo.filter(licenseView, filterSources = true)
            .applyChoices(ruleSet.ortResult.getPackageLicenseChoices(pkg.id), licenseView)
            .applyChoices(ruleSet.ortResult.getRepositoryLicenseChoices(), licenseView).forEach { resolvedLicense ->
                resolvedLicense.sources.forEach { licenseSource ->
                    licenseRules += LicenseRule(name, resolvedLicense, licenseSource).apply(block)
                }
            }
    }

    fun issue(severity: Severity, message: String, howToFix: String) =
        issue(severity, pkg.id, null, null, message, howToFix)

    /**
     * Add a [hint][Severity.HINT] to the list of [violations].
     */
    fun hint(message: String, howToFix: String) = hint(pkg.id, null, null, message, howToFix)

    /**
     * Add a [warning][Severity.WARNING] to the list of [violations].
     */
    fun warning(message: String, howToFix: String) = warning(pkg.id, null, null, message, howToFix)

    /**
     * Add an [error][Severity.ERROR] to the list of [violations].
     */
    fun error(message: String, howToFix: String) = error(pkg.id, null, null, message, howToFix)

    /**
     * A [Rule] to check a single license of the [package][pkg].
     */
    inner class LicenseRule(
        name: String,

        /**
         * The [ResolvedLicense].
         */
        val resolvedLicense: ResolvedLicense,

        /**
         * The source of the license.
         */
        val licenseSource: LicenseSource
    ) : Rule(ruleSet, name) {
        /**
         * A shortcut for the [license][ResolvedLicense.license] in [resolvedLicense].
         */
        val license = resolvedLicense.license

        /**
         * A helper function to access [PackageRule.pkg] in extension functions for [LicenseRule], required because the
         * properties of the outer class [PackageRule] cannot be accessed from an extension function.
         */
        fun pkg() = pkg

        override val description = "\tEvaluating license rule '$name' for $licenseSource license " +
                "'${resolvedLicense.license}'."

        override fun issueSource() = "$name - ${pkg.id.toCoordinates()} - ${resolvedLicense.license} ($licenseSource)"

        /**
         * A [RuleMatcher] that checks if a [detected][LicenseSource.DETECTED] license is
         * [excluded][ResolvedLicense.isDetectedExcluded].
         */
        fun isExcluded() =
            object : RuleMatcher {
                override val description = "isDetectedExcluded($license)"

                override fun matches() = licenseSource == LicenseSource.DETECTED && resolvedLicense.isDetectedExcluded
            }

        /**
         * A [RuleMatcher] that checks if the [license] is a valid SPDX license.
         */
        fun isSpdxLicense() =
            object : RuleMatcher {
                override val description = "isSpdxLicense($license)"

                override fun matches() =
                    when (license) {
                        !is SpdxLicenseReferenceExpression ->
                            license.isValid(SpdxExpression.Strictness.ALLOW_DEPRECATED)
                        else -> false
                    }
            }

        fun issue(severity: Severity, message: String, howToFix: String) =
            issue(severity, pkg.id, license, licenseSource, message, howToFix)

        /**
         * Add a [hint][Severity.HINT] to the list of [violations].
         */
        fun hint(message: String, howToFix: String) = hint(pkg.id, license, licenseSource, message, howToFix)

        /**
         * Add a [warning][Severity.WARNING] to the list of [violations].
         */
        fun warning(message: String, howToFix: String) = warning(pkg.id, license, licenseSource, message, howToFix)

        /**
         * Add an [error][Severity.ERROR] to the list of [violations].
         */
        fun error(message: String, howToFix: String) = error(pkg.id, license, licenseSource, message, howToFix)
    }
}
