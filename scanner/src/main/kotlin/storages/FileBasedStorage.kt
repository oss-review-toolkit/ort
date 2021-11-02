/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.showStackTrace
import org.ossreviewtoolkit.utils.core.storage.FileStorage

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
    override val name = "${javaClass.simpleName} with ${backend.javaClass.simpleName} backend"

    override fun readInternal(id: Identifier): Result<List<ScanResult>> {
        val path = storagePath(id)

        @Suppress("TooGenericExceptionCaught")
        return try {
            backend.read(path).use { input ->
                Success(yamlMapper.readValue<ScanResultContainer>(input).results)
            }
        } catch (e: Exception) {
            when (e) {
                is FileNotFoundException -> {
                    // If the file cannot be found it means no scan results have been stored, yet.
                    Success(emptyList())
                }
                else -> {
                    val message = "Could not read scan results for '${id.toCoordinates()}' from path '$path': " +
                            e.collectMessagesAsString()

                    log.info { message }
                    Failure(message)
                }
            }
        }
    }

    override fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit> {
        val existingScanResults = when (val readResult = read(id)) {
            is Success -> readResult.result
            is Failure -> emptyList()
        }

        if (existingScanResults.any { it.scanner == scanResult.scanner && it.provenance == scanResult.provenance }) {
            val message = "Did not store scan result for '${id.toCoordinates()}' because a scan result for the same " +
                    "scanner and provenance was already stored."

            log.warn { message }

            return Failure(message)
        }

        val scanResults = existingScanResults + scanResult

        val path = storagePath(id)
        val yamlBytes = yamlMapper.writeValueAsBytes(ScanResultContainer(id, scanResults))
        val input = ByteArrayInputStream(yamlBytes)

        @Suppress("TooGenericExceptionCaught")
        return try {
            backend.write(path, input)
            log.debug { "Stored scan result for '${id.toCoordinates()}' at path '$path'." }
            Success(Unit)
        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException, is IOException -> {
                    e.showStackTrace()

                    val message = "Could not store scan result for '${id.toCoordinates()}' at path '$path': " +
                            e.collectMessagesAsString()
                    log.warn { message }

                    Failure(message)
                }
                else -> throw e
            }
        }
    }

    private fun storagePath(id: Identifier) = "${id.toPath()}/$SCAN_RESULTS_FILE_NAME"
}
