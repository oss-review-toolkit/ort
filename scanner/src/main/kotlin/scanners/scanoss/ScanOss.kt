/*
 * Copyright (C) 2020-2021 SCANOSS TECNOLOGIAS SL
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners.scanoss

import com.scanoss.scanner.ScanFormat
import com.scanoss.scanner.Scanner
import com.scanoss.scanner.ScannerConf

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.readJsonFile
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.BuildConfig
import org.ossreviewtoolkit.scanner.PathScanner
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.core.createOrtTempDir

class ScanOss internal constructor(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : PathScanner(name, scannerConfig, downloaderConfig) {
    class Factory : AbstractScannerFactory<ScanOss>("SCANOSS") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            ScanOss(scannerName, scannerConfig, downloaderConfig)
    }

    override val version = BuildConfig.SCANOSS_VERSION
    override val configuration = ""

    override fun scanPathInternal(path: File): ScanSummary {
        val resultFile = createOrtTempDir().resolve("result.json")
        val startTime = Instant.now()

        // TODO: Support API configurations other than OSSKB.
        val scannerConf = ScannerConf.defaultConf()
        val scanner = Scanner(scannerConf)

        val scanType = null
        val sbomPath = ""

        if (path.isDirectory) {
            scanner.scanDirectory(path.absolutePath, scanType, sbomPath, ScanFormat.plain, resultFile.absolutePath)
        } else if (path.isFile) {
            scanner.scanFileAndSave(path.absolutePath, scanType, sbomPath, ScanFormat.plain, resultFile.absolutePath)
        }

        val endTime = Instant.now()
        val result = readJsonFile(resultFile)
        resultFile.parentFile.safeDeleteRecursively(force = true)

        return generateSummary(startTime, endTime, path, result)
    }
}
