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

package org.ossreviewtoolkit.plugins.licensefactproviders.dir

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProviderFactory
import org.ossreviewtoolkit.utils.ort.ORT_CUSTOM_LICENSE_TEXTS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

/** The configuration for the directory-based license fact provider. */
data class DirLicenseFactProviderConfig(
    /** The directory that contains the license texts. */
    val licenseTextDir: String
)

@OrtPlugin(
    displayName = "Default Directory License Fact Provider",
    description = "A license fact provider that reads license information from the default directory.",
    factory = LicenseFactProviderFactory::class
)
class DefaultDirLicenseFactProvider(descriptor: PluginDescriptor = DefaultDirLicenseFactProviderFactory.descriptor) :
    DirLicenseFactProvider(
        descriptor,
        DirLicenseFactProviderConfig(
            licenseTextDir = ortConfigDirectory.resolve(ORT_CUSTOM_LICENSE_TEXTS_DIRNAME).absolutePath
        )
    )

@OrtPlugin(
    id = "Dir",
    displayName = "Directory License Fact Provider",
    description = "A license fact provider that reads license information from a local directory. The files must be " +
        "named after the SPDX-conform license IDs, e.g., 'Apache-2.0' or 'LicenseRef-custom-license'.",
    factory = LicenseFactProviderFactory::class
)
open class DirLicenseFactProvider(
    override val descriptor: PluginDescriptor = DirLicenseFactProviderFactory.descriptor,
    private val config: DirLicenseFactProviderConfig
) : LicenseFactProvider {
    private val licenseTextDir = File(config.licenseTextDir).also {
        if (!it.isDirectory) {
            logger.warn { "The license text directory '${it.absolutePath}' does not exist or is not a directory." }
        }
    }

    override fun getLicenseText(licenseId: String) = getLicenseTextFile(licenseId)?.readText()

    override fun hasLicenseText(licenseId: String) = getLicenseTextFile(licenseId) != null

    private fun getLicenseTextFile(licenseId: String) = licenseTextDir.resolve(licenseId).takeIf { it.isFile }
}
