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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.scanner.experimental.ScanStorageException

/**
 * A [ScanResultsStorage] implementation that manages multiple concrete storages for reading and writing scan results.
 *
 * This class can be used to construct a flexible setup of storages to use to obtain scan results requested for the
 * current run and to persist them to speed up later runs. When a scan result is requested the class iterates
 * over the list of configured reader storages and returns the first successful, non-empty result. So a prioritized
 * chain of places where to look up results can be configured.
 *
 * Analogously, when a result is to be written the class passes the result to all the storages configured as writers.
 * For many use cases, a single writer is probably sufficient; but if there are special requirements - e.g. to
 * create backups of results automatically or to have a fast, but transient cache of results -, it is possible to
 * specify multiple writers.
 */
class CompositeStorage(
    val readers: List<ScanResultsStorage>,
    val writers: List<ScanResultsStorage>
) : ScanResultsStorage() {
    override val name: String
        get() {
            val readerNames = readers.map { it.name }
            val writerNames = writers.map { it.name }
            return "composite[readers:$readerNames, writers:$writerNames]"
        }

    /**
     * Try to find scan results for the provided [id]. This implementation iterates over all the reader storages
     * provided until it receives a non-empty success result.
     */
    override fun readInternal(id: Identifier): Result<List<ScanResult>> =
        fetchReadResult { read(id) }

    /**
     * Try to find scan results for the provided [pkg] and [scannerCriteria]. This implementation iterates over all
     * the reader storages provided until it receives a non-empty success result.
     */
    override fun readInternal(pkg: Package, scannerCriteria: ScannerCriteria): Result<List<ScanResult>> =
        fetchReadResult { read(pkg, scannerCriteria) }

    override fun readInternal(
        packages: Collection<Package>,
        scannerCriteria: ScannerCriteria
    ): Result<Map<Identifier, List<ScanResult>>> {
        if (readers.isEmpty()) return Result.success(emptyMap())

        val remainingPackages = packages.toMutableList()
        val results = mutableMapOf<Identifier, List<ScanResult>>()
        val failures = mutableListOf<Throwable>()

        readers.forEach { reader ->
            if (remainingPackages.isEmpty()) return@forEach

            reader.read(remainingPackages, scannerCriteria)
                .onSuccess { compatibleResults ->
                    remainingPackages.removeIf { it.id in compatibleResults.keys }
                    results += compatibleResults
                }
                .onFailure {
                    failures += it
                }
        }

        return if (failures.size < readers.size) {
            Result.success(results)
        } else {
            Result.failure(ScanStorageException(failures.mapNotNull { it.message }.joinToString()))
        }
    }

    /**
     * Trigger all configured writer storages to add the [scanResult] for the given [id]. Return a success result
     * if all writers are successful; otherwise return a failure result with an accumulated error message.
     */
    override fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit> {
        val writeResults = writers.map { it.add(id, scanResult) }
        val failures = writeResults.mapNotNull { it.exceptionOrNull()?.message }
        return if (failures.isEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(ScanStorageException(failures.joinToString()))
        }
    }

    /**
     * Iterate over the configured readers to find a scan result invoking the [readFunc] on each reader until a result
     * is found. If none of the configured readers returns a defined result, but at least one returns a success result,
     * return an empty success result. If all readers fail, return a failure result with an accumulated failure message.
     */
    private fun fetchReadResult(readFunc: ScanResultsStorage.() -> Result<List<ScanResult>>): Result<List<ScanResult>> {
        if (readers.isEmpty()) return EMPTY_RESULT

        val failures = mutableListOf<Throwable>()
        readers.forEach { reader ->
            reader.readFunc()
                .onSuccess {
                    if (it.isNotEmpty()) return Result.success(it)
                }
                .onFailure {
                    failures += it
                }
        }

        return if (failures.size < readers.size) {
            EMPTY_RESULT
        } else {
            Result.failure(ScanStorageException(failures.mapNotNull { it.message }.joinToString()))
        }
    }
}
