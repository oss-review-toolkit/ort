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

package org.ossreviewtoolkit.scanner.storages

import kotlin.time.measureTimedValue

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.scanner.PackageBasedScanStorage
import org.ossreviewtoolkit.scanner.ScanStorageException
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceScanResult
import org.ossreviewtoolkit.scanner.toNestedProvenanceScanResult

/**
 * The abstract class that storage backends for scan results need to implement.
 */
abstract class AbstractPackageBasedScanStorage : PackageBasedScanStorage {
    /**
     * The name to refer to this storage implementation.
     */
    open val name: String = javaClass.simpleName

    /**
     * Read all [ScanResult]s for the provided [package][pkg]. The results have to match the
     * [provenance][KnownProvenance.matches] of the package and can optionally be filtered by the provided
     * [scannerMatcher].
     */
    fun read(pkg: Package, scannerMatcher: ScannerMatcher? = null): Result<List<ScanResult>> {
        val (result, duration) = measureTimedValue {
            readInternal(pkg, scannerMatcher).map { results ->
                results.filter { scannerMatcher?.matches(it.scanner) != false }
            }
        }

        result.onSuccess { results ->
            logger.info {
                "Read ${results.size} matching scan result(s) for '${pkg.id.toCoordinates()}' from " +
                    "${javaClass.simpleName} in $duration."
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
     * Internal version of [read]. Implementations do not have to filter results using the provided [scannerMatcher] as
     * this is done by the public [read] function. They can use the [scannerMatcher] if they can implement the filtering
     * more efficiently, for example, as part of a database query.
     */
    abstract fun readInternal(pkg: Package, scannerMatcher: ScannerMatcher? = null): Result<List<ScanResult>>

    /**
     * Internal version of [add] that skips common sanity checks.
     */
    protected abstract fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit>

    override fun read(
        pkg: Package,
        nestedProvenance: NestedProvenance,
        scannerMatcher: ScannerMatcher?
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
