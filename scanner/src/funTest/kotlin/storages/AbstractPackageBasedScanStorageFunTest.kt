/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.time.Duration
import java.time.Instant

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.ScannerMatcher

import org.semver4j.Semver

private val DUMMY_TEXT_LOCATION = TextLocation("fakepath", 13, 21)

abstract class AbstractPackageBasedScanStorageFunTest(vararg listeners: TestListener) : WordSpec() {
    private val id = Identifier("type", "namespace", "name1", "version")

    private val sourceArtifact = RemoteArtifact("url1", Hash.create("0123456789abcdef0123456789abcdef01234567"))

    private val vcs = VcsInfo(VcsType.forName("type"), "url1", "revision", "path")
    private val vcsWithoutRevision = VcsInfo(VcsType.forName("type"), "url", "")

    private val pkg = Package.EMPTY.copy(
        id = id,
        sourceArtifact = sourceArtifact,
        vcs = VcsInfo.EMPTY,
        vcsProcessed = vcs
    )

    private val pkgWithoutRevision = pkg.copy(vcs = vcsWithoutRevision, vcsProcessed = vcsWithoutRevision.normalize())

    private val provenanceEmpty = UnknownProvenance
    private val provenanceWithoutRevision = RepositoryProvenance(
        vcsInfo = pkgWithoutRevision.vcsProcessed,
        resolvedRevision = "resolvedRevision"
    )
    private val provenanceWithSourceArtifact = ArtifactProvenance(sourceArtifact = sourceArtifact)
    private val provenanceWithVcsInfo = RepositoryProvenance(vcsInfo = vcs, resolvedRevision = "resolvedRevision")

    private val scannerDetails1 = ScannerDetails("name 1", "1.0.0", "config 1")
    private val scannerDetails2 = ScannerDetails("name 2", "2.0.0", "config 2")
    private val scannerDetailsCompatibleVersion1 = ScannerDetails("name 1", "1.0.1", "config 1")
    private val scannerDetailsCompatibleVersion2 = ScannerDetails("name 1", "1.0.1-alpha.1", "config 1")
    private val scannerDetailsIncompatibleVersion = ScannerDetails("name 1", "2.0.0", "config 1")

    private val scannerMatcherForDetails1 = ScannerMatcher.create(scannerDetails1)

    private val scanSummaryWithFiles = ScanSummary.EMPTY.copy(
        startTime = Instant.EPOCH + Duration.ofMinutes(1),
        endTime = Instant.EPOCH + Duration.ofMinutes(2),
        licenseFindings = setOf(
            LicenseFinding("license-1.1", DUMMY_TEXT_LOCATION),
            LicenseFinding("license-1.2", DUMMY_TEXT_LOCATION)
        ),
        issues = listOf(
            Issue(source = "source-1", message = "error-1"),
            Issue(source = "source-2", message = "error-2")
        )
    )

    private lateinit var storage: AbstractPackageBasedScanStorage

    abstract fun createStorage(): AbstractPackageBasedScanStorage

    init {
        extensions(listeners.asList())

        beforeEach {
            storage = createStorage()
        }

        "Adding a scan result" should {
            "succeed for a valid scan result" {
                val scanResult = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)

                val addResult = storage.add(id, scanResult)
                val readResult = storage.read(pkg)

                addResult.shouldBeSuccess()
                readResult shouldBeSuccess {
                    it should containExactly(scanResult)
                }
            }

            "fail if provenance information is missing" {
                val scanResult = ScanResult(provenanceEmpty, scannerDetails1, scanSummaryWithFiles)

                val addResult = storage.add(id, scanResult)
                val readResult = storage.read(pkg)

                addResult shouldBeFailure {
                    it.message shouldBe "Not storing scan result for '${id.toCoordinates()}' because no provenance " +
                        "information is available."
                }

                readResult shouldBeSuccess {
                    it should beEmpty()
                }
            }

            "not store a result for the same scanner and provenance twice" {
                val summary1 = scanSummaryWithFiles
                val summary2 = scanSummaryWithFiles.copy(
                    startTime = scanSummaryWithFiles.startTime.plusSeconds(10)
                )

                val scanResult1 = ScanResult(provenanceWithSourceArtifact, scannerDetails1, summary1)
                val scanResult2 = ScanResult(provenanceWithSourceArtifact, scannerDetails1, summary2)

                val addResult1 = storage.add(id, scanResult1)
                val addResult2 = storage.add(id, scanResult2)

                addResult1.shouldBeSuccess()
                addResult2.shouldBeFailure()

                val readResult = storage.read(pkg)
                readResult shouldBeSuccess {
                    it should containExactly(scanResult1)
                }
            }
        }

        "Reading a scan result" should {
            "find all scan results for an id" {
                val scanResult1 = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResult2 = ScanResult(provenanceWithSourceArtifact, scannerDetails2, scanSummaryWithFiles)

                storage.add(id, scanResult1).shouldBeSuccess()
                storage.add(id, scanResult2).shouldBeSuccess()
                val readResult = storage.read(pkg)

                readResult shouldBeSuccess {
                    it should containExactlyInAnyOrder(scanResult1, scanResult2)
                }
            }

            "find all scan results for a specific scanner" {
                val scanResult1 = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResult2 = ScanResult(provenanceWithVcsInfo, scannerDetails1, scanSummaryWithFiles)
                val scanResult3 = ScanResult(provenanceWithSourceArtifact, scannerDetails2, scanSummaryWithFiles)

                storage.add(id, scanResult1).shouldBeSuccess()
                storage.add(id, scanResult2).shouldBeSuccess()
                storage.add(id, scanResult3).shouldBeSuccess()
                val readResult = storage.read(pkg, scannerMatcherForDetails1)

                readResult shouldBeSuccess {
                    it should containExactlyInAnyOrder(scanResult1, scanResult2)
                }
            }

            "find all scan results for scanners with names matching a pattern" {
                val detailsCompatibleOtherScanner = scannerDetails1.copy(name = "name 2")
                val detailsIncompatibleOtherScanner = scannerDetails1.copy(name = "other Scanner name")
                val scanResult1 = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResult2 =
                    ScanResult(provenanceWithSourceArtifact, detailsCompatibleOtherScanner, scanSummaryWithFiles)
                val scanResult3 =
                    ScanResult(provenanceWithSourceArtifact, detailsIncompatibleOtherScanner, scanSummaryWithFiles)
                val matcher = scannerMatcherForDetails1.copy(regScannerName = "name.+")

                storage.add(id, scanResult1).shouldBeSuccess()
                storage.add(id, scanResult2).shouldBeSuccess()
                storage.add(id, scanResult3).shouldBeSuccess()
                val readResult = storage.read(pkg, matcher)

                readResult shouldBeSuccess {
                    it should containExactlyInAnyOrder(scanResult1, scanResult2)
                }
            }

            "find all scan results for compatible scanners" {
                val scanResult = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResultCompatible1 =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsCompatibleVersion1, scanSummaryWithFiles)
                val scanResultCompatible2 =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsCompatibleVersion2, scanSummaryWithFiles)
                val scanResultIncompatible =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsIncompatibleVersion, scanSummaryWithFiles)

                storage.add(id, scanResult).shouldBeSuccess()
                storage.add(id, scanResultCompatible1).shouldBeSuccess()
                storage.add(id, scanResultCompatible2).shouldBeSuccess()
                storage.add(id, scanResultIncompatible).shouldBeSuccess()
                val readResult = storage.read(pkg, scannerMatcherForDetails1)

                readResult shouldBeSuccess {
                    it should containExactlyInAnyOrder(scanResult, scanResultCompatible1, scanResultCompatible2)
                }
            }

            "find all scan results for a scanner in a version range" {
                val scanResult = ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResultCompatible1 =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsCompatibleVersion1, scanSummaryWithFiles)
                val scanResultCompatible2 =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsCompatibleVersion2, scanSummaryWithFiles)
                val scanResultIncompatible =
                    ScanResult(provenanceWithSourceArtifact, scannerDetailsIncompatibleVersion, scanSummaryWithFiles)
                val matcher = scannerMatcherForDetails1.copy(maxVersion = Semver("2.0.1"))

                storage.add(id, scanResult).shouldBeSuccess()
                storage.add(id, scanResultCompatible1).shouldBeSuccess()
                storage.add(id, scanResultCompatible2).shouldBeSuccess()
                storage.add(id, scanResultIncompatible).shouldBeSuccess()
                val readResult = storage.read(pkg, matcher)

                readResult shouldBeSuccess {
                    it should containExactlyInAnyOrder(
                        scanResult,
                        scanResultCompatible1,
                        scanResultCompatible2,
                        scanResultIncompatible
                    )
                }
            }

            "find only packages with matching provenance" {
                val scanResultSourceArtifactMatching =
                    ScanResult(provenanceWithSourceArtifact, scannerDetails1, scanSummaryWithFiles)
                val scanResultVcsMatching = ScanResult(provenanceWithVcsInfo, scannerDetails1, scanSummaryWithFiles)
                val provenanceSourceArtifactNonMatching = provenanceWithSourceArtifact.copy(
                    sourceArtifact = sourceArtifact.copy(
                        hash = Hash.create("0000000000000000000000000000000000000000")
                    )
                )
                val scanResultSourceArtifactNonMatching =
                    ScanResult(provenanceSourceArtifactNonMatching, scannerDetails1, scanSummaryWithFiles)
                val provenanceVcsNonMatching = provenanceWithVcsInfo.copy(
                    vcsInfo = vcs.copy(revision = "revision2"),
                    resolvedRevision = "resolvedRevision2"
                )
                val scanResultVcsNonMatching =
                    ScanResult(provenanceVcsNonMatching, scannerDetails1, scanSummaryWithFiles)

                storage.add(id, scanResultSourceArtifactMatching).shouldBeSuccess()
                storage.add(id, scanResultVcsMatching).shouldBeSuccess()
                storage.add(id, scanResultSourceArtifactNonMatching).shouldBeSuccess()
                storage.add(id, scanResultVcsNonMatching).shouldBeSuccess()
                val readResult = storage.read(pkg, scannerMatcherForDetails1)

                readResult shouldBeSuccess {
                    it should containExactlyInAnyOrder(scanResultSourceArtifactMatching, scanResultVcsMatching)
                }
            }

            "find a scan result if the revision was resolved from a version" {
                val scanResult = ScanResult(provenanceWithoutRevision, scannerDetails1, scanSummaryWithFiles)

                storage.add(id, scanResult).shouldBeSuccess()
                val readResult = storage.read(pkgWithoutRevision, scannerMatcherForDetails1)

                readResult shouldBeSuccess {
                    it should containExactly(scanResult)
                }
            }

            "not find a scan result if vcs matches (but not vcsProcessed)" {
                val pkg = Package.EMPTY.copy(
                    id = id,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = vcs,
                    vcsProcessed = VcsInfo.EMPTY
                )
                val scanResult = ScanResult(provenanceWithVcsInfo, scannerDetails1, scanSummaryWithFiles)

                storage.add(id, scanResult).shouldBeSuccess()

                val readResult = storage.read(pkg, scannerMatcherForDetails1)

                readResult shouldBeSuccess {
                    it should beEmpty()
                }
            }
        }
    }
}
