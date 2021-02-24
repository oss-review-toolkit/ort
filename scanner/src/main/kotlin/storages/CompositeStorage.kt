/*
 * Copyright (C) 2020 Bosch.IO GmbH
 * Copyright (C) 2021 HERE Europe B.V.
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

import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScannerCriteria

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
    override fun readInternal(id: Identifier): Result<ScanResultContainer> =
        fetchReadResult(id) { read(id) }

    /**
     * Try to find scan results for the provided [pkg] and [scannerCriteria]. This implementation iterates over all
     * the reader storages provided until it receives a non-empty success result.
     */
    override fun readInternal(pkg: Package, scannerCriteria: ScannerCriteria): Result<ScanResultContainer> =
        fetchReadResult(pkg.id) { read(pkg, scannerCriteria) }

    override fun readInternal(
        packages: List<Package>,
        scannerCriteria: ScannerCriteria
    ): Result<Map<Identifier, List<ScanResult>>> {
        if (readers.isEmpty()) return Success(emptyMap())

        val remainingPackages = packages.toMutableList()
        val result = mutableMapOf<Identifier, List<ScanResult>>()
        val failures = mutableListOf<String>()

        readers.forEach { reader ->
            if (remainingPackages.isEmpty()) return@forEach

            when (val readerResult = reader.read(remainingPackages, scannerCriteria)) {
                is Success -> {
                    remainingPackages.filter { it.id !in readerResult.result.keys }
                    result += readerResult.result
                }
                is Failure -> failures += readerResult.error
            }
        }

        return if (failures.size < readers.size) {
            Success(result)
        } else {
            Failure(failures.joinToString())
        }
    }

    /**
     * Trigger all configured writer storages to add the [scanResult] for the given [id]. Return a success result
     * if all of the writers are successful; otherwise return a failure result with an accumulated error message.
     */
    override fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit> {
        val writeResults = writers.map { it.add(id, scanResult) }
        val errors = writeResults.filterIsInstance<Failure<Unit>>()
        return if (errors.isEmpty()) {
            Success(Unit)
        } else {
            Failure(errors.joinToString { it.error })
        }
    }

    /**
     * Iterate over the configured readers to find a scan result for the given [id] invoking the [readFunc] on each
     * reader until a result is found. If none of the configured readers returns a defined result, but at least one
     * returns a success result, return an empty success result. If all readers fail, return a failure result with
     * an accumulated failure message.
     */
    private fun fetchReadResult(
        id: Identifier, readFunc: ScanResultsStorage.() -> Result<ScanResultContainer>
    ): Result<ScanResultContainer> {
        if (readers.isEmpty()) return emptyResult(id)

        val failures = mutableListOf<String>()
        readers.forEach { reader ->
            when (val result = reader.readFunc()) {
                is Success -> if (result.result.results.isNotEmpty()) return result
                is Failure -> failures += result.error
            }
        }

        return if (failures.size < readers.size) {
            emptyResult(id)
        } else {
            Failure(failures.joinToString())
        }
    }
}

/**
 * Return a success result with an empty [ScanResultContainer] for the given [id].
 */
private fun emptyResult(id: Identifier): Result<ScanResultContainer> =
    Success(ScanResultContainer(id, emptyList()))
