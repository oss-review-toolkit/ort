/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.utils.ort.CopyrightStatementsProcessor
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

/**
 * Resolved information for a single license.
 */
data class ResolvedLicense(
    /**
     * The license.
     */
    val license: SpdxSingleLicenseExpression,

    /**
     * The set of original declared license that were [processed][DeclaredLicenseProcessor] to this [license], or an
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

internal fun Collection<ResolvedCopyrightFinding>.toResolvedCopyrights(process: Boolean): List<ResolvedCopyright> {
    val statementToFindings = groupBy { it.statement }

    if (!process) {
        return statementToFindings.map { (statement, findings) -> ResolvedCopyright(statement, findings.toSet()) }
    }

    val result = CopyrightStatementsProcessor.process(statementToFindings.keys)

    val processedCopyrights = result.processedStatements.map { (statement, originalStatements) ->
        val findings = originalStatements.flatMapTo(mutableSetOf()) { statementToFindings.getValue(it) }
        ResolvedCopyright(statement, findings)
    }

    val unprocessedCopyrights = result.unprocessedStatements.mapNotNull { statement ->
        val findings = filterTo(mutableSetOf()) { it.statement == statement }
        findings.takeUnless { it.isEmpty() }?.let { ResolvedCopyright(statement, it) }
    }

    return processedCopyrights + unprocessedCopyrights
}
