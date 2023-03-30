/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.storages

import com.fasterxml.jackson.module.kotlin.readValue

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScanStorageException
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.showStackTrace
import org.ossreviewtoolkit.utils.ort.storage.FileStorage

const val SCAN_RESULTS_FILE_NAME = "scan-results.yml"

/**
 * A [ScanResultsStorage] using a [FileStorage] as backend. Scan results are serialized using [YAML][yamlMapper].
 */
class FileBasedStorage(
    /**
     * The [FileStorage] to use for storing scan results.
     */
    val backend: FileStorage
) : ScanResultsStorage() {
    private companion object : Logging

    override val name = "${javaClass.simpleName} with ${backend.javaClass.simpleName} backend"

    override fun readInternal(id: Identifier): Result<List<ScanResult>> {
        val path = storagePath(id)

        return runCatching {
            backend.read(path).use { input ->
                yamlMapper.readValue<ScanResultContainer>(input).results
            }
        }.recoverCatching {
            // If the file cannot be found it means no scan results have been stored, yet.
            if (it is FileNotFoundException) return EMPTY_RESULT

            val message = "Could not read scan results for '${id.toCoordinates()}' from path '$path': " +
                    it.collectMessages()

            logger.info { message }

            throw ScanStorageException(message)
        }
    }

    override fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit> {
        val existingScanResults = read(id).getOrDefault(emptyList())

        if (existingScanResults.any { it.scanner == scanResult.scanner && it.provenance == scanResult.provenance }) {
            val message = "Did not store scan result for '${id.toCoordinates()}' because a scan result for the same " +
                    "scanner and provenance was already stored."

            logger.warn { message }

            return Result.failure(ScanStorageException(message))
        }

        val scanResults = existingScanResults + scanResult

        val path = storagePath(id)
        val yamlBytes = yamlMapper.writeValueAsBytes(ScanResultContainer(id, scanResults))
        val input = ByteArrayInputStream(yamlBytes)

        return runCatching {
            backend.write(path, input)
            logger.debug { "Stored scan result for '${id.toCoordinates()}' at path '$path'." }
        }.onFailure {
            if (it is IllegalArgumentException || it is IOException) {
                it.showStackTrace()

                val message = "Could not store scan result for '${id.toCoordinates()}' at path '$path': " +
                        it.collectMessages()

                logger.warn { message }

                return Result.failure(ScanStorageException(message))
            }
        }
    }

    private fun storagePath(id: Identifier) = "${id.toPath()}/$SCAN_RESULTS_FILE_NAME"
}

/**
 * A container for [ScanResult]s for the package identified by [id].
 */
private data class ScanResultContainer(
    /**
     * The [Identifier] of the package these [results] belong to.
     */
    val id: Identifier,

    /**
     * The list of [ScanResult]s from potentially multiple scanners and / or with different package provenance.
     */
    val results: List<ScanResult>
)
