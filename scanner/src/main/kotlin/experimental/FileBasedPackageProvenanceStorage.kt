/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.experimental

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.showStackTrace
import org.ossreviewtoolkit.utils.ort.storage.FileStorage

class FileBasedPackageProvenanceStorage(val backend: FileStorage) : PackageProvenanceStorage {
    override fun readProvenance(id: Identifier, sourceArtifact: RemoteArtifact): PackageProvenanceResolutionResult? =
        readResults(id).find { it.sourceArtifact == sourceArtifact }?.result

    override fun readProvenance(id: Identifier, vcs: VcsInfo): PackageProvenanceResolutionResult? =
        readResults(id).find { it.vcs == vcs }?.result

    private fun readResults(id: Identifier): List<StorageEntry> {
        val path = storagePath(id)

        return runCatching {
            backend.read(path).use { input ->
                yamlMapper.readValue<List<StorageEntry>>(input)
            }
        }.getOrElse {
            when (it) {
                is FileNotFoundException -> {
                    // If the file cannot be found it means no scan results have been stored yet.
                    emptyList()
                }
                else -> {
                    log.info {
                        "Could not read resolved provenances for '${id.toCoordinates()}' from path '$path': " +
                                it.collectMessages()
                    }

                    emptyList()
                }
            }
        }
    }

    override fun putProvenance(
        id: Identifier,
        sourceArtifact: RemoteArtifact,
        result: PackageProvenanceResolutionResult
    ) = putProvenance(id, sourceArtifact, null, result)

    override fun putProvenance(id: Identifier, vcs: VcsInfo, result: PackageProvenanceResolutionResult) =
        putProvenance(id, null, vcs, result)

    private fun putProvenance(
        id: Identifier,
        sourceArtifact: RemoteArtifact?,
        vcs: VcsInfo?,
        result: PackageProvenanceResolutionResult
    ) {
        val results = readResults(id).toMutableList()
        if (sourceArtifact != null) results.removeAll { it.sourceArtifact == sourceArtifact }
        if (vcs != null) results.removeAll { it.vcs == vcs }
        results += StorageEntry(sourceArtifact, vcs, result)

        val path = storagePath(id)
        val yamlBytes = yamlMapper.writeValueAsBytes(results)
        val input = ByteArrayInputStream(yamlBytes)

        runCatching {
            backend.write(path, input)
            log.debug { "Stored resolved provenances for '${id.toCoordinates()}' at path '$path'." }
        }.onFailure {
            when (it) {
                is IllegalArgumentException, is IOException -> {
                    it.showStackTrace()

                    log.warn {
                        "Could not store resolved provenances for '${id.toCoordinates()}' at path '$path': " +
                                it.collectMessages()
                    }
                }
                else -> throw it
            }
        }
    }
}

private const val FILE_NAME = "resolved_provenance.yml"

private fun storagePath(id: Identifier) = "${id.toPath()}/$FILE_NAME"

private data class StorageEntry(
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val sourceArtifact: RemoteArtifact?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val vcs: VcsInfo?,
    val result: PackageProvenanceResolutionResult
)
