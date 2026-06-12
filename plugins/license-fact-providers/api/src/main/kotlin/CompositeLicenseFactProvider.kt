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

/**
 * A composition of multiple [LicenseFactProvider]s.
 */
class CompositeLicenseFactProvider(
    /**
     *  A list of [LicenseFactProvider]s to aggregate. The order of the list determines the precedence of the
     *  providers: the first provider in the list that has a fact for a given license ID will be used.
     */
    private val providers: List<LicenseFactProvider>
) {
    /** Return the [LicenseText] for the given [licenseId] and [id], or `null` if no valid text is available. */
    fun getLicenseText(licenseId: String, id: Identifier? = null): LicenseText? {
        val idSpecificLicenseText = if (id != null) {
            providers.firstNotNullOfOrNull { it.getIdSpecificLicenseText(licenseId, id) }
        } else {
            null
        }

        return idSpecificLicenseText ?: providers.firstNotNullOfOrNull { it.getLicenseText(licenseId) }
    }

    /** Return a non-blank license text for the given [licenseId] and [id], or `null` if no valid text is available. */
    @Deprecated("Java-only API", level = DeprecationLevel.HIDDEN)
    @JvmName("getLicenseText")
    fun getNonBlankLicenseText(licenseId: String, id: Identifier? = null): String? = getLicenseText(licenseId, id)?.text

    /** Return `true´ if this provider has a license text for the given [licenseId] and [id]. */
    fun hasLicenseText(licenseId: String, id: Identifier? = null): Boolean {
        val hasIdSpecificLicenseText = if (id != null) {
            providers.any { it.hasIdSpecificLicenseText(licenseId, id) }
        } else {
            false
        }

        return hasIdSpecificLicenseText || providers.any { it.hasLicenseText(licenseId) }
    }
}
