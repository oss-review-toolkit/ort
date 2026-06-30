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

/** A provider for license facts. */
abstract class LicenseFactProvider : Plugin {
    /** Return `true´ if this provider has a license text for the given [licenseOrExceptionId]. */
    abstract fun hasLicenseText(licenseOrExceptionId: String): Boolean

    /** Return a [LicenseText] for the given [licenseOrExceptionId], or `null` if no valid text is available. */
    abstract fun getLicenseText(licenseOrExceptionId: String): LicenseText?

    /** Return `true´ if this provider has an id-specific license text for the given [licenseId] and [id]. */
    open fun hasLicenseTextForId(licenseId: String, id: Identifier): Boolean =
        getLicenseTextForId(licenseId, id) != null

    /**
     * Return an id-specific [LicenseText] for the given [licenseId] and [id], or `null` if no such text is
     * available.
     */
    open fun getLicenseTextForId(licenseId: String, id: Identifier): LicenseText? = null

    /**
     * Return an id-specific [LicenseText] for the given [licenseId] and [id] if available, or the non-id-specific
     * license text for [licenseId], or `null` if no such text is available.
     */
    fun hasLicenseText(licenseId: String, id: Identifier): Boolean =
        hasLicenseTextForId(licenseId, id) || hasLicenseText(licenseId)

    /**
     * Return an id-specific [LicenseText] for the given [licenseId] and [id] if available, or the non-id-specific
     * license text for [licenseId], or `null` if no such text is available.
     */
    fun getLicenseText(licenseId: String, id: Identifier): LicenseText? =
        getLicenseTextForId(licenseId, id) ?: getLicenseText(licenseId)

    /** Return a non-blank license text for the given [licenseId], or `null` if no valid text is available. */
    @Deprecated("Java-only API", level = DeprecationLevel.HIDDEN)
    @JvmName("getLicenseText")
    fun getNonBlankLicenseText(licenseOrExceptionId: String): String? = getLicenseText(licenseOrExceptionId)?.text

    /**
     * Return a non-blank id-specific [LicenseText] for the given [licenseId] and [id], or `null` if no such text is
     * available.
     */
    @Deprecated("Java-only API", level = DeprecationLevel.HIDDEN)
    @JvmName("getLicenseTextForId")
    fun getNonBlankLicenseTextForId(licenseId: String, id: Identifier): String? =
        getLicenseTextForId(licenseId, id)?.text

    /**
     * Return a non-blank id-specific [LicenseText] for the given [licenseId] and [id] if available, or the
     * non-id-specific license text for [licenseId], or `null` if no such text is available.
     */
    @Deprecated("Java-only API", level = DeprecationLevel.HIDDEN)
    @JvmName("getLicenseText")
    fun getNonBlankLicenseText(licenseId: String, id: Identifier): String? = getLicenseText(licenseId, id)?.text
}
