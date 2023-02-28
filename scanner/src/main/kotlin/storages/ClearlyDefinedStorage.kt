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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

import okhttp3.OkHttpClient

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.clients.clearlydefined.ComponentType
import org.ossreviewtoolkit.clients.clearlydefined.Coordinates
import org.ossreviewtoolkit.clients.clearlydefined.toCoordinates
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.utils.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.model.utils.toClearlyDefinedSourceLocation
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScanStorageException
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.scanner.scanners.scancode.generateScannerDetails
import org.ossreviewtoolkit.scanner.scanners.scancode.generateSummary
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.showStackTrace
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

import retrofit2.HttpException

/** The name used by ClearlyDefined for the ScanCode tool. */
private const val TOOL_SCAN_CODE = "scancode"

/**
 * Given a list of [tools], return the version of ScanCode that was used to scan the package with the given
 * [coordinates], or return null if no such tool entry is found.
 */
private fun findScanCodeVersion(tools: List<String>, coordinates: Coordinates): String? {
    val toolUrl = "$coordinates/$TOOL_SCAN_CODE/"
    return tools.find { it.startsWith(toolUrl) }?.substring(toolUrl.length)
}

/**
 * A storage implementation that tries to download ScanCode results from ClearlyDefined.
 *
 * This storage implementation maps the requested package to coordinates used by ClearlyDefined. It then tries
 * to find a harvested ScanCode result for these coordinates using the ClearlyDefined REST API.
 */
class ClearlyDefinedStorage(
    /** The configuration for this storage implementation. */
    config: ClearlyDefinedStorageConfiguration,
    client: OkHttpClient? = null
) : ScanResultsStorage() {
    companion object : Logging

    constructor(serverUrl: String, client: OkHttpClient? = null) : this(
        ClearlyDefinedStorageConfiguration(serverUrl), client
    )

    /** The service for interacting with ClearlyDefined. */
    private val service by lazy {
        ClearlyDefinedService.create(config.serverUrl, client ?: OkHttpClientHelper.buildClient())
    }

    override fun readInternal(id: Identifier): Result<List<ScanResult>> =
        runBlocking(Dispatchers.IO) { readFromClearlyDefined(Package.EMPTY.copy(id = id)) }

    override fun readInternal(pkg: Package, scannerCriteria: ScannerCriteria): Result<List<ScanResult>> =
        runBlocking(Dispatchers.IO) { readFromClearlyDefined(pkg) }

    override fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit> =
        Result.failure(ScanStorageException("Adding scan results directly to ClearlyDefined is not supported."))

    /**
     * Try to obtain a [ScanResult] produced by ScanCode from ClearlyDefined using the given [package][pkg]. Its
     * coordinates may not necessarily be equivalent to the identifier, as we try to lookup source code results if a
     * GitHub repository is known.
     */
    private suspend fun readFromClearlyDefined(pkg: Package): Result<List<ScanResult>> {
        val coordinates = pkg.toClearlyDefinedSourceLocation()?.toCoordinates() ?: pkg.toClearlyDefinedCoordinates()
            ?: return Result.failure(ScanStorageException("Unable to create ClearlyDefined coordinates for $pkg."))

        return runCatching {
            logger.debug { "Looking up ClearlyDefined scan results for $coordinates." }

            val tools = service.harvestTools(
                coordinates.type,
                coordinates.provider,
                coordinates.namespace ?: "-",
                coordinates.name,
                coordinates.revision.orEmpty()
            )

            val version = findScanCodeVersion(tools, coordinates)
            if (version != null) {
                loadScanCodeResults(coordinates, version)
            } else {
                logger.debug { "$coordinates was not scanned with any version of ScanCode." }

                emptyList()
            }
        }.recoverCatching { e ->
            e.showStackTrace()

            val message = "Error when reading results for '${pkg.id.toCoordinates()}' from ClearlyDefined: " +
                    e.collectMessages()

            logger.error { message }

            if (e is HttpException) {
                e.response()?.errorBody()?.string()?.let {
                    logger.error { "Error response from ClearlyDefined was: $it" }
                }
            }

            throw ScanStorageException(message)
        }
    }

    /**
     * Load the ScanCode results file for the package with the given [coordinates] from ClearlyDefined.
     * The results have been produced by ScanCode in the given [version].
     */
    private suspend fun loadScanCodeResults(coordinates: Coordinates, version: String): List<ScanResult> {
        val toolResponse = service.harvestToolData(
            coordinates.type,
            coordinates.provider,
            coordinates.namespace ?: "-",
            coordinates.name,
            coordinates.revision.orEmpty(),
            TOOL_SCAN_CODE,
            version
        )

        return toolResponse.use {
            jsonMapper.readTree(it.byteStream())["content"]?.let { result ->
                val definitions = service.getDefinitions(listOf(coordinates))
                val described = definitions.getValue(coordinates).described
                val sourceLocation = described.sourceLocation

                val provenance = when {
                    sourceLocation == null -> UnknownProvenance

                    sourceLocation.type == ComponentType.GIT -> {
                        RepositoryProvenance(
                            vcsInfo = VcsInfo(
                                type = VcsType.GIT,
                                url = sourceLocation.url.orEmpty(),
                                revision = sourceLocation.revision,
                                path = sourceLocation.path.orEmpty()
                            ),
                            resolvedRevision = sourceLocation.revision
                        )
                    }

                    else -> {
                        ArtifactProvenance(
                            sourceArtifact = RemoteArtifact(
                                url = sourceLocation.url.orEmpty(),
                                hash = Hash.create(described.hashes?.sha1.orEmpty())
                            )
                        )
                    }
                }

                val summary = generateSummary(SpdxConstants.NONE, result)
                val details = generateScannerDetails(result)

                listOf(ScanResult(provenance, details, summary))
            }.orEmpty()
        }
    }
}
