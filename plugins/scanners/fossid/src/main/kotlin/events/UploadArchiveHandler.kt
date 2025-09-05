/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid.events

import java.io.File

import kotlin.io.path.createTempFile

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.fossid.FossIdServiceWithVersion
import org.ossreviewtoolkit.clients.fossid.PolymorphicDataResponseBody
import org.ossreviewtoolkit.clients.fossid.checkResponse
import org.ossreviewtoolkit.clients.fossid.createScan
import org.ossreviewtoolkit.clients.fossid.extractArchives
import org.ossreviewtoolkit.clients.fossid.model.CreateScanResponse
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.removeUploadedContent
import org.ossreviewtoolkit.clients.fossid.uploadFile
import org.ossreviewtoolkit.downloader.DefaultWorkingTreeCache
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.plugins.scanners.fossid.FossIdConfig
import org.ossreviewtoolkit.plugins.scanners.fossid.FossIdFactory
import org.ossreviewtoolkit.plugins.scanners.fossid.OrtScanComment
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.provenance.DefaultProvenanceDownloader
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.packZip

/**
 * An event handler where an archive containing the source code to scan is created and uploaded to FossID.
 */
class UploadArchiveHandler(
    val config: FossIdConfig,
    val service: FossIdServiceWithVersion,
    val provenance: NestedProvenance
) : EventHandler {
    override suspend fun createScan(
        repositoryUrl: String,
        projectCode: String,
        scanCode: String,
        comment: OrtScanComment
    ): PolymorphicDataResponseBody<CreateScanResponse> =
        service
            .createScan(
                config.user.value,
                config.apiKey.value,
                projectCode,
                scanCode,
                comment = comment.asJsonString()
            )
            .checkResponse("create scan")

    override suspend fun afterScanCreation(
        scanCode: String,
        existingScan: Scan?,
        issues: MutableList<Issue>,
        context: ScanContext
    ) {
        val downloader = DefaultProvenanceDownloader(DownloaderConfiguration(), DefaultWorkingTreeCache())
        logger.info {
            "Downloading the repository at $provenance..."
        }

        val path = runCatching {
            downloader.downloadRecursively(provenance)
        }.onFailure {
            issues += createAndLogIssue(FossIdFactory.descriptor.displayName, it.collectMessages(), Severity.WARNING)
        }.getOrThrow()

        deleteExcludedFiles(path, context.includes, context.excludes)

        val sourceArchive = createTempFile("fossid-source-archive", ".zip")
        logger.info {
            "Creating archive ${sourceArchive.toFile().absolutePath}..."
        }

        try {
            path.packZip(sourceArchive.toFile(), overwrite = true)
        } finally {
            path.deleteRecursively()
        }

        logger.info {
            "Remove previously uploaded content for scan '$scanCode'..."
        }

        service.removeUploadedContent(config.user.value, config.apiKey.value, scanCode)
            .checkResponse("remove previously uploaded content", false)

        logger.info {
            "Uploading source archive '${sourceArchive.toFile().name}' for scan '$scanCode'..."
        }

        service.uploadFile(config.user.value, config.apiKey.value, scanCode, sourceArchive.toFile())
            .checkResponse("upload source archive", false)

        logger.info {
            "Extracting source archive '${sourceArchive.toFile().name}' for scan '$scanCode'..."
        }

        // This operation is asynchronous. If a scan is triggered, it will automatically wait for the end of the
        // extraction.
        service.extractArchives(config.user.value, config.apiKey.value, scanCode, sourceArchive.toFile().name)
            .checkResponse("extract archive", true)
    }

    override suspend fun afterCheckScan(scanCode: String) {
        if (config.deleteUploadedArchiveAfterScan) {
            service.removeUploadedContent(config.user.value, config.apiKey.value, scanCode)
                .checkResponse("remove previously uploaded content 2", false)
        }
    }

    internal fun deleteExcludedFiles(path: File, includes: Includes?, excludes: Excludes?) {
        path.walkBottomUp()
            .filter { it != path }
            .forEach { file ->
                val relativePath = file.relativeTo(path).invariantSeparatorsPath
                if (shouldDeleteFile(relativePath, includes, excludes)) {
                    file.delete()
                }
            }
    }

    private fun shouldDeleteFile(relativePath: String, includes: Includes?, excludes: Excludes?): Boolean =
        includes?.isPathIncluded(relativePath) == false || excludes?.isPathExcluded(relativePath) == true
}
