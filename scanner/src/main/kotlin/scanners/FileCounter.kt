/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.scanner.scanners

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.Provenance
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.scanner.AbstractScannerFactory
import com.here.ort.scanner.LocalScanner
import com.here.ort.spdx.calculatePackageVerificationCode

import java.io.File
import java.time.Instant

/**
 * A simple [LocalScanner] that only counts the files in the scan path. Because it is much faster than the other
 * scanners it is useful for testing the scanner tool, for example during development or when integrating it with other
 * tools.
 */
class FileCounter(name: String, config: ScannerConfiguration) : LocalScanner(name, config) {
    class Factory : AbstractScannerFactory<FileCounter>("FileCounter") {
        override fun create(config: ScannerConfiguration) = FileCounter(scannerName, config)
    }

    data class FileCountResult(val fileCount: Int)

    override val resultFileExt = "json"
    override val scannerVersion = "1.0"

    override fun command(workingDir: File?) = ""

    override fun getVersion(dir: File) = scannerVersion

    override fun getConfiguration() = ""

    override fun scanPath(path: File, resultsFile: File): ScanResult {
        val startTime = Instant.now()

        val fileCountResult = FileCountResult(path.walk().count())
        val fileCountJson = jsonMapper.writeValueAsString(fileCountResult)
        resultsFile.writeText(fileCountJson)

        val endTime = Instant.now()

        val result = getRawResult(resultsFile)
        val summary = generateSummary(startTime, endTime, path, result)
        return ScanResult(Provenance(), getDetails(), summary, result)
    }

    override fun getRawResult(resultsFile: File) =
        if (resultsFile.isFile && resultsFile.length() > 0L) {
            jsonMapper.readTree(resultsFile)
        } else {
            EMPTY_JSON_NODE
        }

    private fun generateSummary(startTime: Instant, endTime: Instant, scanPath: File, result: JsonNode): ScanSummary {
        val fileCount = result["file_count"].intValue()
        return ScanSummary(
            startTime = startTime,
            endTime = endTime,
            fileCount = fileCount,
            packageVerificationCode = calculatePackageVerificationCode(scanPath),
            licenseFindings = sortedSetOf(),
            copyrightFindings = sortedSetOf(),
            errors = mutableListOf()
        )
    }
}
