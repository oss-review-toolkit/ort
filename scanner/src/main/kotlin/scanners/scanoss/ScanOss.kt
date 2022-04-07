/*
 * Copyright (C) 2020-2021 SCANOSS TECNOLOGIAS SL
 * Copyright (C) 2020-2022 Bosch.IO GmbH
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

import com.scanoss.scanner.BlacklistRules
import com.scanoss.scanner.Winnowing

import java.io.File
import java.time.Instant
import java.util.UUID

import kotlinx.coroutines.runBlocking

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

import org.ossreviewtoolkit.clients.scanoss.ScanOssService
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.BuildConfig
import org.ossreviewtoolkit.scanner.PathScanner
import org.ossreviewtoolkit.scanner.experimental.AbstractScannerWrapperFactory
import org.ossreviewtoolkit.scanner.experimental.PathScannerWrapper
import org.ossreviewtoolkit.scanner.experimental.ScanContext
import org.ossreviewtoolkit.utils.core.log

// An arbitrary name to use for the multipart body being sent.
private const val FAKE_WFP_FILE_NAME = "fake.wfp"

class ScanOss internal constructor(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : PathScanner(name, scannerConfig, downloaderConfig), PathScannerWrapper {
    class ScanOssFactory : AbstractScannerWrapperFactory<ScanOss>("SCANOSS") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            ScanOss(scannerName, scannerConfig, downloaderConfig)
    }

    class Factory : AbstractScannerFactory<ScanOss>("SCANOSS") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            ScanOss(scannerName, scannerConfig, downloaderConfig)
    }

    private val config = ScanOssConfig.create(scannerConfig).also {
        log.info { "The $scannerName API URL is ${it.apiUrl}." }
    }

    private val service = ScanOssService.create(config.apiUrl)

    override val name = scannerName
    override val criteria by lazy { getScannerCriteria() }

    // TODO: Find out the best / cheapest way to query the SCANOSS server for its version.
    override val version = BuildConfig.SCANOSS_VERSION

    override val configuration = ""

    /**
     * The name of the file corresponding to the fingerprints can be sent to SCANOSS for more precise matches.
     * However, for anonymity, a unique identifier should be generated and used instead. This property holds the
     * mapping between the file paths and the unique identifiers. When receiving the response, the UUID will be
     * replaced by the actual file path.
     *
     * TODO: This behavior should be driven by a configuration parameter enabled by default.
     */
    private val fileNamesAnonymizationMapping = mutableMapOf<UUID, String>()

    override fun scanPathInternal(path: File): ScanSummary {
        val startTime = Instant.now()

        val wfpString = buildString {
            path.walk()
                // TODO: Consider not applying the (somewhat arbitrary) blacklist.
                .filter { !it.isDirectory && !BlacklistRules.hasBlacklistedExt(it.name) }
                .forEach {
                    this@ScanOss.log.info { "Computing fingerprint for file ${it.absolutePath}..." }
                    append(createWfpForFile(it.path))
                }
        }

        val response = runBlocking {
            val wfpBody = wfpString.toRequestBody("application/octet-stream".toMediaType())
            val wfpFile = MultipartBody.Part.createFormData(FAKE_WFP_FILE_NAME, FAKE_WFP_FILE_NAME, wfpBody)

            service.scan(wfpFile)
        }

        // Replace the anonymized UUIDs by their file paths.
        val resolvedResponse = response.map { entry ->
            val uuid = UUID.fromString(entry.key)

            val fileName = fileNamesAnonymizationMapping[uuid] ?: throw IllegalArgumentException(
                "The $scannerName server returned an UUID not present in the mapping."
            )

            fileName to entry.value
        }.toMap()

        val endTime = Instant.now()
        return generateSummary(startTime, endTime, path, resolvedResponse)
    }

    internal fun generateRandomUUID() = UUID.randomUUID()

    internal fun createWfpForFile(filePath: String): String {
        generateRandomUUID().let { uuid ->
            // TODO: Let's keep the original file extension to give SCANOSS some hint about the mime type.
            fileNamesAnonymizationMapping[uuid] = filePath
            return Winnowing.wfpForFile(uuid.toString(), filePath)
        }
    }

    override fun scanPath(path: File, context: ScanContext) = scanPathInternal(path)
}
