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
     **/
    open fun hasLicenseTextForId(singleLicenseExpression: String, id: Identifier): Boolean =
        getLicenseTextForId(singleLicenseExpression, id) != null

    /**
     * Return an id-specific [LicenseText] for the given [singleLicenseExpression] and [id], or `null` if no such text
     * is available.
     */
    open fun getLicenseTextForId(singleLicenseExpression: String, id: Identifier): LicenseText? = null

    /**
     * Return an id-specific [LicenseText] for the given [singleLicenseExpression] and [id] if available, or the
     * non-id-specific license text for [singleLicenseExpression], or `null` if no such text is available.
     */
    fun hasLicenseText(singleLicenseExpression: String, id: Identifier): Boolean {
        if (hasLicenseText(singleLicenseExpression, id)) return true

        val spdxExpression = SpdxSingleLicenseExpression.parse(singleLicenseExpression)
        if (!hasLicenseText(spdxExpression.simpleLicense())) return false

        return spdxExpression.exception()?.let { hasLicenseText(it) } == true
    }

    /**
     * Return an id-specific [LicenseText] for the given [singleLicenseExpression] and [id] if available, or the
     * non-id-specific license text for [singleLicenseExpression], or `null` if no such text is available.
     */
    fun getLicenseText(singleLicenseExpression: String, id: Identifier): LicenseText? =
        getLicenseTextForId(singleLicenseExpression, id) ?: LicenseText(
            buildString {
                val spdxExpression = SpdxSingleLicenseExpression.parse(singleLicenseExpression)
                val licenseText = getLicenseText(spdxExpression.simpleLicense()) ?: return null
                val exceptionText = spdxExpression.exception()?.let { getLicenseText(it) ?: return null }

                append(licenseText.text)
                if (exceptionText != null) {
                    appendLine()
                    append(exceptionText.text)
                }
            }.trim()
        )

    /**
     * Return a non-blank license text for the given [licenseOrExceptionId], or `null` if no valid text is available.
     */
    @Deprecated("Java-only API", level = DeprecationLevel.HIDDEN)
    @JvmName("getLicenseText")
    fun getNonBlankLicenseText(licenseOrExceptionId: String): String? = getLicenseText(licenseOrExceptionId)?.text

    /**
     * Return a non-blank id-specific [LicenseText] for the given [singleLicenseExpression] and [id], or `null` if no
     * such text is available.
     */
    @Deprecated("Java-only API", level = DeprecationLevel.HIDDEN)
    @JvmName("getLicenseTextForId")
    fun getNonBlankLicenseTextForId(singleLicenseExpression: String, id: Identifier): String? =
        getLicenseTextForId(singleLicenseExpression, id)?.text

    /**
     * Return a non-blank id-specific [LicenseText] for the given [singleLicenseExpression] and [id] if available, or
     * the non-id-specific license text for [singleLicenseExpression], or `null` if no such text is available.
     */
    @Deprecated("Java-only API", level = DeprecationLevel.HIDDEN)
    @JvmName("getLicenseText")
    fun getNonBlankLicenseText(singleLicenseExpression: String, id: Identifier): String? =
        getLicenseText(singleLicenseExpression, id)?.text
}
