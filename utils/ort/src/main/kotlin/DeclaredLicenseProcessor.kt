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
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.annotation.JsonSerialize

import org.ossreviewtoolkit.utils.common.unquote
import org.ossreviewtoolkit.utils.spdx.SpdxCompoundExpression
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxDeclaredLicenseMapping
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxOperator
import org.ossreviewtoolkit.utils.spdx.toSpdxOrNull

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

    /**
     * Return a declared license that has known URL prefixes and file suffixes stripped, so that the remaining string
     * can more generally be mapped in further processing steps.
     */
    internal fun stripUrlSurroundings(declaredLicense: String): String {
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

    /**
     * Try to map the [declaredLicense] to an [SpdxExpression] by taking both built-in mappings and
     * [customLicenseMapping] into account. As a special case, a license may be mapped to [SpdxConstants.NONE] to mark
     * it as something that is not a license, like a copyright that was accidentally entered as a license. Return the
     * successfully mapped license expression, or null if the declared license could not be mapped.
     */
    fun process(
        declaredLicense: String,
        customLicenseMapping: Map<String, SpdxExpression> = emptyMap()
    ): SpdxExpression? {
        val strippedLicense = stripUrlSurroundings(declaredLicense)

        val mappedLicense = customLicenseMapping[strippedLicense]
            // When looking up built-in mappings, try some variations of the license name.
            ?: SpdxDeclaredLicenseMapping.map(strippedLicense)
            ?: SpdxDeclaredLicenseMapping.map(strippedLicense.unquote())
            ?: SpdxDeclaredLicenseMapping.map(strippedLicense.removePrefix(SpdxConstants.TAG).trim())

        val processedLicense = mappedLicense ?: strippedLicense.toSpdxOrNull()
        return processedLicense?.normalize()?.takeIf {
            it.isValid(SpdxExpression.Strictness.ALLOW_LICENSEREF_EXCEPTIONS) || it.toString() == SpdxConstants.NONE
        }
    }

    /**
     * Try to map all [declaredLicenses] to a (compound) [SpdxExpression] by taking both built-in mappings and
     * [customLicenseMapping] into account. As a special case, a license may be mapped to [SpdxConstants.NONE] to mark
     * it as something that is not a license, like a copyright that was accidentally entered as a license. Multiple
     * declared licenses are reduced to a [SpdxCompoundExpression] using the specified [operator]. Return a
     * [ProcessedDeclaredLicense] which contains the final [SpdxExpression] (or null if none of the declared licenses
     * could be mapped), and the mapped and unmapped licenses respectively.
     */
    fun process(
        declaredLicenses: Set<String>,
        customLicenseMapping: Map<String, SpdxExpression> = emptyMap(),
        operator: SpdxOperator = SpdxOperator.AND
    ): ProcessedDeclaredLicense {
        val processedLicenses = mutableMapOf<String, SpdxExpression>()
        val unmapped = mutableSetOf<String>()

        declaredLicenses.forEach { declaredLicense ->
            process(declaredLicense, customLicenseMapping)?.let {
                processedLicenses[declaredLicense] = it
            } ?: run {
                unmapped += declaredLicense
            }
        }

        val spdxExpression = processedLicenses.values.toSet().filter {
            it.toString() != SpdxConstants.NONE
        }.reduceOrNull { left, right ->
            SpdxCompoundExpression(left, operator, right)
        }

        val mapped = processedLicenses.filterNot { (key, value) ->
            key.removeSurrounding("(", ")") == value.toString()
        }

        return ProcessedDeclaredLicense(spdxExpression, mapped, unmapped)
    }
}

data class ProcessedDeclaredLicense(
    /**
     * The resulting SPDX expression, or null if no license could be mapped.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(converter = SpdxExpressionSortedConverter::class)
    val spdxExpression: SpdxExpression?,

    /**
     * A map from the original declared license strings to the SPDX expressions they were mapped to. If the original
     * declared license string and the processed declared license are identical they are not contained in this map.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyOrder(alphabetic = true)
    val mapped: Map<String, SpdxExpression> = emptyMap(),

    /**
     * Declared licenses that could not be mapped to an SPDX expression.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(converter = StringSortedSetConverter::class)
    val unmapped: Set<String> = emptySet()
) {
    companion object {
        @JvmField
        val EMPTY = ProcessedDeclaredLicense(
            spdxExpression = null,
            mapped = emptyMap(),
            unmapped = emptySet()
        )
    }

    /**
     * The list of all mapped and unmapped licenses.
     */
    @JsonIgnore
    val allLicenses = decompose().map { it.toString() } + unmapped

    /**
     * [Decompose][SpdxExpression.decompose] the [spdxExpression] or return an empty string if it is null.
     */
    fun decompose() = spdxExpression?.decompose().orEmpty()
}
