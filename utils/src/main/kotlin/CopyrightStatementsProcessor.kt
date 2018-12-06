/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.utils

private data class Parts(
        val prefix: String,
        val years: Set<Int>,
        val owner: String,
        val originalStatements: List<String>
)

private val INVALID_OWNER_START_CHARS = charArrayOf(' ', ';', '.', ',', '-', '+', '~', '&')
private val INVALID_OWNER_KEY_CHARS = charArrayOf('<', '>', '(', ')', '[', ']') + INVALID_OWNER_START_CHARS

private fun String.removeDuplicateWhitespaces() = replace(Regex("\\s+"), " ")

private fun String.toNormalizedOwnerKey() = filter { it !in INVALID_OWNER_KEY_CHARS }.toUpperCase()

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
    companion object {
        private val YEAR_PLACEHOLDER = "<ORT_YEAR_PLACEHOLDER_TRO>"
        private val KNOWN_PREFIX_REGEX = listOf(
                "^(\\(c\\))",
                "^(\\(c\\) [C|c]opyright)",
                "^(\\(c\\) [C|c]opyrighted)",
                "^([C|c]opyright)",
                "^([C|c]opyright \\(c\\))",
                "^([C|c]opyright [O|o]wnership)",
                "^([C|c]opyright')",
                "^([C|c]opyright' \\(c\\))",
                "^(COPYRIGHT)",
                "^([C|c]opyrighted)",
                "^([C|c]opyrighted \\(c\\))",
                "^([P|p]ortions [C|c]opyright)",
                "^([P|p]ortions [C|c]opyright \\(c\\))"
        ).map { it.toRegex() }

        private fun prettyPrintYears(years: Collection<Int>) =
                getYearRanges(years).joinToString (separator = ", ") { (fromYear, toYear) ->
                    if (fromYear == toYear) fromYear.toString() else "$fromYear-$toYear"
                }

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
    }

    data class Result(val processedStatements: LinkedHashMap<String, List<String>>,
                      val unprocessedStatements: List<String>) {
        fun toMutableSet() = (unprocessedStatements + processedStatements.keys).toMutableSet()
    }

    fun process(copyrightStatments: Collection<String>): Result {
        val unprocessedStatements = mutableListOf<String>()
        val processableStatements = mutableListOf<Parts>()

        copyrightStatments.forEach {
            val parts = determineParts(it)
            if (parts != null) {
                processableStatements += parts
            } else {
                unprocessedStatements += it
            }
        }

        val mergedParts = processableStatements.groupByPrefixAndOwner().sortedWith(
                compareBy(
                        { it.owner },
                        { prettyPrintYears(it.years) },
                        { it.prefix }
                )
        )

        val processedStatements = linkedMapOf<String, List<String>>()
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
                processedStatements[statement] = it.originalStatements.sorted()
            }
        }

        return Result(
                unprocessedStatements = unprocessedStatements.toList().sorted(),
                processedStatements = processedStatements
        )
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

    private fun stripKnownCopyrightPrefix(copyrightStatement: String): Pair<String, String> {
        val match = KNOWN_PREFIX_REGEX.mapNotNull { regex ->
            regex.find(copyrightStatement)
        }.maxBy {
            it.groups[1]!!.value.length
        } ?: return Pair(first = copyrightStatement, second = "")

        return Pair(
                first = copyrightStatement.removeRange(match.groups[1]!!.range),
                second = match.groups[1]!!.value
        )
    }

    private fun stripYears(copyrightStatement: String): Pair<String, Set<Int>> =
            replaceYears(copyrightStatement, YEAR_PLACEHOLDER).let {
                it.copy(first = it.first.replace(YEAR_PLACEHOLDER, ""))
            }

    /**
     * This function removes all found years from the given string and replaces the first match
     * with a placeholder. While replacement is not necessary for implementing the needed functionality
     * it is helpful for debugging.
     */
    private fun replaceYears(copyrightStatement: String, placeholder: String): Pair<String, Set<Int>> {
        val resultYears = mutableSetOf<Int>()

        // Fix up strings containing e.g.: 'copyright u'2013'
        var currentStatement = copyrightStatement.replace("(.*\\b)u'(\\d{4}\\b)".toRegex(), "$1$2")

        val replaceRangeResult = replaceAllYearRanges(currentStatement, placeholder)
        currentStatement = replaceRangeResult.first
        resultYears += replaceRangeResult.second

        // Replace comma separated years.
        val commaSeparatedYearsRegex = "(?=.*)\\b(\\d{4})\\b([ ]*[,][ ]*)\\b(\\d{4})\\b".toRegex()
        var matchResult = commaSeparatedYearsRegex.find(currentStatement)

        while (matchResult != null) {
            currentStatement = currentStatement.removeRange(matchResult.groups[2]!!.range)
            currentStatement = currentStatement.replaceRange(matchResult.groups[1]!!.range, "$placeholder ")
            resultYears += matchResult.groups[1]!!.value.toInt()

            matchResult = commaSeparatedYearsRegex.find(currentStatement)
        }

        // Replace single years.
        val singleYearsRegex = "(?=.*)\\b([\\d]{4})\\b".toRegex()
        matchResult = singleYearsRegex.find(currentStatement)

        while (matchResult != null) {
            currentStatement = currentStatement.replaceRange(matchResult.groups[1]!!.range, placeholder)
            resultYears += matchResult.groups[1]!!.value.toInt()

            matchResult = singleYearsRegex.find(currentStatement)
        }

        currentStatement = currentStatement.replace("$placeholder $placeholder", placeholder)
        return Pair(currentStatement, resultYears)
    }

    private fun replaceAllYearRanges(copyrightStatement: String, placeholder: String): Pair<String, Set<Int>> {
        var years = mutableSetOf<Int>()
        var currentStatement = copyrightStatement

        while (true) {
            val replaceResult = replaceYearRange(currentStatement, placeholder)
            if (replaceResult.second.isEmpty()) {
                return Pair(currentStatement, years)
            }
            years.addAll(replaceResult.second)
            currentStatement = replaceResult.first
        }
    }

    private fun replaceYearRange(copyrightStatement: String, placeholder: String): Pair<String, Set<Int>> {
        val yearRangeRegex = "(?=.*)\\b([\\d]{4})([ ]*[-][ ]*)([\\d]{4}|[\\d]{2}|[\\d]{1})\\b".toRegex()

        yearRangeRegex.findAll(copyrightStatement).forEach { matchResult ->
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
                                .replaceRange(fromGroup.range, placeholder),
                        (fromYear..toYear).toSet()
                )
            }
        }

        return Pair(copyrightStatement, emptySet())
    }
}
