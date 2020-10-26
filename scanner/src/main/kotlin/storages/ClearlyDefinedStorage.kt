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
import org.ossreviewtoolkit.model.utils.toClearlyDefinedSourceLocation
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.scanners.generateScannerDetails
import org.ossreviewtoolkit.scanner.scanners.generateSummary
import org.ossreviewtoolkit.utils.log

import retrofit2.Call

/** The name used by ClearlyDefined for the ScanCode tool. */
private const val TOOL_SCAN_CODE = "scancode"

/**
 * Definition of a filter function that checks for a specific ScanCode version. The function expects a version
 * string as input and returns a flag whether this version is accepted.
 */
typealias ScanCodeVersionFilter = (String) -> Boolean

/**
 * Definition of a filter for the ScanCode version that accepts all versions. This filter can be used if all
 * results produced by ScanCode are of interest.
 */
private val ALL_VERSIONS_FILTER: ScanCodeVersionFilter = { _ -> true }

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
 * Return a filter that checks whether a URL pointing to the ScanCode tool is compatible with the version
 * specified in the given [details]. This filter can then be used when evaluating tool URLs returned by
 * ClearlyDefined.
 */
private fun scanCodeVersionFilter(details: ScannerDetails): ScanCodeVersionFilter = details::isCompatibleVersion

/**
 * Search in the given list of [tools] for an entry for ScanCode that has processed the package identified by
 * [packageUrl] and is compatible with the [filter] provided. The exact version of this entry is returned.
 */
private fun scanCodeToolUrlVersion(tools: List<String>, packageUrl: String, filter: ScanCodeVersionFilter): String? {
    val toolUrl = "$packageUrl/$TOOL_SCAN_CODE/"
    return tools.filter { it.startsWith(toolUrl) }
        .map { it.substring(toolUrl.length) }
        .find(filter)
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

    /**
     * Try to read all scan results produced by ScanCode for artifacts related to the given [Identifier][id]. This
     * implementation uses the ClearlyDefined endpoint to search for patterns to obtain all the results
     * available for the identifier. Then all the results generated by any version of ScanCode are downloaded.
     */
    override fun readFromStorage(id: Identifier): Result<ScanResultContainer> {
        val components = listOfNotNull(id.namespace.takeIf { it != "-" }, id.name, id.version)
        val pattern = components.joinToString(separator = " ")
        val searchResult = executeAndProcess(clearlyDefinedService.searchDefinitions(pattern)) { it }
        return searchResult.flatMap { uris ->
            val coordinates = uris.mapNotNull(this::coordinatesFromString)
            loadResultsForCoordinates(id, coordinates, ALL_VERSIONS_FILTER)
        }
    }

    /**
     * Try to read scan results for ScanCode compatible with the [scannerDetails] provided for the given
     * [Package][pkg]. This implementation checks whether the package has a source artifact or VCS information
     * supported by ClearlyDefined. If so, scan results for these artifacts are looked up and filtered based on
     * the [scannerDetails].
     */
    override fun readFromStorage(pkg: Package, scannerDetails: ScannerDetails): Result<ScanResultContainer> =
        if (isScannerSupported(scannerDetails)) {
            // TODO: Filter for the configuration of the scannerDetails provided.
            val coordinates = listOfNotNull(
                pkg.id.toClearlyDefinedSourceLocation(pkg.vcs.toVcsInfoCurationData(), null),
                pkg.id.toClearlyDefinedSourceLocation(null, pkg.sourceArtifact.takeIf { it.url.isNotEmpty() })
            ).map { it.toCoordinates() }
            loadResultsForCoordinates(pkg.id, coordinates, scanCodeVersionFilter(scannerDetails))
        } else {
            emptyResult(pkg.id)
        }

    override fun addToStorage(id: Identifier, scanResult: ScanResult): Result<Unit> =
        Failure("Adding scan results directly to ClearlyDefined is not supported.")

    /**
     * Generate a [Result] with a [ScanResultContainer] for all the [coordinates] provided that are related to the
     * given [identifier][id]. The function iterates over the coordinates and for each tries to find a result produced
     * by ScanCode. This result is loaded and added to the resulting container.
     */
    private fun loadResultsForCoordinates(
        id: Identifier,
        coordinates: List<ClearlyDefinedService.Coordinates>,
        filter: ScanCodeVersionFilter
    ): Result<ScanResultContainer> {
        val downloadTime = Instant.now()
        return coordinates.map { loadScanCodeToolVersion(it, filter) }
            .filterSuccess().flatMap { coordinatesAndTools ->
                coordinatesAndTools.filterNotNull()
                    .mapNotNull { loadScanCodeResults(it.first, it.second, downloadTime) }
                    .filterSuccess()
                    .flatMap { scanResults ->
                        Success(ScanResultContainer(id, scanResults))
                    }
            }
    }

    /**
     * Load information about the tool results available for the given [coordinates] and tries to find an
     * entry for the ScanCode tool in a version compatible with the given [filter].
     */
    private fun loadScanCodeToolVersion(coordinates: ClearlyDefinedService.Coordinates, filter: ScanCodeVersionFilter):
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
            scanCodeToolUrlVersion(tools, coordinates.toString(), filter)?.let { coordinates to it }
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
        return executeAndProcess(
            clearlyDefinedService.harvestToolData(
                coordinates.type, coordinates.provider, coordinates.namespace.orEmpty(), coordinates.name,
                coordinates.revision.orEmpty(), TOOL_SCAN_CODE, version
            )
        ) { body ->
            body.use {
                jsonMapper.readTree(it.byteStream())["content"]?.let { result ->
                    val summary = generateSummary(startTime, Instant.now(), "", result)
                    val details = generateScannerDetails(result)
                    ScanResult(Provenance(), details, summary)
                } ?: throw IOException("Could not parse scan result for coordinates $coordinates.")
            }
        }
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

    /**
     * Convert a string [s] to ClearlyDefined coordinates and handle failures. If the string cannot be converted,
     * return *null*.
     */
    private fun coordinatesFromString(s: String): ClearlyDefinedService.Coordinates? =
        @Suppress("TooGenericExceptionCaught")
        try {
            ClearlyDefinedService.Coordinates.fromString(s)
        } catch (e: Exception) {
            log.warn("Failed to convert URI to ClearlyDefined coordinates: $s.", e)
            null
        }
}
