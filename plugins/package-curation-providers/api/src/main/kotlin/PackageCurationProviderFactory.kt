/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.api

import org.ossreviewtoolkit.model.ResolvedPackageCurations.Companion.REPOSITORY_CONFIGURATION_PROVIDER_ID
import org.ossreviewtoolkit.model.config.PackageCurationProviderConfiguration
import org.ossreviewtoolkit.model.utils.PackageCurationProvider
import org.ossreviewtoolkit.utils.common.ConfigurablePluginFactory
import org.ossreviewtoolkit.utils.common.Plugin
import org.ossreviewtoolkit.utils.common.getDuplicates

/**
 * The extension point for [PackageCurationProvider]s.
 */
interface PackageCurationProviderFactory<CONFIG> : ConfigurablePluginFactory<PackageCurationProvider> {
    companion object {
        val ALL = Plugin.getAll<PackageCurationProviderFactory<*>>()

        /**
         * Return a new (identifier, provider instance) tuple for each
         * [enabled][PackageCurationProviderConfiguration.enabled] provider configuration in [configurations] ordered
         * highest-priority first. The given [configurations] must be ordered highest-priority first as well.
         */
        fun create(
            configurations: List<PackageCurationProviderConfiguration>
        ): List<Pair<String, PackageCurationProvider>> =
            configurations.filter {
                it.enabled
            }.map {
                it.id to ALL.getValue(it.type).create(it.config)
            }.apply {
                require(none { (id, _) -> id.isBlank() }) {
                    "The configuration contains a package curations provider with a blank ID which is not allowed."
                }

                val duplicateIds = getDuplicates { (id, _) -> id }.keys
                require(duplicateIds.isEmpty()) {
                    "Found multiple package curation providers for the IDs ${duplicateIds.joinToString()}, which is " +
                            "not allowed. Please configure a unique ID for each package curation provider."
                }

                require(none { (id, _) -> id == REPOSITORY_CONFIGURATION_PROVIDER_ID }) {
                    "Found a package curation provider which uses '$REPOSITORY_CONFIGURATION_PROVIDER_ID' as its id " +
                            "which is reserved and not allowed."
                }
            }
    }

    override fun create(config: Map<String, String>): PackageCurationProvider = create(parseConfig(config))

    /**
     * Create a new [PackageCurationProvider] with [config].
     */
    fun create(config: CONFIG): PackageCurationProvider

    /**
     * Parse the [config] map into an object.
     */
    fun parseConfig(config: Map<String, String>): CONFIG
}
