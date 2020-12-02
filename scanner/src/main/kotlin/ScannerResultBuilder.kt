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

import com.fasterxml.jackson.core.JsonEncoding

import java.io.File
import java.io.IOException
import java.time.Instant

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.safeMkdirs

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

    /**
     * Return a flag whether results have been added to this builder. This can be used as an indicator whether a
     * scan operation was successful.
     */
    fun hasResults(): Boolean

    /**
     * Return a flag whether at least one result added to this builder has issues set.
     */
    fun hasIssues(): Boolean
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

    override fun hasResults(): Boolean = scanResults.isNotEmpty()

    override fun hasIssues(): Boolean = scanResults.any { it.hasIssues() }

    override fun close() {
        // Nothing to do here.
    }

    /**
     * Return the [OrtResult] produced by this builder. Call this method after invoking *complete()* to obtain the
     * final result.
     */
    fun result(): OrtResult = ortResult
}

/**
 * An implementation of [ScannerResultBuilder] that uses the Jackson Streaming API to construct result files.
 *
 * Whenever new data becomes available, it is directly written to the output file. So the memory consumed by this
 * builder class is reduced.
 */
class StreamingScannerResultBuilder(
    /** The file to write the result to. */
    val outputFile: File
) : ScannerResultBuilder {
    /** Store a reference to the object mapper. */
    private val mapper = outputFile.mapper()

    /** The generator to produce the result in a streaming fashion. */
    private val generator = mapper.createGenerator(outputFile.safeMkdirsParent(), JsonEncoding.UTF8)

    /** Stores the labels from the analyzer result. */
    private var analyzerLabels: Map<String, String>? = null

    /** Records whether this builder was passed a result. */
    private var resultsAdded = false

    /** Records whether a result with issues has been added to this builder. */
    private var issuesFound = false

    override fun initFromAnalyzerResult(analyzerResult: OrtResult) {
        analyzerLabels = analyzerResult.labels

        with(generator) {
            writeStartObject()
            writeObjectField("repository", analyzerResult.repository)
            writeObjectField("analyzer", analyzerResult.analyzer)
            writeObjectField("advisor", analyzerResult.advisor)
            writeObjectField("evaluator", analyzerResult.evaluator)

            writeObjectFieldStart("scanner")
            writeObjectFieldStart("results")
            writeArrayFieldStart("scan_results")
        }
    }

    override fun addScanResult(resultContainer: ScanResultContainer) {
        mapper.writeValue(generator, resultContainer)
        resultsAdded = true
        issuesFound = issuesFound || resultContainer.hasIssues()
    }

    override fun complete(
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: ScannerConfiguration,
        storageStats: AccessStatistics,
        labels: Map<String, String>
    ) {
        val allLabels = analyzerLabels.orEmpty() + labels

        with(generator) {
            writeEndArray()
            writeObjectField("storage_stats", storageStats)
            writeEndObject()
            writeObjectField("start_time", startTime)
            writeObjectField("end_time", endTime)
            writeObjectField("environment", environment)
            writeObjectField("config", config)
            writeEndObject()
            writeObjectField("labels", allLabels)
            writeEndObject()
        }
    }

    override fun hasResults(): Boolean = resultsAdded

    override fun hasIssues(): Boolean = issuesFound

    override fun close() {
        generator.close()
    }

    /**
     * Make sure that the parent directory of this file exists.
     */
    private fun File.safeMkdirsParent(): File {
        parentFile?.safeMkdirs()
        return this
    }
}

/**
 * A composite implementation of [ScannerResultBuilder], which forwards all invocations to its [childBuilders].
 *
 * By making use of this builder class, it is possible to generate multiple results for a single scan operation.
 * This is needed for the scan command when multiple output formats are specified. Then, rather than creating a
 * result once and saving it multiple times to different formats (which would again require to have the complete
 * result in memory), multiple streaming builders can be configured writing their output files in parallel.
 */
class MultiScannerResultBuilder(
    /** The list of child builders to manage by this multi builder. */
    val childBuilders: List<ScannerResultBuilder>
) : ScannerResultBuilder {
    override fun initFromAnalyzerResult(analyzerResult: OrtResult) {
        childBuilders.forEach { it.initFromAnalyzerResult(analyzerResult) }
    }

    override fun addScanResult(resultContainer: ScanResultContainer) {
        childBuilders.forEach { it.addScanResult(resultContainer) }
    }

    override fun complete(
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: ScannerConfiguration,
        storageStats: AccessStatistics,
        labels: Map<String, String>
    ) {
        childBuilders.forEach { it.complete(startTime, endTime, environment, config, storageStats, labels) }
    }

    override fun hasResults(): Boolean = childBuilders.any { it.hasResults() }

    override fun hasIssues(): Boolean = childBuilders.any { it.hasIssues() }

    override fun close() {
        childBuilders.forEach {
            try {
                it.close()
            } catch (e: IOException) {
                log.warn(e) { "Failed to close child builder $it." }
            }
        }
    }
}

/**
 * Return a flag whether the [ScanResultContainer] contains at least one result with an issue.
 */
private fun ScanResultContainer.hasIssues(): Boolean =
    results.any { it.summary.issues.isNotEmpty() }
