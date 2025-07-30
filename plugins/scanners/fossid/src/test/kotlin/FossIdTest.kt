/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

import io.kotest.assertions.async.shouldTimeout
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject

import java.time.Instant

import kotlin.time.Duration.Companion.milliseconds

import kotlinx.coroutines.runInterruptible

import org.ossreviewtoolkit.clients.fossid.EntityResponseBody
import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.MapResponseBody
import org.ossreviewtoolkit.clients.fossid.PolymorphicData
import org.ossreviewtoolkit.clients.fossid.checkDownloadStatus
import org.ossreviewtoolkit.clients.fossid.createIgnoreRule
import org.ossreviewtoolkit.clients.fossid.createProject
import org.ossreviewtoolkit.clients.fossid.createScan
import org.ossreviewtoolkit.clients.fossid.deleteScan
import org.ossreviewtoolkit.clients.fossid.downloadFromGit
import org.ossreviewtoolkit.clients.fossid.extractArchives
import org.ossreviewtoolkit.clients.fossid.listIgnoreRules
import org.ossreviewtoolkit.clients.fossid.listScansForProject
import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanStatus
import org.ossreviewtoolkit.clients.fossid.removeUploadedContent
import org.ossreviewtoolkit.clients.fossid.runScan
import org.ossreviewtoolkit.clients.fossid.uploadFile
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.plugins.scanners.fossid.FossId.Companion.SCAN_CODE_KEY
import org.ossreviewtoolkit.plugins.scanners.fossid.FossId.Companion.SCAN_ID_KEY
import org.ossreviewtoolkit.plugins.scanners.fossid.FossId.Companion.SERVER_URL_KEY
import org.ossreviewtoolkit.plugins.scanners.fossid.FossId.Companion.extractRepositoryName

import org.semver4j.Semver

@Suppress("LargeClass")
class FossIdTest : WordSpec({
    beforeSpec {
        mockkStatic("org.ossreviewtoolkit.clients.fossid.ExtensionsKt")
    }

    beforeTest {
        // Here a static function of the companion object is mocked therefore `mockkobject` needs to be used.
        // See https://lifesaver.codes/answer/cannot-mockkstatic-for-kotlin-companion-object-static-method-136
        mockkObject(FossIdRestService)
        mockkObject(VersionControlSystem)

        coEvery { FossIdRestService.create(any()) } returns createServiceMock()
        every { VersionControlSystem.forUrl(any()) } returns createVersionControlSystemMock()
    }

    afterTest {
        unmockkObject(FossIdRestService)
        unmockkObject(VersionControlSystem)
    }

    "version()" should {
        "return the version reported by FossIdServiceWithVersion" {
            val fossId = createFossId(createConfig())

            fossId.version shouldBe FOSSID_VERSION
        }

        "return a comparable version" {
            val fossId = createFossId(createConfig())

            val currentVersion = Semver.coerce(fossId.version).shouldNotBeNull()
            val minVersion = Semver.coerce("2020.2").shouldNotBeNull()
            currentVersion shouldBeGreaterThanOrEqualTo minVersion
            val minVersion2 = Semver.coerce("2023.3").shouldNotBeNull()
            currentVersion shouldBeLessThanOrEqualTo minVersion2
        }
    }

    "extractRepositoryName()" should {
        "extract the repository name from the Git URL without the .git suffix" {
            extractRepositoryName("https://github.com/jshttp/mime-types.git") shouldBe "mime-types"
            extractRepositoryName("https://github.com/vdurmont/semver4j.git") shouldBe "semver4j"
            extractRepositoryName("https://dev.azure.com/org/project/_git/repo") shouldBe "repo"
        }
    }

    "scanPackages()" should {
        "create issues for packages not hosted in Git" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo(type = VcsType.UNKNOWN)
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo)).summary

            summary.issues shouldHaveSize 1
            with(summary.issues.first()) {
                message shouldContain pkgId.toCoordinates()
                message shouldContain "but only Git is supported"
                severity shouldBe Severity.WARNING
            }
        }

        "create a new scan for an existing project" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode)

            val fossId = createFossId(config)

            fossId.scan(createPackage(createIdentifier(index = 1), vcsInfo))

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, "").asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
            }

            coVerify(exactly = 0) {
                service.createProject(any())
            }
        }

        "create a new scan for an existing project by uploading an archive" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, isArchiveMode = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "", true)
                .expectRemoveUploadedContent(scanCode)
                .expectUploadFile(scanCode)
                .expectExtractArchives(scanCode)
                .mockFiles(scanCode)

            val fossId = createFossId(config)

            fossId.scan(createPackage(createIdentifier(index = 1), vcsInfo))

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, "").asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, null, null, comment)
                service.removeUploadedContent(USER, API_KEY, scanCode)
                service.uploadFile(USER, API_KEY, scanCode, any())
                service.extractArchives(USER, API_KEY, scanCode, any())
            }

            coVerify(exactly = 0) {
                service.createProject(any())
            }
        }

        "throw an exception if the scan download failed" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDeleteScan(scanCode)

            coEvery { service.downloadFromGit(USER, API_KEY, scanCode) } returns
                EntityResponseBody(status = 1)
            coEvery { service.checkDownloadStatus(USER, API_KEY, scanCode) } returns
                EntityResponseBody(status = 1, data = PolymorphicData(DownloadStatus.FAILED))

            val fossId = createFossId(config)
            fossId.scan(createPackage(createIdentifier(index = 1), vcsInfo))

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, "").asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.downloadFromGit(USER, API_KEY, scanCode)
                // The fact that deleteScan has been called is a proof that an exception has been thrown.
                service.deleteScan(USER, API_KEY, scanCode)
            }
        }

        "return copyright and license findings from a new scan" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode, identifiedRange = 1..2, markedRange = 1..2)

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo)).summary

            val expectedCopyrightFindings = listOf(
                CopyrightFinding("copyrightMarked1", textLocation(1)),
                CopyrightFinding("copyrightMarked2", textLocation(2))
            )
            val expectedLicenseFindings = listOf(
                LicenseFinding("licenseMarkedIdentifier1", textLocation(1)),
                LicenseFinding("licenseMarkedIdentifier2", textLocation(2))
            )
            summary.copyrightFindings shouldContainExactlyInAnyOrder expectedCopyrightFindings
            summary.licenseFindings shouldContainExactlyInAnyOrder expectedLicenseFindings
        }

        "take ignored files into account" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode, identifiedRange = 1..2, markedRange = 1..2, ignoredRange = 2..3)

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo)).summary

            val expectedCopyrightFindings = listOf(
                CopyrightFinding("copyrightMarked1", textLocation(1))
            )
            val expectedLicenseFindings = listOf(
                LicenseFinding("licenseMarkedIdentifier1", textLocation(1))
            )
            summary.copyrightFindings shouldContainExactlyInAnyOrder expectedCopyrightFindings
            summary.licenseFindings shouldContainExactlyInAnyOrder expectedLicenseFindings
        }

        "fallback to identified files if no marked identified files are available" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode, identifiedRange = 1..2)

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo)).summary

            val expectedCopyrightFindings = listOf(
                CopyrightFinding("copyright1", textLocation(1)),
                CopyrightFinding("copyright2", textLocation(2))
            )
            val expectedLicenseFindings = listOf(
                LicenseFinding("lic1", textLocation(1)),
                LicenseFinding("lic2", textLocation(2))
            )
            summary.copyrightFindings shouldContainExactlyInAnyOrder expectedCopyrightFindings
            summary.licenseFindings shouldContainExactlyInAnyOrder expectedLicenseFindings
        }

        "create an issue if there are files pending identification" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode, pendingRange = 4..5, snippetRange = 1..5)

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo)).summary

            val expectedIssues = listOf(
                Issue(
                    timestamp = Instant.EPOCH,
                    source = "FossId",
                    message = "This scan has 2 file(s) pending identification in FossID. " +
                        "Please review and resolve them at: " +
                        "https://www.example.org/fossid/index.html?action=scanview&sid=1",
                    severity = Severity.HINT
                )
            )

            summary.issues.map { it.copy(timestamp = Instant.EPOCH) } shouldBe expectedIssues
        }

        "report snippets of pending files" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode, pendingRange = 1..5, snippetRange = 1..5)

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo)).summary

            val expectedPendingFile = (1..5).map(::createPendingFile).toSet()
            val expectedSnippetFindings = (1..5).map(::createSnippetFindings)

            summary.snippetFindings shouldHaveSize expectedPendingFile.size
            summary.snippetFindings.map { it.sourceLocation.path }.toSet() shouldBe expectedPendingFile
            summary.snippetFindings shouldBe expectedSnippetFindings
        }

        "list matched lines of snippets" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, fetchSnippetMatchedLines = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode, pendingRange = 1..1, snippetRange = 1..1, matchedLinesFlag = true)

            val fossId = createFossId(config)

            val summary = fossId.scan(createPackage(pkgId, vcsInfo)).summary

            summary.snippetFindings shouldHaveSize 3
            summary.snippetFindings.first().apply {
                sourceLocation shouldBe TextLocation("/pending/file/1", 1, 3)
                snippets shouldHaveSize 1
                snippets.first().apply {
                    location.startLine shouldBe 11
                    location.endLine shouldBe 12
                    additionalData[FossId.SNIPPET_DATA_MATCHED_LINE_SOURCE] shouldBe "1-3, 21-22, 36"
                    additionalData[FossId.SNIPPET_DATA_MATCHED_LINE_SNIPPET] shouldBe "11-12"
                }
            }

            summary.snippetFindings.elementAt(1).apply {
                sourceLocation shouldBe TextLocation("/pending/file/1", 21, 22)
                snippets shouldHaveSize 1
                snippets.first().apply {
                    location.startLine shouldBe 11
                    location.endLine shouldBe 12
                    additionalData[FossId.SNIPPET_DATA_MATCHED_LINE_SOURCE] shouldBe "1-3, 21-22, 36"
                    additionalData[FossId.SNIPPET_DATA_MATCHED_LINE_SNIPPET] shouldBe "11-12"
                }
            }

            summary.snippetFindings.last().apply {
                sourceLocation shouldBe TextLocation("/pending/file/1", 36)
                snippets shouldHaveSize 1
                snippets.first().apply {
                    location.startLine shouldBe 11
                    location.endLine shouldBe 12
                    additionalData[FossId.SNIPPET_DATA_MATCHED_LINE_SOURCE] shouldBe "1-3, 21-22, 36"
                    additionalData[FossId.SNIPPET_DATA_MATCHED_LINE_SNIPPET] shouldBe "11-12"
                }
            }
        }

        "create a new project if none exists yet" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode, status = 0, error = "Project does not exist")
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode)
            coEvery { service.createProject(USER, API_KEY, projectCode, projectCode, "Created by ORT") } returns
                MapResponseBody(status = 1, data = mapOf())

            val fossId = createFossId(config)

            fossId.scan(createPackage(createIdentifier(index = 1), vcsInfo))

            coVerify {
                service.createProject(USER, API_KEY, projectCode, projectCode, "Created by ORT")
            }
        }

        "explicitly start a scan if necessary" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode)
            coEvery {
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(createPackage(createIdentifier(index = 1), vcsInfo))

            coVerify {
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            }
        }

        "works if scan was queued in FossID older than 2021.2" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode)

            coEvery { service.version } returns "2021.1.1"

            coEvery {
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            } returns EntityResponseBody(
                status = 0,
                error = "Scan was added to queue."
            )

            val fossId = createFossId(config)

            fossId.scan(createPackage(createIdentifier(index = 1), vcsInfo))

            coVerify {
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            }
        }

        "wait for a scan to complete" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val pkgId = createIdentifier(index = 1)
            val vcsInfo = createVcsInfo()

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.SCANNING, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .expectDeleteScan(scanCode)
                .mockFiles(scanCode)
            coEvery {
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            shouldTimeout(1000.milliseconds) {
                runInterruptible {
                    fossId.scan(createPackage(pkgId, vcsInfo))
                }
            }

            coVerify {
                service.checkScanStatus(USER, API_KEY, scanCode)
            }
        }

        "support triggering asynchronous scans" {
            val pkgId = createIdentifier(index = 1)
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, waitForResult = false)
            val vcsInfo = createVcsInfo()

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.SCANNING, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)

            val fossId = createFossId(config)

            val result = fossId.scan(createPackage(pkgId, vcsInfo))

            result.summary.issues shouldHaveSize 1
            with(result.summary.issues.first()) {
                message shouldContain pkgId.toCoordinates()
                message shouldContain "asynchronous mode"
                severity shouldBe Severity.HINT
            }

            with(result) {
                additionalData[SCAN_CODE_KEY] shouldBe scanCode
                additionalData[SCAN_ID_KEY] shouldBe "1"
                additionalData[SERVER_URL_KEY] shouldBe "https://www.example.org/fossid"
            }

            coVerify(exactly = 0) {
                service.checkScanStatus(USER, API_KEY, any())
            }
        }

        "create a delta scan for an existing scan" {
            val projectCode = PROJECT
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig()
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, vcsInfo.revision, originCode)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(originCode, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .expectListIgnoreRules(originCode, emptyList())
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(
                createPackage(createIdentifier(index = 1), vcsInfo),
                mapOf(FossId.PROJECT_REVISION_LABEL to "master")
            )

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, "master").asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        *FossId.deltaScanRunParameters(originCode),
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            }
        }

        "should not fail when trying to create a legacy rule if it already exist" {
            val projectCode = PROJECT
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig()
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, vcsInfo.revision, originCode)

            val existingRule = IgnoreRule(
                SCAN_ID,
                RuleType.DIRECTORY,
                "*.git",
                SCAN_ID,
                "Global rule for .git directories"
            )

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(originCode, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .expectListIgnoreRules(originCode, listOf(existingRule))
                .expectCreateIgnoreRule(scanCode, existingRule.type, existingRule.value, error = true)
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(
                createPackage(createIdentifier(index = 1), vcsInfo),
                mapOf(FossId.PROJECT_REVISION_LABEL to "master")
            )

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, "master").asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        *FossId.deltaScanRunParameters(originCode),
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            }
        }

        "create a delta scan for an existing scan with a legacy comment" {
            val projectCode = PROJECT
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig()
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, vcsInfo.revision, originCode, legacyComment = true)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(originCode, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .expectListIgnoreRules(originCode, emptyList())
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(
                createPackage(createIdentifier(index = 1), vcsInfo),
                mapOf(FossId.PROJECT_REVISION_LABEL to "master")
            )

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, "master").asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        *FossId.deltaScanRunParameters(originCode),
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            }
        }

        "create a delta scan for a branch (no fallback)" {
            val branchName = "aTestBranch"

            val projectCode = PROJECT
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig()
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, vcsInfo.revision, originCode, comment = branchName)

            val originCode2 = "originalScanCode2"
            val scan2 = createScan(vcsInfo.url, vcsInfo.revision, scanId = 2, scanCode = originCode2)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan, scan2))
                .expectCheckScanStatus(originCode, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, branchName)
                .expectDownload(scanCode)
                .expectListIgnoreRules(originCode, emptyList())
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(
                createPackage(createIdentifier(index = 1), vcsInfo),
                mapOf(FossId.PROJECT_REVISION_LABEL to branchName)
            )

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, branchName).asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        *FossId.deltaScanRunParameters(originCode),
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            }
        }

        "create a delta scan for a branch (fallback to default branch)" {
            val branchName = "aTestBranch"

            val projectCode = PROJECT
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig()
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, vcsInfo.revision, originCode, comment = "branch A")

            val originCode2 = "originalScanCode2"
            val scan2 = createScan(vcsInfo.url, vcsInfo.revision, scanId = 2, scanCode = originCode2)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan, scan2))
                .expectCheckScanStatus(originCode2, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, branchName)
                .expectDownload(scanCode)
                .expectListIgnoreRules(originCode2, emptyList())
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(
                createPackage(createIdentifier(index = 1), vcsInfo),
                mapOf(FossId.PROJECT_REVISION_LABEL to branchName)
            )

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, branchName).asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        *FossId.deltaScanRunParameters(originCode2),
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            }
        }

        "create a delta scan for a branch (fallback to latest scan)" {
            val branchName = "aTestBranch"

            val projectCode = PROJECT
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig()
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, vcsInfo.revision, originCode, comment = "branch A")

            val originCode2 = "originalScanCode2"
            val scan2 = createScan(vcsInfo.url, vcsInfo.revision, originCode2, scanId = 2, comment = "branch B")

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan, scan2))
                .expectCheckScanStatus(originCode2, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, branchName)
                .expectDownload(scanCode)
                .expectListIgnoreRules(originCode2, emptyList())
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(
                createPackage(createIdentifier(index = 1), vcsInfo),
                mapOf(FossId.PROJECT_REVISION_LABEL to branchName)
            )

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, branchName).asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        *FossId.deltaScanRunParameters(originCode2),
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            }
        }

        "carry exclusion rules to a delta scan from an existing scan (legacy behavior)" {
            val projectCode = PROJECT
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig()
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, vcsInfo.revision, originCode)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(originCode, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .expectListIgnoreRules(originCode, listOf(IGNORE_RULE))
                .expectCreateIgnoreRule(scanCode, IGNORE_RULE.type, IGNORE_RULE.value, DEFAULT_IGNORE_RULE_SCOPE)
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(
                createPackage(createIdentifier(index = 1), vcsInfo),
                mapOf(FossId.PROJECT_REVISION_LABEL to "master")
            )

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, "master").asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        *FossId.deltaScanRunParameters(originCode),
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
                service.listIgnoreRules(USER, API_KEY, originCode)
                service.createIgnoreRule(
                    USER,
                    API_KEY,
                    scanCode,
                    IGNORE_RULE.type,
                    IGNORE_RULE.value,
                    DEFAULT_IGNORE_RULE_SCOPE
                )
            }
        }

        "carry exclusion rules to a delta scan from path excludes" {
            val projectCode = PROJECT
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig()
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, vcsInfo.revision, originCode)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(originCode, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .expectListIgnoreRules(originCode, emptyList())
                .expectCreateIgnoreRule(scanCode, IGNORE_RULE.type, IGNORE_RULE.value, DEFAULT_IGNORE_RULE_SCOPE)
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(
                createPackage(createIdentifier(index = 1), vcsInfo),
                mapOf(FossId.PROJECT_REVISION_LABEL to "master"),
                Excludes(listOf(PathExclude("*.docx", PathExcludeReason.OTHER)))
            )

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, "master").asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        *FossId.deltaScanRunParameters(originCode),
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
                service.listIgnoreRules(USER, API_KEY, originCode)
                service.createIgnoreRule(
                    USER,
                    API_KEY,
                    scanCode,
                    IGNORE_RULE.type,
                    IGNORE_RULE.value,
                    DEFAULT_IGNORE_RULE_SCOPE
                )
            }
        }

        "create an origin scan if no scan exists yet" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.ORIGIN)
            val config = createConfig()
            val vcsInfo = createVcsInfo()

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.NEW, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode, identifiedRange = 1..2, markedRange = 1..2)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(createPackage(createIdentifier(index = 1), vcsInfo))

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, "").asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
            }

            coVerify(exactly = 1) {
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            }
        }

        "create a delta scan in archive mode for an existing scan not created in archive mode (legacy scan)" {
            val projectCode = PROJECT
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig(isArchiveMode = true)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, vcsInfo.revision, originCode)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(originCode, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "master", true)
                .expectRemoveUploadedContent(scanCode)
                .expectUploadFile(scanCode)
                .expectExtractArchives(scanCode)
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(
                createPackage(createIdentifier(index = 1), vcsInfo),
                mapOf(FossId.PROJECT_REVISION_LABEL to "master")
            )

            coVerify {
                service.removeUploadedContent(USER, API_KEY, scanCode)
                service.uploadFile(USER, API_KEY, scanCode, any())
                service.extractArchives(USER, API_KEY, scanCode, any())
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        *FossId.deltaScanRunParameters(originCode),
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            }
        }

        "create a delta scan in archive mode for an existing scan created in archive mode" {
            val projectCode = PROJECT
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig(isArchiveMode = true)
            val vcsInfo = createVcsInfo()
            val scan = createScanWithUploadedContent(vcsInfo.url, vcsInfo.revision, originCode)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(originCode, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "master", true)
                .expectRemoveUploadedContent(scanCode)
                .expectUploadFile(scanCode)
                .expectExtractArchives(scanCode)
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(
                createPackage(createIdentifier(index = 1), vcsInfo),
                mapOf(FossId.PROJECT_REVISION_LABEL to "master")
            )

            coVerify {
                service.removeUploadedContent(USER, API_KEY, scanCode)
                service.uploadFile(USER, API_KEY, scanCode, any())
                service.extractArchives(USER, API_KEY, scanCode, any())
                service.runScan(
                    USER,
                    API_KEY,
                    scanCode,
                    mapOf(
                        *FossId.deltaScanRunParameters(originCode),
                        "auto_identification_detect_declaration" to "0",
                        "auto_identification_detect_copyright" to "0",
                        "sensitivity" to "10"
                    )
                )
            }
        }

        "apply exclusion rules to a non-delta scan" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.NEW, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .expectCreateIgnoreRule(scanCode, IGNORE_RULE.type, IGNORE_RULE.value, DEFAULT_IGNORE_RULE_SCOPE)
                .mockFiles(scanCode, identifiedRange = 1..2, markedRange = 1..2)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(
                createPackage(createIdentifier(index = 1), vcsInfo),
                mapOf(FossId.PROJECT_REVISION_LABEL to ""),
                Excludes(listOf(PathExclude("*.docx", PathExcludeReason.OTHER)))
            )

            val comment = createOrtScanComment(vcsInfo.url, vcsInfo.revision, "").asJsonString()
            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision, comment)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.createIgnoreRule(
                    USER,
                    API_KEY,
                    scanCode,
                    IGNORE_RULE.type,
                    IGNORE_RULE.value,
                    DEFAULT_IGNORE_RULE_SCOPE
                )
            }
        }

        "delete newly triggered scans if a package cannot be scanned" {
            val projectCode = PROJECT

            val id1 = createIdentifier(index = 1)
            val vcsInfo1 = createVcsInfo()
            val pkg1 = createPackage(id1, vcsInfo1)
            val scanCode1 = scanCode(projectCode, FossId.DeltaTag.ORIGIN)

            val failedProject = "failedProject"
            val id2 = createIdentifier(index = 2)
            val failedVcsInfo = createVcsInfo(projectName = failedProject)
            val pkg2 = createPackage(id2, failedVcsInfo)

            val project3 = "project3"
            val id3 = createIdentifier(index = 3)
            val vcsInfo3 = createVcsInfo(projectName = project3)
            val pkg3 = createPackage(id3, vcsInfo3)
            val scanCode3 = scanCode(project3, FossId.DeltaTag.ORIGIN, index = 2)

            val config = createConfig(projectName = null)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode1, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode1, vcsInfo1, "")
                .expectDownload(scanCode1)
                .mockFiles(scanCode1, markedRange = 1..2)

            service.expectProjectRequest(failedProject)
            coEvery { service.listScansForProject(USER, API_KEY, failedProject) } throws IllegalStateException()
            coEvery { service.deleteScan(any()) } returns EntityResponseBody(status = 1)

            service.expectProjectRequest(project3)
                .expectListScans(project3, emptyList())
                .expectCheckScanStatus(scanCode3, ScanStatus.FINISHED)
                .expectCreateScan(project3, scanCode3, vcsInfo3, "")
                .expectDownload(scanCode3)
                .mockFiles(scanCode3, markedRange = 1..2)

            val fossId = createFossId(config)

            fossId.scan(pkg1)

            coEvery { service.listScansForProject(USER, API_KEY, projectCode) } throws IllegalStateException()

            val result = fossId.scan(pkg2)

            fossId.scan(pkg3)

            result.summary.issues shouldHaveSize 1
            with(result.summary.issues.first()) {
                message shouldContain id2.toCoordinates()
                severity shouldBe Severity.ERROR
            }

            coVerify {
                service.deleteScan(USER, API_KEY, scanCode1)
            }

            // This shows a bug in the current implementation where scans started after a failed scan are not deleted.
            coVerify(exactly = 0) {
                service.deleteScan(USER, API_KEY, scanCode3)
            }
        }

        "enforce a limit on the number of delta scans" {
            val numberOfDeltaScans = 8
            val deltaScanLimit = 4
            val projectCode = PROJECT
            val originCode = scanCode(PROJECT, FossId.DeltaTag.ORIGIN, index = 0)
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig(deltaScanLimit = deltaScanLimit)
            val vcsInfo = createVcsInfo()
            val originScan = createScan(vcsInfo.url, vcsInfo.revision, originCode, scanId = SCAN_ID)
            val otherScan1 = createScan(
                vcsInfo.url,
                "${vcsInfo.revision}_other",
                "anotherCode",
                scanId = 0
            )
            val otherScan2 = createScan("someURL", "someRevision", "someCode", scanId = 999)
            val deltaScans = (1..numberOfDeltaScans).map {
                createScan(
                    vcsInfo.url,
                    vcsInfo.revision,
                    scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA, it),
                    SCAN_ID + it
                )
            }

            val recentScan = deltaScans.last()
            val scans = deltaScans + listOf(otherScan1, otherScan2, originScan)

            val service = FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, scans)
                .expectCheckScanStatus(recentScan.code!!, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .expectListIgnoreRules(recentScan.code!!, emptyList())
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)
            coEvery { service.deleteScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(createPackage(createIdentifier(index = 1), vcsInfo))

            coVerify {
                (1..(numberOfDeltaScans - deltaScanLimit + 1)).map {
                    service.deleteScan(USER, API_KEY, scanCode(PROJECT, FossId.DeltaTag.DELTA, index = it))
                }

                service.deleteScan(USER, API_KEY, originCode)
            }

            coVerify(exactly = 0) {
                service.deleteScan(
                    USER,
                    API_KEY,
                    scanCode(PROJECT, FossId.DeltaTag.DELTA, index = numberOfDeltaScans - deltaScanLimit + 2)
                )
            }
        }

        "return scan code of a scan" {
            val projectCode = PROJECT
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.create(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo, "")
                .expectDownload(scanCode)
                .mockFiles(scanCode, identifiedRange = 1..2, markedRange = 1..2)

            val fossId = createFossId(config)

            val result = fossId.scan(createPackage(pkgId, vcsInfo))

            val expectedAdditionalData = mapOf(
                SCAN_CODE_KEY to scanCode,
                SCAN_ID_KEY to "1",
                SERVER_URL_KEY to "https://www.example.org/fossid"
            )

            result.additionalData shouldBe expectedAdditionalData
        }
    }
})
