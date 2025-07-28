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

import java.time.Instant

import kotlin.coroutines.cancellation.CancellationException

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

import okhttp3.OkHttpClient

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.clients.clearlydefined.ComponentType
import org.ossreviewtoolkit.clients.clearlydefined.Coordinates
import org.ossreviewtoolkit.clients.clearlydefined.toCoordinates
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.utils.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.model.utils.toClearlyDefinedSourceLocation
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.scanner.LocalPathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanStorageException
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.utils.common.AlphaNumericComparator
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.okHttpClient
import org.ossreviewtoolkit.utils.ort.runBlocking

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
) : AbstractPackageBasedScanStorage() {
    constructor(serverUrl: String, client: OkHttpClient? = null) : this(
        ClearlyDefinedStorageConfiguration(serverUrl), client
    )

    /** The service for interacting with ClearlyDefined. */
    private val service by lazy {
        ClearlyDefinedService.create(config.serverUrl, client ?: okHttpClient)
    }

    override fun readInternal(pkg: Package, scannerMatcher: ScannerMatcher?): Result<List<ScanResult>> =
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
            ?: return Result.failure(
                ScanStorageException("Unable to create ClearlyDefined coordinates for '${pkg.id.toCoordinates()}'.")
            )

        return runCatching {
            logger.debug { "Looking up ClearlyDefined scan results for '$coordinates'." }

            val tools = service.harvestTools(coordinates)

            val toolVersionsByName = tools.mapNotNull { it.withoutPrefix("$coordinates/") }
                .groupBy({ it.substringBefore('/') }, { it.substringAfter('/') })
                .mapValues { (_, versions) -> versions.sortedWith(AlphaNumericComparator) }

            val supportedScanners = toolVersionsByName.mapNotNull { (name, versions) ->
                // For the ClearlyDefined tool names see https://github.com/clearlydefined/service#tool-name-registry.
                ScannerWrapperFactory.ALL[name]?.let { factory ->
                    val scanner = factory.create(PluginConfig.EMPTY)
                    (scanner as? LocalPathScannerWrapper)?.let { cliScanner -> cliScanner to versions.last() }
                }.also { factory ->
                    factory ?: logger.debug { "Unsupported tool '$name' for coordinates '$coordinates'." }
                }
            }

            supportedScanners.mapNotNull { (cliScanner, version) ->
                val startTime = Instant.now()
                val name = cliScanner.descriptor.id.lowercase()
                val data = loadToolData(coordinates, name, version)
                val provenance = getProvenance(coordinates)
                val endTime = Instant.now()

                when (cliScanner.descriptor.id) {
                    "ScanCode" -> {
                        data["content"]?.let { result ->
                            val resultString = result.toString()
                            val details = cliScanner.parseDetails(resultString)
                            val summary = cliScanner.createSummary(resultString, startTime, endTime)

                            ScanResult(provenance, details, summary)
                        }
                    }

                    "Licensee" -> {
                        data["licensee"]?.let { result ->
                            val details = cliScanner.parseDetails(result.toString())
                            val output = result["output"]["content"].toString()
                            val summary = cliScanner.createSummary(output, startTime, endTime)

                            ScanResult(provenance, details, summary)
                        }
                    }

                    else -> null
                }
            }
        }.recoverCatching { e ->
            if (e is CancellationException) currentCoroutineContext().ensureActive()

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
     * and return it as a [JsonNode].
     */
    private suspend fun loadToolData(coordinates: Coordinates, name: String, version: String): JsonNode {
        val toolData = service.harvestToolData(coordinates, name, version)
        return toolData.use { jsonMapper.readTree(it.byteStream()) }
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
                    vcsInfo = VcsHost.parseUrl(sourceLocation.url.orEmpty()),
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
