/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.storages

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import java.nio.file.Path

import kotlin.io.path.createTempDirectory

import org.eclipse.sw360.clients.adapter.AttachmentUploadRequest
import org.eclipse.sw360.clients.adapter.SW360Connection
import org.eclipse.sw360.clients.adapter.SW360ConnectionFactory
import org.eclipse.sw360.clients.config.SW360ClientConfig
import org.eclipse.sw360.clients.rest.resource.attachments.SW360AttachmentType
import org.eclipse.sw360.clients.rest.resource.attachments.SW360SparseAttachment
import org.eclipse.sw360.clients.rest.resource.releases.SW360Release
import org.eclipse.sw360.clients.utils.SW360ClientException
import org.eclipse.sw360.http.HttpClientFactoryImpl
import org.eclipse.sw360.http.config.HttpClientConfig

import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.safeDeleteRecursively

/**
 * The SW360 storage back-end uses the sw360-client library in order to read/add attachments from the configured
 * SW360 instance.
 */
class Sw360Storage(
    sw360Configuration: Sw360StorageConfiguration
) : ScanResultsStorage() {
    private val sw360ConnectionFactory = createSw360Connection(
        sw360Configuration,
        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    )
    private val releaseClient = sw360ConnectionFactory.releaseAdapter

    override fun readFromStorage(id: Identifier): Result<ScanResultContainer> {
        val tempScanResultFile = createTempFileForUpload(id)

        return try {
            val scanResults = releaseClient.getSparseReleaseByNameAndVersion(createReleaseName(id), id.version)
                .flatMap { releaseClient.getReleaseById(it.releaseId) }
                .map { getScanResultOfRelease(it, tempScanResultFile.toPath()) }
                .orElse(emptyList())
                .map { path ->
                    yamlMapper.readValue<ScanResult>(path.toFile())
                }
            Success(ScanResultContainer(id, scanResults))
        } catch (e: SW360ClientException) {
            val message = "Could not read scan results for ${id.toCoordinates()} in SW360."
            log.info { message }
            Failure(message)
        } finally {
            tempScanResultFile.safeDeleteRecursively(force = true)
        }
    }

    override fun addToStorage(id: Identifier, scanResult: ScanResult): Result<Unit> {
        val tempScanResultFile = createTempFileForUpload(id)

        return try {
            yamlMapper.writeValue(tempScanResultFile, scanResult)

            val uploadResult = releaseClient.getSparseReleaseByNameAndVersion(createReleaseName(id), id.version)
                .flatMap { releaseClient.getReleaseById(it.releaseId) }
                .map { deleteScanResultAttachment(it) }
                .map { createAttachmentOfScanResult(it, tempScanResultFile.toPath()) }
                .map { releaseClient.uploadAttachments(it) }

            if (uploadResult.isPresent && uploadResult.get().isSuccess) {
                log.debug { "Stored scan result for '${id.toCoordinates()}' in SW360." }
                Success(Unit)
            } else {
                Failure("Failed to add scan results for '${id.toCoordinates()}' in SW360.")
            }
        } catch (e: SW360ClientException) {
            val message = "Failed to add scan results for '${id.toCoordinates()}' in SW360: " +
                    e.collectMessagesAsString()
            log.info { message }
            Failure(message)
        } finally {
            tempScanResultFile.safeDeleteRecursively(force = true)
        }
    }

    private fun deleteScanResultAttachment(release: SW360Release?) =
        releaseClient.deleteAttachments(
            release,
            release?.embedded?.attachments
                ?.filter { isAttachmentAScanResult(it) }
                ?.map { it.id }
        )

    private fun isAttachmentAScanResult(attachment: SW360SparseAttachment) =
        attachment.attachmentType == SW360AttachmentType.SCAN_RESULT_REPORT
                && attachment.filename == SCAN_RESULTS_FILE_NAME

    private fun createReleaseName(id: Identifier) =
        listOfNotNull(id.namespace, id.name).joinToString("/")

    private fun getScanResultOfRelease(release: SW360Release, output: Path) =
        release.embedded.attachments
            .filter { isAttachmentAScanResult(it) }
            .mapNotNull { releaseClient.downloadAttachment(release, it, output).orElse(null) }

    private fun createAttachmentOfScanResult(release: SW360Release, cachedScanResult: Path) =
        AttachmentUploadRequest.builder(release)
            .addAttachment(cachedScanResult, SW360AttachmentType.SCAN_RESULT_REPORT)
            .build()

    private fun createTempFileForUpload(id: Identifier) =
        createTempDirectory(id.toCoordinates())
            .resolve(SCAN_RESULTS_FILE_NAME)
            .toFile()

    private fun createSw360Connection(config: Sw360StorageConfiguration, jsonMapper: ObjectMapper): SW360Connection {
        val httpClientConfig = HttpClientConfig
            .basicConfig()
            .withObjectMapper(jsonMapper)
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
            jsonMapper
        )

        return SW360ConnectionFactory().newConnection(sw360ClientConfig)
    }
}
