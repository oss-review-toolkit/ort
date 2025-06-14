/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.licensetextproviders.api

import org.ossreviewtoolkit.plugins.api.PluginDescriptor

/**
 * A [LicenseTextProvider] that aggregates multiple [LicenseTextProvider]s. When multiple [providers] have the text for
 * the same license ID, the text from the first provider that returns a non-null value is returned.
 */
class CompositeLicenseTextProvider(
    /**
     *  A list of [LicenseTextProvider]s to aggregate. The order of the list determines the precedence of the
     *  providers: the first provider in the list that has a license text for a given license ID will be used.
     */
    private val providers: List<LicenseTextProvider>
) : LicenseTextProvider {

    override val descriptor = PluginDescriptor(
        id = "Composite",
        displayName = "Composite License Text Provider",
        description = "A license text provider that aggregates multiple license text providers."
    )

    override fun getLicenseText(licenseId: String) =
        providers.asSequence().mapNotNull { it.getLicenseText(licenseId) }.firstOrNull()

    override fun hasLicenseText(licenseId: String) = providers.any { it.hasLicenseText(licenseId) }
}
