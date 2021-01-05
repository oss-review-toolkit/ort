/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

import java.io.IOException
import java.lang.IllegalArgumentException
import java.time.Instant

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.utils.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.model.utils.toClearlyDefinedSourceLocation
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.scanner.scanners.scancode.generateScannerDetails
import org.ossreviewtoolkit.scanner.scanners.scancode.generateSummary
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace

import retrofit2.Call

/** The name used by ClearlyDefined for the ScanCode tool. */
private const val TOOL_SCAN_CODE = "scancode"

/**
 * Convert a [ClearlyDefinedService.SourceLocation] to a [ClearlyDefinedService.Coordinates] object.
 */
private fun ClearlyDefinedService.SourceLocation.toCoordinates(): ClearlyDefinedService.Coordinates =
    ClearlyDefinedService.Coordinates(type, provider, namespace, name, revision)

/**
 * Convert a [VcsInfo] to a [VcsInfoCurationData] object.
 */
private fun VcsInfo.toVcsInfoCurationData(): VcsInfoCurationData =
    VcsInfoCurationData(type, url, revision, resolvedRevision, path)

/**
 * Generate the coordinates for ClearlyDefined based on the [id], the [vcs], and a [sourceArtifact].
 * If information about a Git repository in GitHub is available, this is used. Otherwise, the coordinates
 * are derived from the identifier. Throws [IllegalArgumentException] if generating coordinates is not possible.
 */
private fun packageCoordinates(
    id: Identifier,
    vcs: VcsInfo?,
    sourceArtifact: RemoteArtifact?
): ClearlyDefinedService.Coordinates {
    val sourceLocation = id.toClearlyDefinedSourceLocation(vcs?.toVcsInfoCurationData(), sourceArtifact)
    return sourceLocation?.toCoordinates() ?: id.toClearlyDefinedCoordinates()
        ?: throw IllegalArgumentException("Unable to create ClearlyDefined coordinates for '${id.toCoordinates()}'.")
}

/**
 * Search in the given list of [tools] for the entry for ScanCode that has processed the
 * [packageUrl]. If such an entry is found, the version of ScanCode is returned; otherwise, result is null.
 */
private fun findScanCodeVersion(tools: List<String>, packageUrl: String): String? {
    val toolUrl = "$packageUrl/$TOOL_SCAN_CODE/"
    return tools.find { it.startsWith(toolUrl) }?.substring(toolUrl.length)
}

/**
 * Return an empty success result for the given [id].
 */
private fun emptyResult(id: Identifier): Result<ScanResultContainer> =
    Success(ScanResultContainer(id, listOf()))

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
    private val clearlyDefinedService = ClearlyDefinedService.create(configuration.serverUrl)

    override fun readFromStorage(id: Identifier): Result<ScanResultContainer> =
        readPackageFromClearlyDefined(id, null, null)

    override fun readFromStorage(pkg: Package, scannerCriteria: ScannerCriteria): Result<ScanResultContainer> =
        readPackageFromClearlyDefined(pkg.id, pkg.vcs, pkg.sourceArtifact.takeIf { it.url.isNotEmpty() })

    override fun addToStorage(id: Identifier, scanResult: ScanResult): Result<Unit> =
        Failure("Adding scan results directly to ClearlyDefined is not supported.")

    /**
     * Try to obtain a [ScanResult] produced by ScanCode from ClearlyDefined for the package with the given [id].
     * Use the VCS information [vcs] and an optional [sourceArtifact] to determine the coordinates for looking up
     * the result.
     */
    private fun readPackageFromClearlyDefined(
        id: Identifier,
        vcs: VcsInfo?,
        sourceArtifact: RemoteArtifact?
    ): Result<ScanResultContainer> =
        try {
            readFromClearlyDefined(id, packageCoordinates(id, vcs, sourceArtifact))
        } catch (e: IllegalArgumentException) {
            e.showStackTrace()

            log.warn { "Could not obtain ClearlyDefined coordinates for package '${id.toCoordinates()}'." }

            emptyResult(id)
        }

    /**
     * Try to obtain a [ScanResult] produced by ScanCode from ClearlyDefined for the package with the given [id],
     * using the given [coordinates] for looking up the data. These may not necessarily be equivalent to the
     * identifier, as we try to lookup source code results if a GitHub repository is known.
     */
    private fun readFromClearlyDefined(
        id: Identifier,
        coordinates: ClearlyDefinedService.Coordinates
    ): Result<ScanResultContainer> {
        val startTime = Instant.now()
        log.info { "Looking up results for '${id.toCoordinates()}'." }

        return try {
            val tools = execute(
                clearlyDefinedService.harvestTools(
                    coordinates.type,
                    coordinates.provider,
                    coordinates.namespace.orEmpty(),
                    coordinates.name,
                    coordinates.revision.orEmpty()
                )
            )
            val scanCodeVersion = findScanCodeVersion(tools, coordinates.toString())
            scanCodeVersion?.let { version ->
                loadScanCodeResults(id, coordinates, version, startTime)
            } ?: emptyResult(id)
        } catch (e: IOException) {
            e.showStackTrace()

            log.error { "Error when reading results for package '${id.toCoordinates()}' from ClearlyDefined." }

            Failure(e.collectMessagesAsString())
        }
    }

    /**
     * Load the ScanCode results file for the package with the given [id] and [coordinates] from ClearlyDefined.
     * The results have been produced by ScanCode in the given [version]; use the [startTime] for metadata.
     */
    private fun loadScanCodeResults(
        id: Identifier,
        coordinates: ClearlyDefinedService.Coordinates,
        version: String,
        startTime: Instant
    ): Result<ScanResultContainer> {
        val toolResponse = execute(
            clearlyDefinedService.harvestToolData(
                coordinates.type, coordinates.provider, coordinates.namespace.orEmpty(), coordinates.name,
                coordinates.revision.orEmpty(), TOOL_SCAN_CODE, version
            )
        )

        return toolResponse.use {
            jsonMapper.readTree(it.byteStream())["content"]?.let { result ->
                val summary = generateSummary(startTime, Instant.now(), "", result)
                val details = generateScannerDetails(result)
                Success(ScanResultContainer(id, listOf(ScanResult(Provenance(), details, summary))))
            } ?: emptyResult(id)
        }
    }

    /**
     * Execute an HTTP request specified by the given [call]. The response status is checked. If everything went
     * well, the marshalled body of the request is returned; otherwise, the function throws an exception.
     */
    private fun <T> execute(call: Call<T>): T {
        val request = "${call.request().method} on ${call.request().url}"
        log.debug { "Executing HTTP $request." }

        val response = call.execute()
        log.debug { "HTTP response is (status ${response.code()}): ${response.message()}" }

        val body = response.body()
        if (!response.isSuccessful || body == null) {
            throw IOException(
                "Failed HTTP $request with status ${response.code()} and error: ${response.errorBody()?.string()}."
            )
        }

        return body
    }
}
