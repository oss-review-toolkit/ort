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

package org.ossreviewtoolkit.plugins.licensefactproviders.api

import org.ossreviewtoolkit.plugins.api.PluginDescriptor

/**
 * A [LicenseFactProvider] that aggregates multiple [LicenseFactProvider]s.
 */
class CompositeLicenseFactProvider(
    /**
     *  A list of [LicenseFactProvider]s to aggregate. The order of the list determines the precedence of the
     *  providers: the first provider in the list that has a fact for a given license ID will be used.
     */
    private val providers: List<LicenseFactProvider>
) : LicenseFactProvider {
    override val descriptor = PluginDescriptor(
        id = "Composite",
        displayName = "Composite License Fact Provider",
        description = "A license fact provider that aggregates multiple license fact providers."
    )

    override fun getLicenseText(licenseId: String) = providers.firstNotNullOfOrNull { it.getLicenseText(licenseId) }

    override fun hasLicenseText(licenseId: String) = providers.any { it.hasLicenseText(licenseId) }
}
