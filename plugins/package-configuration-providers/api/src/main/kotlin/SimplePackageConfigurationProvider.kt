/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.VcsMatcher
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

/**
 * The default [PluginDescriptor] for a [SimplePackageConfigurationProvider]. Classes inheriting from this class
 * have to provide their own descriptor.
 */
private val pluginDescriptor = PluginDescriptor(
    id = "Simple",
    displayName = "Simple",
    description = "A simple package configuration provider, which provides a fixed set of package configurations."
)

/**
 * A [PackageConfigurationProvider] that provides the [PackageConfiguration]s specified in the collection. Throws an
 * exception if there is more than one configuration per [Identifier] and [Provenance].
 */
open class SimplePackageConfigurationProvider(
    override val descriptor: PluginDescriptor = pluginDescriptor,
    configurations: Collection<PackageConfiguration>
) : PackageConfigurationProvider {
    /**
     * A map that stores all package configurations by their [Identifier] for fast lookup. The version of the
     * [Identifier]s must be ignored because it could be a version range.
     */
    private val configurationsById: Map<Identifier, List<PackageConfiguration>>

    init {
        configurations.checkAtMostOneConfigurationPerIdAndProvenance()

        configurationsById = configurations.groupBy { it.id.copy(version = "") }
    }

    override fun getPackageConfigurations(packageId: Identifier, provenance: Provenance): List<PackageConfiguration> =
        configurationsById[packageId.copy(version = "")]?.filter { it.matches(packageId, provenance) }.orEmpty()
}

private fun Collection<PackageConfiguration>.checkAtMostOneConfigurationPerIdAndProvenance() {
    data class Key(val id: Identifier, val sourceArtifactUrl: String?, val vcsMatcher: VcsMatcher?)

    fun PackageConfiguration.key() = Key(id, sourceArtifactUrl, vcs)

    val configurationsWithSameMatcher = groupBy { it.key() }.filter { it.value.size > 1 }

    require(configurationsWithSameMatcher.isEmpty()) {
        "There must be at most one package configuration per Id and provenance, but found multiple for:\n" +
            "${configurationsWithSameMatcher.keys.joinToString(prefix = "  ", separator = "\n  ")}."
    }
}
