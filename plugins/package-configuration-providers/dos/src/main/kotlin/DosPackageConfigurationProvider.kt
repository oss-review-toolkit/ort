/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
package org.ossreviewtoolkit.plugins.packageconfigurationproviders.dos

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.utils.PackageConfigurationProvider
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.utils.common.Options

data class DosPackageConfigurationProviderConfig(
    /** The URL where the DOS service is running. */
    val serverUrl: String
)

open class DosPackageConfigurationProviderFactory :
    PackageConfigurationProviderFactory<DosPackageConfigurationProviderConfig> {
    override val type = "DOS"

    override fun create(config: DosPackageConfigurationProviderConfig) = DosPackageConfigurationProvider(config)

    override fun parseConfig(options: Options, secrets: Options) =
        DosPackageConfigurationProviderConfig(
            serverUrl = options.getValue("serverUrl").toString()
        )
}
/**
 * A [PackageConfigurationProvider] that loads [PackageConfiguration]s from all given package configuration files.
 * Supports all file formats specified in [FileFormat].
 */
class DosPackageConfigurationProvider(config: DosPackageConfigurationProviderConfig) : PackageConfigurationProvider {
    private companion object : Logging
    private val serverUrl = config.serverUrl
    override fun getPackageConfigurations(packageId: Identifier, provenance: Provenance): List<PackageConfiguration> {
        logger.info { "Loading package configuration for ${packageId.toPurl()} from $serverUrl." }
        return emptyList()
    }
}

