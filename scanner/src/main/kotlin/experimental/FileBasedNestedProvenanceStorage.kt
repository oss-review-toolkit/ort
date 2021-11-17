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

import com.fasterxml.jackson.module.kotlin.readValue

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException

import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.common.fileSystemEncode
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.showStackTrace
import org.ossreviewtoolkit.utils.core.storage.FileStorage

class FileBasedNestedProvenanceStorage(private val backend: FileStorage) : NestedProvenanceStorage {
    override fun readNestedProvenance(root: RepositoryProvenance): NestedProvenanceResolutionResult? =
        readResults(root).find { it.nestedProvenance.root == root }

    private fun readResults(root: RepositoryProvenance): List<NestedProvenanceResolutionResult> {
        val path = storagePath(root)

        return runCatching {
            backend.read(path).use { input ->
                yamlMapper.readValue<List<NestedProvenanceResolutionResult>>(input)
            }
        }.getOrElse {
            when (it) {
                is FileNotFoundException -> {
                    // If the file cannot be found it means no scan results have been stored, yet.
                    emptyList()
                }
                else -> {
                    log.info {
                        "Could not read resolved nested provenances for '$root' from path '$path': " +
                                it.collectMessagesAsString()
                    }

                    emptyList()
                }
            }
        }
    }

    override fun putNestedProvenance(root: RepositoryProvenance, result: NestedProvenanceResolutionResult) {
        val results = readResults(root).toMutableList()
        results.removeAll { it.nestedProvenance.root == root }
        results += result

        val path = storagePath(root)
        val yamlBytes = yamlMapper.writeValueAsBytes(results)
        val input = ByteArrayInputStream(yamlBytes)

        runCatching {
            backend.write(path, input)
            log.debug { "Stored resolved nested provenance for '$root' at path '$path'." }
        }.onFailure {
            when (it) {
                is IllegalArgumentException, is IOException -> {
                    it.showStackTrace()

                    log.warn {
                        "Could not store resolved nested provenance for '$root' at path '$path': " +
                                it.collectMessagesAsString()
                    }
                }
                else -> throw it
            }
        }
    }
}

private const val FILE_NAME = "resolved_nested_provenance.yml"

private fun storagePath(root: RepositoryProvenance) =
    "${root.vcsInfo.type.toString().fileSystemEncode()}/" +
            "${root.vcsInfo.url.fileSystemEncode()}/" +
            "${root.resolvedRevision.fileSystemEncode()}/" +
            FILE_NAME
