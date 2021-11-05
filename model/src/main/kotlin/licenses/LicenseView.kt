/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.licenses

import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice

/**
 * A [LicenseView] provides a custom view on the licenses that belong to a [Package]. It can be used to filter the
 * licenses relevant to an evaluator rule whereas the [licenseSources] is the filter criteria. Only the entry with the
 * lowest index in the given [licenseSources] which yields a non-empty result is used as filter criteria.
 */
class LicenseView(vararg licenseSources: Set<LicenseSource>) {
    companion object {
        /**
         * Return all licenses.
         */
        val ALL = LicenseView(setOf(LicenseSource.DECLARED, LicenseSource.DETECTED, LicenseSource.CONCLUDED))

        /**
         * Return only the concluded licenses if they exist, otherwise return declared and detected licenses.
         */
        val CONCLUDED_OR_DECLARED_AND_DETECTED = LicenseView(
            setOf(LicenseSource.CONCLUDED),
            setOf(LicenseSource.DECLARED, LicenseSource.DETECTED)
        )

        /**
         * Return only the concluded licenses if they exist, or return only the declared licenses if they exist, or
         * return the detected licenses.
         */
        val CONCLUDED_OR_DECLARED_OR_DETECTED = LicenseView(
            setOf(LicenseSource.CONCLUDED),
            setOf(LicenseSource.DECLARED),
            setOf(LicenseSource.DETECTED)
        )

        /**
         * Return only the concluded licenses if they exist, otherwise return detected licenses.
         */
        val CONCLUDED_OR_DETECTED = LicenseView(
            setOf(LicenseSource.CONCLUDED),
            setOf(LicenseSource.DETECTED)
        )

        /**
         * Return only the concluded licenses.
         */
        val ONLY_CONCLUDED = LicenseView(setOf(LicenseSource.CONCLUDED))

        /**
         * Return only the declared licenses.
         */
        val ONLY_DECLARED = LicenseView(setOf(LicenseSource.DECLARED))

        /**
         * Return only the detected licenses.
         */
        val ONLY_DETECTED = LicenseView(setOf(LicenseSource.DETECTED))
    }

    private val licenseSources = licenseSources.toSet()

    /**
     * Use this [LicenseView] to filter a [ResolvedLicenseInfo]. This function will filter the [ResolvedLicense]s based
     * on the configured [LicenseSource]s, but it will not remove information from other sources. For example, if
     * [ONLY_CONCLUDED] is used, it will remove all [ResolvedLicense]s that do not have [LicenseSource.CONCLUDED] in
     * their [sources][ResolvedLicense.sources], but it will not remove any information about declared or detected
     * licenses from the [ResolvedLicense] object. This is so, because even if only concluded licenses are requested, it
     * can still be required to access the detected locations or copyrights for the licenses. This function only changes
     * [ResolvedLicenseInfo.licenses], all other properties of the class are kept unchanged.
     *
     * If [filterSources] is true, only the license sources are kept that caused the [ResolvedLicense] to be part of the
     * result. Otherwise all original license sources are kept.
     */
    @JvmOverloads
    fun filter(resolvedLicense: ResolvedLicenseInfo, filterSources: Boolean = false): ResolvedLicenseInfo =
        resolvedLicense.copy(licenses = filter(resolvedLicense.licenses, filterSources))

    /**
     * Use this [LicenseView] to filter a list of [ResolvedLicense]s. This function will filter the licenses based
     * on the configured [LicenseSource]s, but it will not remove information from other sources. For example, if
     * [ONLY_CONCLUDED] is used, it will remove all [ResolvedLicense]s that do not have [LicenseSource.CONCLUDED] in
     * their [sources][ResolvedLicense.sources], but it will not remove any information about declared or detected
     * licenses from the [ResolvedLicense] object. This is so, because even if only concluded licenses are requested, it
     * can still be required to access the detected locations or copyrights for the licenses.
     *
     * If [filterSources] is true, only the license sources are kept that caused the [ResolvedLicense] to be part of the
     * result. Otherwise all original license sources are kept.
     */
    @JvmOverloads
    fun filter(resolvedLicenses: List<ResolvedLicense>, filterSources: Boolean = false): List<ResolvedLicense> {
        // Collect only the licenses instead of the full ResolvedLicense objects here, because calculating the hash
        // codes can be expensive for resolved licenses with many license and copyright findings.
        val remainingLicenses = mutableSetOf<SpdxSingleLicenseExpression>()
        val remainingSources = mutableMapOf<SpdxSingleLicenseExpression, Set<LicenseSource>>()

        run loop@{
            licenseSources.forEach { sources ->
                val matchingLicenses = resolvedLicenses.filter { license ->
                    license.sources.any { it in sources }
                }

                matchingLicenses.mapTo(remainingLicenses) { it.license }
                matchingLicenses.associateTo(remainingSources) {
                    Pair(it.license, it.sources.intersect(sources))
                }

                if (remainingLicenses.isNotEmpty()) return@loop
            }
        }

        return resolvedLicenses.filter { it.license in remainingLicenses }.let { result ->
            if (filterSources) {
                result.map { resolvedLicense ->
                    val remainingOriginalExpressions = resolvedLicense.originalExpressions.filterTo(mutableSetOf()) {
                        it.source in remainingSources.getValue(resolvedLicense.license)
                    }

                    resolvedLicense.copy(originalExpressions = remainingOriginalExpressions)
                }
            } else {
                result
            }
        }
    }

    /**
     * Use this [LicenseView] to filter a [ResolvedLicenseInfo]. This function will filter the [ResolvedLicense]s based
     * on the configured [LicenseSource]s, but it will not remove information from other sources. For example, if
     * [ONLY_CONCLUDED] is used, it will remove all [ResolvedLicense]s that do not have [LicenseSource.CONCLUDED] in
     * their [sources][ResolvedLicense.sources], but it will not remove any information about declared or detected
     * licenses from the [ResolvedLicense] object. This is so, because even if only concluded licenses are requested, it
     * can still be required to access the detected locations or copyrights for the licenses. This function only changes
     * [ResolvedLicenseInfo.licenses], all other properties of the class are kept unchanged.
     *
     * If [filterSources] is true, only the license sources are kept that caused the [ResolvedLicense] to be part of the
     * result. Otherwise all original license sources are kept.
     *
     * Additionally, the [licenseChoices] are applied, removing all licenses that were not chosen from the
     * [ResolvedLicenseInfo]. The information is still obtainable through the [ResolvedLicense.originalExpressions].
     */
    @JvmOverloads
    fun filter(
        resolvedLicenseInfo: ResolvedLicenseInfo,
        licenseChoices: List<SpdxLicenseChoice>,
        filterSources: Boolean = false
    ): ResolvedLicenseInfo {
        val filteredResolvedLicenseInfo = filter(resolvedLicenseInfo, filterSources)

        return filteredResolvedLicenseInfo.applyChoices(licenseChoices, this)
    }
}
