/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners.fossid

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

import org.ossreviewtoolkit.clients.fossid.EntityResponseBody
import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.FossIdServiceWithVersion
import org.ossreviewtoolkit.clients.fossid.MapResponseBody
import org.ossreviewtoolkit.clients.fossid.PolymorphicInt
import org.ossreviewtoolkit.clients.fossid.PolymorphicList
import org.ossreviewtoolkit.clients.fossid.PolymorphicResponseBody
import org.ossreviewtoolkit.clients.fossid.checkDownloadStatus
import org.ossreviewtoolkit.clients.fossid.createIgnoreRule
import org.ossreviewtoolkit.clients.fossid.createProject
import org.ossreviewtoolkit.clients.fossid.createScan
import org.ossreviewtoolkit.clients.fossid.deleteScan
import org.ossreviewtoolkit.clients.fossid.downloadFromGit
import org.ossreviewtoolkit.clients.fossid.getProject
import org.ossreviewtoolkit.clients.fossid.listIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listIgnoreRules
import org.ossreviewtoolkit.clients.fossid.listIgnoredFiles
import org.ossreviewtoolkit.clients.fossid.listMarkedAsIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listPendingFiles
import org.ossreviewtoolkit.clients.fossid.listScansForProject
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.identification.common.LicenseMatchType
import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.License
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.LicenseFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleScope
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanStatus
import org.ossreviewtoolkit.clients.fossid.model.status.UnversionedScanDescription
import org.ossreviewtoolkit.clients.fossid.runScan
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.scanOrtResult
import org.ossreviewtoolkit.scanner.scanners.fossid.FossId.Companion.SCAN_CODE_KEY
import org.ossreviewtoolkit.scanner.scanners.fossid.FossId.Companion.convertGitUrlToProjectName
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class FossIdTest : WordSpec({
    beforeSpec {
        mockkStatic("org.ossreviewtoolkit.clients.fossid.ExtensionsKt")
    }

    beforeTest {
        // Here a static function of the companion object is mocked therefore `mockkobject` needs to be used.
        // See https://lifesaver.codes/answer/cannot-mockkstatic-for-kotlin-companion-object-static-method-136
        mockkObject(FossIdRestService)

        every { FossIdRestService.createService(any()) } returns createServiceMock()
    }

    afterTest {
        unmockkObject(FossIdRestService)
    }

    "version()" should {
        "return the version reported by FossIdServiceWithVersion" {
            val fossId = createFossId(createConfig())

            fossId.version shouldBe FOSSID_VERSION
        }
    }

    "convertGitUrlToProjectName()" should {
        "extract the repository name from the Git URL without the .git suffix" {
            convertGitUrlToProjectName("https://github.com/jshttp/mime-types.git") shouldBe "mime-types"
            convertGitUrlToProjectName("https://github.com/vdurmont/semver4j.git") shouldBe "semver4j"
            convertGitUrlToProjectName("https://dev.azure.com/org/project/_git/repo") shouldBe "repo"
        }
    }

    "scanPackages()" should {
        "create issues for packages not hosted in Git" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo(type = VcsType.UNKNOWN)
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)

            val fossId = createFossId(config)

            val summary = fossId.scan(listOf(createPackage(pkgId, vcsInfo))).summary(pkgId)

            summary.issues shouldHaveSize 1
            with(summary.issues.first()) {
                message shouldContain pkgId.toCoordinates()
                message shouldContain "but only Git is supported"
                severity shouldBe Severity.WARNING
            }
        }

        "create a new scan for an existing project" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)

            val service = FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .mockFiles(scanCode)

            val fossId = createFossId(config)

            fossId.scan(listOf(createPackage(createIdentifier(index = 1), vcsInfo)))

            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
            }

            coVerify(exactly = 0) {
                service.createProject(any())
            }
        }

        "throw an exception if the scan download failed" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)

            val service = FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDeleteScan(scanCode)

            coEvery { service.downloadFromGit(USER, API_KEY, scanCode) } returns
                    EntityResponseBody(status = 1)
            coEvery { service.checkDownloadStatus(USER, API_KEY, scanCode) } returns
                    EntityResponseBody(status = 1, data = DownloadStatus.FAILED)

            val fossId = createFossId(config)
            fossId.scan(listOf(createPackage(createIdentifier(index = 1), vcsInfo)))

            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.downloadFromGit(USER, API_KEY, scanCode)
                // The fact that deleteScan has been called is a proof that an exception has been thrown.
                service.deleteScan(USER, API_KEY, scanCode)
            }
        }

        "return copyright and license findings from a new scan" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .mockFiles(scanCode, identifiedRange = 1..2, markedRange = 1..2)

            val fossId = createFossId(config)

            val summary = fossId.scan(listOf(createPackage(pkgId, vcsInfo))).summary(pkgId)

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
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .mockFiles(scanCode, identifiedRange = 1..2, markedRange = 1..2, ignoredRange = 2..3)

            val fossId = createFossId(config)

            val summary = fossId.scan(listOf(createPackage(pkgId, vcsInfo))).summary(pkgId)

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
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .mockFiles(scanCode, identifiedRange = 1..2)

            val fossId = createFossId(config)

            val summary = fossId.scan(listOf(createPackage(pkgId, vcsInfo))).summary(pkgId)

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

        "report pending files as issues" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .mockFiles(scanCode, pendingRange = 4..5)

            val fossId = createFossId(config)

            val summary = fossId.scan(listOf(createPackage(pkgId, vcsInfo))).summary(pkgId)

            val expectedIssues = listOf(createPendingFile(4), createPendingFile(5)).map {
                OrtIssue(Instant.EPOCH, "FossId", "Pending identification for '$it'.", Severity.HINT)
            }

            summary.issues.map { it.copy(timestamp = Instant.EPOCH) } shouldBe expectedIssues
        }

        "create a new project if none exists yet" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)

            val service = FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode, status = 0, error = "Project does not exist")
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .mockFiles(scanCode)
            coEvery { service.createProject(USER, API_KEY, projectCode, projectCode) } returns
                    MapResponseBody(status = 1, data = mapOf())

            val fossId = createFossId(config)

            fossId.scan(listOf(createPackage(createIdentifier(index = 1), vcsInfo)))

            coVerify {
                service.createProject(USER, API_KEY, projectCode, projectCode)
            }
        }

        "explicitly start a scan if necessary" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()

            val service = FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .mockFiles(scanCode)
            coEvery { service.runScan(USER, API_KEY, scanCode) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(listOf(createPackage(createIdentifier(index = 1), vcsInfo)))

            coVerify {
                service.runScan(USER, API_KEY, scanCode)
            }
        }

        "works if scan was queued in FossID older than 2021.2" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()

            val service = FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .mockFiles(scanCode)

            coEvery { service.version } returns "2021.1.1"

            coEvery { service.runScan(USER, API_KEY, scanCode) } returns EntityResponseBody(
                status = 0,
                error = "Scan was added to queue."
            )

            val fossId = createFossId(config)

            fossId.scan(listOf(createPackage(createIdentifier(index = 1), vcsInfo)))

            coVerify {
                service.runScan(USER, API_KEY, scanCode)
            }
        }

        "wait for a scan to complete" {
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()

            val service = FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.SCANNING, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .expectDeleteScan(scanCode)
                .mockFiles(scanCode)
            coEvery { service.runScan(USER, API_KEY, scanCode) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            shouldThrow<TimeoutCancellationException> {
                withTimeout(1000) {
                    fossId.scanPackages(
                        setOf(createPackage(createIdentifier(index = 1), vcsInfo)), emptyMap()
                    )
                }
            }

            coVerify {
                service.checkScanStatus(USER, API_KEY, scanCode)
            }
        }

        "support triggering asynchronous scans" {
            val pkgId = createIdentifier(index = 1)
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false, waitForResult = false)
            val vcsInfo = createVcsInfo()

            val service = FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.SCANNING, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)

            val fossId = createFossId(config)

            val scannerRun = fossId.scan(listOf(createPackage(pkgId, vcsInfo)))

            scannerRun.results.collectIssues()[pkgId] shouldNotBeNull {
                this shouldHaveSize 1
                val issue = first()
                issue.message shouldContain pkgId.toCoordinates()
                issue.message shouldContain "asynchronous mode"
                issue.severity shouldBe Severity.HINT
            }

            coVerify(exactly = 0) {
                service.checkScanStatus(USER, API_KEY, any())
            }
        }

        "create a delta scan for an existing scan" {
            val projectCode = projectCode(PROJECT)
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig()
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, vcsInfo.revision, originCode)

            val service = FossIdRestService.createService(config.serverUrl)
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

            fossId.scan(listOf(createPackage(createIdentifier(index = 1), vcsInfo)))

            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.runScan(USER, API_KEY, scanCode, *FossId.deltaScanRunParameters(originCode))
            }
        }

        "carry exclusion rules to a delta scan from an existing scan" {
            val projectCode = projectCode(PROJECT)
            val originCode = "originalScanCode"
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.DELTA)
            val config = createConfig()
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, vcsInfo.revision, originCode)

            val service = FossIdRestService.createService(config.serverUrl)
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

            fossId.scan(listOf(createPackage(createIdentifier(index = 1), vcsInfo)))

            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
                service.runScan(USER, API_KEY, scanCode, *FossId.deltaScanRunParameters(originCode))
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
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.ORIGIN)
            val config = createConfig()
            val vcsInfo = createVcsInfo()

            val service = FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.NEW, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .mockFiles(scanCode, identifiedRange = 1..2, markedRange = 1..2)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(listOf(createPackage(createIdentifier(index = 1), vcsInfo)))

            coVerify {
                service.createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision)
                service.downloadFromGit(USER, API_KEY, scanCode)
                service.checkDownloadStatus(USER, API_KEY, scanCode)
            }

            coVerify(exactly = 0) {
                service.runScan(USER, API_KEY, scanCode, any())
            }
        }

        "delete newly triggered scans if a package cannot be scanned" {
            val id1 = createIdentifier(index = 1)
            val vcsInfo1 = createVcsInfo()
            val pkg1 = createPackage(id1, vcsInfo1)
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, FossId.DeltaTag.ORIGIN)

            val failedProject = "failedProject"
            val id2 = createIdentifier(index = 2)
            val failedVcsInfo = createVcsInfo(projectName = failedProject)
            val pkg2 = createPackage(id2, failedVcsInfo)
            val failedProjectCode = projectCode(failedProject)
            val config = createConfig()

            val service = FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, emptyList())
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo1)
                .expectDownload(scanCode)
                .mockFiles(scanCode, markedRange = 1..2)

            service.expectProjectRequest(failedProjectCode)
            coEvery { service.listScansForProject(USER, API_KEY, failedProjectCode) } throws IllegalStateException()
            coEvery { service.deleteScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            val scannerRun = fossId.scan(listOf(pkg1, pkg2))

            scannerRun.results.scanResults.keys shouldHaveSize 2
            scannerRun.results.collectIssues()[id2].shouldNotBeNull {
                val issue = first()
                issue.message shouldContain id2.toCoordinates()
                issue.severity shouldBe Severity.ERROR
            }

            coVerify {
                service.deleteScan(USER, API_KEY, scanCode)
            }
        }

        "enforce a limit on the number of delta scans" {
            val numberOfDeltaScans = 8
            val deltaScanLimit = 4
            val projectCode = projectCode(PROJECT)
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

            val service = FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, scans)
                .expectCheckScanStatus(recentScan.code!!, ScanStatus.FINISHED)
                .expectCheckScanStatus(scanCode, ScanStatus.NOT_STARTED, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .expectListIgnoreRules(recentScan.code!!, emptyList())
                .mockFiles(scanCode)
            coEvery { service.runScan(any()) } returns EntityResponseBody(status = 1)
            coEvery { service.deleteScan(any()) } returns EntityResponseBody(status = 1)

            val fossId = createFossId(config)

            fossId.scan(listOf(createPackage(createIdentifier(index = 1), vcsInfo)))

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
            val projectCode = projectCode(PROJECT)
            val scanCode = scanCode(PROJECT, null)
            val config = createConfig(deltaScans = false)
            val vcsInfo = createVcsInfo()
            val scan = createScan(vcsInfo.url, "${vcsInfo.revision}_other", scanCode)
            val pkgId = createIdentifier(index = 42)

            FossIdRestService.createService(config.serverUrl)
                .expectProjectRequest(projectCode)
                .expectListScans(projectCode, listOf(scan))
                .expectCheckScanStatus(scanCode, ScanStatus.FINISHED)
                .expectCreateScan(projectCode, scanCode, vcsInfo)
                .expectDownload(scanCode)
                .mockFiles(scanCode, identifiedRange = 1..2, markedRange = 1..2)

            val fossId = createFossId(config)

            val results = fossId.scan(listOf(createPackage(pkgId, vcsInfo))).scanResults(pkgId)

            val expectedAdditionalData = mapOf(SCAN_CODE_KEY to scanCode)

            results.forAtLeastOne { result ->
                result.additionalData shouldBe expectedAdditionalData
            }
        }
    }
})

/** A test user ID. */
private const val USER = "fossIdTestUser"

/** An API key used by tests. */
private const val API_KEY = "fossId-API-key"

/** A test project name. */
private const val PROJECT = "fossId-test-project"

/** A test revision. */
private const val REVISION = "test-revision"

/** The version to be reported by the FossID server. */
private const val FOSSID_VERSION = "2021.2.2"

/** A test scan ID that is returned by default when mocking the creation of a scan. */
private const val SCAN_ID = 1

/** An [IgnoreRule], returned by default wrapped in a list when mocking the listing of exclusion rules. */
private val IGNORE_RULE = IgnoreRule(1, RuleType.EXTENSION, ".docx", SCAN_ID, "2021-06-09 14:45:25")

/** The default scope used when creating ignore rule. */
private val DEFAULT_IGNORE_RULE_SCOPE = RuleScope.SCAN

/**
 * Create a new [FossId] instance with the specified [config].
 */
private fun createFossId(config: FossIdConfig): FossId =
    FossId("FossId", ScannerConfiguration(), DownloaderConfiguration(), config)

/**
 * Create a standard [FossIdConfig] whose properties can be partly specified.
 */
private fun createConfig(
    waitForResult: Boolean = true,
    deltaScans: Boolean = true,
    deltaScanLimit: Int = Int.MAX_VALUE
): FossIdConfig {
    val config = FossIdConfig(
        serverUrl = "https://www.example.org/fossid",
        user = USER,
        apiKey = API_KEY,
        waitForResult = waitForResult,
        addAuthenticationToUrl = false,
        keepFailedScans = false,
        deltaScans = deltaScans,
        deltaScanLimit = deltaScanLimit,
        timeout = 60,
        options = emptyMap()
    )

    val namingProvider = createNamingProviderMock()
    val configSpy = spyk(config)
    every { configSpy.createNamingProvider() } returns namingProvider

    return configSpy
}

/**
 * Create a mock [FossIdNamingProvider] that returns deterministic names derived from the parameters provided to its
 * _createXXX()_ functions.
 */
private fun createNamingProviderMock(): FossIdNamingProvider {
    val counter = AtomicInteger()
    val provider = mockk<FossIdNamingProvider>()

    every { provider.createProjectCode(any()) } answers {
        projectCode(firstArg())
    }

    every { provider.createScanCode(any(), any()) } answers {
        scanCode(firstArg(), secondArg(), index = counter.incrementAndGet())
    }

    return provider
}

/**
 * Create a mock for the [FossIdRestService]. The mock is prepared to return its version. (This is queried directly
 * in the constructor of [FossId].)
 */
private fun createServiceMock(): FossIdServiceWithVersion {
    val service = mockk<FossIdServiceWithVersion>()

    coEvery { service.version } returns FOSSID_VERSION

    return service
}

/**
 * Generate a project code for the project with the given [name].
 */
private fun projectCode(name: String): String = "$name:projectCode"

/**
 * Generate a synthetic scan code for the project with the given [name], [tag], and [index].
 */
private fun scanCode(name: String, tag: FossId.DeltaTag? = null, index: Int = 1): String =
    "$name:${tag?.name}:scanCode$index"

/**
 * Create a mock [UnversionedScanDescription] that returns the given [state].
 */
private fun createScanDescription(state: ScanStatus): UnversionedScanDescription {
    val description = mockk<UnversionedScanDescription>()
    every { description.status } returns state
    every { description.comment } returns "status$state"
    return description
}

/**
 * Create a mock [Scan] with the given properties.
 */
private fun createScan(url: String, revision: String, scanCode: String, scanId: Int = SCAN_ID): Scan {
    val scan = mockk<Scan>()
    every { scan.gitRepoUrl } returns url
    every { scan.gitBranch } returns revision
    every { scan.code } returns scanCode
    every { scan.id } returns scanId
    every { scan.isArchived } returns null
    return scan
}

/**
 * Create a [VcsInfo] object for a project with the given [name][projectName] and the optional parameters for [type],
 * [path], and [revision].
 */
private fun createVcsInfo(
    projectName: String = PROJECT,
    type: VcsType = VcsType.GIT,
    path: String = "",
    revision: String = REVISION
): VcsInfo =
    VcsInfo(type = type, path = path, revision = revision, url = "https://github.com/test/$projectName.git")

/**
 * Create a test [Identifier] with properties derived from the given [index].
 */
private fun createIdentifier(index: Int = 1): Identifier =
    Identifier(type = "test", namespace = "test-ns", name = "test$index", version = "1.0.$index")

/**
 * Create a test [Package] with the given [id] , [vcsInfo], and [authors].
 */
private fun createPackage(id: Identifier, vcsInfo: VcsInfo, authors: Set<String> = emptySet()): Package =
    Package.EMPTY.copy(id = id, vcsProcessed = vcsInfo, authors = authors.toSortedSet())

/**
 * Generate the path to a test file based on the given [index].
 */
private fun filePath(index: Int): String = "/path/to/file$index.kt"

/**
 * Create a [TextLocation] that references a test file without any line information.
 */
private fun textLocation(fileIndex: Int): TextLocation =
    TextLocation(filePath(fileIndex), TextLocation.UNKNOWN_LINE, TextLocation.UNKNOWN_LINE)

/**
 * Create an [IdentifiedFile] based on the given [index].
 */
private fun createIdentifiedFile(index: Int): IdentifiedFile {
    val file = IdentifiedFile(
        comment = null,
        identificationId = index,
        identificationCopyright = "copyright$index",
        isDistributed = index,
        rowId = index,
        userName = "$USER$index",
        userSurname = null,
        userUsername = null
    )

    val license = org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.License(
        fileLicenseMatchType = LicenseMatchType.SNIPPET,
        id = index,
        identifier = "lic$index",
        isFoss = 0,
        isOsiApproved = 0,
        isSpdxStandard = 0,
        name = "name$index"
    )

    file.file = org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.File(
        id = "identified$index",
        licenseIdentifier = "licenseIdentifier1",
        licenseIncludeInReport = false,
        licenseIsCopyleft = false,
        licenseIsFoss = true,
        licenseIsSpdxStandard = false,
        licenseMatchType = null,
        licenseName = "license$index",
        licenses = mutableMapOf(index to license),
        md5 = null,
        path = filePath(index),
        sha1 = null,
        sha256 = null
    )

    return file
}

/**
 * Create a [MarkedAsIdentifiedFile] based on the given [index].
 */
private fun createMarkedIdentifiedFile(index: Int): MarkedAsIdentifiedFile {
    val file = MarkedAsIdentifiedFile(
        identificationId = index,
        identificationCopyright = "copyrightMarked$index",
        isDistributed = index,
        rowId = index,
        comment = null
    )

    val license = License(index, LicenseMatchType.FILE, index, index, index, index, "created$index", "updated$index")

    license.file = LicenseFile(
        licenseIdentifier = "licenseMarkedIdentifier$index",
        licenseIncludeInReport = true,
        licenseIsCopyleft = false,
        licenseIsFoss = true,
        licenseIsSpdxStandard = true,
        licenseName = "test$index"
    )

    file.file = org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.File(
        id = "marked$index",
        md5 = null,
        path = filePath(index),
        sha1 = null,
        sha256 = null,
        size = index,
        licenses = mutableMapOf(index to license)
    )

    return file
}

/**
 * Create an [IgnoredFile] based on the given [index].
 */
private fun createIgnoredFile(index: Int): IgnoredFile =
    IgnoredFile(id = index, path = filePath(index), reason = "ignoreReason$index", matchType = "match$index")

/**
 * Generate a string representing a pending file based on the given [index].
 */
private fun createPendingFile(index: Int): String = "/pending/file/$index"

/**
 * Prepare this service mock to answer a request for a project with the given [projectCode]. Return a response with
 * the given [status] and [error].
 */
private fun FossIdServiceWithVersion.expectProjectRequest(
    projectCode: String,
    status: Int = 200,
    error: String? = null
): FossIdServiceWithVersion {
    coEvery { getProject(USER, API_KEY, projectCode) } returns
            EntityResponseBody(status = status, error = error, data = mockk())
    return this
}

/**
 * Prepare this service mock to answer requests for the status of the scan with the given [scanCode]. The service
 * returns responses with the given [states] in succeeding invocations.
 */
private fun FossIdServiceWithVersion.expectCheckScanStatus(
    scanCode: String,
    vararg states: ScanStatus
): FossIdServiceWithVersion {
    val statusResponses = states.map { EntityResponseBody(status = 1, data = createScanDescription(it)) }
    coEvery { checkScanStatus(USER, API_KEY, scanCode) } returnsMany statusResponses
    return this
}

/**
 * Prepare this service mock to return the list of [scans] for the given [projectCode].
 */
private fun FossIdServiceWithVersion.expectListScans(projectCode: String, scans: List<Scan>): FossIdServiceWithVersion {
    coEvery { listScansForProject(USER, API_KEY, projectCode) } returns
            PolymorphicResponseBody(status = 1, data = PolymorphicList(scans))
    return this
}

/**
 * Prepare this service mock to return the list of [rules] for the given [scanCode].
 */
private fun FossIdServiceWithVersion.expectListIgnoreRules(
    scanCode: String, rules: List<IgnoreRule>
): FossIdServiceWithVersion {
    coEvery { listIgnoreRules(USER, API_KEY, scanCode) } returns
            PolymorphicResponseBody(status = 1, data = PolymorphicList(rules))
    return this
}

/**
 * Prepare this service mock to expect a request to create an 'ignore rule' for the given [scanCode], [ruleType],
 * [value] and [scope].
 */
private fun FossIdServiceWithVersion.expectCreateIgnoreRule(
    scanCode: String,
    ruleType: RuleType,
    value: String,
    scope: RuleScope
): FossIdServiceWithVersion {
    coEvery {
        createIgnoreRule(USER, API_KEY, scanCode, ruleType, value, scope)
    } returns EntityResponseBody(status = 1)
    return this
}

/**
 * Prepare this service mock to expect a download trigger for the given [scanCode] and later on to report that the
 * download has finished.
 */
private fun FossIdServiceWithVersion.expectDownload(scanCode: String): FossIdServiceWithVersion {
    coEvery { downloadFromGit(USER, API_KEY, scanCode) } returns
            EntityResponseBody(status = 1)
    coEvery { checkDownloadStatus(USER, API_KEY, scanCode) } returns
            EntityResponseBody(status = 1, data = DownloadStatus.FINISHED)
    return this
}

/**
 * Prepare this service mock to expect a request to create a scan for the given [projectCode], [scanCode], and
 * [vcsInfo].
 */
private fun FossIdServiceWithVersion.expectCreateScan(
    projectCode: String,
    scanCode: String,
    vcsInfo: VcsInfo
): FossIdServiceWithVersion {
    coEvery {
        createScan(USER, API_KEY, projectCode, scanCode, vcsInfo.url, vcsInfo.revision)
    } returns MapResponseBody(status = 1, data = mapOf("scan_id" to SCAN_ID.toString()))
    return this
}

/**
 * Prepare this service mock to expect a request to delete the scan with the given [scanCode].
 */
private fun FossIdServiceWithVersion.expectDeleteScan(scanCode: String): FossIdServiceWithVersion {
    coEvery {
        deleteScan(USER, API_KEY, scanCode)
    } returns EntityResponseBody(status = 1, data = PolymorphicInt(0))
    return this
}

/**
 * Prepare this service mock to answer queries for the different file types associated with the given [scanCode].
 * Based on the passed in ranges, test files are created.
 */
private fun FossIdServiceWithVersion.mockFiles(
    scanCode: String,
    identifiedRange: IntRange = IntRange.EMPTY,
    markedRange: IntRange = IntRange.EMPTY,
    ignoredRange: IntRange = IntRange.EMPTY,
    pendingRange: IntRange = IntRange.EMPTY
): FossIdServiceWithVersion {
    val identifiedFiles = identifiedRange.map(::createIdentifiedFile)
    val markedFiles = markedRange.map(::createMarkedIdentifiedFile)
    val ignoredFiles = ignoredRange.map(::createIgnoredFile)
    val pendingFiles = pendingRange.map(::createPendingFile)

    coEvery { listIdentifiedFiles(USER, API_KEY, scanCode) } returns
            PolymorphicResponseBody(
                status = 1, data = PolymorphicList(identifiedFiles)
            )
    coEvery { listMarkedAsIdentifiedFiles(USER, API_KEY, scanCode) } returns
            PolymorphicResponseBody(
                status = 1, data = PolymorphicList(markedFiles)
            )
    coEvery { listIgnoredFiles(USER, API_KEY, scanCode) } returns
            PolymorphicResponseBody(status = 1, data = PolymorphicList(ignoredFiles))
    coEvery { listPendingFiles(USER, API_KEY, scanCode) } returns
            PolymorphicResponseBody(status = 1, data = PolymorphicList(pendingFiles))

    return this
}

/**
 * Obtain the [ScanSummary] for the package with the given [pkgId] from this [ScannerRun].
 */
private fun ScannerRun.summary(pkgId: Identifier): ScanSummary {
    val scanResults = scanResults(pkgId)
    scanResults shouldHaveSize 1

    return scanResults[0].summary
}

/**
 * Obtain the [ScanResult]s for the package with the given [pkgId] from this [ScannerRun] or fail if it cannot be
 * resolved.
 */
private fun ScannerRun.scanResults(pkgId: Identifier): List<ScanResult> =
    results.scanResults[pkgId] ?: fail("No result for package $pkgId found.")

/**
 * Trigger a FossID scan of the given [packages]. Return the resulting [ScannerRun].
 */
private fun FossId.scan(packages: List<Package>): ScannerRun {
    val mockResult = spyk(OrtResult.EMPTY.copy(analyzer = mockk()))
    val curatedPackages = packages.map { CuratedPackage(it) }.toSet()
    every { mockResult.getPackages(any()) } returns curatedPackages
    every { mockResult.getProjects(any()) } returns emptySet()

    val newResult = runBlocking { scanOrtResult(this@scan, mockResult) }

    return newResult.scanner!!
}
