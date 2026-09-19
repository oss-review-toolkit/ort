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

/** The labels of project URLs that usually point to the source code repository. */
private val SOURCE_URL_LABEL_REGEX = Regex("""\b(repo|repository|sources?)\b|^code$|github|gitlab|bitbucket|codeberg""")

/** The labels of funding URLs, which often are on a source code host but never point to the repository. */
private val FUNDING_URL_LABEL_REGEX = Regex("fund|sponsor|donat")

/** The placeholder old versions of setuptools write for fields that have no value. */
private const val UNKNOWN_VALUE = "UNKNOWN"

/** The separator between several authors in the `Author-email` field, which follows the closing angle bracket. */
private val AUTHOR_SEPARATOR_REGEX = Regex("""(?<=>)\s*,\s*""")

/**
 * The subset of the Python core metadata that ORT uses, see
 * https://packaging.python.org/en/latest/specifications/core-metadata/. Values that are empty or the "UNKNOWN"
 * placeholder are null.
 */
internal data class PythonCoreMetadata(
    val summary: String? = null,
    val homepage: String? = null,

    /** The project URLs keyed by their label in lower case. */
    val projectUrls: Map<String, String> = emptyMap(),

    val author: String? = null,
    val authorEmail: String? = null,

    /** The legacy `License` field, which is free text and may contain a full license text. */
    val license: String? = null,

    val licenseExpression: String? = null,

    val classifiers: List<String> = emptyList()
) {
    companion object {
        /**
         * Parse the headers of the given [text] of a `METADATA` file, or return null if it lacks the mandatory
         * `Metadata-Version` header.
         */
        fun parse(text: String): PythonCoreMetadata? {
            var currentHeader: String? = null

            val headers = buildMap<String, MutableList<String>> {
                for (line in text.lineSequence()) {
                    if (line.isEmpty()) break

                    // Continuation lines belong to the previous header.
                    if (line.first().isWhitespace()) {
                        val values = currentHeader?.let { getValue(it) } ?: continue
                        values[values.lastIndex] += "\n$line"
                        continue
                    }

                    val (name, value) = line.split(':', limit = 2).takeIf { it.size == 2 } ?: continue
                    val header = name.trim().lowercase()
                    getOrPut(header) { mutableListOf() } += value.trim()
                    currentHeader = header
                }
            }

            if ("metadata-version" !in headers) return null

            fun header(name: String) = headers[name]?.firstOrNull().toMetadataValue()

            return PythonCoreMetadata(
                summary = header("summary"),
                homepage = header("home-page"),
                projectUrls = headers["project-url"].orEmpty().mapNotNull { projectUrl ->
                    projectUrl.split(',', limit = 2).takeIf { it.size == 2 }?.let { (label, url) ->
                        label.trim().lowercase() to url.trim()
                    }
                }.toMap(),
                author = header("author"),
                authorEmail = header("author-email"),
                license = header("license"),
                licenseExpression = header("license-expression"),
                classifiers = headers["classifier"].orEmpty()
            )
        }
    }

    /**
     * Return the declared licenses. A PEP 639 license expression takes precedence over the legacy `License` field,
     * which is only used if it holds a short license name, and the license classifiers.
     */
    fun getDeclaredLicenses(): Set<String> =
        licenseExpression?.let { setOf(it) } ?: buildSet {
            getLicenseFromLicenseField(license)?.also { add(it) }
            classifiers.mapNotNullTo(this) { getLicenseFromClassifier(it) }
        }

    /** Return the authors in the `Name <email>` notation, which legacy metadata splits into two fields. */
    fun getAuthors(): Set<String> =
        when {
            authorEmail != null && '<' in authorEmail ->
                authorEmail.split(AUTHOR_SEPARATOR_REGEX).mapTo(mutableSetOf()) { it.trim() }

            author != null && authorEmail != null -> setOf("$author <$authorEmail>")

            else -> setOfNotNull(author ?: authorEmail)
        }

    fun getHomepageUrl(): String? = homepage ?: projectUrls["homepage"]

    /** Return the URLs that may point to the source code repository, most likely first. */
    fun getVcsFallbackUrls(): List<String> {
        val (sourceUrls, otherUrls) = projectUrls.entries
            .filterNot { FUNDING_URL_LABEL_REGEX.containsMatchIn(it.key) }
            .partition { SOURCE_URL_LABEL_REGEX.containsMatchIn(it.key) }

        return (sourceUrls.map { it.value } + listOfNotNull(homepage) + otherUrls.map { it.value }).distinct()
    }
}

/** Return this metadata value trimmed, or null if it is empty or the "UNKNOWN" placeholder. */
internal fun String?.toMetadataValue(): String? = this?.trim()?.takeUnless { it.isEmpty() || it == UNKNOWN_VALUE }
