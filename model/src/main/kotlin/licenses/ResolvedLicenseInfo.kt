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

package org.ossreviewtoolkit.model.licenses

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.utils.PathLicenseMatcher
import org.ossreviewtoolkit.utils.ort.CopyrightStatementsProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.SpdxOperator
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.spdx.toExpression

/**
 * Resolved license information about a package (or project).
 */
data class ResolvedLicenseInfo(
    /**
     * The identifier of the package (or project).
     */
    val id: Identifier,

    /**
     * The unresolved license info.
     */
    val licenseInfo: LicenseInfo,

    /**
     * The list of [ResolvedLicense]s for this package (or project).
     */
    val licenses: List<ResolvedLicense>,

    /**
     * All copyright findings with statements that are contained in [CopyrightGarbage], mapped to the [Provenance] where
     * they were detected.
     */
    val copyrightGarbage: Map<Provenance, Set<CopyrightFinding>>,

    /**
     * All copyright findings that could not be matched to a license finding, mapped to the [Provenance] where they were
     * detected.
     */
    val unmatchedCopyrights: Map<Provenance, Set<ResolvedCopyrightFinding>>
) : Iterable<ResolvedLicense> by licenses {
    operator fun get(license: SpdxSingleLicenseExpression): ResolvedLicense? = find { it.license == license }

    /**
     * Map all original resolved license expressions to a single expression with top-level [operator]s, or return null
     * if there are no licenses.
     */
    fun toExpression(operator: SpdxOperator = SpdxOperator.AND): SpdxExpression? =
        licenses.flatMapTo(mutableSetOf()) { resolvedLicense ->
            resolvedLicense.originalExpressions.map { it.expression }
        }.toExpression(operator)

    /**
     * Return the main license of a package (or project) as an [SpdxExpression], or null if there is no main license.
     * The main license is the conjunction of the declared license of the package (or project) and the licenses detected
     * in any of the configured [LicenseFilePatterns] matched against the root path of the package (or project).
     */
    fun mainLicense(): SpdxExpression? {
        val matcher = PathLicenseMatcher(LicenseFilePatterns.getInstance())
        val licensePaths = flatMap { resolvedLicense ->
            resolvedLicense.locations.map { it.location.path }
        }

        val applicablePathsCache = mutableMapOf<String, Map<String, Set<String>>>()
        val detectedLicenses = filterTo(mutableSetOf()) { resolvedLicense ->
            resolvedLicense.locations.any {
                val rootPath = (it.provenance as? RepositoryProvenance)?.vcsInfo?.path.orEmpty()

                val applicableLicensePaths = applicablePathsCache.getOrPut(rootPath) {
                    matcher.getApplicableLicenseFilesForDirectories(
                        licensePaths,
                        listOf(rootPath)
                    )
                }

                val applicableLicenseFiles = applicableLicensePaths[rootPath].orEmpty()

                it.location.path in applicableLicenseFiles
            }
        }

        val declaredLicenses = filter(LicenseView.ONLY_DECLARED)
        val mainLicenses = (detectedLicenses + declaredLicenses.licenses)
            .flatMap { it.originalExpressions }
            .map { it.expression }

        return mainLicenses.toExpression()
    }

    /**
     * Return the effective [SpdxExpression] of this [ResolvedLicenseInfo] based on their [licenses] filtered by the
     * [licenseView] and the applied [licenseChoices]. Effective, in this context, refers to an [SpdxExpression] that
     * can be used as a final license of this [ResolvedLicenseInfo]. [licenseChoices] will be applied in the order they
     * are given to the function.
     */
    fun effectiveLicense(licenseView: LicenseView, vararg licenseChoices: List<SpdxLicenseChoice>): SpdxExpression? {
        val resolvedLicenseInfo = filter(licenseView, filterSources = true)

        val resolvedLicenses = resolvedLicenseInfo.toExpression()

        val choices = licenseChoices.asList().flatten()

        return if (choices.isEmpty()) {
            resolvedLicenses
        } else {
            resolvedLicenses?.applyChoices(choices)?.validChoices()?.toExpression(SpdxOperator.OR)
        }
    }

    /**
     * Return all copyright statements associated to this license info. Copyright findings that are excluded by
     * [PathExclude]s are [omitted][omitExcluded] by default. The copyrights are [processed][process] by default
     * using the [CopyrightStatementsProcessor].
     */
    @JvmOverloads
    fun getCopyrights(process: Boolean = true, omitExcluded: Boolean = true): Set<String> {
        val copyrightStatements = licenses.flatMapTo(mutableSetOf()) { license ->
            license.getCopyrights(process = false, omitExcluded = omitExcluded)
        }

        return copyrightStatements.takeIf { !process }
            ?: CopyrightStatementsProcessor.process(copyrightStatements).allStatements
    }

    /**
     * Call [LicenseView.filter] on this [ResolvedLicenseInfo].
     */
    @JvmOverloads
    fun filter(licenseView: LicenseView, filterSources: Boolean = false) = licenseView.filter(this, filterSources)

    /**
     * Filter all licenses that have a location matching [provenance] and [path].
     */
    fun filter(provenance: Provenance, path: String): List<ResolvedLicense> =
        filter { resolvedLicense ->
            resolvedLicense.locations.any {
                it.provenance == provenance && it.location.path == path
            }
        }

    /**
     * Apply [licenseChoices] on the effective license of the [licenseView].
     */
    fun applyChoices(
        licenseChoices: List<SpdxLicenseChoice>,
        licenseView: LicenseView = LicenseView.ALL
    ): ResolvedLicenseInfo {
        val effectiveLicenses = effectiveLicense(licenseView, licenseChoices)?.decompose().orEmpty()

        return copy(licenses = licenses.filter { it.license in effectiveLicenses })
    }

    /**
     * Filter all excluded licenses and copyrights. Licenses are removed if they are only
     * [detected][LicenseSource.DETECTED] and all [locations][ResolvedLicense.locations] have
     * [matching path excludes][ResolvedLicenseLocation.matchingPathExcludes]. Copyrights are removed if all
     * [findings][ResolvedCopyright.findings] have
     * [matching path excludes][ResolvedCopyrightFinding.matchingPathExcludes].
     */
    fun filterExcluded(): ResolvedLicenseInfo = copy(licenses = licenses.filterExcluded())
}

/**
 * Filter all excluded licenses and copyrights. Licenses are removed if they are only
 * [detected][LicenseSource.DETECTED] and all [locations][ResolvedLicense.locations] have
 * [matching path excludes][ResolvedLicenseLocation.matchingPathExcludes]. Copyrights are removed if all
 * [findings][ResolvedCopyright.findings] have
 * [matching path excludes][ResolvedCopyrightFinding.matchingPathExcludes]. Original expressions are removed if all
 * corresponding license findings have [matching path excludes][ResolvedLicenseLocation.matchingPathExcludes].
 */
fun List<ResolvedLicense>.filterExcluded() =
    mapNotNull { it.filterExcludedOriginalExpressions() }.filter { resolvedLicense ->
        resolvedLicense.sources != setOf(LicenseSource.DETECTED) ||
            resolvedLicense.locations.any { it.matchingPathExcludes.isEmpty() }
    }.map { it.filterExcludedCopyrights() }
