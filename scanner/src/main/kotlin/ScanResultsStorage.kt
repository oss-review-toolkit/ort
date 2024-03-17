/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner

import kotlin.time.measureTimedValue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceScanResult

/**
 * The abstract class that storage backends for scan results need to implement.
 */
abstract class ScanResultsStorage : PackageBasedScanStorage {
    companion object {
        /**
         * A successful [Result] with an empty list of [ScanResult]s.
         */
        val EMPTY_RESULT = Result.success<List<ScanResult>>(emptyList())
    }

    /**
     * The name to refer to this storage implementation.
     */
    open val name: String = javaClass.simpleName

    /**
     * Return all [ScanResult]s contained in this [ScanResultsStorage] corresponding to the [package][pkg] wrapped in a
     * [Result].
     */
    fun read(pkg: Package): Result<List<ScanResult>> {
        val (result, duration) = measureTimedValue { readInternal(pkg) }

        result.onSuccess { results ->
            logger.info {
                "Read ${results.size} scan result(s) for '${pkg.id.toCoordinates()}' from ${javaClass.simpleName} in " +
                    "$duration."
            }
        }

        return result
    }

    /**
     * Return all [ScanResult]s contained in this [ScanResultsStorage] corresponding to the given [package][pkg] that
     * are [compatible][ScannerMatcher.matches] with the provided [scannerMatcher] wrapped in a [Result]. Also,
     * [Package.sourceArtifact], [Package.vcs], and [Package.vcsProcessed] are used to check if the scan result matches
     * the expected source code location. That check is important to find the correct results when different revisions
     * of a package using the same version name are used (e.g. multiple scans of a "1.0-SNAPSHOT" version during
     * development).
     */
    fun read(pkg: Package, scannerMatcher: ScannerMatcher): Result<List<ScanResult>> {
        val (result, duration) = measureTimedValue { readInternal(pkg, scannerMatcher) }

        result.onSuccess { results ->
            logger.info {
                "Read ${results.size} matching scan result(s) for '${pkg.id.toCoordinates()}' from " +
                    "${javaClass.simpleName} in $duration."
            }
        }

        return result
    }

    /**
     * Return all [ScanResult]s contained in this [ScanResultsStorage] corresponding to the given [packages] that
     * are [compatible][ScannerMatcher.matches] with the provided [scannerMatcher] wrapped in a [Result]. Also,
     * [Package.sourceArtifact], [Package.vcs], and [Package.vcsProcessed] are used to check if the scan result matches
     * the expected source code location. That check is important to find the correct results when different revisions
     * of a package using the same version name are used (e.g. multiple scans of a "1.0-SNAPSHOT" version during
     * development).
     */
    fun read(packages: Collection<Package>, scannerMatcher: ScannerMatcher): Result<Map<Identifier, List<ScanResult>>> {
        val (result, duration) = measureTimedValue { readInternal(packages, scannerMatcher) }

        result.onSuccess { results ->
            logger.info {
                val count = results.values.sumOf { it.size }
                "Read $count matching scan result(s) from ${javaClass.simpleName} in $duration."
            }
        }

        return result
    }

    /**
     * Add the given [scanResult] to the stored [ScanResult]s for the scanned [Package] with the provided [id].
     * Depending on the storage implementation this might first read any existing [ScanResult]s and write the new
     * [ScanResult]s to the storage again, implicitly deleting the original storage entry by overwriting it.
     * Return a [Result] describing whether the operation was successful.
     */
    fun add(id: Identifier, scanResult: ScanResult): Result<Unit> {
        // Do not store scan results without provenance information, because they cannot be assigned to the revision of
        // the package source code later.
        if (scanResult.provenance is UnknownProvenance) {
            val message =
                "Not storing scan result for '${id.toCoordinates()}' because no provenance information is available."
            logger.info { message }

            return Result.failure(ScanStorageException(message))
        }

        val (result, duration) = measureTimedValue { addInternal(id, scanResult) }

        logger.info { "Added scan result for '${id.toCoordinates()}' to ${javaClass.simpleName} in $duration." }

        return result
    }

    /**
     * Internal version of [read].
     */
    protected abstract fun readInternal(pkg: Package): Result<List<ScanResult>>

    /**
     * Internal version of [read]. Implementations may want to override this function if they can filter for the wanted
     * [scannerMatcher] in a more efficient way than this default implementation.
     */
    protected open fun readInternal(pkg: Package, scannerMatcher: ScannerMatcher): Result<List<ScanResult>> =
        readInternal(pkg).map { results ->
            if (results.isEmpty()) {
                results
            } else {
                val (matchingProvenance, nonMatchingProvenance) = results.partition { it.provenance.matches(pkg) }

                if (matchingProvenance.isEmpty()) {
                    logger.debug {
                        "No stored scan results found for $pkg. The following entries with non-matching provenance " +
                            "have been ignored: ${nonMatchingProvenance.map { it.provenance }}"
                    }

                    matchingProvenance
                } else {
                    val (matchingCriteria, nonMatchingCriteria) = matchingProvenance.partition {
                        scannerMatcher.matches(it.scanner)
                    }

                    if (matchingCriteria.isEmpty()) {
                        logger.debug {
                            "No stored scan results for '${pkg.id.toCoordinates()}' match $scannerMatcher. The " +
                                "following entries with non-matching criteria have been ignored: " +
                                nonMatchingCriteria.map { it.scanner }
                        }
                    }

                    matchingCriteria
                }
            }
        }

    /**
     * Internal version of [read]. The default implementation uses [Dispatchers.IO] to run requests for individual
     * packages in parallel. Implementations may want to override this function if they can filter for the wanted
     * [scannerMatcher] or fetch results for multiple packages in a more efficient way.
     */
    protected open fun readInternal(
        packages: Collection<Package>,
        scannerMatcher: ScannerMatcher
    ): Result<Map<Identifier, List<ScanResult>>> {
        val results = runBlocking(Dispatchers.IO) {
            packages.map { async { it.id to readInternal(it, scannerMatcher) } }.awaitAll()
        }.associate { it }

        val successfulResults = results.mapNotNull { (id, scanResults) ->
            scanResults.getOrNull()?.let { id to it }
        }.toMap()

        return if (successfulResults.isEmpty()) {
            Result.failure(ScanStorageException("Could not read any scan results from ${javaClass.simpleName}."))
        } else {
            Result.success(successfulResults)
        }
    }

    /**
     * Internal version of [add] that skips common sanity checks.
     */
    protected abstract fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit>

    override fun read(pkg: Package, nestedProvenance: NestedProvenance): List<NestedProvenanceScanResult> =
        read(pkg).toNestedProvenanceScanResult(nestedProvenance)

    override fun read(
        pkg: Package,
        nestedProvenance: NestedProvenance,
        scannerMatcher: ScannerMatcher
    ): List<NestedProvenanceScanResult> = read(pkg, scannerMatcher).toNestedProvenanceScanResult(nestedProvenance)

    private fun Result<List<ScanResult>>.toNestedProvenanceScanResult(nestedProvenance: NestedProvenance) =
        map { scanResults ->
            scanResults.filter { it.provenance == nestedProvenance.root }
                .map { it.toNestedProvenanceScanResult(nestedProvenance) }
        }.getOrThrow()

    override fun write(pkg: Package, nestedProvenanceScanResult: NestedProvenanceScanResult) {
        nestedProvenanceScanResult.merge().forEach { scanResult ->
            add(pkg.id, scanResult).getOrThrow()
        }
    }
}
