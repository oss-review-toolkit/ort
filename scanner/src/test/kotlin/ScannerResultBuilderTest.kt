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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.safeDeleteRecursively

class ScannerResultBuilderTest : WordSpec({
    "InMemoryScannerResultBuilder" should {
        "initially not have results" {
            val builder = InMemoryScannerResultBuilder()

            builder.hasResults() shouldBe false
        }

        "correctly report that results have been added" {
            val builder = InMemoryScannerResultBuilder()
            builder.initFromAnalyzerResult(OrtResult.EMPTY)
            builder.addScanResult(ScanResultContainer(id, emptyList()))

            builder.hasResults() shouldBe true
        }

        "correctly report that there are no issues" {
            val builder = InMemoryScannerResultBuilder()

            builder.hasIssues() shouldBe false

            builder.addScanResult(ScanResultContainer(id, emptyList()))
            builder.hasIssues() shouldBe false
        }

        "correctly report that there are some issues" {
            val builder = InMemoryScannerResultBuilder()
            builder.addScanResult(ScanResultContainer(id, listOf(resultWithIssues())))

            builder.hasIssues() shouldBe true
        }
    }

    "StreamingScannerResultBuilder" should {
        "handle null fields in the result from the analyzer" {
            val outputFile = Files.createTempFile("scan-result", ".yml").toFile()
            outputFile.deleteOnExit()
            val analyzerResult = OrtResult(
                Repository(
                    VcsInfo(VcsType.CVS, "someUri", "someRevision")
                )
            )

            StreamingScannerResultBuilder(outputFile).use { builder ->
                builder.initFromAnalyzerResult(analyzerResult)
                builder.complete(
                    Instant.now(),
                    Instant.now(),
                    Environment(),
                    ScannerConfiguration(),
                    AccessStatistics(),
                    emptyMap()
                )
            }

            val result = outputFile.readText()
            result shouldContain "analyzer: null"
            result shouldContain "advisor: null"
            result shouldContain "evaluator: null"
        }

        "create the output folder if it does not exist yet" {
            val outputDir = createTempDirectory("$ORT_NAME-scanner-builder").toFile()
            val outputFile = File(outputDir, "sub/folder/scan-result.yml")

            StreamingScannerResultBuilder(outputFile).use { builder ->
                builder.initFromAnalyzerResult(OrtResult.EMPTY)
                builder.complete(
                    Instant.now(),
                    Instant.now(),
                    Environment(),
                    ScannerConfiguration(),
                    AccessStatistics(),
                    emptyMap()
                )
            }

            outputFile.isFile shouldBe true

            outputDir.safeDeleteRecursively(force = true)
        }

        "initially not have results" {
            val builder = StreamingScannerResultBuilder(createTempOutputFile())

            builder.hasResults() shouldBe false
        }

        "correctly report that it has results" {
            val builder = StreamingScannerResultBuilder(createTempOutputFile())
            builder.addScanResult(ScanResultContainer(id, emptyList()))

            builder.hasResults() shouldBe true
        }

        "correctly report that there are no issues" {
            val builder = StreamingScannerResultBuilder(createTempOutputFile())

            builder.hasIssues() shouldBe false

            builder.addScanResult(ScanResultContainer(id, emptyList()))
            builder.hasIssues() shouldBe false
        }

        "correctly report that there are some issues" {
            val builder = StreamingScannerResultBuilder(createTempOutputFile())
            builder.addScanResult(ScanResultContainer(id, listOf(resultWithIssues())))

            builder.hasIssues() shouldBe true
        }
    }

    "MultiScannerResultBuilder" should {
        "initialize all child builders with an analyzer result" {
            val child1 = mockk<ScannerResultBuilder>()
            val child2 = mockk<ScannerResultBuilder>()
            val analyzerResult = mockk<OrtResult>()
            every { child1.initFromAnalyzerResult(analyzerResult) } just runs
            every { child2.initFromAnalyzerResult(analyzerResult) } just runs
            val builder = MultiScannerResultBuilder(listOf(child1, child2))

            builder.initFromAnalyzerResult(analyzerResult)

            verify {
                child1.initFromAnalyzerResult(analyzerResult)
                child2.initFromAnalyzerResult(analyzerResult)
            }
        }

        "pass a new scan result to all child builders" {
            val child1 = mockk<ScannerResultBuilder>()
            val child2 = mockk<ScannerResultBuilder>()
            val resultContainer = mockk<ScanResultContainer>()
            every { child1.addScanResult(resultContainer) } just runs
            every { child2.addScanResult(resultContainer) } just runs
            val builder = MultiScannerResultBuilder(listOf(child1, child2))

            builder.addScanResult(resultContainer)

            verify {
                child1.addScanResult(resultContainer)
                child2.addScanResult(resultContainer)
            }
        }

        "complete all child builders" {
            val child1 = mockk<ScannerResultBuilder>()
            val child2 = mockk<ScannerResultBuilder>()
            val startTime = Instant.now().minusSeconds(180)
            val endTime = Instant.now()
            val environment = mockk<Environment>()
            val scannerConfig = mockk<ScannerConfiguration>()
            val stats = mockk<AccessStatistics>()
            val labels = mapOf("test" to "true")
            every { child1.complete(startTime, endTime, environment, scannerConfig, stats, labels) } just runs
            every { child2.complete(startTime, endTime, environment, scannerConfig, stats, labels) } just runs
            val builder = MultiScannerResultBuilder(listOf(child1, child2))

            builder.complete(startTime, endTime, environment, scannerConfig, stats, labels)

            verify {
                child1.complete(startTime, endTime, environment, scannerConfig, stats, labels)
                child2.complete(startTime, endTime, environment, scannerConfig, stats, labels)
            }
        }

        "close all child builders" {
            val child1 = mockk<ScannerResultBuilder>()
            val child2 = mockk<ScannerResultBuilder>()
            every { child1.close() } just runs
            every { child2.close() } just runs
            val builder = MultiScannerResultBuilder(listOf(child1, child2))

            builder.close()

            verify {
                child1.close()
                child2.close()
            }
        }

        "close all child builders even if exceptions occur" {
            val child1 = mockk<ScannerResultBuilder>()
            val child2 = mockk<ScannerResultBuilder>()
            every { child1.close() } throws IOException("Not closed!")
            every { child2.close() } just runs
            val builder = MultiScannerResultBuilder(listOf(child1, child2))

            builder.close()

            verify {
                child1.close()
                child2.close()
            }
        }

        "return false if no child builder has results" {
            val child1 = mockk<ScannerResultBuilder>()
            val child2 = mockk<ScannerResultBuilder>()
            every { child1.hasResults() } returns false
            every { child2.hasResults() } returns false
            val builder = MultiScannerResultBuilder(listOf(child1, child2))

            builder.hasResults() shouldBe false
        }

        "return true if any child builder has results" {
            val child1 = mockk<ScannerResultBuilder>()
            val child2 = mockk<ScannerResultBuilder>()
            every { child1.hasResults() } returns true
            val builder = MultiScannerResultBuilder(listOf(child1, child2))

            builder.hasResults() shouldBe true
        }

        "return false if no child builder has issues" {
            val child1 = mockk<ScannerResultBuilder>()
            val child2 = mockk<ScannerResultBuilder>()
            every { child1.hasIssues() } returns false
            every { child2.hasIssues() } returns false
            val builder = MultiScannerResultBuilder(listOf(child1, child2))

            builder.hasIssues() shouldBe false
        }

        "return true if any child builder has issues" {
            val child1 = mockk<ScannerResultBuilder>()
            val child2 = mockk<ScannerResultBuilder>()
            every { child1.hasIssues() } returns true
            val builder = MultiScannerResultBuilder(listOf(child1, child2))

            builder.hasIssues() shouldBe true
        }
    }
})

/** A test identifier. */
private val id = Identifier("GIT", "test", "name", "1.0")

/**
 * Return a [ScanResult] that reports an issue.
 */
private fun resultWithIssues(): ScanResult {
    val summary = ScanSummary(
        startTime = Instant.now(),
        endTime = Instant.now(),
        fileCount = 42,
        packageVerificationCode = "foo",
        licenseFindings = sortedSetOf(),
        copyrightFindings = sortedSetOf(),
        issues = listOf(OrtIssue(source = "test", message = "an issue"))
    )
    return ScanResult(
        Provenance(),
        ScannerDetails("testScanner", "0815", "aConfig"),
        summary
    )
}

/**
 * Create a temporary file to be used as output file for a streaming builder.
 */
private fun createTempOutputFile(): File =
    Files.createTempFile("scan-result", ".yml").toFile().apply {
        deleteOnExit()
    }
