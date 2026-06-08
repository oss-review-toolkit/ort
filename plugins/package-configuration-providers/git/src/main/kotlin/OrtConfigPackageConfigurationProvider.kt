/*
 * Copyright (C) 2023 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packageconfigurationproviders.git

import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory

private const val ORT_CONFIG_REPOSITORY_BRANCH = "main"
internal const val ORT_CONFIG_REPOSITORY_URL = "https://github.com/oss-review-toolkit/ort-config.git"
private const val PACKAGE_CONFIGURATIONS_DIR = "package-configurations"

/**
 * A [PackageConfigurationProvider] that provides [PackageConfiguration]s from the
 * [ort-config repository](https://github.com/oss-review-toolkit/ort-config).
 */
@OrtPlugin(
    id = "ORTConfig",
    displayName = "ORT Config Repository",
    summary = "A package configuration provider that loads package configurations from the ort-config repository.",
    factory = PackageConfigurationProviderFactory::class
)
class OrtConfigPackageConfigurationProvider(
    override val descriptor: PluginDescriptor = OrtConfigPackageConfigurationProviderFactory.descriptor
) : GitPackageConfigurationProvider(
    descriptor = descriptor,
    config = GitPackageConfigurationProviderConfig(
        repositoryUrl = ORT_CONFIG_REPOSITORY_URL,
        revision = ORT_CONFIG_REPOSITORY_BRANCH,
        path = PACKAGE_CONFIGURATIONS_DIR
    )
)
