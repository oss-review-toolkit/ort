/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.bazel

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.bazelmoduleregistry.BazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.LocalBazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleMetadata
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleSourceInfo
import org.ossreviewtoolkit.clients.bazelmoduleregistry.RemoteBazelModuleRegistryService

/**
 * A composite Bazel module registry service that aggregates multiple [BazelModuleRegistryService] instances and
 * delegates the package lookup to the corresponding registry. The first registry that contains the package is used.
 * [packagesPerRegistry] is a map of [BazelModuleRegistryService] instances to the list of packages they are
 * responsible for.
 * The [MultiBazelModuleRegistryService] is used to work directly on registry URLs extracted from Bazel < 7.2.0
 * lockfile, whereas the [CompositeBazelModuleRegistryService] is used to work on registry URLs containing the package
 * names, extracted from Bazel >= 7.2.0 lockfile.
 */
internal class CompositeBazelModuleRegistryService(
    private val packagesPerRegistry: Map<BazelModuleRegistryService, Set<String>>
) : BazelModuleRegistryService {
    companion object {
        // A regular expression to extract the server and package parts of a registry URL.
        internal val URL_REGEX = "^(?<server>.*/)modules/(?<package>[^/]+)/[^/]+/source\\.json$".toRegex()

        /**
         * Create a Composite Bazel Module Registry client instance. The wrapped [BazelModuleRegistryService]s are
         * created based on the passed in [urls]; local registries use the given [projectDir] as workspace.
         */
        fun create(urls: Collection<String>, projectDir: File): CompositeBazelModuleRegistryService {
            val packageNamesForServer = urls.distinct().filter { it.endsWith("source.json") }.mapNotNull { url ->
                val groups = URL_REGEX.matchEntire(url)?.groups

                val serverName = groups?.get("server")?.value ?: let {
                    logger.warn {
                        "$it cannot be mapped to a server root."
                    }

                    return@mapNotNull null
                }

                val packageName = groups["package"]?.value ?: url

                serverName to packageName
            }.groupByTo(mutableMapOf(), { it.first }) { it.second }.mapValues { it.value.toSet() }

            val packageNamesForRegistry = packageNamesForServer.mapKeys { (url, _) ->
                LocalBazelModuleRegistryService.create(url, projectDir)
                    ?: RemoteBazelModuleRegistryService.create(url)
            }

            return CompositeBazelModuleRegistryService(packageNamesForRegistry)
        }
    }

    override val urls = packagesPerRegistry.keys.flatMap { it.urls }

    override suspend fun getModuleMetadata(name: String): ModuleMetadata {
        val registry = packagesPerRegistry.entries.find { name in it.value }
            ?: throw IllegalArgumentException("No registry found for package '$name'.")
        // TODO check if other registry entries contain the package

        return registry.key.getModuleMetadata(name)
    }

    override suspend fun getModuleSourceInfo(name: String, version: String): ModuleSourceInfo {
        val registry = packagesPerRegistry.entries.find { name in it.value }
            ?: throw IllegalArgumentException("No registry found for package '$name'.")
        // TODO check if other registry entries contain the package

        return registry.key.getModuleSourceInfo(name, version)
    }
}
