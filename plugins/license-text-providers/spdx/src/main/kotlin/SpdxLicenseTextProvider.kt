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

package org.ossreviewtoolkit.plugins.licensetextproviders.spdx

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.licensetextproviders.api.LicenseTextProvider
import org.ossreviewtoolkit.plugins.licensetextproviders.api.LicenseTextProviderFactory

@OrtPlugin(
    id = "SPDX",
    displayName = "SPDX License Text Provider",
    description = "A provider for SPDX license texts.",
    factory = LicenseTextProviderFactory::class
)
class SpdxLicenseTextProvider(
    override val descriptor: PluginDescriptor = SpdxLicenseTextProviderFactory.descriptor
) : LicenseTextProvider {
    override fun getLicenseText(licenseId: String) = getLicenseTextResource(licenseId)?.readText()

    override fun hasLicenseText(licenseId: String) = getLicenseTextResource(licenseId) != null

    private fun getLicenseTextResource(licenseId: String) = object {}.javaClass.getResource("/licenses/$licenseId")
}
