/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.licensefactproviders.api

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.plugins.api.Plugin
import org.ossreviewtoolkit.utils.spdxexpression.SpdxSingleLicenseExpression

/** A provider for license facts. */
abstract class LicenseFactProvider : Plugin {
    /** Return `true´ if this provider has a license text for the given [licenseOrExceptionId]. */
    abstract fun hasLicenseText(licenseOrExceptionId: String): Boolean

    /** Return a [LicenseText] for the given [licenseOrExceptionId], or `null` if no valid text is available. */
    abstract fun getLicenseText(licenseOrExceptionId: String): LicenseText?

    /**
     * Return `true´ if this provider has an id-specific license text for the given [singleLicenseExpression] and [id].
     */
    open fun hasLicenseTextsForId(singleLicenseExpression: String, id: Identifier): Boolean =
        getLicenseTextsForId(singleLicenseExpression, id).isNotEmpty()

    /**
     * Return all id-specific [LicenseText]s for the given [singleLicenseExpression] and [id], or an empty set if no
     * such text is available.
     */
    open fun getLicenseTextsForId(singleLicenseExpression: String, id: Identifier): Set<LicenseText> = emptySet()

    /**
     * Return `true` if any id-specific [LicenseText]s for the given [singleLicenseExpression] and [id], or if a
     * non-id-specific license text for [singleLicenseExpression] is available, or `false` otherwise.
     */
    fun hasLicenseText(singleLicenseExpression: String, id: Identifier): Boolean {
        if (hasLicenseTextsForId(singleLicenseExpression, id)) return true

        val spdxExpression = runCatching { SpdxSingleLicenseExpression.parse(singleLicenseExpression) }.getOrElse {
            logger.info { "Could not parse '$singleLicenseExpression' as a single license expression." }
            return false
        }

        return listOfNotNull(spdxExpression.simpleLicense(), spdxExpression.exception()).all { hasLicenseText(it) }
    }

    /**
     * Return an id-specific [LicenseText] for the given [singleLicenseExpression] and [id] if available, or the
     * non-id-specific license text for [singleLicenseExpression], or `null` if no such text is available.
     */
    fun getLicenseTexts(singleLicenseExpression: String, id: Identifier): Set<LicenseText> =
        getLicenseTextsForId(singleLicenseExpression, id).ifEmpty {
            val spdxExpression = runCatching {
                SpdxSingleLicenseExpression.parse(singleLicenseExpression)
            }.getOrElse {
                logger.info { "Could not parse '$singleLicenseExpression' as a single license expression." }
                return emptySet()
            }

            listOfNotNull(spdxExpression.simpleLicense(), spdxExpression.exception())
                .map { getLicenseText(it) ?: return emptySet() }
                .joinToString("\n") { it.text }
                .let { setOf(LicenseText(it)) }
        }

    /**
     * Return a non-blank license text for the given [licenseOrExceptionId], or `null` if no valid text is available.
     */
    @Deprecated("Java-only API", level = DeprecationLevel.HIDDEN)
    @JvmName("getLicenseText")
    fun getNonBlankLicenseText(licenseOrExceptionId: String): String? = getLicenseText(licenseOrExceptionId)?.text

    @JvmName("getLicenseTextsStringForId")
    fun getNonBlankLicenseTextsForId(singleLicenseExpression: String, id: Identifier): Set<String> =
        getLicenseTextsForId(singleLicenseExpression, id).mapTo(mutableSetOf()) { it.text }

    /**
     * Return all non-blank id-specific [LicenseText] for the given [singleLicenseExpression] and [id] if available, or
     * the non-id-specific license text for [singleLicenseExpression], or an empty set if no such text is available.
     */
    @Deprecated("Java-only API", level = DeprecationLevel.HIDDEN)
    @JvmName("getLicenseTextsString")
    fun getNonBlankLicenseTexts(singleLicenseExpression: String, id: Identifier): Set<String> =
        getLicenseTexts(singleLicenseExpression, id).mapTo(mutableSetOf()) { it.text }
}
