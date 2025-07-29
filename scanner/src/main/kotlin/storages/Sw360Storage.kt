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

package org.ossreviewtoolkit.scanner.storages

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper

import java.nio.file.Path

import org.apache.logging.log4j.kotlin.logger

import org.eclipse.sw360.clients.adapter.AttachmentUploadRequest
import org.eclipse.sw360.clients.adapter.SW360Connection
import org.eclipse.sw360.clients.adapter.SW360ConnectionFactory
import org.eclipse.sw360.clients.config.SW360ClientConfig
import org.eclipse.sw360.clients.rest.resource.attachments.SW360AttachmentType
import org.eclipse.sw360.clients.rest.resource.attachments.SW360SparseAttachment
import org.eclipse.sw360.clients.rest.resource.releases.SW360Release
import org.eclipse.sw360.http.HttpClientFactoryImpl
import org.eclipse.sw360.http.config.HttpClientConfig

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.scanner.ScanStorageException
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

/**
 * The SW360 storage back-end uses the SW360-client library in order to read/add attachments from the configured
 * SW360 instance.
 */
class Sw360Storage(
    configuration: Sw360StorageConfiguration
) : AbstractPackageBasedScanStorage() {
    companion object {
        val JSON_MAPPER: ObjectMapper = jsonMapper.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun createConnection(config: Sw360StorageConfiguration): SW360Connection {
            val httpClientConfig = HttpClientConfig
                .basicConfig()
                .withObjectMapper(JSON_MAPPER)

            val httpClient = HttpClientFactoryImpl().newHttpClient(httpClientConfig)

            val sw360ClientConfig = SW360ClientConfig.createConfig(
                config.restUrl,
                config.authUrl,
                config.username,
                config.password,
                config.clientId,
                config.clientPassword,
                config.token,
                httpClient,
                JSON_MAPPER
            )

            return SW360ConnectionFactory().newConnection(sw360ClientConfig)
        }
    }

    private val connectionFactory = createConnection(configuration)
    private val releaseClient = connectionFactory.releaseAdapter

    override fun readInternal(pkg: Package, scannerMatcher: ScannerMatcher?): Result<List<ScanResult>> {
        val tempScanResultFile = createTempFileForUpload(pkg.id)

        val result = runCatching {
            releaseClient.getSparseReleaseByNameAndVersion(createReleaseName(pkg.id), pkg.id.version)
                .flatMap { releaseClient.getReleaseById(it.releaseId) }
                .map { getScanResultOfRelease(it, tempScanResultFile.toPath()) }
                .orElse(emptyList())
                .map { path ->
                    path.toFile().readValue<ScanResult>()
                }
        }.recoverCatching {
            val message = "Could not read scan results for '${pkg.id.toCoordinates()}' in SW360: ${it.message}"

            logger.info { message }

            throw ScanStorageException(message)
        }

        tempScanResultFile.safeDeleteRecursively()

        return result
    }

    override fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit> {
        val tempScanResultFile = createTempFileForUpload(id)

        val result = runCatching {
            tempScanResultFile.writeValue(scanResult)

            val uploadResult = releaseClient.getSparseReleaseByNameAndVersion(createReleaseName(id), id.version)
                .flatMap { releaseClient.getReleaseById(it.releaseId) }
                .map { deleteScanResultAttachment(it) }
                .map { createAttachmentOfScanResult(it, tempScanResultFile.toPath()) }
                .map { releaseClient.uploadAttachments(it) }

            if (uploadResult.isPresent && uploadResult.get().isSuccess) {
                logger.debug { "Stored scan result for '${id.toCoordinates()}' in SW360." }
            } else {
                throw ScanStorageException("Failed to upload scan results for '${id.toCoordinates()}' to SW360.")
            }
        }.recoverCatching {
            val message = "Failed to add scan results for '${id.toCoordinates()}' to SW360: " +
                it.collectMessages()

            logger.info { message }

            throw ScanStorageException(message)
        }

        tempScanResultFile.safeDeleteRecursively()

        return result
    }

    private fun deleteScanResultAttachment(release: SW360Release?) =
        releaseClient.deleteAttachments(
            release,
            release?.embedded?.attachments
                ?.filter { isAttachmentAScanResult(it) }
                ?.map { it.id }
        )

    private fun getScanResultOfRelease(release: SW360Release, output: Path) =
        release.embedded.attachments
            .filter { isAttachmentAScanResult(it) }
            .mapNotNull { releaseClient.downloadAttachment(release, it, output).orElse(null) }
}

private fun isAttachmentAScanResult(attachment: SW360SparseAttachment) =
    attachment.attachmentType == SW360AttachmentType.SCAN_RESULT_REPORT
        && attachment.filename == SCAN_RESULTS_FILE_NAME

private fun createReleaseName(id: Identifier) = "${id.namespace}/${id.name}"

private fun createAttachmentOfScanResult(release: SW360Release, cachedScanResult: Path) =
    AttachmentUploadRequest.builder(release)
        .addAttachment(cachedScanResult, SW360AttachmentType.SCAN_RESULT_REPORT)
        .build()

private fun createTempFileForUpload(id: Identifier) = createOrtTempDir(id.toCoordinates()) / SCAN_RESULTS_FILE_NAME
