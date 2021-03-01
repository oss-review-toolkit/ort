/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import com.vdurmont.semver4j.Semver

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType

import java.time.Duration
import java.time.Instant

import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScannerCriteria

abstract class AbstractStorageFunTest : WordSpec() {
    private companion object {
        val DUMMY_TEXT_LOCATION = TextLocation("fakepath", 13, 21)
    }

    private val id = Identifier("type", "namespace", "name", "version")

    private val sourceArtifact = RemoteArtifact("url", Hash.create("0123456789abcdef0123456789abcdef01234567"))

    private val vcs = VcsInfo(VcsType("type"), "url", "revision", "resolvedRevision", "path")
    private val vcsWithoutRevision = VcsInfo(VcsType("type"), "url", "", "")

    private val pkg = Package.EMPTY.copy(
        id = id,
        sourceArtifact = sourceArtifact,
        vcs = vcs,
        vcsProcessed = vcs.normalize()
    )
    private val pkgWithoutRevision = pkg.copy(vcs = vcsWithoutRevision, vcsProcessed = vcsWithoutRevision.normalize())

    private val downloadTime1 = Instant.EPOCH + Duration.ofDays(1)
    private val downloadTime2 = Instant.EPOCH + Duration.ofDays(2)
    private val downloadTime3 = Instant.EPOCH + Duration.ofDays(3)

    private val provenanceWithSourceArtifact = Provenance(
        downloadTime = downloadTime1,
        sourceArtifact = sourceArtifact
    )
    private val provenanceWithVcsInfo = Provenance(
        downloadTime = downloadTime2,
        vcsInfo = vcs
    )
    private val provenanceWithOriginalVcsInfo = Provenance(
        downloadTime = downloadTime2,
        vcsInfo = vcs,
        originalVcsInfo = pkgWithoutRevision.vcsProcessed
    )
    private val provenanceEmpty = Provenance(downloadTime3)

    private val scannerDetails1 = ScannerDetails("name 1", "1.0.0", "config 1")
    private val scannerDetails2 = ScannerDetails("name 2", "2.0.0", "config 2")
    private val scannerDetailsCompatibleVersion1 = ScannerDetails("name 1", "1.0.1", "config 1")
    private val scannerDetailsCompatibleVersion2 = ScannerDetails("name 1", "1.0.1-alpha.1", "config 1")
    private val scannerDetailsIncompatibleVersion = ScannerDetails("name 1", "1.1.0", "config 1")

    private val scannerStartTime1 = downloadTime1 + Duration.ofMinutes(1)
    private val scannerEndTime1 = scannerStartTime1 + Duration.ofMinutes(1)
    private val scannerStartTime2 = downloadTime2 + Duration.ofMinutes(1)
    private val scannerEndTime2 = scannerStartTime2 + Duration.ofMinutes(1)

    private val error1 = OrtIssue(source = "source-1", message = "error-1")
    private val error2 = OrtIssue(source = "source-2", message = "error-2")

    private val scanSummaryWithFiles = ScanSummary(
        scannerStartTime1,
        scannerEndTime1,
        1,
        "packageVerificationCode",
        sortedSetOf(
            LicenseFinding("license-1.1", DUMMY_TEXT_LOCATION),
            LicenseFinding("license-1.2", DUMMY_TEXT_LOCATION)
        ),
        sortedSetOf(),
        mutableListOf(error1, error2)
    )
    private val scanSummaryWithoutFiles = ScanSummary(
        scannerStartTime2,
        scannerEndTime2,
        0,
        "packageVerificationCode",
        sortedSetOf(),
        sortedSetOf(),
        mutableListOf()
    )

    abstract fun createStorage(): ScanResultsStorage

    /**
     * Generate a [ScannerCriteria] object that is compatible with the given [details].
     */
    private fun criteriaForDetails(details: ScannerDetails): ScannerCriteria =
        ScannerCriteria(
            regScannerName = details.name,
            minVersion = Semver(details.version),
            maxVersion = Semver(details.version).nextMinor(),
            configMatcher = ScannerCriteria.exactConfigMatcher(details.configuration)
        )

    init {
        "Adding a scan result" should {
            "succeed for a valid scan result" {
                val storage = createStorage()
                val scanResult = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)

                val addResult = storage.add(id, scanResult)
                val readResult = storage.read(id)

                addResult should beOfType(Success::class)
                readResult should beOfType(Success::class)
                (readResult as Success).result.let { result ->
                    result.id shouldBe id
                    result.results should containExactly(scanResult)
                }
            }

            "fail if the fileCount is 0" {
                val storage = createStorage()
                val scanResult = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithoutFiles)

                val addResult = storage.add(id, scanResult)
                val readResult = storage.read(id)

                addResult should beOfType(Failure::class)
                (addResult as Failure).error shouldBe
                        "Not storing scan result for 'type:namespace:name:version' because no files were scanned."
                readResult should beOfType(Success::class)
                (readResult as Success).result.let { result ->
                    result.id shouldBe id
                    result.results should beEmpty()
                }
            }

            "fail if provenance information is missing" {
                val storage = createStorage()
                val scanResult = ScanResult(provenanceEmpty, scannerDetails1, scanSummaryWithFiles)

                val addResult = storage.add(id, scanResult)
                val readResult = storage.read(id)

                addResult should beOfType(Failure::class)
                (addResult as Failure).error shouldBe "Not storing scan result for 'type:namespace:name:version' " +
                        "because no provenance information is available."
                readResult should beOfType(Success::class)
                (readResult as Success).result.let { result ->
                    result.id shouldBe id
                    result.results should beEmpty()
                }
            }
        }

        "Reading a scan result" should {
            "find all scan results for an id" {
                val storage = createStorage()
                val scanResult1 = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResult2 = ScanResult(provenanceWithSourceArtifact, scannerDetails2, scanSummaryWithFiles)

                val addResult1 = storage.add(id, scanResult1)
                val addResult2 = storage.add(id, scanResult2)
                val readResult = storage.read(id)

                addResult1 should beOfType(Success::class)
                addResult2 should beOfType(Success::class)
                readResult should beOfType(Success::class)
                (readResult as Success).result.let { result ->
                    result.id shouldBe id
                    result.results should containExactlyInAnyOrder(scanResult1, scanResult2)
                }
            }

            "find all scan results for a specific scanner" {
                val storage = createStorage()
                val scanResult1 = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResult2 = ScanResult(provenanceWithVcsInfo, scannerDetails1, scanSummaryWithFiles)
                val scanResult3 = ScanResult(provenanceWithSourceArtifact, scannerDetails2, scanSummaryWithFiles)

                val addResult1 = storage.add(id, scanResult1)
                val addResult2 = storage.add(id, scanResult2)
                val addResult3 = storage.add(id, scanResult3)
                val readResult = storage.read(pkg, criteriaForDetails(scannerDetails1))

                addResult1 should beOfType(Success::class)
                addResult2 should beOfType(Success::class)
                addResult3 should beOfType(Success::class)
                readResult should beOfType(Success::class)
                (readResult as Success).result.let { result ->
                    result.id shouldBe id
                    result.results should containExactlyInAnyOrder(scanResult1, scanResult2)
                }
            }

            "find all scan results for scanners with names matching a pattern" {
                val storage = createStorage()
                val detailsCompatibleOtherScanner = scannerDetails1.copy(name = "name 2")
                val detailsIncompatibleOtherScanner = scannerDetails1.copy(name = "other Scanner name")
                val scanResult1 = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResult2 =
                    ScanResult(provenanceWithSourceArtifact, detailsCompatibleOtherScanner, scanSummaryWithFiles)
                val scanResult3 =
                    ScanResult(provenanceWithSourceArtifact, detailsIncompatibleOtherScanner, scanSummaryWithFiles)
                val criteria = criteriaForDetails(scannerDetails1).copy(regScannerName = "name.+")

                val addResult1 = storage.add(id, scanResult1)
                val addResult2 = storage.add(id, scanResult2)
                val addResult3 = storage.add(id, scanResult3)
                val readResult = storage.read(pkg, criteria)

                addResult1 should beOfType(Success::class)
                addResult2 should beOfType(Success::class)
                addResult3 should beOfType(Success::class)
                readResult should beOfType(Success::class)
                (readResult as Success).result.let { result ->
                    result.id shouldBe id
                    result.results should containExactlyInAnyOrder(scanResult1, scanResult2)
                }
            }

            "find all scan results for compatible scanners" {
                val storage = createStorage()
                val scanResult = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResultCompatible1 =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsCompatibleVersion1, scanSummaryWithFiles)
                val scanResultCompatible2 =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsCompatibleVersion2, scanSummaryWithFiles)
                val scanResultIncompatible =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsIncompatibleVersion, scanSummaryWithFiles)

                val addResult = storage.add(id, scanResult)
                val addResultCompatible1 = storage.add(id, scanResultCompatible1)
                val addResultCompatible2 = storage.add(id, scanResultCompatible2)
                val addResultIncompatible = storage.add(id, scanResultIncompatible)
                val readResult = storage.read(pkg, criteriaForDetails(scannerDetails1))

                addResult should beOfType(Success::class)
                addResultCompatible1 should beOfType(Success::class)
                addResultCompatible2 should beOfType(Success::class)
                addResultIncompatible should beOfType(Success::class)
                readResult should beOfType(Success::class)
                (readResult as Success).result.let { result ->
                    result.id shouldBe id
                    result.results should containExactlyInAnyOrder(
                        scanResult,
                        scanResultCompatible1,
                        scanResultCompatible2
                    )
                }
            }

            "find all scan results for a scanner in a version range" {
                val storage = createStorage()
                val scanResult = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResultCompatible1 =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsCompatibleVersion1, scanSummaryWithFiles)
                val scanResultCompatible2 =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsCompatibleVersion2, scanSummaryWithFiles)
                val scanResultIncompatible =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsIncompatibleVersion, scanSummaryWithFiles)
                val criteria = criteriaForDetails(scannerDetails1).copy(maxVersion = Semver("1.5.0"))

                val addResult = storage.add(id, scanResult)
                val addResultCompatible1 = storage.add(id, scanResultCompatible1)
                val addResultCompatible2 = storage.add(id, scanResultCompatible2)
                val addResultIncompatible = storage.add(id, scanResultIncompatible)
                val readResult = storage.read(pkg, criteria)

                addResult should beOfType(Success::class)
                addResultCompatible1 should beOfType(Success::class)
                addResultCompatible2 should beOfType(Success::class)
                addResultIncompatible should beOfType(Success::class)
                readResult should beOfType(Success::class)
                (readResult as Success).result.let { result ->
                    result.id shouldBe id
                    result.results should containExactlyInAnyOrder(
                        scanResult,
                        scanResultCompatible1,
                        scanResultCompatible2,
                        scanResultIncompatible
                    )
                }
            }

            "find only packages with matching provenance" {
                val storage = createStorage()
                val scanResultSourceArtifactMatching =
                    ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResultVcsMatching = ScanResult(provenanceWithVcsInfo, scannerDetails1, scanSummaryWithFiles)
                val provenanceSourceArtifactNonMatching = provenanceWithSourceArtifact.copy(
                    sourceArtifact = sourceArtifact.copy(hash = Hash.create("0123456789012345678901234567890123456789"))
                )
                val scanResultSourceArtifactNonMatching =
                    ScanResult(provenanceSourceArtifactNonMatching, scannerDetails1, scanSummaryWithFiles)
                val provenanceVcsInfoNonMatching = provenanceWithVcsInfo.copy(
                    vcsInfo = vcs.copy(revision = "revision2", resolvedRevision = "resolvedRevision2")
                )
                val scanResultVcsInfoNonMatching =
                    ScanResult(provenanceVcsInfoNonMatching, scannerDetails1, scanSummaryWithFiles)

                val addResult1 = storage.add(id, scanResultSourceArtifactMatching)
                val addResult2 = storage.add(id, scanResultVcsMatching)
                val addResult3 = storage.add(id, scanResultSourceArtifactNonMatching)
                val addResult4 = storage.add(id, scanResultVcsInfoNonMatching)
                val readResult = storage.read(pkg, criteriaForDetails(scannerDetails1))

                addResult1 should beOfType(Success::class)
                addResult2 should beOfType(Success::class)
                addResult3 should beOfType(Success::class)
                addResult4 should beOfType(Success::class)
                readResult should beOfType(Success::class)
                (readResult as Success).result.let { result ->
                    result.id shouldBe id
                    result.results should containExactlyInAnyOrder(
                        scanResultSourceArtifactMatching,
                        scanResultVcsMatching
                    )
                }
            }

            "find a scan result if the revision was detected from a version" {
                val storage = createStorage()
                val scanResult = ScanResult(provenanceWithOriginalVcsInfo, scannerDetails1, scanSummaryWithFiles)

                val addResult = storage.add(id, scanResult)
                val readResult = storage.read(pkgWithoutRevision, criteriaForDetails(scannerDetails1))

                addResult should beOfType(Success::class)
                readResult should beOfType(Success::class)
                (readResult as Success).result.let { result ->
                    result.id shouldBe id
                    result.results should containExactly(scanResult)
                }
            }
        }
    }
}
