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

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.utils.hasIvyVersionRange
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.utils.common.collectMessages

import org.semver4j.Semver

/** A [PackageCurationProvider] that provides [PackageCuration]s for [Spring](https://spring.io/) packages. */
@OrtPlugin(
    displayName = "Spring",
    summary = "A package curation provider for Spring packages.",
    factory = PackageCurationProviderFactory::class
)
open class SpringPackageCurationProvider(
    override val descriptor: PluginDescriptor = SpringPackageCurationProviderFactory.descriptor
) : PackageCurationProvider {
    private typealias SpringProjectVersionPaths = MutableMap<String, SpringSubProjectPaths>

    // Lookup subproject paths by first "projectName", and then "coercedVersion", returning the map of paths.
    private val springProjectPaths = mutableMapOf<String, SpringProjectVersionPaths>()

    /**
     * Return the path of the subproject identified by [pkgId] for the Spring project identified by [orgName] and
     * [projectName].
     */
    private fun getSpringSubProjectPath(orgName: String, projectName: String, pkgId: Identifier): String {
        val plainVersion = if (pkgId.hasIvyVersionRange()) {
            // Use a simple way to get the first valid version out of a range.
            Semver.coerce(pkgId.version).toString()
        } else {
            pkgId.version
        }

        val projectVersions = springProjectPaths.getOrPut("$orgName/$projectName") {
            mutableMapOf()
        }

        val subProjectPaths = projectVersions.getOrPut(plainVersion) {
            getSpringSubProjectPaths(orgName, projectName, plainVersion)
        }

        return subProjectPaths.getValue(pkgId.name)
    }

    override fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration> {
        val springPackages = packages.filter {
            it.id.type == "Maven" && with(it.id.namespace) {
                startsWith("org.springdoc") || startsWith("org.springframework")
            }
        }

        if (springPackages.isEmpty()) return emptySet()

        val springCurations = mutableSetOf<PackageCuration>()
        val metadataOnlyNames = setOf("boot", "cloud")

        springPackages.mapNotNullTo(springCurations) { pkg ->
            var data = PackageCurationData()

            val isMetadataOnly = with(pkg.id) {
                name.endsWith("-metadata") || metadataOnlyNames.any {
                    namespace == "org.springframework.$it"
                        && (name.startsWith("spring-$it-starter") || name.startsWith("spring-$it-contract-spec"))
                }
            }

            if (isMetadataOnly) {
                data = data.copy(isMetadataOnly = true)
            }

            val orgName = VcsHost.GITHUB.getUserOrOrganization(pkg.vcsProcessed.url)
            val projectName = VcsHost.GITHUB.getProject(pkg.vcsProcessed.url)

            if (orgName != null && projectName != null) {
                runCatching {
                    getSpringSubProjectPath(orgName, projectName, pkg.id)
                }.onSuccess { path ->
                    data = data.copy(vcs = VcsInfoCurationData(path = path))
                }.onFailure {
                    logger.warn {
                        "Failed to get Spring project path for package '${pkg.id.toCoordinates()}': " +
                            it.collectMessages()
                    }
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
