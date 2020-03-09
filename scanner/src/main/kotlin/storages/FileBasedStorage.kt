/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.scanner.storages

import com.fasterxml.jackson.module.kotlin.readValue

import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScannerDetails
import com.here.ort.model.yamlMapper
import com.here.ort.scanner.ScanResultsStorage
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.log
import com.here.ort.utils.storage.FileStorage

import java.io.ByteArrayInputStream
import java.io.IOException

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

    override fun readFromStorage(id: Identifier): ScanResultContainer {
        val path = storagePath(id)

        @Suppress("TooGenericExceptionCaught")
        return try {
            backend.read(path).use { input ->
                yamlMapper.readValue(input)
            }
        } catch (e: Exception) {
            when (e) {
                is java.lang.IllegalArgumentException, is IOException -> {
                    log.info {
                        "Could not read scan results for '${id.toCoordinates()}' from path '$path': " +
                                e.collectMessagesAsString()
                    }

                    ScanResultContainer(id, emptyList())
                }
                else -> throw e
            }
        }
    }

    override fun readFromStorage(pkg: Package, scannerDetails: ScannerDetails): ScanResultContainer {
        val scanResults = read(pkg.id).results.toMutableList()

        if (scanResults.isEmpty()) return ScanResultContainer(pkg.id, scanResults)

        // Only keep scan results whose provenance information matches the package information.
        scanResults.retainAll { it.provenance.matches(pkg) }
        if (scanResults.isEmpty()) {
            log.info {
                "No stored scan results found for $pkg. The following entries with non-matching provenance have " +
                        "been ignored: ${scanResults.map { it.provenance }}"
            }
            return ScanResultContainer(pkg.id, scanResults)
        }

        // Only keep scan results from compatible scanners.
        scanResults.retainAll { scannerDetails.isCompatible(it.scanner) }
        if (scanResults.isEmpty()) {
            log.info {
                "No stored scan results found for $scannerDetails. The following entries with incompatible scanners " +
                        "have been ignored: ${scanResults.map { it.scanner }}"
            }
            return ScanResultContainer(pkg.id, scanResults)
        }

        log.info {
            "Found ${scanResults.size} stored scan result(s) for ${pkg.id.toCoordinates()} that are compatible with " +
                    "$scannerDetails."
        }

        return ScanResultContainer(pkg.id, scanResults)
    }

    override fun addToStorage(id: Identifier, scanResult: ScanResult): AddResult {
        val scanResults = ScanResultContainer(id, read(id).results + scanResult)

        val path = storagePath(id)
        val yamlBytes = yamlMapper.writeValueAsBytes(scanResults)
        val input = ByteArrayInputStream(yamlBytes)

        @Suppress("TooGenericExceptionCaught")
        return try {
            backend.write(path, input)
            log.info { "Stored scan result for '${id.toCoordinates()}' at path '$path'." }
            AddResult(true)
        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException, is IOException -> {
                    e.printStackTrace()

                    val message = "Could not store scan result for '${id.toCoordinates()}' at path '$path': " +
                            e.collectMessagesAsString()
                    log.info { message }

                    AddResult(false, message)
                }
                else -> throw e
            }
        }
    }

    private fun storagePath(id: Identifier) = "${id.toPath()}/$SCAN_RESULTS_FILE_NAME"
}
