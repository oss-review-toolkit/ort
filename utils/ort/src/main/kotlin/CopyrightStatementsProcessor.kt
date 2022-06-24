/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.utils.ort

import java.util.SortedMap
import java.util.SortedSet

private data class Parts(
    val prefix: String,
    val years: Set<Int>,
    val owner: String,
    val originalStatements: List<String>
)

private val INVALID_OWNER_START_CHARS = charArrayOf(' ', ';', '.', ',', '-', '+', '~', '&')
private val INVALID_OWNER_KEY_CHARS = charArrayOf('<', '>', '(', ')', '[', ']') + INVALID_OWNER_START_CHARS

private const val YEAR_PLACEHOLDER = "<ORT_YEAR_PLACEHOLDER_TRO>"

private val COMMA_SEPARATED_YEARS_REGEX = "(?=.*)\\b(\\d{4})\\b( *, *)\\b(\\d{4})\\b".toRegex()

private val DUPLICATE_WHITESPACES_REGEX = "\\s+".toRegex()

private val KNOWN_PREFIX_REGEX = listOf(
    "^(?:\\(c\\))",
    "^(?:\\(c\\) [C|c]opyright)",
    "^(?:\\(c\\) [C|c]opyrighted)",
    "^(?:[C|c]opyright)",
    "^(?:[C|c]opyright \\(c\\))",
    "^(?:[C|c]opyright [O|o]wnership)",
    "^(?:[C|c]opyright')",
    "^(?:[C|c]opyright' \\(c\\))",
    "^(?:COPYRIGHT)",
    "^(?:[C|c]opyrighted)",
    "^(?:[C|c]opyrighted \\(c\\))",
    "^(?:[P|p]ortions [C|c]opyright)",
    "^(?:[P|p]ortions \\(c\\))",
    "^(?:[P|p]ortions [C|c]opyright \\(c\\))"
).map { it.toRegex() }

private val SINGLE_YEARS_REGEX = "(?=.*)\\b([\\d]{4})\\b".toRegex()

private val U_QUOTE_REGEX = "(.*\\b)u'(\\d{4}\\b)".toRegex()

private val YEAR_RANGE_REGEX = "(?=.*)\\b([\\d]{4})( *- *)([\\d]{4}|[\\d]{2}|[\\d])\\b".toRegex()

private val PARTS_COMPARATOR = compareBy<Parts>({ it.owner }, { prettyPrintYears(it.years) }, { it.prefix })

private fun getYearRanges(years: Collection<Int>): List<Pair<Int, Int>> {
    fun divideAndConquer(years: IntArray, start: Int = 0, end: Int = years.size - 1): List<Pair<Int, Int>> {
        if (end < start) return emptyList()

        for (i in start + 1..end) {
            if (years[i - 1] + 1 != years[i]) {
                return listOf(Pair(years[start], years[i - 1])) + divideAndConquer(years, i, end)
            }
        }

        return listOf(Pair(years[start], years[end]))
    }

    val sortedYears = years.toSortedSet().toIntArray()
    return divideAndConquer(sortedYears, 0, sortedYears.size - 1)
}

private fun prettyPrintYears(years: Collection<Int>) =
    getYearRanges(years).joinToString { (fromYear, toYear) ->
        if (fromYear == toYear) fromYear.toString() else "$fromYear-$toYear"
    }

private fun determineParts(copyrightStatement: String): Parts? {
    val prefixStripResult = stripKnownCopyrightPrefix(copyrightStatement)
    if (prefixStripResult.second.isEmpty()) return null

    val yearsStripResult = stripYears(prefixStripResult.first)
    return Parts(
        prefix = prefixStripResult.second,
        years = yearsStripResult.second,
        owner = yearsStripResult.first
            .trimStart(*INVALID_OWNER_START_CHARS)
            .removeDuplicateWhitespaces()
            .trim(),
        originalStatements = listOf(copyrightStatement)
    )
}

/**
 * Strip the longest [known copyright prefix][KNOWN_PREFIX_REGEX] from [copyrightStatement] and return a pair of the
 * copyright statement without the prefix and the prefix that was stripped from it.
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
 * This function removes all found years from the given string and replaces the first match
 * with a placeholder. While replacement is not necessary for implementing the needed functionality
 * it is helpful for debugging.
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
        currentStatement = currentStatement.removeRange(matchResult.groups[2]!!.range)
        currentStatement = currentStatement.replaceRange(matchResult.groups[1]!!.range, "$YEAR_PLACEHOLDER ")
        resultYears += matchResult.groups[1]!!.value.toInt()

        matchResult = COMMA_SEPARATED_YEARS_REGEX.find(currentStatement)
    }

    // Replace single years.
    matchResult = SINGLE_YEARS_REGEX.find(currentStatement)

    while (matchResult != null) {
        currentStatement = currentStatement.replaceRange(matchResult.groups[1]!!.range, YEAR_PLACEHOLDER)
        resultYears += matchResult.groups[1]!!.value.toInt()

        matchResult = SINGLE_YEARS_REGEX.find(currentStatement)
    }

    currentStatement = currentStatement.replace("$YEAR_PLACEHOLDER $YEAR_PLACEHOLDER", YEAR_PLACEHOLDER)
    return Pair(currentStatement, resultYears)
}

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

private fun replaceYearRange(copyrightStatement: String): Pair<String, Set<Int>> {
    YEAR_RANGE_REGEX.findAll(copyrightStatement).forEach { matchResult ->
        val fromGroup = matchResult.groups[1]!!
        val separatorGroup = matchResult.groups[2]!!
        val toGroup = matchResult.groups[3]!!

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

private fun stripYears(copyrightStatement: String): Pair<String, Set<Int>> =
    replaceYears(copyrightStatement).let {
        it.copy(first = it.first.replace(YEAR_PLACEHOLDER, ""))
    }

private fun String.removeDuplicateWhitespaces() = replace(DUPLICATE_WHITESPACES_REGEX, " ")

private fun String.toNormalizedOwnerKey() = filter { it !in INVALID_OWNER_KEY_CHARS }.uppercase()

private fun Collection<Parts>.groupByPrefixAndOwner(): List<Parts> {
    val map = mutableMapOf<String, Parts>()

    forEach { part ->
        val key = "${part.prefix}:${part.owner.toNormalizedOwnerKey()}"
        map.merge(key, part) { existing, other ->
            Parts(
                prefix = existing.prefix,
                years = existing.years + other.years,
                owner = existing.owner,
                originalStatements = existing.originalStatements + other.originalStatements
            )
        }
    }

    return map.values.toList()
}

/**
 * A copyright statement consists in most cases of three parts: a copyright prefix, years and the owner. For legal
 * reasons the prefix part must not be modified at all while adjusting some special characters in the owner part is
 * acceptable. Entries can be merged by year as well. The main idea of the algorithm is to process only entries with
 * a known copyright prefix. This allows stripping the prefix and processing the remaining string separately and thus
 * guarantees that the prefix part is not modified at all.
 *
 * Future improvement ideas:
 *   -URLs could be treated similar to years, e.g. entries which differ only in terms of URLs and year can be merged.
 */
class CopyrightStatementsProcessor {
    data class Result(
        /**
         * The copyright statements that were processed by the [CopyrightStatementsProcessor], mapped to the original
         * copyright statements. An original statement can be identical to the processed statement if the processor did
         * process but not modify it.
         */
        val processedStatements: SortedMap<String, SortedSet<String>>,

        /**
         * The copyright statements that were ignored by the [CopyrightStatementsProcessor].
         */
        val unprocessedStatements: SortedSet<String>
    ) {
        fun getAllStatements(): Set<String> = (unprocessedStatements + processedStatements.keys).toSet()
    }

    fun process(copyrightStatements: Collection<String>): Result {
        val unprocessedStatements = sortedSetOf<String>()
        val processableStatements = mutableListOf<Parts>()

        copyrightStatements.distinct().forEach {
            val parts = determineParts(it)
            if (parts != null) {
                processableStatements += parts
            } else {
                unprocessedStatements += it
            }
        }

        val mergedParts = processableStatements.groupByPrefixAndOwner().sortedWith(PARTS_COMPARATOR)

        val processedStatements = sortedMapOf<String, SortedSet<String>>()
        mergedParts.forEach {
            if (it.owner.isNotEmpty()) {
                val statement = buildString {
                    append(it.prefix)
                    if (it.years.isNotEmpty()) {
                        append(" ")
                        append(prettyPrintYears(it.years))
                    }
                    append(" ")
                    append(it.owner)
                }
                processedStatements[statement] = it.originalStatements.toSortedSet()
            }
        }

        return Result(
            processedStatements = processedStatements,
            unprocessedStatements = unprocessedStatements
        )
    }
}
