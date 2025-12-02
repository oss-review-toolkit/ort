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

package org.ossreviewtoolkit.plugins.licensefactproviders.spdx

import java.net.URL

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProviderFactory
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseText

@OrtPlugin(
    id = "SPDX",
    displayName = "SPDX License Fact Provider",
    description = "A provider for SPDX license facts.",
    factory = LicenseFactProviderFactory::class
)
class SpdxLicenseFactProvider(
    override val descriptor: PluginDescriptor = SpdxLicenseFactProviderFactory.descriptor
) : LicenseFactProvider() {
    override fun getLicenseText(licenseId: String) =
        getLicenseTextResource(licenseId)?.readText()?.let {
            // It can be safely assumed that the license text is not blank as all SPDX license texts are non-blank.
            LicenseText(it)
        }

    override fun hasLicenseText(licenseId: String) = getLicenseTextResource(licenseId) != null

    private fun getLicenseTextResource(licenseId: String): URL? =
        if (licenseId.isNotEmpty()) {
            javaClass.getResource("/licenses/$licenseId") ?: javaClass.getResource("/exceptions/$licenseId")
        } else {
            null
        }
}
