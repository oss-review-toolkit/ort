/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.spring

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory

/**
 * A [PackageCurationProvider] that provides [PackageCuration]s for Spring (https://spring.io/) packages.
 */
@OrtPlugin(
    displayName = "Spring",
    description = "A package curation provider for Spring packages.",
    factory = PackageCurationProviderFactory::class
)
open class SpringPackageCurationProvider(
    override val descriptor: PluginDescriptor = SpringPackageCurationProviderFactory.descriptor
) : PackageCurationProvider {
    val springBootProjectPaths = mutableMapOf<String, Map<String, String>>()

    fun getSpringBootProjectPath(subProjectName: String, projectVersion: String): String {
        val paths = springBootProjectPaths.getOrPut(projectVersion) {
            getSpringProjectPaths("spring-boot", projectVersion)
        }

        return paths.getValue(subProjectName)
    }

    override fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration> {
        val springPackages = packages.filter {
            it.id.type == "Maven" && it.id.namespace.startsWith("org.springframework")
        }

        if (springPackages.isEmpty()) return emptySet()

        val springCurations = mutableSetOf<PackageCuration>()
        val metadataOnlyNames = setOf("boot", "cloud")

        springPackages.mapNotNullTo(springCurations) { pkg ->
            var data = PackageCurationData()

            val isMetadataOnly = with(pkg.id) {
                metadataOnlyNames.any {
                    namespace == "org.springframework.$it"
                        && (name.startsWith("spring-$it-starter") || name.startsWith("spring-$it-contract-spec"))
                }
            }

            if (isMetadataOnly) {
                data = data.copy(isMetadataOnly = isMetadataOnly)
            }

            if (pkg.vcsProcessed.url == "https://github.com/spring-projects/spring-boot.git") {
                runCatching {
                    getSpringBootProjectPath(pkg.id.name, pkg.id.version)
                }.onSuccess { path ->
                    data = data.copy(vcs = VcsInfoCurationData(path = path))
                }
            }

            if (data != PackageCurationData()) {
                PackageCuration(pkg.id, data)
            } else {
                null
            }
        }

        return springCurations
    }
}
