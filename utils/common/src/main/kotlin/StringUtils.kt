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

package org.ossreviewtoolkit.utils.common

/**
 * Trim leading and trailing whitespace, and collapse consecutive inner whitespace to a single space.
 */
fun String.collapseWhitespace() = trim().replace(CONSECUTIVE_WHITESPACE_REGEX, " ")

private val CONSECUTIVE_WHITESPACE_REGEX = Regex("\\s+")

/**
 * Return the string encoded for safe use as a file name or [emptyValue] encoded for safe use as a file name, if this
 * string is empty. Throws an exception if [emptyValue] is empty.
 */
fun String.encodeOr(emptyValue: String): String {
    require(emptyValue.isNotEmpty())

    return ifEmpty { emptyValue }.fileSystemEncode()
}

/**
 * Return the string encoded for safe use as a file name or "unknown", if the string is empty.
 */
fun String.encodeOrUnknown(): String = encodeOr("unknown")

/**
 * Return the string encoded for safe use as a file name. Also limit the length to 255 characters which is the maximum
 * length in most modern filesystems, see
 * [comparison of file system limits](https://en.wikipedia.org/wiki/Comparison_of_file_systems#Limits).
 */
fun String.fileSystemEncode() =
    percentEncode()
        // Percent-encoding does not necessarily encode some characters that are invalid in some file systems, so map
        // these afterwards.
        .replace(Regex("(^\\.|\\.$)"), "%2E")
        .take(255)

/**
 * Replace "\r\n" and "\r" line breaks with "\n".
 */
fun String.normalizeLineBreaks() = replace(NON_LINUX_LINE_BREAKS_REGEX, "\n")

private val NON_LINUX_LINE_BREAKS_REGEX = Regex("\\r\\n?")

/**
 * Return all substrings that do not contain any whitespace as a list.
 */
fun String.splitOnWhitespace(): List<String> = NON_SPACE_REGEX.findAll(this).mapTo(mutableListOf()) { it.value }

private val NON_SPACE_REGEX = Regex("\\S+")

/**
 * Return this string lower-cased except for the first character which is upper-cased.
 */
fun String.titlecase() = lowercase().replaceFirstChar { it.titlecase() }

/**
 * Return this string with (nested) single- and double-quotes removed. If [trimWhitespace] is true, then intermediate
 * whitespace is also removed, otherwise it is kept.
 */
fun String.unquote(trimWhitespace: Boolean = true) =
    trim { (trimWhitespace && it.isWhitespace()) || it == '\'' || it == '"' }

/**
 * If this string starts with [prefix], return the string without the prefix, otherwise return [missingPrefixValue].
 */
fun String?.withoutPrefix(prefix: String, missingPrefixValue: () -> String? = { null }): String? =
    this?.removePrefix(prefix)?.takeIf { it != this } ?: missingPrefixValue()

/**
 * If this string ends with [suffix], return the string without the suffix, otherwise return [missingSuffixValue].
 */
fun String?.withoutSuffix(suffix: String, missingSuffixValue: () -> String? = { null }): String? =
    this?.removeSuffix(suffix)?.takeIf { it != this } ?: missingSuffixValue()
