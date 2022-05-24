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
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.utils.ort.CopyrightStatementsProcessor
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
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
        val licenses = effectiveLicense(licenseView, licenseChoices)?.decompose().orEmpty()

        return this.copy(licenses = this.licenses.filter { it.license in licenses })
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
 * Resolved information for a single license.
 */
data class ResolvedLicense(
    /**
     * The license.
     */
    val license: SpdxSingleLicenseExpression,

    /**
     * The list of original declared license that were [processed][DeclaredLicenseProcessor] to this [license], or an
     * empty list, if this [license] was not modified during processing.
     */
    val originalDeclaredLicenses: Set<String>,

    /**
     * The original SPDX expressions of this license.
     */
    val originalExpressions: Set<ResolvedOriginalExpression>,

    /**
     * All text locations where this license was found.
     */
    val locations: Set<ResolvedLicenseLocation>
) {
    /**
     * The sources where this license was found.
     */
    val sources: Set<LicenseSource> = originalExpressions.mapTo(mutableSetOf()) { it.source }

    /**
     * True, if this license was [detected][LicenseSource.DETECTED] and all [locations] have matching path excludes.
     */
    val isDetectedExcluded by lazy {
        LicenseSource.DETECTED in sources && locations.all { it.matchingPathExcludes.isNotEmpty() }
    }

    init {
        require(sources.isNotEmpty()) {
            "A resolved license must have at least one license source."
        }
    }

    /**
     * Return all resolved copyrights for this license. Copyright findings that are excluded by [PathExclude]s are
     * [omitted][omitExcluded] by default. The copyrights are [processed][process] by default using the
     * [CopyrightStatementsProcessor].
     */
    fun getResolvedCopyrights(process: Boolean = true, omitExcluded: Boolean = true): List<ResolvedCopyright> {
        val resolvedCopyrightFindings = locations.flatMap { location ->
            location.copyrights.filter { copyright ->
                !omitExcluded || copyright.matchingPathExcludes.isEmpty()
            }
        }

        return resolvedCopyrightFindings.toResolvedCopyrights(process)
    }

    /**
     * Return all copyright statements associated to this license. Copyright findings that are excluded by
     * [PathExclude]s are [omitted][omitExcluded] by default. The copyrights are [processed][process] by default
     * using the [CopyrightStatementsProcessor].
     */
    @JvmOverloads
    fun getCopyrights(process: Boolean = true, omitExcluded: Boolean = true): Set<String> =
        getResolvedCopyrights(process, omitExcluded).mapTo(mutableSetOf()) { it.statement }

    /**
     * Filter all excluded copyrights. Copyrights which have
     * [matching path excludes][ResolvedCopyrightFinding.matchingPathExcludes] are removed.
     */
    fun filterExcludedCopyrights(): ResolvedLicense =
        copy(
            locations = locations.mapTo(mutableSetOf()) { location ->
                location.copy(
                    copyrights = location.copyrights.filterTo(mutableSetOf()) { it.matchingPathExcludes.isEmpty() }
                )
            }
        )

    /**
     * Filter all excluded original expressions. Detected license findings which have
     * [matching path excludes][ResolvedCopyrightFinding.matchingPathExcludes] are removed. If the resolved license
     * becomes empty, then null is returned.
     */
    fun filterExcludedOriginalExpressions(): ResolvedLicense? {
        if (LicenseSource.DETECTED !in sources) return this

        val filteredOriginalExpressions = originalExpressions.filterNotTo(mutableSetOf()) { it.isDetectedExcluded }
        if (filteredOriginalExpressions.isEmpty()) return null

        return copy(originalExpressions = filteredOriginalExpressions)
    }
}

/**
 * A resolved text location.
 */
data class ResolvedLicenseLocation(
    /**
     * The provenance of the file.
     */
    val provenance: Provenance,

    /**
     * The text location relative to the root of the VCS or source artifact defined by [provenance].
     */
    val location: TextLocation,

    /**
     * The applied [LicenseFindingCuration], or null if none were applied.
     */
    val appliedCuration: LicenseFindingCuration?,

    /**
     * All [PathExclude]s matching this [location].
     */
    val matchingPathExcludes: List<PathExclude>,

    /**
     * All copyright findings associated to this license location, excluding copyright garbage.
     */
    val copyrights: Set<ResolvedCopyrightFinding>
)

/**
 * A resolved copyright.
 */
data class ResolvedCopyright(
    /**
     * The resolved copyright statement.
     */
    val statement: String,

    /**
     * The resolved findings for this copyright. The statements in the findings can be different to [statement] if they
     * were processed by the [CopyrightStatementsProcessor].
     */
    val findings: Set<ResolvedCopyrightFinding>
)

/**
 * A resolved copyright finding.
 */
data class ResolvedCopyrightFinding(
    /**
     * The copyright statement.
     */
    val statement: String,

    /**
     * The location where this copyright was found.
     */
    val location: TextLocation,

    /**
     * All [PathExclude]s matching this [location].
     */
    val matchingPathExcludes: List<PathExclude>
)

/**
 * A resolved original expression.
 */
data class ResolvedOriginalExpression(
    /**
     * The license expression.
     */
    val expression: SpdxExpression,

    /**
     * The license source.
     */
    val source: LicenseSource,

    /**
     * Indicate whether all license findings corresponding to [expression] are excluded. Must be false if [source] does
     * not equal [LicenseSource.DETECTED].
     */
    val isDetectedExcluded: Boolean = false
) {
    init {
        require(!isDetectedExcluded || source == LicenseSource.DETECTED) {
            "For license source '$source' the flag isDetectedExcluded must not be set."
        }
    }
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

private fun Collection<ResolvedCopyrightFinding>.toResolvedCopyrights(process: Boolean): List<ResolvedCopyright> {
    val allStatements = map { it.statement }
    val processedStatements = if (process) {
        CopyrightStatementsProcessor().process(allStatements).toMap()
    } else {
        allStatements.associateBy({ it }, { mutableSetOf(it) })
    }

    return processedStatements.mapValues { (_, originalStatements) ->
        filter { it.statement in originalStatements }
    }.filterValues { it.isNotEmpty() }.entries.map { (statement, findings) ->
        ResolvedCopyright(statement, findings.toSet())
    }
}

private fun CopyrightStatementsProcessor.Result.toMap(): Map<String, Set<String>> =
    processedStatements + unprocessedStatements.associateWith { setOf(it) }
