/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.OrtConfiguration

/**
 * Return a list of enabled package configuration providers. The sources taken into account are the [ORT configuration]
 * [ortConfig], the resolved configurations in an [ORT result][ortResult], and an [override provider][ortConfigOverride]
 * which, if non-null, takes precedence over the providers from the ORT configuration.
 */
fun getEnabledPackageConfigurationProviders(
    ortConfig: OrtConfiguration,
    ortResult: OrtResult,
    ortConfigOverride: () -> PackageConfigurationProvider?
): List<PackageConfigurationProvider> {
    @Suppress("FunctionExpressionBody")
    return buildList {
        val repositoryPackageConfigurations = ortResult.repository.config.packageConfigurations

        if (ortConfig.enableRepositoryPackageConfigurations) {
            add(SimplePackageConfigurationProvider(configurations = repositoryPackageConfigurations))
        } else {
            if (repositoryPackageConfigurations.isNotEmpty()) {
                logger.info { "Local package configurations were not applied because the feature is not enabled." }
            }
        }

        ortConfigOverride()?.also {
            add(it)
        } ?: run {
            // This only adds those package configuration providers that are both enabled in the ORT configuration
            // (which is the default) and available in the classpath.
            val providers = PackageConfigurationProviderFactory.create(ortConfig.packageConfigurationProviders)
            addAll(providers.map { it.second })
        }
    }
}
