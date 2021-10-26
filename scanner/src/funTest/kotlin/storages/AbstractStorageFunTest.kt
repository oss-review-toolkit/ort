/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.scanner.storages

import com.vdurmont.semver4j.Semver

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType

import java.time.Duration
import java.time.Instant

import kotlin.contracts.contract

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScannerCriteria

private val DUMMY_TEXT_LOCATION = TextLocation("fakepath", 13, 21)

abstract class AbstractStorageFunTest : WordSpec() {
    private val id1 = Identifier("type", "namespace", "name1", "version")
    private val id2 = Identifier("type", "namespace", "name2", "version")

    private val sourceArtifact1 = RemoteArtifact("url1", Hash.create("0123456789abcdef0123456789abcdef01234567"))
    private val sourceArtifact2 = RemoteArtifact("url2", Hash.create("0123456789abcdef0123456789abcdef01234567"))

    private val vcs1 = VcsInfo(VcsType("type"), "url1", "revision", "path")
    private val vcs2 = VcsInfo(VcsType("type"), "url2", "revision", "path")
    private val vcsWithoutRevision = VcsInfo(VcsType("type"), "url", "")

    private val pkg1 = Package.EMPTY.copy(
        id = id1,
        sourceArtifact = sourceArtifact1,
        vcs = vcs1,
        vcsProcessed = vcs1.normalize()
    )

    private val pkg2 = Package.EMPTY.copy(
        id = id2,
        sourceArtifact = sourceArtifact2,
        vcs = vcs2,
        vcsProcessed = vcs2.normalize()
    )

    private val pkgWithoutRevision = pkg1.copy(vcs = vcsWithoutRevision, vcsProcessed = vcsWithoutRevision.normalize())

    private val provenanceEmpty = UnknownProvenance
    private val provenanceWithoutRevision = RepositoryProvenance(
        vcsInfo = pkgWithoutRevision.vcsProcessed,
        resolvedRevision = "resolvedRevision"
    )
    private val provenanceWithSourceArtifact1 = ArtifactProvenance(sourceArtifact = sourceArtifact1)
    private val provenanceWithSourceArtifact2 = ArtifactProvenance(sourceArtifact = sourceArtifact2)
    private val provenanceWithVcsInfo1 = RepositoryProvenance(vcsInfo = vcs1, resolvedRevision = "resolvedRevision")
    private val provenanceWithVcsInfo2 = RepositoryProvenance(vcsInfo = vcs2, resolvedRevision = "resolvedRevision")

    private val scannerDetails1 = ScannerDetails("name 1", "1.0.0", "config 1")
    private val scannerDetails2 = ScannerDetails("name 2", "2.0.0", "config 2")
    private val scannerDetailsCompatibleVersion1 = ScannerDetails("name 1", "1.0.1", "config 1")
    private val scannerDetailsCompatibleVersion2 = ScannerDetails("name 1", "1.0.1-alpha.1", "config 1")
    private val scannerDetailsIncompatibleVersion = ScannerDetails("name 1", "1.1.0", "config 1")

    private val scanSummaryWithFiles = ScanSummary(
        startTime = Instant.EPOCH + Duration.ofMinutes(1),
        endTime = Instant.EPOCH + Duration.ofMinutes(2),
        packageVerificationCode = "packageVerificationCode",
        licenseFindings = sortedSetOf(
            LicenseFinding("license-1.1", DUMMY_TEXT_LOCATION),
            LicenseFinding("license-1.2", DUMMY_TEXT_LOCATION)
        ),
        copyrightFindings = sortedSetOf(),
        issues = mutableListOf(
            OrtIssue(source = "source-1", message = "error-1"),
            OrtIssue(source = "source-2", message = "error-2")
        )
    )

    private lateinit var storage: ScanResultsStorage

    override fun beforeTest(testCase: TestCase) {
        storage = createStorage()
    }

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
                val scanResult = ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)

                val addResult = storage.add(id1, scanResult)
                val readResult = storage.read(id1)

                addResult.shouldBeSuccess()
                readResult.shouldBeSuccess()
                readResult.result should containExactly(scanResult)
            }

            "fail if provenance information is missing" {
                val scanResult = ScanResult(provenanceEmpty, scannerDetails1, scanSummaryWithFiles)

                val addResult = storage.add(id1, scanResult)
                val readResult = storage.read(id1)

                addResult.shouldBeFailure()
                addResult.error shouldBe "Not storing scan result for '${id1.toCoordinates()}' because no provenance " +
                        "information is available."

                readResult.shouldBeSuccess()
                readResult.result should beEmpty()
            }
        }

        "Reading a scan result" should {
            "find all scan results for an id" {
                val scanResult1 = ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)
                val scanResult2 = ScanResult(provenanceWithSourceArtifact1, scannerDetails2, scanSummaryWithFiles)

                storage.add(id1, scanResult1).shouldBeSuccess()
                storage.add(id1, scanResult2).shouldBeSuccess()
                val readResult = storage.read(id1)

                readResult.shouldBeSuccess()
                readResult.result should containExactlyInAnyOrder(scanResult1, scanResult2)
            }

            "find all scan results for a specific scanner" {
                val scanResult1 = ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)
                val scanResult2 = ScanResult(provenanceWithVcsInfo1, scannerDetails1, scanSummaryWithFiles)
                val scanResult3 = ScanResult(provenanceWithSourceArtifact1, scannerDetails2, scanSummaryWithFiles)

                storage.add(id1, scanResult1).shouldBeSuccess()
                storage.add(id1, scanResult2).shouldBeSuccess()
                storage.add(id1, scanResult3).shouldBeSuccess()
                val readResult = storage.read(pkg1, criteriaForDetails(scannerDetails1))

                readResult.shouldBeSuccess()
                readResult.result should containExactlyInAnyOrder(scanResult1, scanResult2)
            }

            "find all scan results for scanners with names matching a pattern" {
                val detailsCompatibleOtherScanner = scannerDetails1.copy(name = "name 2")
                val detailsIncompatibleOtherScanner = scannerDetails1.copy(name = "other Scanner name")
                val scanResult1 = ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)
                val scanResult2 =
                    ScanResult(provenanceWithSourceArtifact1, detailsCompatibleOtherScanner, scanSummaryWithFiles)
                val scanResult3 =
                    ScanResult(provenanceWithSourceArtifact1, detailsIncompatibleOtherScanner, scanSummaryWithFiles)
                val criteria = criteriaForDetails(scannerDetails1).copy(regScannerName = "name.+")

                storage.add(id1, scanResult1).shouldBeSuccess()
                storage.add(id1, scanResult2).shouldBeSuccess()
                storage.add(id1, scanResult3).shouldBeSuccess()
                val readResult = storage.read(pkg1, criteria)

                readResult.shouldBeSuccess()
                readResult.result should containExactlyInAnyOrder(scanResult1, scanResult2)
            }

            "find all scan results for compatible scanners" {
                val scanResult = ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)
                val scanResultCompatible1 =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsCompatibleVersion1, scanSummaryWithFiles)
                val scanResultCompatible2 =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsCompatibleVersion2, scanSummaryWithFiles)
                val scanResultIncompatible =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsIncompatibleVersion, scanSummaryWithFiles)

                storage.add(id1, scanResult).shouldBeSuccess()
                storage.add(id1, scanResultCompatible1).shouldBeSuccess()
                storage.add(id1, scanResultCompatible2).shouldBeSuccess()
                storage.add(id1, scanResultIncompatible).shouldBeSuccess()
                val readResult = storage.read(pkg1, criteriaForDetails(scannerDetails1))

                readResult.shouldBeSuccess()
                readResult.result should containExactlyInAnyOrder(
                    scanResult,
                    scanResultCompatible1,
                    scanResultCompatible2
                )
            }

            "find all scan results for a scanner in a version range" {
                val scanResult = ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)
                val scanResultCompatible1 =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsCompatibleVersion1, scanSummaryWithFiles)
                val scanResultCompatible2 =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsCompatibleVersion2, scanSummaryWithFiles)
                val scanResultIncompatible =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsIncompatibleVersion, scanSummaryWithFiles)
                val criteria = criteriaForDetails(scannerDetails1).copy(maxVersion = Semver("1.5.0"))

                storage.add(id1, scanResult).shouldBeSuccess()
                storage.add(id1, scanResultCompatible1).shouldBeSuccess()
                storage.add(id1, scanResultCompatible2).shouldBeSuccess()
                storage.add(id1, scanResultIncompatible).shouldBeSuccess()
                val readResult = storage.read(pkg1, criteria)

                readResult.shouldBeSuccess()
                readResult.result should containExactlyInAnyOrder(
                    scanResult,
                    scanResultCompatible1,
                    scanResultCompatible2,
                    scanResultIncompatible
                )
            }

            "find only packages with matching provenance" {
                val scanResultSourceArtifactMatching =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)
                val scanResultVcsMatching = ScanResult(provenanceWithVcsInfo1, scannerDetails1, scanSummaryWithFiles)
                val provenanceSourceArtifactNonMatching = provenanceWithSourceArtifact1.copy(
                    sourceArtifact = sourceArtifact1.copy(
                        hash = Hash.create("0000000000000000000000000000000000000000")
                    )
                )
                val scanResultSourceArtifactNonMatching =
                    ScanResult(provenanceSourceArtifactNonMatching, scannerDetails1, scanSummaryWithFiles)
                val provenanceVcsNonMatching = provenanceWithVcsInfo1.copy(
                    vcsInfo = vcs1.copy(revision = "revision2"),
                    resolvedRevision = "resolvedRevision2"
                )
                val scanResultVcsNonMatching =
                    ScanResult(provenanceVcsNonMatching, scannerDetails1, scanSummaryWithFiles)

                storage.add(id1, scanResultSourceArtifactMatching).shouldBeSuccess()
                storage.add(id1, scanResultVcsMatching).shouldBeSuccess()
                storage.add(id1, scanResultSourceArtifactNonMatching).shouldBeSuccess()
                storage.add(id1, scanResultVcsNonMatching).shouldBeSuccess()
                val readResult = storage.read(pkg1, criteriaForDetails(scannerDetails1))

                readResult.shouldBeSuccess()
                readResult.result should containExactlyInAnyOrder(
                    scanResultSourceArtifactMatching,
                    scanResultVcsMatching
                )
            }

            "find a scan result if the revision was resolved from a version" {
                val scanResult = ScanResult(provenanceWithoutRevision, scannerDetails1, scanSummaryWithFiles)

                storage.add(id1, scanResult).shouldBeSuccess()
                val readResult = storage.read(pkgWithoutRevision, criteriaForDetails(scannerDetails1))

                readResult.shouldBeSuccess()
                readResult.result should containExactly(scanResult)
            }
        }

        "Reading scan results for multiple packages" should {
            "find all scan results for a specific scanner" {
                val scanResult1 = ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)
                val scanResult2 = ScanResult(provenanceWithVcsInfo1, scannerDetails1, scanSummaryWithFiles)
                val scanResult3 = ScanResult(provenanceWithSourceArtifact1, scannerDetails2, scanSummaryWithFiles)
                val scanResult4 = ScanResult(provenanceWithSourceArtifact2, scannerDetails1, scanSummaryWithFiles)
                val scanResult5 = ScanResult(provenanceWithVcsInfo2, scannerDetails1, scanSummaryWithFiles)
                val scanResult6 = ScanResult(provenanceWithSourceArtifact2, scannerDetails2, scanSummaryWithFiles)

                storage.add(id1, scanResult1).shouldBeSuccess()
                storage.add(id1, scanResult2).shouldBeSuccess()
                storage.add(id1, scanResult3).shouldBeSuccess()
                storage.add(id2, scanResult4).shouldBeSuccess()
                storage.add(id2, scanResult5).shouldBeSuccess()
                storage.add(id2, scanResult6).shouldBeSuccess()
                val readResult = storage.read(listOf(pkg1, pkg2), criteriaForDetails(scannerDetails1))

                readResult.shouldBeSuccess()
                readResult.result.let { result ->
                    result.keys should containExactly(id1, id2)
                    result[id1] should containExactlyInAnyOrder(scanResult1, scanResult2)
                    result[id2] should containExactlyInAnyOrder(scanResult4, scanResult5)
                }
            }

            "find all scan results for scanners with names matching a pattern" {
                val detailsCompatibleOtherScanner = scannerDetails1.copy(name = "name 2")
                val detailsIncompatibleOtherScanner = scannerDetails1.copy(name = "other Scanner name")
                val scanResult1 = ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)
                val scanResult2 =
                    ScanResult(provenanceWithSourceArtifact1, detailsCompatibleOtherScanner, scanSummaryWithFiles)
                val scanResult3 =
                    ScanResult(provenanceWithSourceArtifact1, detailsIncompatibleOtherScanner, scanSummaryWithFiles)
                val scanResult4 = ScanResult(provenanceWithSourceArtifact2, scannerDetails1, scanSummaryWithFiles)
                val scanResult5 =
                    ScanResult(provenanceWithSourceArtifact2, detailsCompatibleOtherScanner, scanSummaryWithFiles)
                val scanResult6 =
                    ScanResult(provenanceWithSourceArtifact2, detailsIncompatibleOtherScanner, scanSummaryWithFiles)
                val criteria = criteriaForDetails(scannerDetails1).copy(regScannerName = "name.+")

                storage.add(id1, scanResult1).shouldBeSuccess()
                storage.add(id1, scanResult2).shouldBeSuccess()
                storage.add(id1, scanResult3).shouldBeSuccess()
                storage.add(id2, scanResult4).shouldBeSuccess()
                storage.add(id2, scanResult5).shouldBeSuccess()
                storage.add(id2, scanResult6).shouldBeSuccess()
                val readResult = storage.read(listOf(pkg1, pkg2), criteria)

                readResult.shouldBeSuccess()
                readResult.result.let { result ->
                    result.keys should containExactly(id1, id2)
                    result[id1] should containExactlyInAnyOrder(scanResult1, scanResult2)
                    result[id2] should containExactlyInAnyOrder(scanResult4, scanResult5)
                }
            }

            "find all scan results for compatible scanners" {
                val scanResult1 = ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)
                val scanResult1Compatible1 =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsCompatibleVersion1, scanSummaryWithFiles)
                val scanResult1Compatible2 =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsCompatibleVersion2, scanSummaryWithFiles)
                val scanResult1Incompatible =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsIncompatibleVersion, scanSummaryWithFiles)

                val scanResult2 = ScanResult(provenanceWithSourceArtifact2, scannerDetails1, scanSummaryWithFiles)
                val scanResult2Compatible1 =
                    ScanResult(provenanceWithSourceArtifact2, scannerDetailsCompatibleVersion1, scanSummaryWithFiles)
                val scanResult2Compatible2 =
                    ScanResult(provenanceWithSourceArtifact2, scannerDetailsCompatibleVersion2, scanSummaryWithFiles)
                val scanResult2Incompatible =
                    ScanResult(provenanceWithSourceArtifact2, scannerDetailsIncompatibleVersion, scanSummaryWithFiles)

                storage.add(id1, scanResult1).shouldBeSuccess()
                storage.add(id1, scanResult1Compatible1).shouldBeSuccess()
                storage.add(id1, scanResult1Compatible2).shouldBeSuccess()
                storage.add(id1, scanResult1Incompatible).shouldBeSuccess()

                storage.add(id2, scanResult2).shouldBeSuccess()
                storage.add(id2, scanResult2Compatible1).shouldBeSuccess()
                storage.add(id2, scanResult2Compatible2).shouldBeSuccess()
                storage.add(id2, scanResult2Incompatible).shouldBeSuccess()

                val readResult = storage.read(listOf(pkg1, pkg2), criteriaForDetails(scannerDetails1))

                readResult.shouldBeSuccess()
                readResult.result.let { result ->
                    result.keys should containExactly(id1, id2)
                    result[id1] should containExactlyInAnyOrder(
                        scanResult1,
                        scanResult1Compatible1,
                        scanResult1Compatible2
                    )
                    result[id2] should containExactlyInAnyOrder(
                        scanResult2,
                        scanResult2Compatible1,
                        scanResult2Compatible2
                    )
                }
            }

            "find all scan results for a scanner in a version range" {
                val scanResult1 = ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)
                val scanResult1Compatible1 =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsCompatibleVersion1, scanSummaryWithFiles)
                val scanResult1Compatible2 =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsCompatibleVersion2, scanSummaryWithFiles)
                val scanResult1Incompatible =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetailsIncompatibleVersion, scanSummaryWithFiles)

                val scanResult2 = ScanResult(provenanceWithSourceArtifact2, scannerDetails1, scanSummaryWithFiles)
                val scanResult2Compatible1 =
                    ScanResult(provenanceWithSourceArtifact2, scannerDetailsCompatibleVersion1, scanSummaryWithFiles)
                val scanResult2Compatible2 =
                    ScanResult(provenanceWithSourceArtifact2, scannerDetailsCompatibleVersion2, scanSummaryWithFiles)
                val scanResult2Incompatible =
                    ScanResult(provenanceWithSourceArtifact2, scannerDetailsIncompatibleVersion, scanSummaryWithFiles)

                val criteria = criteriaForDetails(scannerDetails1).copy(maxVersion = Semver("1.5.0"))

                storage.add(id1, scanResult1).shouldBeSuccess()
                storage.add(id1, scanResult1Compatible1).shouldBeSuccess()
                storage.add(id1, scanResult1Compatible2).shouldBeSuccess()
                storage.add(id1, scanResult1Incompatible).shouldBeSuccess()

                storage.add(id2, scanResult2).shouldBeSuccess()
                storage.add(id2, scanResult2Compatible1).shouldBeSuccess()
                storage.add(id2, scanResult2Compatible2).shouldBeSuccess()
                storage.add(id2, scanResult2Incompatible).shouldBeSuccess()

                val readResult = storage.read(listOf(pkg1, pkg2), criteria)

                readResult.shouldBeSuccess()
                readResult.result.let { result ->
                    result.keys should containExactly(id1, id2)
                    result[id1] should containExactlyInAnyOrder(
                        scanResult1,
                        scanResult1Compatible1,
                        scanResult1Compatible2,
                        scanResult1Incompatible
                    )
                    result[id2] should containExactlyInAnyOrder(
                        scanResult2,
                        scanResult2Compatible1,
                        scanResult2Compatible2,
                        scanResult2Incompatible
                    )
                }
            }

            "find only packages with matching provenance" {
                val scanResultSourceArtifactMatching1 =
                    ScanResult(provenanceWithSourceArtifact1, scannerDetails1, scanSummaryWithFiles)
                val scanResultVcsMatching1 = ScanResult(provenanceWithVcsInfo1, scannerDetails1, scanSummaryWithFiles)
                val provenanceSourceArtifactNonMatching1 = provenanceWithSourceArtifact1.copy(
                    sourceArtifact = sourceArtifact1.copy(
                        hash = Hash.create("0000000000000000000000000000000000000000")
                    )
                )
                val scanResultSourceArtifactNonMatching1 =
                    ScanResult(provenanceSourceArtifactNonMatching1, scannerDetails1, scanSummaryWithFiles)
                val provenanceVcsNonMatching1 = provenanceWithVcsInfo1.copy(
                    vcsInfo = vcs1.copy(revision = "revision2"),
                    resolvedRevision = "resolvedRevision2"
                )
                val scanResultVcsNonMatching1 =
                    ScanResult(provenanceVcsNonMatching1, scannerDetails1, scanSummaryWithFiles)

                val scanResultSourceArtifactMatching2 =
                    ScanResult(provenanceWithSourceArtifact2, scannerDetails1, scanSummaryWithFiles)
                val scanResultVcsMatching2 = ScanResult(provenanceWithVcsInfo2, scannerDetails1, scanSummaryWithFiles)
                val provenanceSourceArtifactNonMatching2 = provenanceWithSourceArtifact2.copy(
                    sourceArtifact = sourceArtifact2.copy(
                        hash = Hash.create("0000000000000000000000000000000000000000")
                    )
                )
                val scanResultSourceArtifactNonMatching2 =
                    ScanResult(provenanceSourceArtifactNonMatching2, scannerDetails1, scanSummaryWithFiles)
                val provenanceVcsNonMatching2 = provenanceWithVcsInfo2.copy(
                    vcsInfo = vcs2.copy(revision = "revision2"),
                    resolvedRevision = "resolvedRevision2"
                )
                val scanResultVcsNonMatching2 =
                    ScanResult(provenanceVcsNonMatching2, scannerDetails1, scanSummaryWithFiles)

                storage.add(id1, scanResultSourceArtifactMatching1).shouldBeSuccess()
                storage.add(id1, scanResultVcsMatching1).shouldBeSuccess()
                storage.add(id1, scanResultSourceArtifactNonMatching1).shouldBeSuccess()
                storage.add(id1, scanResultVcsNonMatching1).shouldBeSuccess()

                storage.add(id2, scanResultSourceArtifactMatching2).shouldBeSuccess()
                storage.add(id2, scanResultVcsMatching2).shouldBeSuccess()
                storage.add(id2, scanResultSourceArtifactNonMatching2).shouldBeSuccess()
                storage.add(id2, scanResultVcsNonMatching2).shouldBeSuccess()

                val readResult = storage.read(listOf(pkg1, pkg2), criteriaForDetails(scannerDetails1))

                readResult.shouldBeSuccess()
                readResult.result.let { result ->
                    result.keys should containExactly(id1, id2)
                    result[id1] should containExactlyInAnyOrder(
                        scanResultSourceArtifactMatching1,
                        scanResultVcsMatching1
                    )
                    result[id2] should containExactlyInAnyOrder(
                        scanResultSourceArtifactMatching2,
                        scanResultVcsMatching2
                    )
                }
            }

            "find a scan result if the revision was resolved from a version" {
                val scanResult = ScanResult(provenanceWithoutRevision, scannerDetails1, scanSummaryWithFiles)

                val addResult = storage.add(id1, scanResult)
                val readResult = storage.read(listOf(pkgWithoutRevision), criteriaForDetails(scannerDetails1))

                addResult.shouldBeSuccess()
                readResult.shouldBeSuccess()

                readResult.result.let { result ->
                    result.keys should containExactly(id1)
                    result[id1] should containExactly(scanResult)
                }
            }
        }
    }
}

private fun <T> Result<T>.shouldBeSuccess(): Result<T> {
    contract {
        returns() implies (this@shouldBeSuccess is Success<T>)
    }

    withClue(lazy { (this as Failure).error }) {
        this should beOfType(Success::class)
    }

    return this
}

private fun <T> Result<T>.shouldBeFailure(): Result<T> {
    contract {
        returns() implies (this@shouldBeFailure is Failure<T>)
    }

    this should beOfType(Failure::class)

    return this
}
