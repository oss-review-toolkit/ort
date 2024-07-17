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

private const val BAZEL_MODULES_DIR = "modules"

/**
 * A Bazel registry which is located on the local file system.
 */
class LocalBazelModuleRegistryService(directory: File) : BazelModuleRegistryService {
    private val moduleDirectory: File
    private val bazelRegistry: BazelRegistry

    init {
        val registryFile = directory.resolve("bazel_registry.json")

        require(registryFile.isFile) {
            "The Bazel registry file bazel_registry.json does not exist in '${directory.canonicalPath}'."
        }

        bazelRegistry = JSON.decodeFromString<BazelRegistry>(registryFile.readText())
        moduleDirectory = registryFile.resolveSibling(BAZEL_MODULES_DIR)
    }

    override suspend fun getModuleMetadata(name: String): ModuleMetadata {
        val metadataJson = moduleDirectory.resolve(name).resolve("metadata.json")
        require(metadataJson.isFile)
        return JSON.decodeFromString<ModuleMetadata>(metadataJson.readText())
    }

    override suspend fun getModuleSourceInfo(name: String, version: String): ModuleSourceInfo {
        val sourceJson = moduleDirectory.resolve(name).resolve(version).resolve("source.json")
        require(sourceJson.isFile)
        return JSON.decodeFromString<ModuleSourceInfo>(sourceJson.readText())
    }
}

@Serializable
private data class BazelRegistry(
    val moduleBasePath: String
)
