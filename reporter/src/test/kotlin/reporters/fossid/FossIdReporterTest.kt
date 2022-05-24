/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.reporter.reporters.fossid

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.throwable.shouldHaveMessage

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.FossIdServiceWithVersion
import org.ossreviewtoolkit.clients.fossid.generateReport
import org.ossreviewtoolkit.clients.fossid.model.report.ReportType
import org.ossreviewtoolkit.clients.fossid.model.report.SelectionType
import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ort.Environment

private const val SERVER_URL_SAMPLE = "https://fossid.example.com/instance/"
private const val API_KEY_SAMPLE = "XYZ"
private const val USER_KEY_SAMPLE = "user"
private val DEFAULT_OPTIONS = mapOf(
    FossIdReporter.SERVER_URL_PROPERTY to SERVER_URL_SAMPLE,
    FossIdReporter.API_KEY_PROPERTY to API_KEY_SAMPLE,
    FossIdReporter.USER_PROPERTY to USER_KEY_SAMPLE
)

private const val SCANCODE_1 = "scancode1"
private const val SCANCODE_2 = "scancode2"
private val FILE_SAMPLE = File("fake_file")
private val DIRECTORY_SAMPLE = File("fake_directory")

class FossIdReporterTest : WordSpec({
    beforeSpec {
        mockkStatic("org.ossreviewtoolkit.clients.fossid.ExtensionsKt")
    }

    afterTest {
        clearAllMocks()
    }

    "generateReport of FossIdReport " should {
        "check if a server URL was given" {
            val reporter = FossIdReporter()
            val exception = shouldThrow<IllegalArgumentException> {
                val input = createReporterInput()
                reporter.generateReport(
                    input,
                    DIRECTORY_SAMPLE,
                    DEFAULT_OPTIONS.filterNot { it.key == FossIdReporter.SERVER_URL_PROPERTY }
                )
            }
            exception shouldHaveMessage "No FossID server URL configuration found."
        }

        "check if an API key was given" {
            val reporter = FossIdReporter()
            val exception = shouldThrow<IllegalArgumentException> {
                val input = createReporterInput()
                reporter.generateReport(
                    input,
                    DIRECTORY_SAMPLE,
                    DEFAULT_OPTIONS.filterNot { it.key == FossIdReporter.API_KEY_PROPERTY }
                )
            }
            exception shouldHaveMessage "No FossID API Key configuration found."
        }

        "check if a user was given" {
            val reporter = FossIdReporter()
            val exception = shouldThrow<IllegalArgumentException> {
                val input = createReporterInput()
                reporter.generateReport(
                    input,
                    DIRECTORY_SAMPLE,
                    DEFAULT_OPTIONS.filterNot { it.key == FossIdReporter.USER_PROPERTY }
                )
            }
            exception shouldHaveMessage "No FossID User configuration found."
        }

        "do nothing if no scancode is passed" {
            val (serviceMock, reporterMock) = createReporterMock()
            val input = createReporterInput()

            reporterMock.generateReport(input, DIRECTORY_SAMPLE, DEFAULT_OPTIONS)

            coVerify(exactly = 0) {
                serviceMock.generateReport(any(), any(), any(), any(), any(), any())
            }
        }

        "use HTML_DYNAMIC as default report type" {
            val (serviceMock, reporterMock) = createReporterMock()
            val input = createReporterInput(listOf(SCANCODE_1))

            reporterMock.generateReport(input, DIRECTORY_SAMPLE, DEFAULT_OPTIONS)

            coVerify(exactly = 1) {
                serviceMock.generateReport(
                    USER_KEY_SAMPLE,
                    API_KEY_SAMPLE,
                    SCANCODE_1,
                    ReportType.HTML_DYNAMIC,
                    any(),
                    any()
                )
            }
        }

        "allow to specify a report type" {
            val (serviceMock, reporterMock) = createReporterMock()
            val input = createReporterInput(listOf(SCANCODE_1))

            reporterMock.generateReport(
                input, DIRECTORY_SAMPLE,
                DEFAULT_OPTIONS + (FossIdReporter.REPORT_TYPE_PROPERTY to ReportType.XLSX.name)
            )

            coVerify(exactly = 1) {
                serviceMock.generateReport(
                    USER_KEY_SAMPLE,
                    API_KEY_SAMPLE,
                    SCANCODE_1,
                    ReportType.XLSX,
                    any(),
                    any()
                )
            }
        }

        "use INCLUDE_ALL_LICENSES as default selection type" {
            val (serviceMock, reporterMock) = createReporterMock()
            val input = createReporterInput(listOf(SCANCODE_1))

            reporterMock.generateReport(input, DIRECTORY_SAMPLE, DEFAULT_OPTIONS)

            coVerify(exactly = 1) {
                serviceMock.generateReport(
                    USER_KEY_SAMPLE,
                    API_KEY_SAMPLE,
                    SCANCODE_1,
                    any(),
                    SelectionType.INCLUDE_ALL_LICENSES,
                    any()
                )
            }
        }

        "allow to specify a selection type" {
            val (serviceMock, reporterMock) = createReporterMock()
            val input = createReporterInput(listOf(SCANCODE_1))

            reporterMock.generateReport(
                input, DIRECTORY_SAMPLE,
                DEFAULT_OPTIONS + (FossIdReporter.SELECTION_TYPE_PROPERTY to SelectionType.INCLUDE_COPYLEFT.name)
            )

            coVerify(exactly = 1) {
                serviceMock.generateReport(
                    USER_KEY_SAMPLE,
                    API_KEY_SAMPLE,
                    SCANCODE_1,
                    any(),
                    SelectionType.INCLUDE_COPYLEFT,
                    any()
                )
            }
        }

        "generate a report for each given scancode" {
            val (serviceMock, reporterMock) = createReporterMock()
            val input = createReporterInput(listOf(SCANCODE_1, SCANCODE_2))

            reporterMock.generateReport(input, DIRECTORY_SAMPLE, DEFAULT_OPTIONS)

            coVerifyAll {
                serviceMock.generateReport(USER_KEY_SAMPLE, API_KEY_SAMPLE, SCANCODE_1, any(), any(), any())
                serviceMock.generateReport(USER_KEY_SAMPLE, API_KEY_SAMPLE, SCANCODE_2, any(), any(), any())
            }
        }

        "return the generated file(s)" {
            val (_, reporterMock) = createReporterMock()
            val input = createReporterInput(listOf(SCANCODE_1))

            val result = reporterMock.generateReport(input, DIRECTORY_SAMPLE, DEFAULT_OPTIONS)

            result shouldContainExactly listOf(FILE_SAMPLE)
        }
    }
})

private fun createReporterMock(): Pair<FossIdRestService, FossIdReporter> {
    mockkObject(FossIdRestService)

    val serviceMock = mockk<FossIdServiceWithVersion>()
    val reporterMock = spyk<FossIdReporter>()
    every { FossIdRestService.createService(any()) } returns serviceMock

    coEvery {
        serviceMock.generateReport(any(), any(), any(), any(), any(), any())
    } returns Result.success(FILE_SAMPLE)
    return serviceMock to reporterMock
}

private fun createReporterInput(scanCodes: List<String> = emptyList()): ReporterInput {
    val analyzedVcs = VcsInfo(
        type = VcsType.GIT,
        revision = "master",
        url = "https://github.com/path/first-project.git",
        path = "sub/path"
    )

    val results = scanCodes.groupBy({ Identifier.EMPTY.copy(name = it) }) { createScanResult(it) }.toSortedMap()

    return ReporterInput(
        OrtResult(
            repository = Repository(
                config = RepositoryConfiguration(
                    excludes = Excludes(
                        scopes = listOf(
                            ScopeExclude(
                                pattern = "test",
                                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                                comment = "Packages for testing only."
                            )
                        )
                    )
                ),
                vcs = analyzedVcs,
                vcsProcessed = analyzedVcs
            ),
            scanner = ScannerRun(
                results = ScanRecord(results, AccessStatistics()),
                environment = Environment(),
                config = ScannerConfiguration()
            )
        )
    )
}

private fun createScanResult(scanCode: String): ScanResult {
    val summary = ScanSummary(
        Instant.now(), Instant.now(), "", sortedSetOf(), sortedSetOf()
    )
    return ScanResult(UnknownProvenance, ScannerDetails.EMPTY, summary, mapOf(FossIdReporter.SCAN_CODE_KEY to scanCode))
}
