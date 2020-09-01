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

package org.ossreviewtoolkit.utils

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

import org.ossreviewtoolkit.spdx.SpdxDeclaredLicenseMapping
import org.ossreviewtoolkit.spdx.SpdxException
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.toSpdx

object DeclaredLicenseProcessor {
    private val urlPrefixesToRemove = listOf(
        "choosealicense.com/licenses/",
        "gnu.org/licenses/old-licenses/",
        "gnu.org/licenses/",
        "licenses.nuget.org/",
        "opensource.org/licenses/",
        "spdx.dev/licenses/",
        "spdx.org/licenses/",
        "tldrlegal.com/license/"
    ).flatMap {
        listOf("http://$it", "https://$it", "http://www.$it", "https://www.$it")
    }

    private val fileSuffixesToRemove = listOf(
        ".dbk", ".html", ".md", ".odt", ".php", ".rtf", ".tex", ".txt"
    )

    internal fun preprocess(declaredLicense: String): String {
        val licenseWithoutPrefix = urlPrefixesToRemove.fold(declaredLicense) { license, url ->
            license.removePrefix(url)
        }

        // Only also strip suffixes if at least one prefix was stripped. Otherwise URLs like
        // "http://ant-contrib.sourceforge.net/tasks/LICENSE.txt" would be stripped, but for such URLs separate
        // mappings might exist.
        return if (licenseWithoutPrefix != declaredLicense) {
            fileSuffixesToRemove.fold(licenseWithoutPrefix) { license, extension ->
                license.removeSuffix(extension)
            }
        } else {
            licenseWithoutPrefix
        }
    }

    internal fun process(declaredLicense: String): SpdxExpression? {
        val licenseWithoutPrefixOrSuffix = preprocess(declaredLicense)
        val mappedLicense = SpdxDeclaredLicenseMapping.map(licenseWithoutPrefixOrSuffix)
        return (mappedLicense ?: parseLicense(licenseWithoutPrefixOrSuffix))?.normalize()?.takeIf { it.isValid() }
    }

    fun process(declaredLicenses: Collection<String>): ProcessedDeclaredLicense {
        val processedLicenses = mutableMapOf<String, SpdxExpression>()
        val unmapped = mutableListOf<String>()

        declaredLicenses.distinct().forEach { declaredLicense ->
            process(declaredLicense)?.let {
                processedLicenses[declaredLicense] = it
            } ?: run { unmapped += declaredLicense }
        }

        val spdxExpression = processedLicenses.values.distinct().takeUnless { it.isEmpty() }
            ?.reduce { left, right -> left and right }
        val mapped = processedLicenses.filterNot { (key, value) ->
            key.removeSurrounding("(", ")") == value.toString()
        }

        return ProcessedDeclaredLicense(spdxExpression, mapped, unmapped)
    }

    private fun parseLicense(declaredLicense: String) =
        try {
            declaredLicense.toSpdx()
        } catch (e: SpdxException) {
            log.debug { "Could not parse declared license '$declaredLicense': ${e.collectMessagesAsString()}" }
            null
        }
}

data class ProcessedDeclaredLicense(
    /**
     * The resulting SPDX expression, or null if no license could be mapped.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val spdxExpression: SpdxExpression?,

    /**
     * A map from the original declared license strings to the SPDX expressions they were mapped to. If the original
     * declared license string and the processed declared license are identical they are not contained in this map.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val mapped: Map<String, SpdxExpression> = emptyMap(),

    /**
     * Declared licenses that could not be mapped to an SPDX expression.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val unmapped: List<String> = emptyList()
) {
    companion object {
        @JvmField
        val EMPTY = ProcessedDeclaredLicense(
            spdxExpression = null,
            mapped = emptyMap(),
            unmapped = emptyList()
        )
    }

    /**
     * The list of all mapped and unmapped licenses.
     */
    @JsonIgnore
    val allLicenses = spdxExpression?.decompose().orEmpty().map { it.toString() } + unmapped
}
