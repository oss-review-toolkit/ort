/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import com.scanoss.scanner.Scanner
import com.scanoss.scanner.Winnowing

import java.io.File
import java.time.Instant
import java.util.Properties
import java.util.UUID

import kotlinx.coroutines.runBlocking

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.clients.scanoss.ScanOssService
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.AbstractScannerWrapperFactory
import org.ossreviewtoolkit.scanner.PathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES

// An arbitrary name to use for the multipart body being sent.
private const val FAKE_WFP_FILE_NAME = "fake.wfp"

class ScanOss internal constructor(
    private val name: String,
    private val scannerConfig: ScannerConfiguration
) : PathScannerWrapper {
    companion object : Logging

    class Factory : AbstractScannerWrapperFactory<ScanOss>("SCANOSS") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            ScanOss(type, scannerConfig)
    }

    private val config = ScanOssConfig.create(scannerConfig).also {
        logger.info { "The $name API URL is ${it.apiUrl}." }
    }

    private val service = ScanOssService.create(config.apiUrl)

    override val criteria by lazy { ScannerCriteria.fromConfig(details, scannerConfig) }

    override val details by lazy {
        // TODO: Find out the best / cheapest way to query the SCANOSS server for its version.
        val pomProperties = "/META-INF/maven/com.scanoss/scanner/pom.properties"
        val properties = Scanner::class.java.getResourceAsStream(pomProperties).use { Properties().apply { load(it) } }
        ScannerDetails(name, properties.getProperty("version"), "")
    }

    /**
     * The name of the file corresponding to the fingerprints can be sent to SCANOSS for more precise matches.
     * However, for anonymity, a unique identifier should be generated and used instead. This property holds the
     * mapping between the file paths and the unique identifiers. When receiving the response, the UUID will be
     * replaced by the actual file path.
     *
     * TODO: This behavior should be driven by a configuration parameter enabled by default.
     */
    private val fileNamesAnonymizationMapping = mutableMapOf<UUID, String>()

    override fun scanPath(path: File, context: ScanContext): ScanSummary {
        val startTime = Instant.now()

        val wfpString = buildString {
            path.walk()
                .onEnter { it.name !in VCS_DIRECTORIES }
                // TODO: Consider not applying the (somewhat arbitrary) blacklist.
                .filterNot { it.isDirectory || BlacklistRules.hasBlacklistedExt(it.name) }
                .forEach {
                    logger.info { "Computing fingerprint for file ${it.absolutePath}..." }
                    append(createWfpForFile(it))
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
                "The $name server returned an UUID not present in the mapping."
            )

            fileName to entry.value
        }.toMap()

        val endTime = Instant.now()
        return generateSummary(
            startTime,
            endTime,
            path,
            resolvedResponse,
            scannerConfig.detectedLicenseMapping
        )
    }

    internal fun generateRandomUUID() = UUID.randomUUID()

    internal fun createWfpForFile(file: File): String {
        generateRandomUUID().let { uuid ->
            // TODO: Let's keep the original file extension to give SCANOSS some hint about the mime type.
            fileNamesAnonymizationMapping[uuid] = file.path
            return Winnowing.wfpForFile(uuid.toString(), file.path)
        }
    }
}
