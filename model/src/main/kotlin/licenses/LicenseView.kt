/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.licenses

import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression

/**
 * A [LicenseView] provides a custom view on the licenses that belong to a [Package]. It can be used to filter the
 * licenses relevant to a [Rule] whereas the [licenseSources] is the filter criteria. Only the entry with the lowest
 * index in the given [licenseSources] which yields a non-empty result is used as filter criteria.
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
        val CONCLUDED_OR_REST = LicenseView(
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

    fun licenses(
        pkg: Package,
        detectedLicenses: List<SpdxSingleLicenseExpression>
    ): List<Pair<SpdxSingleLicenseExpression, LicenseSource>> {
        val declaredLicenses = pkg.declaredLicensesProcessed.spdxExpression?.decompose().orEmpty()
        val concludedLicenses = pkg.concludedLicense?.decompose().orEmpty()

        fun getLicenseForSources(
            sources: Collection<LicenseSource>
        ): List<Pair<SpdxSingleLicenseExpression, LicenseSource>> =
            sources.flatMap { source ->
                when (source) {
                    LicenseSource.DECLARED -> declaredLicenses
                    LicenseSource.DETECTED -> detectedLicenses
                    LicenseSource.CONCLUDED -> concludedLicenses
                }.map { license -> Pair(license, source) }.distinct()
            }

        licenseSources.forEach { sources ->
            val licenses = getLicenseForSources(sources)
            if (licenses.isNotEmpty()) {
                return licenses
            }
        }

        return emptyList()
    }

    /**
     * Use this [LicenseView] to filter a [ResolvedLicenseInfo]. This function will filter the [ResolvedLicense]s based
     * on the configured [LicenseSource]s, but it will not remove information from other sources. For example, if
     * [ONLY_CONCLUDED] is used, it will remove all [ResolvedLicense]s that do not have [LicenseSource.CONCLUDED] in
     * their [sources][ResolvedLicense.sources], but it will not remove any information about declared or detected
     * licenses from the [ResolvedLicense] object. This is so, because even if only concluded licenses are requested, it
     * can still be required to access the detected locations or copyrights for the licenses. This function only changes
     * [ResolvedLicenseInfo.licenses], all other properties of the class are kept unchanged.
     */
    fun filter(resolvedLicense: ResolvedLicenseInfo): ResolvedLicenseInfo =
        resolvedLicense.copy(licenses = filter(resolvedLicense.licenses))

    /**
     * Use this [LicenseView] to filter a list of [ResolvedLicense]s. This function will filter the licenses based
     * on the configured [LicenseSource]s, but it will not remove information from other sources. For example, if
     * [ONLY_CONCLUDED] is used, it will remove all [ResolvedLicense]s that do not have [LicenseSource.CONCLUDED] in
     * their [sources][ResolvedLicense.sources], but it will not remove any information about declared or detected
     * licenses from the [ResolvedLicense] object. This is so, because even if only concluded licenses are requested, it
     * can still be required to access the detected locations or copyrights for the licenses.
     */
    fun filter(resolvedLicenses: List<ResolvedLicense>): List<ResolvedLicense> {
        // Collect only the licenses instead of the full ResolvedLicense objects here, because calculating the hash
        // codes can be expensive for resolved licenses with many license and copyright findings.
        val result = mutableSetOf<SpdxSingleLicenseExpression>()

        run loop@{
            licenseSources.forEach { sources ->
                resolvedLicenses.filter { license ->
                    license.sources.any { it in sources }
                }.mapTo(result) { it.license }

                if (result.isNotEmpty()) return@loop
            }
        }

        return resolvedLicenses.filter { it.license in result }
    }
}
