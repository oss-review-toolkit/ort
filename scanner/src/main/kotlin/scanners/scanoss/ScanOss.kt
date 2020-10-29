/*
 * Copyright (C) 2020 SCANOSS TECNOLOGIAS SL
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

package org.ossreviewtoolkit.scanner.scanners.scanoss

import com.fasterxml.jackson.databind.JsonNode

import com.scanoss.scanner.ScanFormat
import com.scanoss.scanner.Scanner
import com.scanoss.scanner.ScannerConf

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.RemoteScanner

class ScanOss(
    name: String,
    config: ScannerConfiguration
) : RemoteScanner(name, config) {
    class Factory : AbstractScannerFactory<ScanOss>("ScanOSS") {
        override fun create(config: ScannerConfiguration) = ScanOss(scannerName, config)
    }

    override val version = "1.1.0"

    override val configuration = ""


    // TODO Implement support for Scanning with SBOM
    // TODO Support API configuration other than OSS KB.
    override fun scanPathInternal(path: File, resultsFile: File): ScanResult {
        val startTime = Instant.now()

        val scannerConf = ScannerConf.defaultConf()
        val scanner = Scanner(scannerConf)
        if (path.isDirectory) {
            scanner.scanDirectory(path.absolutePath, null, "", ScanFormat.plain, resultsFile.absolutePath)
        } else if (path.isFile) {
            scanner.scanFileAndSave(path.absolutePath, null, "", ScanFormat.plain, resultsFile.absolutePath)
        }
        val endTime = Instant.now()
        val result = getRawResult(resultsFile)
        val summary = generateSummary(startTime, endTime, path, result)
        return ScanResult(Provenance(), getDetails(), summary)
    }

    fun getRawResult(resultsFile: File): JsonNode =
        parseResult(resultsFile)



}
