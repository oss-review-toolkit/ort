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

package org.ossreviewtoolkit.model.licenses

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.utils.ort.CopyrightStatementsProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice

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
    val unmatchedCopyrights: Map<Provenance, Set<CopyrightFinding>>
) : Iterable<ResolvedLicense> by licenses {
    operator fun get(license: SpdxSingleLicenseExpression): ResolvedLicense? = find { it.license == license }

    /**
     * Return the effective [SpdxExpression] of this [ResolvedLicenseInfo] based on their [licenses] filtered by the
     * [licenseView] and the applied [licenseChoices]. Effective, in this context, refers to an [SpdxExpression] that
     * can be used as a final license of this [ResolvedLicenseInfo]. [licenseChoices] will be applied in the order they
     * are given to the function.
     */
    fun effectiveLicense(licenseView: LicenseView, vararg licenseChoices: List<SpdxLicenseChoice>): SpdxExpression? {
        val resolvedLicenseInfo = filter(licenseView, filterSources = true)

        return resolvedLicenseInfo.licenses.flatMap { resolvedLicense ->
            resolvedLicense.originalExpressions.map { it.expression }
        }.toSet()
            .reduceOrNull(SpdxExpression::and)
            ?.applyChoices(licenseChoices.asList().flatten())
            ?.validChoices()
            ?.reduceOrNull(SpdxExpression::or)
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
            ?: CopyrightStatementsProcessor().process(copyrightStatements).getAllStatements()
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
