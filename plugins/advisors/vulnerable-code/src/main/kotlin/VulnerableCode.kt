/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.vulnerablecode

import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProvider
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProviderFactory
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

/**
 * An [AdviceProvider] implementation that obtains security vulnerability information from a
 * [VulnerableCode](https://github.com/aboutcode-org/vulnerablecode) instance.
 */
@OrtPlugin(
    displayName = "VulnerableCode",
    summary = "An advisor that uses a VulnerableCode instance to determine vulnerabilities in dependencies.",
    factory = AdviceProviderFactory::class
)
class VulnerableCode(
    override val descriptor: PluginDescriptor = VulnerableCodeFactory.descriptor,
    config: VulnerableCodeConfiguration
) : AdviceProvider {
    /**
     * The details returned with each [AdvisorResult] produced by this instance. As this is constant, it can be
     * created once beforehand.
     */
    override val details = AdvisorDetails(descriptor.id)

    private val api by lazy {
        when (config.apiVersion) {
            VulnerableCodeApiVersion.V1 -> VulnerableCodeApiV1(descriptor, details, config)
        }
    }

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> =
        api.retrievePackageFindings(packages)
}
