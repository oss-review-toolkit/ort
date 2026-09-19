/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python.utils

/**
 * The labels of project URLs that usually point to the source code repository. Labels are free-form, so this is a
 * best effort.
 */
private val SOURCE_URL_LABEL_REGEX = Regex("repo|source|code|github|gitlab|bitbucket", RegexOption.IGNORE_CASE)

/**
 * The subset of the Python core metadata that ORT uses, see
 * https://packaging.python.org/en/latest/specifications/core-metadata/. It is created from a `METADATA` file (PEP 658)
 * or from the JSON API of an index.
 */
internal data class PythonCoreMetadata(
    val summary: String? = null,
    val homepage: String? = null,
    val projectUrls: Map<String, String> = emptyMap(),
    val author: String? = null,
    val authorEmail: String? = null,

    /** The legacy `License` field, which is free text and may contain a full license text. */
    val license: String? = null,

    /** The SPDX expression from the `License-Expression` field (PEP 639). */
    val licenseExpression: String? = null,

    val classifiers: List<String> = emptyList(),

    /** Where the metadata was retrieved from, for logging. */
    val source: String
) {
    companion object {
        /**
         * Parse the given [text] of a `METADATA` file retrieved from [source]. Only the headers are parsed, the
         * body with the long description is ignored.
         */
        fun parse(text: String, source: String): PythonCoreMetadata {
            val headers = mutableMapOf<String, MutableList<String>>()
            var currentHeader: String? = null

            for (line in text.lineSequence()) {
                // The first empty line ends the headers.
                if (line.isEmpty()) break

                // Continuation lines start with whitespace and belong to the previous header, e.g. a multi-line license
                // text.
                if (line.first().isWhitespace()) {
                    currentHeader?.let { headers.getValue(it).let { values -> values[values.lastIndex] += "\n$line" } }
                    continue
                }

                val (name, value) = line.split(':', limit = 2).takeIf { it.size == 2 } ?: continue
                currentHeader = name.trim().lowercase()
                headers.getOrPut(currentHeader) { mutableListOf() } += value.trim()
            }

            fun header(name: String) = headers[name]?.firstOrNull()?.takeUnless { it.isBlank() }

            return PythonCoreMetadata(
                summary = header("summary"),
                homepage = header("home-page"),
                projectUrls = headers["project-url"].orEmpty().mapNotNull { projectUrl ->
                    projectUrl.split(',', limit = 2).takeIf { it.size == 2 }?.let { (label, url) ->
                        label.trim() to url.trim()
                    }
                }.toMap(),
                author = header("author"),
                authorEmail = header("author-email"),
                license = header("license"),
                licenseExpression = header("license-expression"),
                classifiers = headers["classifier"].orEmpty(),
                source = source
            )
        }
    }

    /**
     * Return the declared licenses. A PEP 639 license expression takes precedence. Otherwise the legacy `License` field
     * is used if it holds a short license name, plus the license classifiers. A full license text is not guessed into a
     * license name, in line with how ORT treats Python Inspector results.
     */
    fun getDeclaredLicenses(): Set<String> {
        licenseExpression?.takeUnless { it.isBlank() }?.let { return setOf(it) }

        return buildSet {
            getLicenseFromLicenseField(license)?.let { add(it) }
            classifiers.mapNotNullTo(this) { getLicenseFromClassifier(it) }
        }
    }

    /**
     * Return the authors in the `Name <email>` notation. Modern metadata has both in the `Author-email` field, possibly
     * for several authors, while legacy metadata uses separate `Author` and `Author-email` fields.
     */
    fun getAuthors(): Set<String> {
        val name = author?.takeUnless { it.isBlank() }
        val email = authorEmail?.takeUnless { it.isBlank() }

        return when {
            email != null && '<' in email -> email.split(Regex("""(?<=>)\s*,\s*""")).mapTo(mutableSetOf()) { it.trim() }
            name != null && email != null -> setOf("$name <$email>")
            else -> setOfNotNull(name ?: email)
        }
    }

    /** Return the homepage URL, which modern metadata records as a project URL labeled "Homepage". */
    fun getHomepageUrl(): String? =
        homepage ?: projectUrls.entries.find { it.key.equals("homepage", ignoreCase = true) }?.value

    /**
     * Return the URLs that may point to the source code repository as fallbacks for the VCS information, most likely
     * first: project URLs whose label indicates source code, the homepage, and then all other project URLs.
     */
    fun getVcsFallbackUrls(): List<String> {
        val (sourceUrls, otherUrls) = projectUrls.entries.partition { SOURCE_URL_LABEL_REGEX.containsMatchIn(it.key) }
        return (sourceUrls.map { it.value } + listOfNotNull(homepage) + otherUrls.map { it.value }).distinct()
    }
}
