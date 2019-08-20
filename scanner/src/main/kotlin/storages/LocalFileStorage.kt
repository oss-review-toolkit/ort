/*
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

import com.here.ort.model.Identifier
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.readValue
import com.here.ort.model.yamlMapper
import com.here.ort.utils.log
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.showStackTrace

import java.io.File
import java.io.IOException

class LocalFileStorage(
    /**
     * The base directory under which to store the scan results.
     */
    private val baseDirectory: File
) : FileBasedStorage() {
    val scanResultsDirectory = baseDirectory.resolve("scan-results")

    @Synchronized
    override fun readFromStorage(id: Identifier): ScanResultContainer {
        val scanResultsFile = scanResultsDirectory.resolve("${id.toPath()}/$SCAN_RESULTS_FILE_NAME")

        return if (scanResultsFile.isFile) {
            log.info { "Using stored scan result for '${id.toCoordinates()}' found at '$scanResultsFile'." }

            scanResultsFile.readValue()
        } else {
            log.info { "No stored scan result found for '${id.toCoordinates()}' at '$scanResultsFile'." }

            ScanResultContainer(id, emptyList())
        }
    }

    @Synchronized
    override fun addToStorage(id: Identifier, scanResult: ScanResult): Boolean {
        val scanResults = ScanResultContainer(id, read(id).results + scanResult)
        val scanResultsDirectory = scanResultsDirectory.resolve("${id.toPath()}").apply { safeMkdirs() }
        val scanResultsFile = scanResultsDirectory.resolve(SCAN_RESULTS_FILE_NAME)

        return try {
            yamlMapper.writeValue(scanResultsFile, scanResults)

            log.info { "Successfully added scan result for '${id.toCoordinates()}' to '$scanResultsFile'." }

            true
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Failed to add scan result for '${id.toCoordinates()}' to '$scanResultsFile': ${e.message}" }

            return false
        }
    }
}
