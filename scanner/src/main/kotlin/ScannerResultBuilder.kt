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

package org.ossreviewtoolkit.scanner

import java.time.Instant

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.config.ScannerConfiguration

/**
 * Definition of an interface that allows a scanner to construct an [OrtResult] with a scanner result without
 * having to know about details.
 *
 * This interface supports a more efficient handling of large scanner results. The basic idea is that a scanner
 * adds results for single packages when they become available and does not have to care how these results are
 * combined to a full [OrtResult].
 *
 * Concrete implementations handle the result construction differently: A naive implementation can collect all
 * results in memory, while a more efficient one streams the data to a file reducing the memory consumption.
 */
interface ScannerResultBuilder : AutoCloseable {
    /**
     * Initialize this builder from the [OrtResult][analyzerResult] produced by the analyzer. The result produced by
     * this builder is then based on the result passed to this function. This function must be called initially.
     */
    fun initFromAnalyzerResult(analyzerResult: OrtResult)

    /**
     * Add a [resultContainer] to the result managed by this builder. This function should be called when scan
     * results for a package become available.
     */
    fun addScanResult(resultContainer: ScanResultContainer)

    /**
     * Notify the builder about the completion of the scan operation passing in metadata to add to the result: the
     * [startTime] and [endTime] of the operation, the [environment] and the [config] passed to the scanner,
     * [statistics][storageStats] of the results storage, and arbitrary [labels] to add to the result. After
     * this function has been called, the builder has all the information required to produce a valid [OrtResult]
     * instance.
     */
    fun complete(
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: ScannerConfiguration,
        storageStats: AccessStatistics,
        labels: Map<String, String>
    )
}

/**
 * A straight-forward implementation of [ScannerResultBuilder] that constructs the [OrtResult] with scanner results
 * in memory.
 *
 * This implementation is easy, but it is limited to scan results that do not exceed the amount of heap space
 * available.
 */
class InMemoryScannerResultBuilder : ScannerResultBuilder {
    /** Stores the base result to build upon. */
    private lateinit var ortResult: OrtResult

    /** Collects the incoming scan results for packages. */
    private val scanResults = sortedSetOf<ScanResultContainer>()

    override fun initFromAnalyzerResult(analyzerResult: OrtResult) {
        ortResult = analyzerResult
    }

    override fun addScanResult(resultContainer: ScanResultContainer) {
        scanResults.add(resultContainer)
    }

    /**
     * Record the metadata specified and assemble an [OrtResult] object with the data gathered so far.
     */
    override fun complete(
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: ScannerConfiguration,
        storageStats: AccessStatistics,
        labels: Map<String, String>
    ) {
        val scanRecord = ScanRecord(scanResults, storageStats)
        val scannerRun = ScannerRun(startTime, endTime, environment, config, scanRecord)
        ortResult = ortResult.copy(
            scanner = scannerRun,
            labels = ortResult.labels + labels
        )
    }

    override fun close() {
        // Nothing to do here.
    }

    /**
     * Return the [OrtResult] produced by this builder. Call this method after invoking *complete()* to obtain the
     * final result.
     */
    fun result(): OrtResult = ortResult
}
