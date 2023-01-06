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

package org.ossreviewtoolkit.analyzer

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.config.PackageCurationProviderConfiguration
import org.ossreviewtoolkit.utils.common.ConfigurablePluginFactory
import org.ossreviewtoolkit.utils.common.NamedPlugin

/**
 * The extension point for [PackageCurationProvider]s.
 */
interface PackageCurationProviderFactory<CONFIG> : ConfigurablePluginFactory<PackageCurationProvider> {
    companion object {
        val ALL = NamedPlugin.getAll<PackageCurationProviderFactory<*>>()

        fun create(configurations: List<PackageCurationProviderConfiguration>) =
            // Reverse the list so that curations from providers with higher priority are applied later and can
            // overwrite curations from providers with lower priority.
            configurations.filter { it.enabled }.map { ALL.getValue(it.name).create(it.config) }.asReversed()
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

/**
 * A provider for [PackageCuration]s.
 */
fun interface PackageCurationProvider {
    companion object {
        /**
         * A provider that does not provide any curations.
         */
        @JvmField
        val EMPTY = PackageCurationProvider { emptyMap() }
    }

    /**
     * Return all available [PackageCuration]s for the provided [pkgIds], associated by the package's [Identifier]. Each
     * list of curations must be non-empty; if no curation is available for a package, the returned map must not contain
     * a key for that package's identifier at all.
     */
    // TODO: Maybe make this a suspend function, then all implementing classes could deal with coroutines more easily.
    fun getCurationsFor(pkgIds: Collection<Identifier>): Map<Identifier, List<PackageCuration>>
}
