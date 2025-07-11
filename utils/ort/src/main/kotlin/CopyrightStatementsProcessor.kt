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

package org.ossreviewtoolkit.utils.ort

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.annotation.JsonSerialize

import org.ossreviewtoolkit.utils.common.collapseToRanges
import org.ossreviewtoolkit.utils.common.collapseWhitespace
import org.ossreviewtoolkit.utils.common.prettyPrintRanges

private val INVALID_OWNER_START_CHARS = charArrayOf(' ', ';', '.', ',', '-', '+', '~', '&')
private val INVALID_OWNER_KEY_CHARS = charArrayOf('<', '>', '(', ')', '[', ']') + INVALID_OWNER_START_CHARS

private const val YEAR_PLACEHOLDER = "<ORT_YEAR_PLACEHOLDER_TRO>"

private val COMMA_SEPARATED_YEARS_REGEX = "(?=.*)\\b(\\d{4})\\b( *, *)\\b(\\d{4})\\b".toRegex()

private val KNOWN_PREFIX_REGEX = listOf(
    "^(?:\\([C|c]\\))",
    "^(?:\\([C|c]\\) [C|c]opyright)",
    "^(?:\\([C|c]\\) [C|c]opyrighted)",
    "^(?:[C|c]opyright)",
    "^(?:[C|c]opyright \\([C|c]\\))",
    "^(?:[C|c]opyright [O|o]wnership)",
    "^(?:[C|c]opyright')",
    "^(?:[C|c]opyright' \\([C|c]\\))",
    "^(?:COPYRIGHT)",
    "^(?:[C|c]opyrighted)",
    "^(?:[C|c]opyrighted \\([C|c]\\))",
    "^(?:[P|p]ortions [C|c]opyright)",
    "^(?:[P|p]ortions \\([C|c]\\))",
    "^(?:[P|p]ortions [C|c]opyright \\([C|c]\\))"
).map { it.toRegex() }

private val SINGLE_YEARS_REGEX = "(?=.*)\\b(\\d{4})\\b".toRegex()

private val U_QUOTE_REGEX = "(.*\\b)u'(\\d{4}\\b)".toRegex()

private val YEAR_RANGE_REGEX = "(?=.*)\\b(\\d{4})( *- *)(\\d{4}|\\d{2}|\\d)\\b".toRegex()

/**
 * A copyright statement consists in most cases of three parts: a copyright prefix, years and the owner. For legal
 * reasons the prefix part must not be modified at all while adjusting some special characters in the owner part is
 * acceptable. Entries can be merged by year as well. The main idea of the algorithm is to process only entries with
 * a known copyright prefix. This allows stripping the prefix and processing the remaining string separately and thus
 * guarantees that the prefix part is not modified at all.
 *
 * TODO: Maybe treat URLs similar to years, e.g. entries which differ only in URLs and years can be merged.
 */
object CopyrightStatementsProcessor {
    data class Result(
        /**
         * The copyright statements that were processed by the [CopyrightStatementsProcessor], mapped to the original
         * copyright statements. An original statement can be identical to the processed statement if the processor did
         * process but not modify it.
         */
        @JsonPropertyOrder(alphabetic = true)
        @JsonSerialize(contentConverter = StringSortedSetConverter::class)
        val processedStatements: Map<String, Set<String>>,

        /**
         * The copyright statements that were ignored by the [CopyrightStatementsProcessor].
         */
        @JsonSerialize(converter = StringSortedSetConverter::class)
        val unprocessedStatements: Set<String>
    ) {
        @get:JsonIgnore
        val allStatements by lazy { unprocessedStatements + processedStatements.keys }
    }

    /**
     * Try to process the [copyrightStatements] into a more condensed form grouped by owner / prefix and with years
     * collapsed. The returned [Result] contains successfully processed as well as unprocessed statements.
     */
    fun process(copyrightStatements: Collection<String>): Result {
        val unprocessedStatements = mutableSetOf<String>()
        val processableStatements = mutableListOf<Parts>()

        copyrightStatements.distinct().forEach { statement ->
            val parts = determineParts(statement)
            if (parts != null) {
                processableStatements += parts
            } else {
                unprocessedStatements += statement
            }
        }

        val mergedParts = processableStatements.groupByPrefixAndOwner()

        val processedStatements = mergedParts
            .filterNot { it.owner.isEmpty() }
            .associate { it.toString() to it.originalStatements.toSet() }

        return Result(
            processedStatements = processedStatements,
            unprocessedStatements = unprocessedStatements
        )
    }
}

private data class Parts(
    val prefix: String,
    val years: Set<Int>,
    val owner: String,
    val originalStatements: List<String>
) : Comparable<Parts> {
    companion object {
        private val COMPARATOR =
            compareBy<Parts>({ it.owner }, { it.years.collapseToRanges().prettyPrintRanges() }, { it.prefix })
    }

    override fun compareTo(other: Parts) = COMPARATOR.compare(this, other)

    override fun toString() =
        buildString {
            append(prefix)

            if (years.isNotEmpty()) {
                append(" ")
                append(years.collapseToRanges().prettyPrintRanges())
            }

            if (owner.isNotEmpty()) {
                append(" ")
                append(owner)
            }
        }
}

/**
 * Split the [copyrightStatement] into its [Parts], or return null if the [Parts] could not be determined.
 */
private fun determineParts(copyrightStatement: String): Parts? {
    val prefixStripResult = stripKnownCopyrightPrefix(copyrightStatement)
    if (prefixStripResult.second.isEmpty()) return null

    val yearsStripResult = stripYears(prefixStripResult.first)
    return Parts(
        prefix = prefixStripResult.second,
        years = yearsStripResult.second,
        owner = yearsStripResult.first
            .trimStart(*INVALID_OWNER_START_CHARS)
            .collapseWhitespace(),
        originalStatements = listOf(copyrightStatement)
    )
}

/**
 * Group this collection of [Parts] by prefix and owner and return a list of [Parts] with years and original
 * statements merged accordingly.
 */
private fun Collection<Parts>.groupByPrefixAndOwner(): List<Parts> =
    buildMap {
        sorted().forEach { parts ->
            merge(parts.key, parts) { existing, other ->
                Parts(
                    prefix = existing.prefix,
                    years = existing.years + other.years,
                    owner = existing.owner,
                    originalStatements = existing.originalStatements + other.originalStatements
                )
            }
        }
    }.values.toList()

/**
 * Strip the longest [known copyright prefix][KNOWN_PREFIX_REGEX] from [copyrightStatement] and return a pair of
 * the copyright statement without the prefix and the prefix that was stripped from it.
 */
private fun stripKnownCopyrightPrefix(copyrightStatement: String): Pair<String, String> {
    val copyrightStatementWithoutPrefix = KNOWN_PREFIX_REGEX.map { regex ->
        copyrightStatement.replace(regex, "")
    }.minByOrNull {
        it.length
    } ?: return Pair(first = copyrightStatement, second = "")

    return Pair(
        first = copyrightStatementWithoutPrefix,
        second = copyrightStatement.removeSuffix(copyrightStatementWithoutPrefix)
    )
}

/**
 * Remove all found years from the [copyrightStatement] and replace them with the [YEAR_PLACEHOLDER]. The replacement is
 * not necessary for implementing the needed functionality, but it is helpful for debugging.
 */
private fun replaceYears(copyrightStatement: String): Pair<String, Set<Int>> {
    val resultYears = mutableSetOf<Int>()

    // Fix up strings containing e.g.: 'copyright u'2013'
    var currentStatement = copyrightStatement.replace(U_QUOTE_REGEX, "$1$2")

    val replaceRangeResult = replaceAllYearRanges(currentStatement)
    currentStatement = replaceRangeResult.first
    resultYears += replaceRangeResult.second

    // Replace comma separated years.
    var matchResult = COMMA_SEPARATED_YEARS_REGEX.find(currentStatement)

    while (matchResult != null) {
        currentStatement = currentStatement.removeRange(matchResult.getGroup(2).range)
        currentStatement = currentStatement.replaceRange(matchResult.getGroup(1).range, "$YEAR_PLACEHOLDER ")
        resultYears += matchResult.getGroup(1).value.toInt()

        matchResult = COMMA_SEPARATED_YEARS_REGEX.find(currentStatement)
    }

    // Replace single years.
    matchResult = SINGLE_YEARS_REGEX.find(currentStatement)

    while (matchResult != null) {
        currentStatement = currentStatement.replaceRange(matchResult.getGroup(1).range, YEAR_PLACEHOLDER)
        resultYears += matchResult.getGroup(1).value.toInt()

        matchResult = SINGLE_YEARS_REGEX.find(currentStatement)
    }

    currentStatement = currentStatement.replace("$YEAR_PLACEHOLDER $YEAR_PLACEHOLDER", YEAR_PLACEHOLDER)
    return Pair(currentStatement, resultYears)
}

/**
 * Replace all year ranges in the [copyrightStatement] with the [YEAR_PLACEHOLDER] and return the resulting string
 * paired to the set of years.
 */
private fun replaceAllYearRanges(copyrightStatement: String): Pair<String, Set<Int>> {
    val years = mutableSetOf<Int>()
    var currentStatement = copyrightStatement

    while (true) {
        val replaceResult = replaceYearRange(currentStatement)
        if (replaceResult.second.isEmpty()) {
            return Pair(currentStatement, years)
        }

        years += replaceResult.second
        currentStatement = replaceResult.first
    }
}

/**
 * Replace the first year range in the [copyrightStatement] with the [YEAR_PLACEHOLDER] and return the resulting
 * string paired to the set of years.
 */
private fun replaceYearRange(copyrightStatement: String): Pair<String, Set<Int>> {
    YEAR_RANGE_REGEX.findAll(copyrightStatement).forEach { matchResult ->
        val fromGroup = matchResult.getGroup(1)
        val separatorGroup = matchResult.getGroup(2)
        val toGroup = matchResult.getGroup(3)

        val fromYearString = fromGroup.value
        val fromYear = fromGroup.value.toInt()

        // Handle also the following cases: '2008 - 9' and '2001 - 10'.
        val toYear = toGroup.value.let { fromYearRaw ->
            "${fromYearString.substring(0, fromYearString.length - fromYearRaw.length)}$fromYearRaw".toInt()
        }

        if (fromYear <= toYear) {
            return Pair(
                copyrightStatement
                    .removeRange(toGroup.range)
                    .removeRange(separatorGroup.range)
                    .replaceRange(fromGroup.range, YEAR_PLACEHOLDER),
                (fromYear..toYear).toSet()
            )
        }
    }

    return Pair(copyrightStatement, emptySet())
}

/**
 * Remove all years from the [copyrightStatement] and return the stripped string paired to the set of years.
 */
private fun stripYears(copyrightStatement: String): Pair<String, Set<Int>> =
    replaceYears(copyrightStatement).let { (statement, years) ->
        statement.replace(YEAR_PLACEHOLDER, "") to years
    }

/**
 * Return a tuple of the copyright prefix and the normalized Copyright owner to group statement parts by.
 */
private val Parts.key: String get() {
    val normalizedOwnerKey = owner.filter { it !in INVALID_OWNER_KEY_CHARS }.uppercase()
    return "$prefix:$normalizedOwnerKey"
}

@Suppress("UnsafeCallOnNullableType")
private fun MatchResult.getGroup(index: Int): MatchGroup = groups[index]!!
