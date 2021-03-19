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

package org.ossreviewtoolkit.scanner.scanners

import com.fasterxml.jackson.databind.JsonNode

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.readJsonFile
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.LocalScanner
import org.ossreviewtoolkit.spdx.calculatePackageVerificationCode

/**
 * A simple [LocalScanner] that only counts the files in the scan path. Because it is much faster than the other
 * scanners it is useful for testing the scanner tool, for example during development or when integrating it with other
 * tools.
 */
class FileCounter(name: String, scannerConfig: ScannerConfiguration) : LocalScanner(name, scannerConfig) {
    class Factory : AbstractScannerFactory<FileCounter>("FileCounter") {
        override fun create(scannerConfig: ScannerConfiguration) = FileCounter(scannerName, scannerConfig)
    }

    data class FileCountResult(val fileCount: Int)

    override val expectedVersion = "1.0"
    override val version = expectedVersion
    override val configuration = ""
    override val resultFileExt = "json"

    override fun command(workingDir: File?) = ""

    override fun scanPathInternal(path: File, resultsFile: File): ScanSummary {
        val startTime = Instant.now()

        val fileCountResult = FileCountResult(path.walk().count())
        val fileCountJson = jsonMapper.writeValueAsString(fileCountResult)
        resultsFile.writeText(fileCountJson)

        val endTime = Instant.now()

        val result = getRawResult(resultsFile)
        return generateSummary(startTime, endTime, path, result)
    }

    override fun getRawResult(resultsFile: File) = readJsonFile(resultsFile)

    private fun generateSummary(startTime: Instant, endTime: Instant, scanPath: File, result: JsonNode): ScanSummary {
        val fileCount = result["file_count"].intValue()
        return ScanSummary(
            startTime = startTime,
            endTime = endTime,
            fileCount = fileCount,
            packageVerificationCode = calculatePackageVerificationCode(scanPath),
            licenseFindings = sortedSetOf(),
            copyrightFindings = sortedSetOf(),
            issues = mutableListOf()
        )
    }
}
