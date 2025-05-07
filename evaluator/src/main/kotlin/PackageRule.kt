/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedLicense
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.vulnerabilities.Cvss2Rating
import org.ossreviewtoolkit.model.vulnerabilities.Cvss3Rating
import org.ossreviewtoolkit.model.vulnerabilities.Cvss4Rating
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseReferenceExpression

/**
 * A [Rule] to check a single [Package].
 */
open class PackageRule(
    ruleSet: RuleSet,
    name: String,

    /**
     * The [CuratedPackage] to check.
     */
    val pkg: CuratedPackage,

    /**
     * The resolved license info for the [Package].
     */
    val resolvedLicenseInfo: ResolvedLicenseInfo
) : Rule(ruleSet, name) {
    private val licenseRules = mutableListOf<LicenseRule>()

    @Suppress("unused") // This is intended to be used by rule implementations.
    val uncuratedPkg: Package by lazy {
        @Suppress("UnsafeCallOnNullableType")
        ruleSet.ortResult.getUncuratedPackageOrProject(pkg.metadata.id)!!
    }

    override val description = "Evaluating rule '$name' for package '${pkg.metadata.id.toCoordinates()}'."

    override fun issueSource() = "$name - ${pkg.metadata.id.toCoordinates()}"

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
                return run.getVulnerabilities(pkg.metadata.id).isNotEmpty()
            }
        }
    }

    /**
     * A [RuleMatcher] that checks whether any vulnerability for the [package][pkg] has a
     * [reference][Vulnerability.references] with a [score][VulnerabilityReference.score] that equals or is
     * greater than [scoreThreshold] according to the [scoringSystem]. If the reference provides no score but a
     * [severity][VulnerabilityReference.severity], the threshold is mapped to a qualitative rating for comparison.
     */
    fun hasVulnerability(scoreThreshold: Float, scoringSystem: String) =
        object : RuleMatcher {
            override val description = "hasVulnerability($scoreThreshold, $scoringSystem)"

            override fun matches(): Boolean {
                val run = ruleSet.ortResult.advisor ?: return false
                val matchingSystems = run.getVulnerabilities(pkg.metadata.id).asSequence()
                    .filter { !ruleSet.resolutionProvider.isResolved(it) }
                    .flatMap { it.references }
                    .filter { it.scoringSystem == scoringSystem }

                val scores = matchingSystems.mapNotNull { it.score }
                if (scores.any()) return scores.any { it >= scoreThreshold }

                // Fall back to a more coarse comparison of qualitative severity ratings if no scores are available.
                val severityThreshold = VulnerabilityReference.getQualitativeRating(scoringSystem, scoreThreshold)
                    ?: return false

                val severities = matchingSystems
                    .mapNotNull { it.severity }
                    .mapNotNull { severity ->
                        val system = scoringSystem.uppercase()
                        when {
                            Cvss2Rating.PREFIXES.any { system.startsWith(it) } -> enumValueOf<Cvss2Rating>(severity)
                            Cvss3Rating.PREFIXES.any { system.startsWith(it) } -> enumValueOf<Cvss3Rating>(severity)
                            Cvss4Rating.PREFIXES.any { system.startsWith(it) } -> enumValueOf<Cvss4Rating>(severity)
                            else -> null
                        }
                    }

                return severities.any { it.ordinal >= severityThreshold.ordinal }
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
     * A [RuleMatcher] that checks if the [package][pkg] has any concluded license.
     */
    fun hasConcludedLicense() =
        object : RuleMatcher {
            override val description = "hasConcludedLicense()"

            override fun matches(): Boolean {
                val concludedLicense = resolvedLicenseInfo.licenseInfo.concludedLicenseInfo.concludedLicense
                return concludedLicense != null && concludedLicense.toString() != SpdxConstants.NOASSERTION
            }
        }

    /**
     * A [RuleMatcher] that checks if the [package][pkg] is [excluded][Excludes].
     */
    fun isExcluded() =
        object : RuleMatcher {
            override val description = "isExcluded()"

            override fun matches() = ruleSet.ortResult.isExcluded(pkg.metadata.id)
        }

    /**
     * A [RuleMatcher] that checks if the [identifier][Package.id] of the [package][pkg] belongs to one of the provided
     * organization [names][Identifier.isFromOrg].
     */
    fun isFromOrg(vararg names: String) =
        object : RuleMatcher {
            override val description = "isFromOrg(${names.joinToString()})"

            override fun matches() = pkg.metadata.id.isFromOrg(*names)
        }

    /**
     * A [RuleMatcher] that checks whether the [package][pkg] is metadata only.
     */
    fun isMetadataOnly() =
        object : RuleMatcher {
            override val description = "isMetadataOnly()"

            override fun matches() = pkg.metadata.isMetadataOnly
        }

    /**
     * A [RuleMatcher] that checks if the [package][pkg] was created from a [Project].
     */
    fun isProject() =
        object : RuleMatcher {
            override val description = "isProject()"

            override fun matches() = ruleSet.ortResult.isProject(pkg.metadata.id)
        }

    /**
     * A [RuleMatcher] that checks if the [identifier type][Identifier.type] of the [package][pkg] equals [type].
     */
    fun isType(type: String) =
        object : RuleMatcher {
            override val description = "isType($type)"

            override fun matches() = pkg.metadata.id.type == type
        }

    /**
     * A DSL function to configure a [LicenseRule] and add it to this rule.
     */
    fun licenseRule(name: String, licenseView: LicenseView, block: LicenseRule.() -> Unit) {
        resolvedLicenseInfo.filter(licenseView, filterSources = true)
            .applyChoices(ruleSet.ortResult.getPackageLicenseChoices(pkg.metadata.id), licenseView)
            .applyChoices(ruleSet.ortResult.getRepositoryLicenseChoices(), licenseView).forEach { resolvedLicense ->
                resolvedLicense.sources.forEach { licenseSource ->
                    licenseRules += LicenseRule(name, resolvedLicense, licenseSource).apply(block)
                }
            }
    }

    fun issue(severity: Severity, message: String, howToFix: String) =
        issue(severity, pkg.metadata.id, null, null, message, howToFix)

    /**
     * Add a [hint][Severity.HINT] to the list of [violations].
     */
    fun hint(message: String, howToFix: String) = hint(pkg.metadata.id, null, null, message, howToFix)

    /**
     * Add a [warning][Severity.WARNING] to the list of [violations].
     */
    fun warning(message: String, howToFix: String) = warning(pkg.metadata.id, null, null, message, howToFix)

    /**
     * Add an [error][Severity.ERROR] to the list of [violations].
     */
    fun error(message: String, howToFix: String) = error(pkg.metadata.id, null, null, message, howToFix)

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

        override fun issueSource() =
            "$name - ${pkg.metadata.id.toCoordinates()} - ${resolvedLicense.license} ($licenseSource)"

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
            issue(severity, pkg.metadata.id, license, licenseSource, message, howToFix)

        /**
         * Add a [hint][Severity.HINT] to the list of [violations].
         */
        fun hint(message: String, howToFix: String) = hint(pkg.metadata.id, license, licenseSource, message, howToFix)

        /**
         * Add a [warning][Severity.WARNING] to the list of [violations].
         */
        fun warning(message: String, howToFix: String) =
            warning(pkg.metadata.id, license, licenseSource, message, howToFix)

        /**
         * Add an [error][Severity.ERROR] to the list of [violations].
         */
        fun error(message: String, howToFix: String) = error(pkg.metadata.id, license, licenseSource, message, howToFix)
    }
}
