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

package org.ossreviewtoolkit.clients.bazelmoduleregistry

import java.io.File

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.utils.common.div

private const val BAZEL_MODULES_DIR = "modules"
const val METADATA_JSON = "metadata.json"
const val SOURCE_JSON = "source.json"

/**
 * A Bazel registry for the workspace [directory] which is located on the local file system. It is configured as [url]
 * in the '.bazelrc' file.
 */
class LocalBazelModuleRegistryService(url: String, directory: File) : BazelModuleRegistryService {
    companion object {
        /** A prefix for URLs pointing to local files. */
        private const val FILE_URL_PREFIX = "file://"

        /** Constant for a placeholder that is replaced by the current project directory. */
        private const val WORKSPACE_PLACEHOLDER = "%workspace%"

        /**
         * Create a [LocalBazelModuleRegistryService] if the given [url] points to a local file. In this case,
         * also replace the placeholder for the workspace by the given [projectDir]. Return *null* for all other URLs.
         */
        fun create(url: String, projectDir: File): LocalBazelModuleRegistryService? =
            url.takeIf { it.startsWith(FILE_URL_PREFIX) }?.let { fileUrl ->
                val directory = fileUrl.removePrefix(FILE_URL_PREFIX)
                    .replace(WORKSPACE_PLACEHOLDER, projectDir.absolutePath)

                logger.info { "Creating local Bazel module registry at '$directory'." }
                LocalBazelModuleRegistryService(url, File(directory))
            }
    }

    private val moduleDirectory: File
    private val bazelRegistry: BazelRegistry

    init {
        val registryFile = directory / "bazel_registry.json"

        require(registryFile.isFile) {
            "The Bazel registry file bazel_registry.json does not exist in '${directory.canonicalPath}'."
        }

        bazelRegistry = registryFile.inputStream().use {
            JSON.decodeFromStream<BazelRegistry>(it)
        }

        moduleDirectory = registryFile.resolveSibling(BAZEL_MODULES_DIR)
    }

    override val urls: List<String> = listOf(url)

    override suspend fun getModuleMetadata(name: String): ModuleMetadata {
        val metadataJson = moduleDirectory / name / METADATA_JSON
        require(metadataJson.isFile)
        return metadataJson.inputStream().use {
            JSON.decodeFromStream<ModuleMetadata>(it)
        }
    }

    override suspend fun getModuleSourceInfo(name: String, version: String): ModuleSourceInfo {
        val sourceJson = moduleDirectory / name / version / SOURCE_JSON
        require(sourceJson.isFile)
        val sourceInfo = sourceJson.inputStream().use {
            JSON.decodeFromStream<ModuleSourceInfo>(it)
        }

        if (sourceInfo is LocalRepositorySourceInfo) {
            val moduleBasePathAsFile = File(bazelRegistry.moduleBasePath)
            val pathAsFile = File(sourceInfo.path)

            when {
                moduleBasePathAsFile.isAbsolute -> logger.warn {
                    "ORT does not support a Bazel registry module base path with an absolute path: " +
                        "$moduleBasePathAsFile."
                }

                pathAsFile.isAbsolute -> logger.warn {
                    "ORT does not support a Bazel module path with an absolute path: ${sourceInfo.path}."
                }

                else -> {
                    // According to the specification, if path and module_base_path are both relative paths, the path
                    // to the module source is <registry_path>/<module_base_path>/<path>.
                    val registryPath = moduleDirectory.parentFile
                    val modulePath = registryPath / moduleBasePathAsFile / pathAsFile
                    VersionControlSystem.getPathInfo(modulePath)
                    sourceInfo.vcs = VersionControlSystem.getPathInfo(modulePath)
                }
            }
        }

        return sourceInfo
    }
}

@Serializable
private data class BazelRegistry(
    val moduleBasePath: String
)
