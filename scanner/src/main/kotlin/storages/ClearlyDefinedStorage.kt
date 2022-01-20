/*
 * Copyright (C) 2020 Bosch.IO GmbH
 * Copyright (C) 2021 HERE Europe B.V.
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

import java.io.IOException
import java.lang.IllegalArgumentException
import java.time.Instant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.clients.clearlydefined.ComponentType
import org.ossreviewtoolkit.clients.clearlydefined.Coordinates
import org.ossreviewtoolkit.clients.clearlydefined.SourceLocation
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.utils.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.model.utils.toClearlyDefinedSourceLocation
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.scanner.experimental.ScanStorageException
import org.ossreviewtoolkit.scanner.scanners.scancode.generateScannerDetails
import org.ossreviewtoolkit.scanner.scanners.scancode.generateSummary
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.showStackTrace

import retrofit2.HttpException

/** The name used by ClearlyDefined for the ScanCode tool. */
private const val TOOL_SCAN_CODE = "scancode"

/**
 * Convert a [SourceLocation] to a [Coordinates] object.
 */
private fun SourceLocation.toCoordinates(): Coordinates = Coordinates(type, provider, namespace, name, revision)

/**
 * Convert a [VcsInfo] to a [VcsInfoCurationData] object.
 */
private fun VcsInfo.toVcsInfoCurationData(): VcsInfoCurationData = VcsInfoCurationData(type, url, revision, path)

/**
 * Generate the coordinates for ClearlyDefined based on the [id], the [vcs], and a [sourceArtifact].
 * If information about a Git repository in GitHub is available, this is used. Otherwise, the coordinates
 * are derived from the identifier. Throws [IllegalArgumentException] if generating coordinates is not possible.
 */
private fun packageCoordinates(
    id: Identifier,
    vcs: VcsInfo?,
    sourceArtifact: RemoteArtifact?
): Coordinates {
    val sourceLocation = id.toClearlyDefinedSourceLocation(vcs?.toVcsInfoCurationData(), sourceArtifact)
    return sourceLocation?.toCoordinates() ?: id.toClearlyDefinedCoordinates()
        ?: throw IllegalArgumentException("Unable to create ClearlyDefined coordinates for '${id.toCoordinates()}'.")
}

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
    val configuration: ClearlyDefinedStorageConfiguration
) : ScanResultsStorage() {
    /** The service for interacting with ClearlyDefined. */
    private val service by lazy {
        ClearlyDefinedService.create(configuration.serverUrl, OkHttpClientHelper.buildClient())
    }

    override fun readInternal(id: Identifier): Result<List<ScanResult>> =
        readPackageFromClearlyDefined(id, null, null)

    override fun readInternal(pkg: Package, scannerCriteria: ScannerCriteria): Result<List<ScanResult>> =
        readPackageFromClearlyDefined(pkg.id, pkg.vcs, pkg.sourceArtifact.takeIf { it.url.isNotEmpty() })

    override fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit> =
        Result.failure(ScanStorageException("Adding scan results directly to ClearlyDefined is not supported."))

    /**
     * Try to obtain a [ScanResult] produced by ScanCode from ClearlyDefined for the package with the given [id].
     * Use the VCS information [vcs] and an optional [sourceArtifact] to determine the coordinates for looking up
     * the result.
     */
    private fun readPackageFromClearlyDefined(
        id: Identifier,
        vcs: VcsInfo?,
        sourceArtifact: RemoteArtifact?
    ): Result<List<ScanResult>> =
        try {
            runBlocking(Dispatchers.IO) { readFromClearlyDefined(id, packageCoordinates(id, vcs, sourceArtifact)) }
        } catch (e: IllegalArgumentException) {
            e.showStackTrace()

            log.warn { "Could not obtain ClearlyDefined coordinates for package '${id.toCoordinates()}'." }

            EMPTY_RESULT
        }

    /**
     * Try to obtain a [ScanResult] produced by ScanCode from ClearlyDefined for the package with the given [id],
     * using the given [coordinates] for looking up the data. These may not necessarily be equivalent to the
     * identifier, as we try to lookup source code results if a GitHub repository is known.
     */
    private suspend fun readFromClearlyDefined(id: Identifier, coordinates: Coordinates): Result<List<ScanResult>> {
        val startTime = Instant.now()
        log.info { "Looking up results for '${id.toCoordinates()}'." }

        return try {
            val tools = service.harvestTools(
                coordinates.type,
                coordinates.provider,
                coordinates.namespace.orEmpty(),
                coordinates.name,
                coordinates.revision.orEmpty()
            )

            findScanCodeVersion(tools, coordinates)?.let { version ->
                loadScanCodeResults(coordinates, version, startTime)
            } ?: EMPTY_RESULT
        } catch (e: HttpException) {
            e.response()?.errorBody()?.string()?.let {
                log.error { "Error response from ClearlyDefined is: $it" }
            }

            handleException(id, e)
        } catch (e: IOException) {
            // There are some other exceptions thrown by Retrofit to be handled as well, e.g. ConnectException.
            handleException(id, e)
        }
    }

    /**
     * Log the exception that occurred during a request to ClearlyDefined and construct an error result from it.
     */
    private fun handleException(id: Identifier, e: Exception): Result<List<ScanResult>> {
        e.showStackTrace()

        val message = "Error when reading results for package '${id.toCoordinates()}' from ClearlyDefined: " +
                e.collectMessagesAsString()

        log.error { message }

        return Result.failure(ScanStorageException(message))
    }

    /**
     * Load the ScanCode results file for the package with the given [coordinates] from ClearlyDefined.
     * The results have been produced by ScanCode in the given [version]; use the [startTime] for metadata.
     */
    private suspend fun loadScanCodeResults(
        coordinates: Coordinates,
        version: String,
        startTime: Instant
    ): Result<List<ScanResult>> {
        val toolResponse = service.harvestToolData(
            coordinates.type, coordinates.provider, coordinates.namespace.orEmpty(), coordinates.name,
            coordinates.revision.orEmpty(), TOOL_SCAN_CODE, version
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

                val summary = generateSummary(startTime, Instant.now(), "", result)
                val details = generateScannerDetails(result)

                Result.success(listOf(ScanResult(provenance, details, summary)))
            } ?: EMPTY_RESULT
        }
    }
}
