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

import com.fasterxml.jackson.databind.JsonNode

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
import org.ossreviewtoolkit.model.Provenance
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
import org.ossreviewtoolkit.utils.common.AlphaNumericComparator
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.showStackTrace

import retrofit2.HttpException

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
    private companion object : Logging

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

            val toolVersionsByName = tools.mapNotNull { it.withoutPrefix("$coordinates/") }
                .groupBy({ it.substringBefore('/') }, { it.substringAfter('/') })
                .mapValues { (_, versions) -> versions.sortedWith(AlphaNumericComparator) }

            toolVersionsByName.mapNotNull { (name, versions) ->
                // See https://github.com/clearlydefined/service#tool-name-registry.
                when (name) {
                    "scancode" -> {
                        loadToolData(coordinates, name, versions.last())?.let { result ->
                            val summary = generateSummary(result)
                            val details = generateScannerDetails(result)

                            val provenance = getProvenance(coordinates)

                            ScanResult(provenance, details, summary)
                        }
                    }

                    else -> {
                        logger.debug { "Unsupported tool '$name' for coordinates '$coordinates'." }

                        null
                    }
                }
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
     * Load the data produced by the tool of the given [name] and [version] for the package with the given [coordinates]
     * and return it as a [JsonNode], or return null if no data is available.
     */
    private suspend fun loadToolData(coordinates: Coordinates, name: String, version: String): JsonNode? {
        val toolData = service.harvestToolData(
            coordinates.type,
            coordinates.provider,
            coordinates.namespace ?: "-",
            coordinates.name,
            coordinates.revision.orEmpty(),
            name,
            version
        )

        return toolData.use {
            val data = jsonMapper.readTree(it.byteStream())
            data["content"].also { content ->
                if (content == null) {
                    logger.warn { "'$coordinates' has no data available for tool '$name' in version '$version'." }
                }
            }
        }
    }

    /**
     * Return the [Provenance] from where the package with the given [coordinates] was harvested.
     */
    private suspend fun getProvenance(coordinates: Coordinates): Provenance {
        val definitions = service.getDefinitions(listOf(coordinates))
        val described = definitions.getValue(coordinates).described
        val sourceLocation = described.sourceLocation

        return when {
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
    }
}
