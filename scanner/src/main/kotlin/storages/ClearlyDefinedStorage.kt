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
import java.time.Instant

import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.utils.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.model.utils.toClearlyDefinedSourceLocation
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.scanners.generateScannerDetails
import org.ossreviewtoolkit.scanner.scanners.generateSummary
import org.ossreviewtoolkit.utils.log

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
 * Search in the given list of [tools] for the entry for ScanCode that has processed the
 * [packageUrl]. If such an entry is found, the version of ScanCode is returned; otherwise, result is null.
 */
private fun findScanCodeVersion(tools: List<String>, packageUrl: String): String? {
    val toolUrl = "$packageUrl/$TOOL_SCAN_CODE/"
    return tools.find { it.startsWith(toolUrl) }?.substring(toolUrl.length)
}

/**
 * Search in the given list of [tools] for an entry for ScanCode that has processed the package identified by
 * [packageUrl] and is compatible with the requested [details]. The exact version of this entry is returned.
 */
private fun scanCodeToolUrlVersion(tools: List<String>, packageUrl: String, details: ScannerDetails): String? {
    val toolUrl = "$packageUrl/$TOOL_SCAN_CODE/"
    return tools.filter { it.startsWith(toolUrl) }
        .map { it.substring(toolUrl.length) }
        .find(details::isCompatibleVersion)
}

/**
 * Return an empty success result for the given [id].
 */
private fun emptyResult(id: Identifier): Result<ScanResultContainer> =
    Success(ScanResultContainer(id, listOf()))

/**
 * Check whether this storage can handle a query for the [ScannerDetails][details] provided. This check is based
 * on the scanner name.
 */
private fun isScannerSupported(details: ScannerDetails): Boolean {
    val scanCodeDetails = details.copy(name = TOOL_SCAN_CODE)
    return details.isCompatible(scanCodeDetails)
}

/**
 * Execute a transformation function [f] on this [Result] object if it is a [Success]. The transformation function
 * can fail itself; thus it returns a [Result].
 */
private fun <T, U> Result<T>.flatMap(f: (T) -> Result<U>): Result<U> =
    when (this) {
        is Success -> f(this.result)
        is Failure -> Failure(this.error)
    }

/**
 * Filter a list with [Result] objects for successful results. Return a [Result] with a list containing the values
 * extracted from the [Success] results. If the list contains only [Failure] objects, return a [Failure], too, with
 * an aggregated error message.
 */
private fun <T> List<Result<T>>.filterSuccess(): Result<List<T>> =
    if (any { it is Success } || isEmpty()) {
        Success(filterIsInstance<Success<T>>()
            .map { it.result })
    } else {
        val combinedError = filterIsInstance<Failure<T>>().joinToString { it.error }
        Failure(combinedError)
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
    private val clearlyDefinedService = ClearlyDefinedService.create(configuration.serverUrl)

    override fun readFromStorage(id: Identifier): Result<ScanResultContainer> =
        readPackageFromClearlyDefined(id)

    /**
     * Try to read scan results for ScanCode compatible with the [scannerDetails] provided for the given
     * [Package][pkg]. This implementation checks whether the package has a source artifact or VCS information
     * supported by ClearlyDefined. If so, scan results for these artifacts are looked up and filtered based on
     * the [scannerDetails].
     */
    override fun readFromStorage(pkg: Package, scannerDetails: ScannerDetails): Result<ScanResultContainer> =
        if (isScannerSupported(scannerDetails)) {
            val downloadTime = Instant.now()
            // TODO: Filter for the configuration of the scannerDetails provided.
            listOfNotNull(
                pkg.id.toClearlyDefinedSourceLocation(pkg.vcs.toVcsInfoCurationData(), null),
                pkg.id.toClearlyDefinedSourceLocation(null, pkg.sourceArtifact.takeIf { it.url.isNotEmpty() })
            ).map { loadScanCodeToolVersion(it.toCoordinates(), scannerDetails) }
                .filterSuccess().flatMap { coordinatesAndTools ->
                    coordinatesAndTools.filterNotNull()
                        .mapNotNull { loadScanCodeResults(it.first, it.second, downloadTime) }
                        .filterSuccess()
                        .flatMap { scanResults ->
                            Success(ScanResultContainer(pkg.id, scanResults))
                        }
                }
        } else {
            emptyResult(pkg.id)
        }

    override fun addToStorage(id: Identifier, scanResult: ScanResult): Result<Unit> =
        Failure("Adding scan results directly to ClearlyDefined is not supported.")

    /**
     * Try to obtain a [ScanResult] produced by ScanCode from ClearlyDefined for the package with the given [id].
     */
    private fun readPackageFromClearlyDefined(id: Identifier): Result<ScanResultContainer> {
        val coordinates = id.toClearlyDefinedCoordinates()
        return if (coordinates != null) {
            readFromClearlyDefined(id, coordinates)
        } else {
            log.warn { "Could not obtain ClearlyDefined coordinates for package '${id.toCoordinates()}'." }
            emptyResult(id)
        }
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
        log.info("Looking up results for '${id.toCoordinates()}'.")

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
                loadScanCodeResults(coordinates, version, startTime)?.let { result ->
                    if (result is Success) {
                        Success(ScanResultContainer(id, listOf(result.result)))
                    } else emptyResult(id)
                } ?: emptyResult(id)
            } ?: emptyResult(id)
        } catch (e: IOException) {
            log.error("Error when reading results for package '${id.toCoordinates()}' from ClearlyDefined.", e)
            Failure("${e.javaClass}: ${e.message}")
        }
    }

    /**
     * Load information about the tool results available for the given [coordinates] and tries to find an
     * entry for the ScanCode tool in a version compatible with the given [details].
     */
    private fun loadScanCodeToolVersion(coordinates: ClearlyDefinedService.Coordinates, details: ScannerDetails):
            Result<Pair<ClearlyDefinedService.Coordinates, String>?> =
        executeAndProcess(
            clearlyDefinedService.harvestTools(
                coordinates.type,
                coordinates.provider,
                coordinates.namespace.orEmpty(),
                coordinates.name,
                coordinates.revision.orEmpty()
            )
        ) { tools ->
            scanCodeToolUrlVersion(tools, coordinates.toString(), details)?.let { coordinates to it }
        }

    /**
     * Load the ScanCode results file for the package with the given [coordinates] from ClearlyDefined.
     * The results have been produced by ScanCode in the given [version]; use the [startTime] for metadata.
     * If the result is of an unexpected format, return a *null* result.
     */
    private fun loadScanCodeResults(
        coordinates: ClearlyDefinedService.Coordinates,
        version: String,
        startTime: Instant
    ): Result<ScanResult>? {
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
                Success(ScanResult(Provenance(), details, summary))
            }
        }
    }

    /**
     * Execute an HTTP request specified by the given [call]. The response status is checked. If everything went
     * well, the marshalled body of the request is returned; otherwise, the function throws an exception.
     */
    private fun <T> execute(call: Call<T>): T {
        val req = "${call.request().method} ${call.request().url}"
        log.debug("Executing $req")
        val response = call.execute()
        log.debug("${response.code()} ${response.message()}")

        if (response.isSuccessful) {
            return response.body()!!
        }

        log.error("$req failed.")
        log.error("${response.code()} ${response.message()}: ${response.errorBody()?.string()}")
        throw IOException("Failed HTTP call: $req with status ${response.code()}")
    }

    /**
     * Executes an HTTP request specified by the given [call] and processes the result. The response status is
     * checked. If everything went well, the [processor function][f] is invoked with the result of the call, and its
     * result is returned as a [Success]. Otherwise, the function returns a [Failure].
     */
    private fun <T, U> executeAndProcess(call: Call<T>, f: (T) -> U): Result<U> {
        val req = "${call.request().method} ${call.request().url}"
        log.debug("Executing $req")
        return try {
            val response = call.execute()
            log.debug("${response.code()} ${response.message()}")

            if (response.isSuccessful) {
                return Success(f(response.body()!!))
            }

            log.error("$req failed.")
            log.error("${response.code()} ${response.message()}: ${response.errorBody()?.string()}")
            Failure("Failed HTTP call: $req with status ${response.code()}")
        } catch (e: IOException) {
            Failure("FailedHTTP call: $req with exception $e")
        }
    }
}
