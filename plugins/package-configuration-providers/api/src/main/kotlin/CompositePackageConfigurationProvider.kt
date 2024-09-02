/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

/**
 * A [PackageConfigurationProvider] that combines the provided [providers] into a single provider. The order of the
 * [providers] determines the order in which they are queried when calling [getPackageConfigurations].
 */
class CompositePackageConfigurationProvider(
    private val providers: List<PackageConfigurationProvider>
) : PackageConfigurationProvider {
    constructor(vararg providers: PackageConfigurationProvider) : this(providers.asList())

    override val descriptor = PluginDescriptor(
        id = "Composite",
        displayName = "Composite Package Configuration Provider",
        description = "A package configuration provider that combines multiple package configuration providers."
    )

    override fun getPackageConfigurations(packageId: Identifier, provenance: Provenance): List<PackageConfiguration> =
        providers.flatMap { it.getPackageConfigurations(packageId, provenance) }
}
