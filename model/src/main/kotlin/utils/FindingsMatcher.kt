/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.model.utils

import java.util.PriorityQueue

import kotlin.math.max
import kotlin.math.min

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.TextLocation

/**
 * A class for matching copyright findings to license findings. Copyright statements may be matched either to license
 * findings located nearby in the same file or to a license found in a license file whereas the given
 * [rootLicenseMatcher] determines whether a file is a license file.
 */
class FindingsMatcher(
    private val rootLicenseMatcher: RootLicenseMatcher = RootLicenseMatcher(),
    private val toleranceLines: Int = DEFAULT_TOLERANCE_LINES,
    private val expandToleranceLines: Int = DEFAULT_EXPAND_TOLERANCE_LINES
) {
    companion object {
        /**
         * The default value of 5 seems to be a good balance between associating findings separated by blank lines but
         * not skipping complete license statements.
         */
        const val DEFAULT_TOLERANCE_LINES = 5

        /**
         * The default value of 2 seems to be a good balance between associating findings separated by blank lines but
         * not skipping complete license statements.
         */
        const val DEFAULT_EXPAND_TOLERANCE_LINES = 2
    }

    /**
     * Return the line range in which copyright statements should be matched against the license finding at the
     * location given by [licenseStartLine] and [licenseEndLine]. The given [copyrightLines] must contain exactly all
     * lines of all copyright statements present in the file where the given license location points to.
     */
    private fun getMatchingRange(
        licenseStartLine: Int,
        licenseEndLine: Int,
        copyrightLines: Collection<Int>
    ): IntRange {
        val range = max(0, licenseStartLine - toleranceLines) until
                max(licenseStartLine + toleranceLines, licenseEndLine) + 1

        var expandedStartLine = copyrightLines.filter { it in range }.minOrNull() ?: return range
        val queue = PriorityQueue<Int>(copyrightLines.size, compareByDescending { it })
        queue += copyrightLines.filter { it in 0 until expandedStartLine }

        while (queue.isNotEmpty()) {
            val line = queue.poll()
            if (expandedStartLine - line > expandToleranceLines) break

            expandedStartLine = line
        }

        return min(range.first, expandedStartLine) until range.last + 1
    }

    /**
     * Return those statements in [copyrights] which match the license location given by [licenseStartLine] and
     * [licenseEndLine]. That matching is configured by [toleranceLines] and [expandToleranceLines].
     */
    private fun getClosestCopyrightStatements(
        copyrights: List<CopyrightFinding>,
        licenseStartLine: Int,
        licenseEndLine: Int
    ): Set<CopyrightFinding> {
        require(copyrights.mapTo(mutableSetOf()) { it.location.path }.size <= 1) {
            "Given copyright statements must all point to the same file."
        }

        val lineRange = getMatchingRange(licenseStartLine, licenseEndLine, copyrights.map { it.location.startLine })

        return copyrights.filterTo(mutableSetOf()) { it.location.startLine in lineRange }
    }

    /**
     * Associate copyright findings to license findings within a single file.
     */
    private fun matchFileFindings(
        licenses: List<LicenseFinding>,
        copyrights: List<CopyrightFinding>
    ): Map<LicenseFinding, Set<CopyrightFinding>> {
        require((licenses.map { it.location.path } + copyrights.map { it.location.path }).distinct().size <= 1) {
            "The given license and copyright findings must all point to the same file."
        }

        // If there is only a single license finding, associate all copyright findings with that license. If there is
        // no license return no matches.
        if (licenses.size <= 1) return licenses.associateWith { copyrights.toSet() }

        // If there are multiple license findings in a single file, search for the closest copyright statements
        // for each of these, if any.
        return licenses.associateWith { licenseFinding ->
            getClosestCopyrightStatements(
                copyrights,
                licenseFinding.location.startLine,
                licenseFinding.location.endLine
            )
        }
    }

    /**
     * Associate the [copyrightFindings] to the [licenseFindings]. Copyright findings are matched to license findings
     * located nearby in the same file. Copyright findings that are not located close to a license finding are
     * associated to the root licenses instead. The root licenses are the licenses found in any of the license files
     * defined by [rootLicenseMatcher].
     */
    fun match(licenseFindings: Set<LicenseFinding>, copyrightFindings: Set<CopyrightFinding>): FindingsMatcherResult {
        val licenseFindingsByPath = licenseFindings.groupBy { it.location.path }
        val copyrightFindingsByPath = copyrightFindings.groupBy { it.location.path }
        val paths = (licenseFindingsByPath.keys + copyrightFindingsByPath.keys).toSet()

        val matchedFindings = mutableMapOf<LicenseFinding, MutableSet<CopyrightFinding>>()
        val unmatchedCopyrights = mutableSetOf<CopyrightFinding>()

        paths.forEach { path ->
            val licenses = licenseFindingsByPath[path].orEmpty()
            val copyrights = copyrightFindingsByPath[path].orEmpty()
            val matchedFileFindings = matchFileFindings(licenses, copyrights)

            matchedFindings.merge(matchedFileFindings)
            unmatchedCopyrights += copyrights.toSet() - matchedFileFindings.values.flatten()
        }

        val matchedRootLicenseFindings = matchWithRootLicenses(licenseFindings, unmatchedCopyrights)

        matchedFindings.merge(matchedRootLicenseFindings)
        unmatchedCopyrights -= matchedRootLicenseFindings.values.flatten()

        return FindingsMatcherResult(matchedFindings, unmatchedCopyrights)
    }

    /**
     * Associate the given [copyrightFindings] to its corresponding applicable root licenses. If no root license is
     * applicable to a given copyright finding, that copyright finding is not contained in the result.
     */
    private fun matchWithRootLicenses(
        licenseFindings: Set<LicenseFinding>,
        copyrightFindings: Set<CopyrightFinding>
    ): Map<LicenseFinding, Set<CopyrightFinding>> {
        val rootLicensesForDirectories = rootLicenseMatcher.getApplicableRootLicenseFindingsForDirectories(
            licenseFindings = licenseFindings,
            directories = copyrightFindings.map { it.location.directory() }
        )

        val result = mutableMapOf<LicenseFinding, MutableSet<CopyrightFinding>>()

        copyrightFindings.forEach { copyrightFinding ->
            rootLicensesForDirectories[copyrightFinding.location.directory()]?.forEach { rootLicenseFinding ->
                result.getOrPut(rootLicenseFinding) { mutableSetOf() } += copyrightFinding
            }
        }

        return result
    }
}

/**
 * The result of the [FindingsMatcher].
 */
data class FindingsMatcherResult(
    /**
     * A map of [LicenseFinding]s mapped to their matched [CopyrightFinding]s.
     */
    val matchedFindings: Map<LicenseFinding, Set<CopyrightFinding>>,

    /**
     * All [CopyrightFinding]s that could not be matched to a [LicenseFinding].
     */
    val unmatchedCopyrights: Set<CopyrightFinding>
)

private fun TextLocation.directory(): String = path.substringBeforeLast(delimiter = "/", missingDelimiterValue = "")

private fun MutableMap<LicenseFinding, MutableSet<CopyrightFinding>>.merge(
    other: Map<LicenseFinding, Collection<CopyrightFinding>>
) {
    other.forEach { (licenseFinding, copyrightFindings) ->
        getOrPut(licenseFinding) { mutableSetOf() } += copyrightFindings
    }
}
