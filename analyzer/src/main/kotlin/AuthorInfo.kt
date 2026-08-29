/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.analyzer

import org.ossreviewtoolkit.utils.common.collapseWhitespace

/**
 * Information about an author, including the [name], [email] address, and [homepage] URL.
 */
data class AuthorInfo(val name: String?, val email: String?, val homepage: String?)

/**
 * Parse a string with metadata about an [author] that several package managers use and try to extract the author's
 * name, email address, and homepage. These properties are typically surrounded by specific delimiters, e.g. the email
 * address is often surrounded by angle brackets (see [emailDelimiters]) and the homepage is often surrounded by
 * parentheses (see [homepageDelimiters]). Return [AuthorInfo] for these properties where unavailable ones are set to
 * null.
 */
fun parseAuthorString(
    author: String?,
    emailDelimiters: Pair<Char, Char> = '<' to '>',
    homepageDelimiters: Pair<Char, Char> = '(' to ')'
): Set<AuthorInfo> =
    author?.split(',', '\n')?.mapTo(mutableSetOf()) { singleAuthor ->
        var cleanedAuthor = singleAuthor
        var email: String? = null
        var homepage: String? = null

        // Extract an email address and remove it from the original author string.
        val e = emailDelimiters.toList().map { Regex.escape(it.toString()) }
        val emailRegex = Regex("${e.first()}(.+@.+)${e.last()}")
        cleanedAuthor = cleanedAuthor.replace(emailRegex) {
            email = it.groupValues.last()
            ""
        }

        // Extract a homepage URL and remove it from the original author string.
        val h = homepageDelimiters.toList().map { Regex.escape(it.toString()) }
        val homepageRegex = Regex("${h.first()}(.+(?:://|www|.).+)${h.last()}")
        cleanedAuthor = cleanedAuthor.replace(homepageRegex) {
            homepage = it.groupValues.last()
            ""
        }

        AuthorInfo(cleanedAuthor.collapseWhitespace().ifEmpty { null }, email, homepage)
    }.orEmpty()

/**
 * Trivially guess an author's name from the given [email] address.
 */
fun guessNameFromEmail(email: String): String =
    email.substringBefore('@')
        .replace('.', ' ')
        .replace(lowercaseStartRegex) { it.value.uppercase() }

private val lowercaseStartRegex = Regex("\\b([a-z])")
