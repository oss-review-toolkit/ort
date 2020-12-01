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

import com.fasterxml.jackson.databind.JsonNode

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File
import java.time.Instant

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class ScannerFunTest : StringSpec() {
    private lateinit var downloadDir: File

    private lateinit var outputDir: File

    override fun beforeSpec(spec: Spec) {
        downloadDir = createTempDirectory("$ORT_NAME-${javaClass.simpleName}-download").toFile()
        outputDir = createTempDirectory("$ORT_NAME-${javaClass.simpleName}-output").toFile()
    }

    override fun afterSpec(spec: Spec) {
        outputDir.safeDeleteRecursively(force = true)
        downloadDir.safeDeleteRecursively(force = true)
        ScanResultsStorage.storage.stats.reset()
    }

    init {
        "Scanner should scan an OrtResult in memory" {
            val analyzerResult = assetsDir.resolve("analyzer-result.yml")
            val builder = InMemoryScannerResultBuilder()
            val scanner = createScanner()

            scanner.scanOrtResult(builder, analyzerResult, outputDir, downloadDir, labels = labels)

            checkResult(builder.result())
        }
    }

    /**
     * Create a scanner implementation for executing test scans that returns very simple, deterministic results.
     */
    private fun createScanner(): LocalScanner =
        object : LocalScanner("TestScanner", ScannerConfiguration()) {
            override val resultFileExt: String
                get() = "tst"
            override val expectedVersion: String
                get() = version

            override fun getConfiguration(): String = "testScannerConfig"

            override fun scanPathInternal(path: File, resultsFile: File): ScanResult {
                val provenance = Provenance()
                val licenseFinding = LicenseFinding(
                    "ASL-2",
                    TextLocation(path.absolutePath, 1, 8)
                )
                val copyrightFinding = CopyrightFinding(
                    "(C) ${path.name}",
                    TextLocation(path.absolutePath, 3, 4)
                )
                val summary = ScanSummary(
                    startTime = Instant.now(),
                    endTime = Instant.now(),
                    packageVerificationCode = "",
                    fileCount = 1,
                    licenseFindings = sortedSetOf(licenseFinding),
                    copyrightFindings = sortedSetOf(copyrightFinding)
                )

                return ScanResult(provenance, getDetails(), summary)
            }

            override fun command(workingDir: File?): String {
                throw UnsupportedOperationException("Unexpected call")
            }

            override fun getRawResult(resultsFile: File): JsonNode {
                return yamlMapper.nullNode()
            }

            override val version: String
                get() = "1.0.0"
        }
}

/** Name of the file holding the expected scan result. */
private const val EXPECTED_RESULT_FILE_NAME = "test-scanner-expected-output-for-analyzer-result.yml"

/** Path to test files. */
private val assetsDir = File("src/funTest/assets")

/**
 * Regular expression to match temporary download paths in a scan result. As these paths change on each test run,
 * they have to be dealt with when comparing results.
 */
private val DOWNLOAD_PATH_REGEX = Regex("-download(\\d+)/")

/**
 * Regular expression to match the *variables* element in a scan result. Variables depend on the current
 * environment; so they have to be deleted when comparing results.
 */
private val VARIABLES_REGEX = Regex("variables:.*?tool_versions:", RegexOption.DOT_MATCHES_ALL)

/** Some labels to assign to the test result. */
private val labels = mapOf(
    "test" to "true",
    "scan" to "full"
)

/** Stores a result object with the expected scanner result to compare to in tests. */
private var expectedScannerResult = constructExpectedResult()

/**
 * Patch the given [scan result][result] by replacing variables part, so that it can be compared.
 */
private fun patchResult(result: String): String =
    stripTempPaths(stripVariables(result))

/**
 * Replace temporary path names in the given [result].
 */
private fun stripTempPaths(result: String): String =
    result.replace(DOWNLOAD_PATH_REGEX, "/")

/**
 * Remove the section with environment variables from the given [result].
 */
private fun stripVariables(result: String): String =
    result.replace(VARIABLES_REGEX, "tool_versions:")

/**
 * Read the file with the expected scanner result, replace variable parts, and convert it to an [OrtResult] object.
 */
private fun constructExpectedResult(): OrtResult {
    val expectedResultYaml = patchResult(
        patchExpectedResult(
            assetsDir.resolve("test-scanner-expected-output-for-analyzer-result.yml"),
            revision = "HEAD"
        )
    )

    return deserializeResult(expectedResultYaml)
}

/**
 * Compares the [result] specified against the expected scanner result. Note that comparison needs to be done on
 * object level, as the raw YAML representation may differ. The [result] passed in needs to be patched first to
 * deal with variable parts. Also, access statistics cannot be compared directly.
 */
private fun checkResult(result: OrtResult) {
    val patchedYaml = patchResult(
        patchActualResult(
            yamlMapper.writeValueAsString(result),
            patchDownloadTime = true,
            patchStartAndEndTime = true
        )
    )
    val patchedResult = deserializeResult(patchedYaml)
    val statistics = patchedResult.statistics()
    val expectedStatistics = expectedScannerResult.statistics()
    statistics.numHits.get() shouldBe expectedStatistics.numHits.get()
    statistics.numReads.get() shouldBe expectedStatistics.numReads.get()
    // AtomicInteger does not implement equals(); so copy objects to make comparison succeed.
    statistics.numHits = expectedStatistics.numHits
    statistics.numReads = expectedStatistics.numReads

    patchedResult shouldBe expectedScannerResult
}

/**
 * Convert a [serialized YAML result][resultYaml] to an [OrtResult] object.
 */
private fun deserializeResult(resultYaml: String): OrtResult =
    yamlMapper.readValue(resultYaml, OrtResult::class.java)

/**
 * Convenience function to return a result's statistics for accessing the scan results storage.
 */
private fun OrtResult.statistics(): AccessStatistics =
    scanner?.results?.storageStats ?: AccessStatistics()
