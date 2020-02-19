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

package com.here.ort.model.utils

import com.here.ort.model.CopyrightFinding
import com.here.ort.model.CopyrightFindings
import com.here.ort.model.LicenseFinding
import com.here.ort.model.LicenseFindings
import com.here.ort.model.TextLocation
import com.here.ort.utils.FileMatcher

import java.util.PriorityQueue

import kotlin.math.max
import kotlin.math.min

private fun Collection<CopyrightFinding>.toCopyrightFindings(): List<CopyrightFindings> {
    val locationsByStatement = mutableMapOf<String, MutableSet<TextLocation>>()

    forEach { copyrightFinding ->
        locationsByStatement.getOrPut(copyrightFinding.statement) { mutableSetOf() } += copyrightFinding.location
    }

    return locationsByStatement.map { (statement, locations) ->
        CopyrightFindings(statement, locations.toSortedSet())
    }
}

private val CopyrightFinding.line get(): Int = location.startLine

/**
 * A class for matching copyright findings to license findings. Copyright statements may be matched either to license
 * findings located nearby in the same file or to a license found in a license file whereas the given
 * [licenseFileMatcher] determines whether a file is a license file.
 */
class FindingsMatcher(
    private val licenseFileMatcher: FileMatcher = FileMatcher.LICENSE_FILE_MATCHER,
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
     * Get the licenses found in all commonly named license files, if any, or an empty list otherwise.
     */
    private fun getRootLicenses(licenseFindings: Collection<LicenseFinding>): List<String> =
        licenseFindings
            .filter { licenseFileMatcher.matches(it.location.path) }
            .map { it.license }
            .distinct()

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

        var expandedStartLine = copyrightLines.filter { it in range }.min() ?: return range
        val queue = PriorityQueue<Int>(copyrightLines.size, compareByDescending { it })
        queue.addAll(copyrightLines.filter { it in 0 until expandedStartLine })

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
        require(copyrights.map { it.location.path }.distinct().size <= 1) {
            "Given copyright statements must all point to the same file."
        }

        val lineRange = getMatchingRange(licenseStartLine, licenseEndLine, copyrights.map { it.line })

        return copyrights.filterTo(mutableSetOf()) { it.line in lineRange }
    }

    /**
     * Associate copyright findings to license findings within a single file.
     */
    private fun matchFileFindings(
        licenses: List<LicenseFinding>,
        copyrights: List<CopyrightFinding>
    ): Map<String, Set<CopyrightFinding>> {
        require((licenses.map { it.location.path } + copyrights.map { it.location.path }).distinct().size <= 1) {
            "The given license and copyright findings must all point to the same file."
        }

        // If there is only a single license finding, associate all copyright findings with that license. If there is
        // no license return no matches.
        if (licenses.size <= 1) return licenses.associateBy({ it.license }, { copyrights.toSet() })

        // If there are multiple license findings in a single file, search for the closest copyright statements
        // for each of these, if any.
        val result = mutableMapOf<String, MutableSet<CopyrightFinding>>()
        licenses.forEach { (license, location) ->
            val closestCopyrights = getClosestCopyrightStatements(copyrights, location.startLine, location.endLine)
            result.getOrPut(license) { mutableSetOf() } += closestCopyrights
        }

        return result
    }

    /**
     * Return an association of the given [copyrightFindings] to [licenseFindings].
     * Copyright findings are either matched to a license finding located nearby in the same file or to a license
     * finding pointing to a license file. Whether a file is a license file is determined by the
     * [FileMatcher] passed to the constructor. All [CopyrightFindings]s which cannot be matched are not present
     * in the result while all given [licenseFindings] are contained in the result exactly once.
     */
    fun match(licenseFindings: Collection<LicenseFinding>, copyrightFindings: Collection<CopyrightFinding>):
            Set<LicenseFindings> {
        val licenseFindingsByPath = licenseFindings.groupBy { it.location.path }
        val copyrightFindingsByPath = copyrightFindings.groupBy { it.location.path }
        val paths = (licenseFindingsByPath.keys + copyrightFindingsByPath.keys).toSet()
        val rootLicenses = getRootLicenses(licenseFindings)

        val locationsByLicense = licenseFindings
            .groupBy({ it.license }, { it.location })
            .mapValuesTo(mutableMapOf()) { it.value.toSortedSet() }

        val copyrightsByLicense = mutableMapOf<String, MutableSet<CopyrightFinding>>()

        paths.forEach { path ->
            val licenses = licenseFindingsByPath[path].orEmpty()
            val copyrights = copyrightFindingsByPath[path].orEmpty()
            val matchedFindings = matchFileFindings(licenses, copyrights)

            matchedFindings.forEach { (license, copyrightFindings) ->
                copyrightsByLicense.getOrPut(license) { mutableSetOf() } += copyrightFindings
            }

            // Associate all unmatched copyright findings with all root licenses.
            val unmatchedCopyrights = copyrights.toSet() - matchedFindings.values.flatten()
            rootLicenses.forEach { license ->
                copyrightsByLicense.getOrPut(license) { mutableSetOf() } += unmatchedCopyrights
            }
        }

        return (copyrightsByLicense.keys + locationsByLicense.keys).mapTo(mutableSetOf()) { license ->
            LicenseFindings(
                license,
                locationsByLicense[license] ?: sortedSetOf(),
                copyrightsByLicense[license].orEmpty().toCopyrightFindings().toSortedSet()
            )
        }
    }
}
