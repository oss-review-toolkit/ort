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

package org.ossreviewtoolkit.scanner.experimental

import com.vdurmont.semver4j.Semver

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import java.time.Instant

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.ScannerCriteria

abstract class AbstractProvenanceBasedStorageFunTest(vararg listeners: TestListener) : WordSpec() {
    private lateinit var storage: ProvenanceBasedScanStorage

    protected abstract fun createStorage(): ProvenanceBasedScanStorage

    init {
        register(*listeners)

        beforeEach {
            storage = createStorage()
        }

        "Adding a scan result" should {
            "succeed for a valid scan result" {
                val scanResult = createScanResult()

                storage.write(scanResult)
                val readResult = storage.read(scanResult.provenance as KnownProvenance)

                readResult should containExactly(scanResult)
            }

            "fail if provenance information is missing" {
                val scanResult = createScanResult(provenance = UnknownProvenance)

                shouldThrow<ScanStorageException> { storage.write(scanResult) }
            }

            "fail if a result for the same provenance and scanner is already stored" {
                val scanResult = createScanResult()

                storage.write(scanResult)
                shouldThrow<ScanStorageException> { storage.write(scanResult) }
            }

            "fail if the provenance contains a VCS path" {
                val scanResult = createScanResult(
                    provenance = createRepositoryProvenance(
                        vcsInfo = VcsInfo.valid().copy(path = "path")
                    )
                )

                shouldThrow<ScanStorageException> { storage.write(scanResult) }
            }

            "ignore the VCS revision in favor of the resolved revision when adding a duplicate" {
                val scanResult1 = createScanResult(provenance = createRepositoryProvenance())
                val scanResult2 = createScanResult(
                    provenance = createRepositoryProvenance(
                        vcsInfo = VcsInfo.valid().copy(revision = "another")
                    )
                )

                storage.write(scanResult1)
                shouldThrow<ScanStorageException> { storage.write(scanResult2) }
            }
        }

        "Reading a scan result" should {
            "find all scan results for a provenance" {
                val scanResult1 = createScanResult()
                val scanResult2 = createScanResult(scannerDetails = createScannerDetails(name = "other"))

                storage.write(scanResult1)
                storage.write(scanResult2)

                val readResult = storage.read(scanResult1.provenance as KnownProvenance)

                readResult should containExactlyInAnyOrder(scanResult1, scanResult2)
            }

            "use only the resolved revision for matching and return the provided provenance" {
                val provenance1 = createRepositoryProvenance()
                val provenance2 = provenance1.copy(vcsInfo = provenance1.vcsInfo.copy(revision = "another"))

                val scanResult = createScanResult(provenance = provenance1)

                storage.write(scanResult)

                val readResult = storage.read(provenance2)

                readResult should containExactlyInAnyOrder(scanResult.copy(provenance = provenance2))
            }

            "fail if the provenance contains a VCS path" {
                val provenance = createRepositoryProvenance(vcsInfo = VcsInfo.valid().copy(path = "path"))
                val criteria = ScannerCriteria.forDetails(createScannerDetails())

                shouldThrow<ScanStorageException> { storage.read(provenance) }
                shouldThrow<ScanStorageException> { storage.read(provenance, criteria) }
            }

            "find scan result for a specific scanner" {
                val scanResult1 = createScanResult()
                val scanResult2 = createScanResult(scannerDetails = createScannerDetails(name = "other"))

                storage.write(scanResult1)
                storage.write(scanResult2)

                val readResult = storage.read(
                    scanResult1.provenance as KnownProvenance,
                    ScannerCriteria.forDetails(scanResult1.scanner)
                )

                readResult should containExactlyInAnyOrder(scanResult1)
            }

            "find all scan results for scanners with names matching a pattern" {
                val scanResult1 = createScanResult(scannerDetails = createScannerDetails(name = "name1"))
                val scanResult2 = createScanResult(scannerDetails = createScannerDetails(name = "name2"))
                val scanResult3 = createScanResult(scannerDetails = createScannerDetails(name = "other name"))
                val criteria = ScannerCriteria.forDetails(scanResult1.scanner).copy(regScannerName = "name.+")

                storage.write(scanResult1)
                storage.write(scanResult2)
                storage.write(scanResult3)

                val readResult = storage.read(scanResult1.provenance as KnownProvenance, criteria)

                readResult should containExactlyInAnyOrder(scanResult1, scanResult2)
            }

            "find all scan results for compatible scanners" {
                val scanResult = createScanResult()
                val scanResultCompatible1 = createScanResult(scannerDetails = createScannerDetails(version = "1.0.1"))
                val scanResultCompatible2 =
                    createScanResult(scannerDetails = createScannerDetails(version = "1.0.1-alpha.1"))
                val scanResultIncompatible = createScanResult(scannerDetails = createScannerDetails(version = "2.0.0"))
                val criteria = ScannerCriteria.forDetails(scanResult.scanner, Semver.VersionDiff.PATCH)

                storage.write(scanResult)
                storage.write(scanResultCompatible1)
                storage.write(scanResultCompatible2)
                storage.write(scanResultIncompatible)

                val readResult = storage.read(scanResult.provenance as KnownProvenance, criteria)

                readResult should containExactlyInAnyOrder(scanResult, scanResultCompatible1, scanResultCompatible2)
            }
        }
    }
}

private fun RemoteArtifact.Companion.valid() =
    RemoteArtifact(
        url = "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.14.1/log4j-api-2.14.1-sources.jar",
        hash = Hash("b2327c47ca413c1ec183575b19598e281fcd74d8", HashAlgorithm.SHA1)
    )

private fun VcsInfo.Companion.valid() =
    VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort.git",
        revision = "f42e41a8fedc1e0acd78fab147e91fa047cb2853"
    )

private fun createKnownProvenance() = ArtifactProvenance(sourceArtifact = RemoteArtifact.valid())

private fun createRepositoryProvenance(
    vcsInfo: VcsInfo = VcsInfo.valid(),
    resolvedRevision: String = VcsInfo.valid().revision
) = RepositoryProvenance(vcsInfo, resolvedRevision)

private fun createScannerDetails(
    name: String = "name",
    version: String = "1.0.0",
    configuration: String = "configuration"
) = ScannerDetails(name, version, configuration)

private fun createScanSummary(licenseFindings: Set<LicenseFinding> = emptySet()) =
    ScanSummary(Instant.EPOCH, Instant.EPOCH, "", licenseFindings.toSortedSet(), sortedSetOf())

private fun createScanResult(
    provenance: Provenance = createKnownProvenance(),
    scannerDetails: ScannerDetails = createScannerDetails(),
    license: String = "Apache-2.0"
) =
    ScanResult(
        provenance,
        scannerDetails,
        createScanSummary(
            licenseFindings = sortedSetOf(
                LicenseFinding(license, TextLocation("file.txt", 1, 2))
            )
        )
    )
