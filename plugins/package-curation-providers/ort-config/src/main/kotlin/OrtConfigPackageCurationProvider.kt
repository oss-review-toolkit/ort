/*
 * Copyright (C) 2022 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.ortconfig

import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory

private const val ORT_CONFIG_REPOSITORY_BRANCH = "main"
internal const val ORT_CONFIG_REPOSITORY_URL = "https://github.com/oss-review-toolkit/ort-config.git"

/**
 * A [PackageCurationProvider] that provides [PackageCuration]s loaded from the
 * [ort-config repository](https://github.com/oss-review-toolkit/ort-config).
 */
@OrtPlugin(
    id = "ORTConfig",
    displayName = "ORT Config Repository",
    summary = "A package curation provider that loads package curations from the ort-config repository.",
    factory = PackageCurationProviderFactory::class
)
class OrtConfigPackageCurationProvider(
    descriptor: PluginDescriptor = OrtConfigPackageCurationProviderFactory.descriptor
) : GitPackageCurationProvider(
    descriptor = descriptor,
    config = GitPackageCurationProviderConfig(
        repositoryUrl = ORT_CONFIG_REPOSITORY_URL,
        revision = ORT_CONFIG_REPOSITORY_BRANCH,
        path = "curations"
    )
)
