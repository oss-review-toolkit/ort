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

package org.ossreviewtoolkit.plugins.packageconfigurationproviders.dir

import java.io.File
import java.io.IOException

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.utils.common.getDuplicates
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CONFIGURATIONS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

data class DirPackageConfigurationProviderConfig(
    /**
     * The path of the package configuration directory.
     */
    val path: String,

    /**
     * A flag to denote whether the path is required to exist.
     */
    @OrtPluginOption(defaultValue = "false")
    val mustExist: Boolean
)

@OrtPlugin(
    displayName = "Default Directory",
    description = "A package configuration provider that loads package curations from the default directory.",
    factory = PackageConfigurationProviderFactory::class
)
class DefaultDirPackageConfigurationProvider(descriptor: PluginDescriptor) : DirPackageConfigurationProvider(
    descriptor,
    DirPackageConfigurationProviderConfig(
        path = ortConfigDirectory.resolve(ORT_PACKAGE_CONFIGURATIONS_DIRNAME).absolutePath,
        mustExist = false
    )
)

/**
 * A [PackageConfigurationProvider] that loads [PackageConfiguration]s from all given package configuration files.
 * Supports all file formats specified in [FileFormat].
 */
@OrtPlugin(
    displayName = "Directory",
    description = "Provides package configurations from a directory.",
    factory = PackageConfigurationProviderFactory::class
)
open class DirPackageConfigurationProvider(
    descriptor: PluginDescriptor = DirPackageConfigurationProviderFactory.descriptor,
    vararg paths: File?
) : SimplePackageConfigurationProvider(descriptor, readConfigurationFiles(paths.filterNotNull())) {
    constructor(vararg paths: File?) : this(DirPackageConfigurationProviderFactory.descriptor, *paths)

    constructor(descriptor: PluginDescriptor, config: DirPackageConfigurationProviderConfig) : this(
        descriptor,
        File(config.path).takeUnless { !it.exists() && !config.mustExist }
    )

    companion object {
        fun readConfigurationFiles(paths: Collection<File>): List<PackageConfiguration> {
            val allConfigurations = mutableListOf<Pair<PackageConfiguration, File>>()

            val configurationFiles = paths.flatMap {
                require(it.exists()) {
                    "The path '$it' does not exist."
                }

                if (it.isDirectory) FileFormat.findFilesWithKnownExtensions(it) else listOf(it)
            }.filterNot { it.length() == 0L }

            configurationFiles.map { configurationFile ->
                val configuration = runCatching {
                    configurationFile.readValue<PackageConfiguration>()
                }.getOrElse {
                    throw IOException(
                        "Failed parsing package configuration(s) from '${configurationFile.absolutePath}'.",
                        it
                    )
                }

                allConfigurations += configuration to configurationFile
            }

            val duplicates = allConfigurations.getDuplicates { it.first }
            if (duplicates.isNotEmpty()) {
                val duplicatesInfo = buildString {
                    duplicates.forEach { (packageConfiguration, origins) ->
                        appendLine("Configurations for '${packageConfiguration.id.toCoordinates()}' found in all of:")
                        val files = origins.joinToString(separator = "\n") { (_, file) -> file.absolutePath }
                        appendLine(files.prependIndent())
                    }
                }

                throw DuplicatedConfigurationException(
                    "Duplicate package configuration found:\n${duplicatesInfo.prependIndent()}"
                )
            }

            return allConfigurations.unzip().first
        }
    }
}

private class DuplicatedConfigurationException(message: String?) : Exception(message)
