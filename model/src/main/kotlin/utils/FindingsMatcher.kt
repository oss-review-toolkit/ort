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

package org.ossreviewtoolkit.model.utils

import java.util.PriorityQueue

import kotlin.math.max
import kotlin.math.min

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.spdx.SpdxCompoundExpression
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseException
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseIdExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseWithExceptionExpression
import org.ossreviewtoolkit.utils.spdx.SpdxOperator
import org.ossreviewtoolkit.utils.spdx.SpdxSimpleExpression
import org.ossreviewtoolkit.utils.spdx.toExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

/**
 * A class for matching copyright findings to license findings. Copyright statements may be matched either to license
 * findings located nearby in the same file or to a license found in a license file whereas the given
 * [pathLicenseMatcher] determines whether a file is a license file.
 */
class FindingsMatcher(
    private val pathLicenseMatcher: PathLicenseMatcher = PathLicenseMatcher(),
    private val toleranceLines: Int = DEFAULT_TOLERANCE_LINES,
    private val expandToleranceLines: Int = DEFAULT_EXPAND_TOLERANCE_LINES
) {
    companion object {
        /**
         * The default value seems to be a good balance between associating findings separated by blank lines but not
         * skipping complete license statements.
         */
        const val DEFAULT_TOLERANCE_LINES = 5

        /**
         * The default value seems to be a good balance between associating findings separated by blank lines but not
         * skipping complete license statements.
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
        val startLine = max(0, licenseStartLine - toleranceLines)
        val endLine = max(licenseStartLine + toleranceLines, licenseEndLine)
        val range = startLine..endLine

        var expandedStartLine = copyrightLines.filter { it in range }.minOrNull() ?: return range
        val queue = PriorityQueue<Int>(copyrightLines.size, compareByDescending { it })
        queue += copyrightLines.filter { it < expandedStartLine }

        while (queue.isNotEmpty()) {
            val line = queue.poll()
            if (expandedStartLine - line > expandToleranceLines) break

            expandedStartLine = line
        }

        return min(startLine, expandedStartLine)..endLine
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
     * defined by [pathLicenseMatcher].
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
            unmatchedCopyrights += copyrights.toSet() - matchedFileFindings.values.flatten().toSet()
        }

        val matchedRootLicenseFindings = matchWithRootLicenses(licenseFindings, unmatchedCopyrights)

        matchedFindings.merge(matchedRootLicenseFindings)
        unmatchedCopyrights -= matchedRootLicenseFindings.values.flatten().toSet()

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
        val rootLicensesForDirectories = pathLicenseMatcher.getApplicableLicenseFindingsForDirectories(
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

/**
 * Process [findings] for stand-alone license exceptions and associate them with nearby (according to [toleranceLines])
 * applicable licenses. Orphan license exceptions will get associated by [SpdxConstants.NOASSERTION]. Return the list of
 * resulting findings.
 */
fun associateLicensesWithExceptions(
    findings: Collection<LicenseFinding>,
    toleranceLines: Int = FindingsMatcher.DEFAULT_TOLERANCE_LINES
): Set<LicenseFinding> {
    val (licenses, exceptions) = findings.partition { SpdxLicenseException.forId(it.license.toString()) == null }

    val fixedLicenses = licenses.toMutableSet()

    val existingExceptions = licenses.mapNotNull { finding ->
        (finding.license as? SpdxLicenseWithExceptionExpression)?.exception?.let { it to finding.location }
    }

    val remainingExceptions = exceptions.filterNotTo(mutableSetOf()) {
        existingExceptions.any { (exception, location) ->
            it.license.toString() == exception && it.location in location
        }
    }

    val i = remainingExceptions.iterator()

    while (i.hasNext()) {
        val exception = i.next()

        // Determine all licenses the exception is applicable to.
        val applicableLicenses = SpdxLicenseException.mapping[exception.license.toString()].orEmpty().map { it.id }

        // Determine applicable license findings from the same path.
        val applicableLicenseFindings = licenses.filter {
            it.location.path == exception.location.path && it.license.toString() in applicableLicenses
        }

        // Find the closest license within the tolerance.
        val associatedLicenseFinding = applicableLicenseFindings
            .map { it to it.location.distanceTo(exception.location) }
            .sortedBy { it.second }
            .firstOrNull { it.second <= toleranceLines }
            ?.first

        if (associatedLicenseFinding != null) {
            // Add the fixed-up license with the exception.
            fixedLicenses += associatedLicenseFinding.copy(
                license = "${associatedLicenseFinding.license} ${SpdxExpression.WITH} ${exception.license}".toSpdx(),
                location = associatedLicenseFinding.location.copy(
                    startLine = min(associatedLicenseFinding.location.startLine, exception.location.startLine),
                    endLine = max(associatedLicenseFinding.location.endLine, exception.location.endLine)
                )
            )

            // Remove the original license and the stand-alone exception.
            fixedLicenses.remove(associatedLicenseFinding)
            i.remove()
        }
    }

    // Associate remaining "orphan" exceptions with "NOASSERTION" to turn them into valid SPDX expressions and at the
    // same time "marking" them for review as "NOASSERTION" is not a real license.
    remainingExceptions.mapTo(fixedLicenses) { exception ->
        exception.copy(license = "${SpdxConstants.NOASSERTION} ${SpdxExpression.WITH} ${exception.license}".toSpdx())
    }

    return fixedLicenses.mapTo(mutableSetOf()) { it.copy(license = associateLicensesWithExceptions(it.license)) }
}

/**
 * Process [license] for stand-alone license exceptions as part of AND-expressions and associate them with applicable
 * licenses. Orphan license exceptions will get associated by [SpdxConstants.NOASSERTION]. Return a new expression that
 * does not contain stand-alone license exceptions anymore.
 */
fun associateLicensesWithExceptions(license: SpdxExpression): SpdxExpression {
    // If this is not a compound expression, there can be no stand-alone license exceptions with belonging licenses.
    if (license !is SpdxCompoundExpression) return license

    // Only search for stand-alone exceptions as part of AND-expressions.
    if (license.operator == SpdxOperator.OR) {
        return SpdxCompoundExpression(SpdxOperator.OR, license.children.map { associateLicensesWithExceptions(it) })
    }

    val (standAloneExceptions, licenses) = license.children.partition {
        it is SpdxSimpleExpression && SpdxLicenseException.forId(it.toString()) != null
    }

    val standAloneExceptionIds = standAloneExceptions.mapTo(mutableSetOf()) { it.toString() }
    val handledExceptions = mutableSetOf<String>()

    val associatedLicenses = licenses.toSet().mapTo(mutableSetOf()) { childLicense ->
        when (childLicense) {
            is SpdxCompoundExpression -> associateLicensesWithExceptions(childLicense)

            is SpdxSimpleExpression -> {
                val licenseId = childLicense.toString()

                val validLicenseExceptionCombinations = standAloneExceptionIds.mapNotNull { exceptionId ->
                    val applicableLicenseIds = SpdxLicenseException.mapping[exceptionId].orEmpty().map { it.id }

                    if (licenseId in applicableLicenseIds) {
                        SpdxLicenseWithExceptionExpression(childLicense, exceptionId)
                    } else {
                        null
                    }
                }

                handledExceptions += validLicenseExceptionCombinations.map { it.exception }

                validLicenseExceptionCombinations.toExpression() ?: childLicense
            }

            else -> childLicense
        }
    }

    // Associate remaining "orphan" exceptions with "NOASSERTION" to turn them into valid SPDX expressions.
    val orphanExceptions = standAloneExceptionIds - handledExceptions

    orphanExceptions.mapTo(associatedLicenses) { exceptionId ->
        SpdxLicenseWithExceptionExpression(SpdxLicenseIdExpression(SpdxConstants.NOASSERTION), exceptionId)
    }

    // Recreate the compound AND-expression from the associated licenses.
    return checkNotNull(associatedLicenses.toExpression())
}
