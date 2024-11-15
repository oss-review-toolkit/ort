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

package org.ossreviewtoolkit.plugins.packageconfigurationproviders.api

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.config.ProviderPluginConfiguration
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.api.PluginFactory
import org.ossreviewtoolkit.utils.common.getDuplicates

/**
 * The extension point for [PackageConfigurationProvider]s.
 */
interface PackageConfigurationProviderFactory : PluginFactory<PackageConfigurationProvider> {
    companion object {
        /**
         * All [package configuration provider factories][PackageConfigurationProviderFactory] available in the
         * classpath, associated by their ids.
         */
        val ALL by lazy { PluginFactory.getAll<PackageConfigurationProviderFactory, PackageConfigurationProvider>() }

        /**
         * Create a new (identifier, provider instance) tuple for each
         * [enabled][ProviderPluginConfiguration.enabled] provider configuration in [configurations] ordered
         * highest priority first. The given [configurations] must be ordered highest-priority first as well.
         */
        fun create(
            configurations: List<ProviderPluginConfiguration>
        ): List<Pair<String, PackageConfigurationProvider>> =
            configurations.filter {
                it.enabled
            }.mapNotNull {
                ALL[it.type]?.let { factory ->
                    it.id to factory.create(PluginConfig(it.options, it.secrets))
                }.also { factory ->
                    factory ?: logger.error {
                        "Configuration provider of type '${it.type}' is enabled in configuration but not available " +
                            "in the classpath."
                    }
                }
            }.apply {
                require(none { (id, _) -> id.isBlank() }) {
                    "The configuration contains a package configuration provider with a blank ID which is not allowed."
                }

                val duplicateIds = getDuplicates { (id, _) -> id }.keys
                require(duplicateIds.isEmpty()) {
                    "Found multiple package configuration providers for the IDs ${duplicateIds.joinToString()}, " +
                        "which is not allowed. Please configure a unique ID for each package configuration provider."
                }
            }
    }
}
