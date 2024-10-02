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
import org.ossreviewtoolkit.clients.bazelmoduleregistry.DEFAULT_URL
import org.ossreviewtoolkit.clients.bazelmoduleregistry.LocalBazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleMetadata
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleSourceInfo
import org.ossreviewtoolkit.clients.bazelmoduleregistry.RemoteBazelModuleRegistryService

/**
 * A special implementation of [BazelModuleRegistryService] that wraps an arbitrary number of other
 * [BazelModuleRegistryService] instances. It can be used for projects that declare multiple registries.
 *
 * The functions of the interface are implemented by iterating over the wrapped services and returning the first
 * successful result.
 */
internal class MultiBazelModuleRegistryService(
    /** The wrapped [BazelModuleRegistryService] instances. */
    private val registryServices: Collection<BazelModuleRegistryService>
) : BazelModuleRegistryService {
    companion object {
        /**
         * Create an instance of [MultiBazelModuleRegistryService] for the given [urls]. Based on the URLs, concrete
         * [BazelModuleRegistryService] implementations are created. Local registry services use the given [projectDir]
         * as workspace. These services are then queried in the order defined by the passed in collection. Note that as
         * the last service a remote module registry for the Bazel Central Registry is added that serves as a fallback.
         */
        fun create(urls: Collection<String>, projectDir: File): MultiBazelModuleRegistryService {
            // Add the default Bazel registry as a fallback.
            val registryUrls = (urls + DEFAULT_URL).distinctBy { it.removeSuffix("/") }

            logger.info { "Creating a multi Bazel module registry:" }

            val registryServices = registryUrls.mapTo(mutableListOf()) { url ->
                LocalBazelModuleRegistryService.create(url, projectDir)
                    ?: RemoteBazelModuleRegistryService.create(url)
            }

            return MultiBazelModuleRegistryService(registryServices)
        }

        /**
         * Return an exception with a message that combines the messages of all [Throwable]s in this list.
         */
        private fun List<String>.combinedException(caption: String): Throwable =
            IllegalArgumentException(
                "$caption:\n${joinToString("\n")}"
            )
    }

    override val urls = registryServices.flatMap { it.urls }

    override suspend fun getModuleMetadata(name: String): ModuleMetadata =
        queryRegistryServices(
            errorMessage = { "Failed to query metadata for package '$name'" },
            query = { it.getModuleMetadata(name) }
        )

    override suspend fun getModuleSourceInfo(name: String, version: String): ModuleSourceInfo =
        queryRegistryServices(
            errorMessage = { "Failed to query source info for package '$name' and version '$version'" },
            query = { it.getModuleSourceInfo(name, version) }
        )

    /**
     * A generic function for sending a [query] to all managed [BazelModuleRegistryService] instances and returning the
     * first successful result. In case no registry service can provide a result, throw an exception with the given
     * [errorMessage] and a summary of all failures.
     */
    private suspend fun <T> queryRegistryServices(
        errorMessage: () -> String,
        query: suspend (BazelModuleRegistryService) -> T
    ): T {
        val failures = mutableListOf<String>()

        tailrec suspend fun queryServices(itServices: Iterator<BazelModuleRegistryService>): T? =
            if (!itServices.hasNext()) {
                null
            } else {
                val nextRegistry = itServices.next()
                val triedResult = runCatching { query(nextRegistry) }
                val result = triedResult.getOrNull()

                // The Elvis operator does not work here because of the tailrec modifier.
                if (result != null) {
                    result
                } else {
                    triedResult.exceptionOrNull()?.let {
                        failures += "${nextRegistry.urls.joinToString()}: ${
                            it.message.orEmpty().ifEmpty { "<no message>" }
                        }"
                    }

                    queryServices(itServices)
                }
            }

        val info = queryServices(registryServices.iterator())
        return info ?: throw failures.combinedException(errorMessage())
    }
}
