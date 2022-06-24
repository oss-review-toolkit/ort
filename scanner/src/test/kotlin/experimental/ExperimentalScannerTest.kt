/*
 * Copyright (C) 2021 HERE Europe B.V.
 * Copyright (C) 2021-2022 Bosch.IO GmbH
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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.maps.beEmpty as beEmptyMap
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify

import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.SortedSet

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration
import org.ossreviewtoolkit.model.config.Options
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class ExperimentalScannerTest : WordSpec({
    "Creating the experimental scanner" should {
        "throw an exception if no scanner wrappers are provided" {
            shouldThrow<IllegalArgumentException> {
                createScanner()
            }
        }
    }

    "Scanning with different scanners for projects and packages" should {
        "Use the correct scanners for each data entity" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val packageScannerWrapper = FakePackageScannerWrapper(name = "package scanner")
            val projectScannerWrapper = FakePackageScannerWrapper(name = "project scanner")
            val scanner = createScanner(
                packageScannerWrappers = listOf(packageScannerWrapper),
                projectScannerWrappers = listOf(projectScannerWrapper)
            )

            val packageContext = createContext(type = PackageType.PACKAGE)
            val projectContext = createContext(type = PackageType.PROJECT)

            scanner.scan(setOf(pkgWithArtifact), packageContext)[pkgWithArtifact.id] shouldNotBeNull {
                this shouldNot beEmpty()
                forEach { it.scanner.name shouldBe "package scanner" }
            }

            scanner.scan(setOf(pkgWithArtifact), projectContext)[pkgWithArtifact.id] shouldNotBeNull {
                this shouldNot beEmpty()
                forEach { it.scanner.name shouldBe "project scanner" }
            }
        }

        "Not scan projects if no scanner wrapper for projects is configured" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val scannerWrapper = FakePackageScannerWrapper()
            val scanner = createScanner(
                packageScannerWrappers = listOf(scannerWrapper),
                projectScannerWrappers = emptyList()
            )

            scanner.scan(setOf(pkgWithArtifact), createContext(type = PackageType.PROJECT)) should beEmptyMap()
        }

        "Not scan packages if no scanner wrapper for packages is configured" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val scannerWrapper = FakePackageScannerWrapper()
            val scanner = createScanner(
                packageScannerWrappers = emptyList(),
                projectScannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact), createContext()) should beEmptyMap()
        }
    }

    "Scanning with a package scanner" should {
        "return a scan result for each package" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(name = "repository").withValidVcs()
            val scannerWrapper = spyk(FakePackageScannerWrapper())
            val scanner = createScanner(packageScannerWrappers = listOf(scannerWrapper))

            every { scannerWrapper.scanPackage(pkgWithArtifact, any()) } returns
                    createScanResult(pkgWithArtifact.artifactProvenance(), scannerWrapper.details)
            every { scannerWrapper.scanPackage(pkgWithVcs, any()) } returns
                    createScanResult(pkgWithVcs.repositoryProvenance(), scannerWrapper.details)

            val result = scanner.scan(setOf(pkgWithArtifact, pkgWithVcs), createContext())

            result should containExactly(
                pkgWithArtifact.id to listOf(
                    createScanResult(
                        provenance = pkgWithArtifact.artifactProvenance(),
                        scannerDetails = scannerWrapper.details
                    )
                ),
                pkgWithVcs.id to listOf(
                    createScanResult(
                        provenance = pkgWithVcs.repositoryProvenance(),
                        scannerDetails = scannerWrapper.details
                    )
                )
            )

            verify(exactly = 1) {
                scannerWrapper.scanPackage(pkgWithArtifact, any())
                scannerWrapper.scanPackage(pkgWithVcs, any())
            }
        }

        "not try to download the source code" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(name = "repository").withValidVcs()
            val provenanceDownloader = mockk<ProvenanceDownloader>()
            val scannerWrapper = FakePackageScannerWrapper()
            val scanner = createScanner(
                provenanceDownloader = provenanceDownloader,
                packageScannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact, pkgWithVcs), createContext())

            verify(exactly = 0) { provenanceDownloader.download(any()) }
        }
    }

    "Scanning with a provenance scanner" should {
        "return a scan result for each provenance" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(name = "repository").withValidVcs()
            val scannerWrapper = spyk(FakeProvenanceScannerWrapper())
            val scanner = createScanner(packageScannerWrappers = listOf(scannerWrapper))

            val result = scanner.scan(setOf(pkgWithArtifact, pkgWithVcs), createContext())

            result should containExactly(
                pkgWithArtifact.id to listOf(
                    createScanResult(
                        provenance = pkgWithArtifact.artifactProvenance(),
                        scannerDetails = scannerWrapper.details
                    )
                ),
                pkgWithVcs.id to listOf(
                    createScanResult(
                        provenance = pkgWithVcs.repositoryProvenance(),
                        scannerDetails = scannerWrapper.details
                    )
                )
            )

            verify(exactly = 1) {
                scannerWrapper.scanProvenance(pkgWithArtifact.artifactProvenance(), any())
                scannerWrapper.scanProvenance(pkgWithVcs.repositoryProvenance(), any())
            }
        }

        "not try to download the source code" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(name = "repository").withValidVcs()
            val provenanceDownloader = mockk<ProvenanceDownloader>()
            val scannerWrapper = FakeProvenanceScannerWrapper()
            val scanner = createScanner(
                provenanceDownloader = provenanceDownloader,
                packageScannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact, pkgWithVcs), createContext())

            verify(exactly = 0) { provenanceDownloader.download(any()) }
        }
    }

    "scanning with a path scanner" should {
        "return a scan result for each provenance" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(name = "repository").withValidVcs()
            val scannerWrapper = spyk(FakePathScannerWrapper())
            val scanner = createScanner(packageScannerWrappers = listOf(scannerWrapper))

            val result = scanner.scan(setOf(pkgWithArtifact, pkgWithVcs), createContext())

            result should containExactly(
                pkgWithArtifact.id to listOf(
                    createScanResult(
                        provenance = pkgWithArtifact.artifactProvenance(),
                        scannerDetails = scannerWrapper.details
                    )
                ),
                pkgWithVcs.id to listOf(
                    createScanResult(
                        provenance = pkgWithVcs.repositoryProvenance(),
                        scannerDetails = scannerWrapper.details
                    )
                )
            )

            verify(exactly = 2) {
                scannerWrapper.scanPath(any(), any())
            }
        }

        "try to download the source code" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(name = "repository").withValidVcs()
            val scannerWrapper = FakePathScannerWrapper()
            val provenanceDownloader = spyk(FakeProvenanceDownloader("${scannerWrapper.name}.txt"))
            val scanner = createScanner(
                provenanceDownloader = provenanceDownloader,
                packageScannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact, pkgWithVcs), createContext())

            verify(exactly = 1) {
                provenanceDownloader.download(pkgWithArtifact.artifactProvenance())
                provenanceDownloader.download(pkgWithVcs.repositoryProvenance())
            }
        }

        "always download the full repository" {
            val pkgWithVcsPath = Package.new(name = "repository").copy(
                vcsProcessed = VcsInfo.valid().copy(path = "subdirectory")
            )
            val scannerWrapper = FakePathScannerWrapper()
            val provenanceDownloader = spyk(FakeProvenanceDownloader())
            val scanner = createScanner(
                provenanceDownloader = provenanceDownloader,
                packageScannerWrappers = listOf(scannerWrapper)
            )

            val provenance = pkgWithVcsPath.repositoryProvenance().copy(
                vcsInfo = pkgWithVcsPath.vcsProcessed.copy(path = "")
            )

            scanner.scan(setOf(pkgWithVcsPath), createContext())

            verify(exactly = 1) {
                provenanceDownloader.download(provenance)
            }
        }

        "download a repository with multiple packages only once" {
            val pkgWithVcsPath1 = Package.new(name = "repository").copy(
                vcsProcessed = VcsInfo.valid().copy(path = "")
            )
            val pkgWithVcsPath2 = Package.new(name = "subdirectory1").copy(
                vcsProcessed = VcsInfo.valid().copy(path = "subdirectory1")
            )
            val pkgWithVcsPath3 = Package.new(name = "subdirectory2").copy(
                vcsProcessed = VcsInfo.valid().copy(path = "subdirectory2")
            )
            val scannerWrapper = FakePathScannerWrapper()
            val provenanceDownloader = spyk(FakeProvenanceDownloader())
            val scanner = createScanner(
                provenanceDownloader = provenanceDownloader,
                packageScannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithVcsPath1, pkgWithVcsPath2, pkgWithVcsPath3), createContext())

            verify(exactly = 1) {
                provenanceDownloader.download(any())
            }
        }
    }

    "scanning with a package based storage reader" should {
        "prefer a stored result over a new scan" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val scannerWrapper = spyk(FakePackageScannerWrapper())
            val reader = spyk(FakePackageBasedStorageReader(scannerWrapper.details))

            every { reader.read(pkgWithArtifact, any()) } returns listOf(
                createStoredNestedScanResult(pkgWithArtifact.artifactProvenance(), scannerWrapper.details)
            )

            val scanner = createScanner(
                storageReaders = listOf(reader),
                packageScannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgWithArtifact), createContext())

            result should containExactly(
                pkgWithArtifact.id to listOf(
                    createStoredScanResult(
                        provenance = pkgWithArtifact.artifactProvenance(),
                        scannerDetails = scannerWrapper.details
                    )
                )
            )

            verify(exactly = 0) {
                scannerWrapper.scanPackage(pkgWithArtifact, any())
            }
        }

        "start a scan if no stored result is available" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val scannerWrapper = spyk(FakePackageScannerWrapper())
            val reader = spyk(FakePackageBasedStorageReader(scannerWrapper.details))

            every { reader.read(any(), any()) } returns emptyList()

            val scanner = createScanner(
                storageReaders = listOf(reader),
                packageScannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgWithArtifact), createContext())

            result should containExactly(
                pkgWithArtifact.id to listOf(
                    createScanResult(
                        provenance = pkgWithArtifact.artifactProvenance(),
                        scannerDetails = scannerWrapper.details
                    )
                )
            )

            verify(exactly = 1) {
                reader.read(pkgWithArtifact, any())
                scannerWrapper.scanPackage(pkgWithArtifact, any())
            }
        }

        "scan only the parts of a nested provenance for which no stored result is available" {
            val pkgCompletelyScanned = Package.new(name = "completely").withValidVcs().withVcsRevision("revision1")
            val pkgPartlyScanned = Package.new(name = "partly").withValidVcs().withVcsRevision("revision2")

            val scannerWrapper = spyk(FakeProvenanceScannerWrapper())

            val scannedSubRepository = RepositoryProvenance(
                VcsInfo(VcsType.GIT, "https://example.com/scanned", "revision"),
                "resolvedRevision"
            )
            val unscannedSubRepository = RepositoryProvenance(
                VcsInfo(VcsType.GIT, "https://example.com/unscanned", "revision"),
                "resolvedRevision"
            )

            val nestedProvenanceCompletelyScanned = NestedProvenance(
                root = pkgCompletelyScanned.repositoryProvenance(),
                subRepositories = mapOf("path" to scannedSubRepository)
            )
            val nestedProvenancePartlyScanned = NestedProvenance(
                root = pkgPartlyScanned.repositoryProvenance(),
                subRepositories = mapOf("path1" to scannedSubRepository, "path2" to unscannedSubRepository)
            )

            val nestedScanResultCompletelyScanned = createNestedScanResult(
                provenance = nestedProvenanceCompletelyScanned.root,
                scannerDetails = scannerWrapper.details,
                subRepositories = nestedProvenanceCompletelyScanned.subRepositories
            )
            val nestedScanResultPartlyScanned = createNestedScanResult(
                provenance = nestedProvenancePartlyScanned.root,
                scannerDetails = scannerWrapper.details,
                subRepositories = nestedProvenancePartlyScanned.subRepositories
            )

            val nestedProvenanceResolver = spyk(FakeNestedProvenanceResolver())

            every {
                nestedProvenanceResolver.resolveNestedProvenance(pkgCompletelyScanned.repositoryProvenance())
            } returns nestedProvenanceCompletelyScanned
            every {
                nestedProvenanceResolver.resolveNestedProvenance(pkgPartlyScanned.repositoryProvenance())
            } returns nestedProvenancePartlyScanned

            val reader = spyk(FakePackageBasedStorageReader(scannerWrapper.details))

            every { reader.read(pkgCompletelyScanned, any()) } returns listOf(nestedScanResultCompletelyScanned)

            val scanner = createScanner(
                storageReaders = listOf(reader),
                nestedProvenanceResolver = nestedProvenanceResolver,
                packageScannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgCompletelyScanned, pkgPartlyScanned), createContext())

            result should containExactly(
                pkgCompletelyScanned.id to nestedScanResultCompletelyScanned.merge(),
                pkgPartlyScanned.id to nestedScanResultPartlyScanned.merge()
            )

            verify(exactly = 0) {
                scannerWrapper.scanProvenance(scannedSubRepository, any())
            }

            verify(exactly = 1) {
                scannerWrapper.scanProvenance(unscannedSubRepository, any())
            }
        }
    }

    "scanning with a provenance based storage reader" should {
        "prefer a stored result over a new scan" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val scannerWrapper = spyk(FakeProvenanceScannerWrapper())
            val reader = spyk(FakeProvenanceBasedStorageReader(scannerWrapper.details))

            every { reader.read(pkgWithArtifact.artifactProvenance()) } returns listOf(
                createStoredScanResult(pkgWithArtifact.artifactProvenance(), scannerWrapper.details)
            )

            val scanner = createScanner(
                storageReaders = listOf(reader),
                packageScannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgWithArtifact), createContext())

            result should containExactly(
                pkgWithArtifact.id to listOf(
                    createStoredScanResult(
                        pkgWithArtifact.artifactProvenance(),
                        scannerWrapper.details
                    )
                )
            )

            verify(exactly = 0) {
                scannerWrapper.scanProvenance(pkgWithArtifact.artifactProvenance(), any())
            }
        }

        "start a scan if no stored result is available" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val scannerWrapper = spyk(FakeProvenanceScannerWrapper())
            val reader = spyk(FakeProvenanceBasedStorageReader(scannerWrapper.details))

            every { reader.read(any()) } returns emptyList()

            val scanner = createScanner(
                storageReaders = listOf(reader),
                packageScannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgWithArtifact), createContext())

            result should containExactly(
                pkgWithArtifact.id to listOf(
                    createScanResult(
                        pkgWithArtifact.artifactProvenance(),
                        scannerWrapper.details
                    )
                )
            )

            verify(exactly = 1) {
                reader.read(pkgWithArtifact.artifactProvenance())
                scannerWrapper.scanProvenance(pkgWithArtifact.artifactProvenance(), any())
            }
        }

        "scan only the parts of a nested provenance for which no stored result is available" {
            val pkgCompletelyScanned = Package.new(name = "completely").withValidVcs().withVcsRevision("revision1")
            val pkgPartlyScanned = Package.new(name = "partly").withValidVcs().withVcsRevision("revision2")

            val scannerWrapper = spyk(FakeProvenanceScannerWrapper())

            val scannedSubRepository = RepositoryProvenance(
                VcsInfo(VcsType.GIT, "https://example.com/scanned", "revision"),
                "resolvedRevision"
            )
            val unscannedSubRepository = RepositoryProvenance(
                VcsInfo(VcsType.GIT, "https://example.com/unscanned", "revision"),
                "resolvedRevision"
            )

            val nestedProvenanceCompletelyScanned = NestedProvenance(
                root = pkgCompletelyScanned.repositoryProvenance(),
                subRepositories = mapOf("path" to scannedSubRepository)
            )
            val nestedProvenancePartlyScanned = NestedProvenance(
                root = pkgPartlyScanned.repositoryProvenance(),
                subRepositories = mapOf("path1" to scannedSubRepository, "path2" to unscannedSubRepository)
            )

            val nestedScanResultCompletelyScanned = createStoredNestedScanResult(
                provenance = nestedProvenanceCompletelyScanned.root,
                scannerDetails = scannerWrapper.details,
                subRepositories = nestedProvenanceCompletelyScanned.subRepositories
            )

            val nestedScanResultPartlyScanned = NestedProvenanceScanResult(
                nestedProvenance = nestedProvenancePartlyScanned,
                scanResults = mapOf(
                    pkgPartlyScanned.repositoryProvenance() to listOf(
                        createStoredScanResult(pkgPartlyScanned.repositoryProvenance(), scannerWrapper.details)
                    ),
                    scannedSubRepository to listOf(
                        createStoredScanResult(scannedSubRepository, scannerWrapper.details)
                    ),
                    unscannedSubRepository to listOf(
                        createScanResult(unscannedSubRepository, scannerWrapper.details)
                    )
                )
            )

            val nestedProvenanceResolver = spyk(FakeNestedProvenanceResolver())

            every {
                nestedProvenanceResolver.resolveNestedProvenance(pkgCompletelyScanned.repositoryProvenance())
            } returns nestedProvenanceCompletelyScanned
            every {
                nestedProvenanceResolver.resolveNestedProvenance(pkgPartlyScanned.repositoryProvenance())
            } returns nestedProvenancePartlyScanned

            val reader = spyk(FakeProvenanceBasedStorageReader(scannerWrapper.details))

            every { reader.read(unscannedSubRepository) } returns emptyList()

            val scanner = createScanner(
                storageReaders = listOf(reader),
                nestedProvenanceResolver = nestedProvenanceResolver,
                packageScannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgCompletelyScanned, pkgPartlyScanned), createContext())

            result should containExactly(
                pkgCompletelyScanned.id to nestedScanResultCompletelyScanned.merge(),
                pkgPartlyScanned.id to nestedScanResultPartlyScanned.merge()
            )

            verify(exactly = 0) {
                scannerWrapper.scanProvenance(scannedSubRepository, any())
            }

            verify(exactly = 1) {
                scannerWrapper.scanProvenance(unscannedSubRepository, any())
            }
        }

        "store the result for the full repository" {
            val pkgWithVcsPath = Package.new(name = "repository").copy(
                vcsProcessed = VcsInfo.valid().copy(path = "subdirectory")
            )

            val provenanceWithoutVcsPath = pkgWithVcsPath.repositoryProvenance().copy(
                vcsInfo = pkgWithVcsPath.vcsProcessed.copy(path = "")
            )

            val scannerWrapper = FakePathScannerWrapper()

            val writer = spyk(FakeProvenanceBasedStorageWriter())

            val scanner = createScanner(
                storageWriters = listOf(writer),
                packageScannerWrappers = listOf(scannerWrapper)
            )

            val fullScanResult = createScanResult(provenanceWithoutVcsPath, scannerWrapper.details)

            scanner.scan(setOf(pkgWithVcsPath), createContext())

            verify(exactly = 1) {
                writer.write(fullScanResult)
            }
        }

        "filter a stored scan result by VCS path" {
            val pkgWithVcsPath = Package.new(name = "repository").copy(
                vcsProcessed = VcsInfo.valid().copy(path = "subdirectory")
            )

            val provenanceWithVcsPath = pkgWithVcsPath.repositoryProvenance()

            val provenanceWithoutVcsPath = pkgWithVcsPath.repositoryProvenance().copy(
                vcsInfo = pkgWithVcsPath.vcsProcessed.copy(path = "")
            )

            val scannerWrapper = FakePathScannerWrapper()

            val reader = spyk(FakeProvenanceBasedStorageReader(scannerWrapper.details))

            val scanResult = createScanResult(
                provenanceWithoutVcsPath,
                scannerWrapper.details,
                sortedSetOf(
                    // Add a license finding outside the subdirectory that is matched by a license file pattern.
                    LicenseFinding("Apache-2.0", TextLocation("LICENSE", 1, 1)),
                    // Add a license finding outside the subdirectory that is not matched by a license file pattern.
                    LicenseFinding("Apache-2.0", TextLocation("other", 1, 1)),
                    // Add a license finding inside the subdirectory.
                    LicenseFinding("Apache-2.0", TextLocation("subdirectory/file", 1, 1))
                )
            )

            every { reader.read(any()) } returns listOf(scanResult)

            val scanner = createScanner(
                storageReaders = listOf(reader),
                packageScannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgWithVcsPath), createContext())

            val filteredScanResult = createScanResult(
                provenanceWithVcsPath,
                scannerWrapper.details,
                sortedSetOf(
                    // Add a license finding outside the subdirectory that is matched by a license file pattern.
                    LicenseFinding("Apache-2.0", TextLocation("LICENSE", 1, 1)),
                    // Add a license finding inside the subdirectory.
                    LicenseFinding("Apache-2.0", TextLocation("subdirectory/file", 1, 1))
                )
            )

            val nestedScanResult = NestedProvenanceScanResult(
                nestedProvenance = NestedProvenance(root = provenanceWithVcsPath, emptyMap()),
                scanResults = mapOf(provenanceWithVcsPath to listOf(filteredScanResult))
            )

            result should containExactly(
                pkgWithVcsPath.id to nestedScanResult.merge()
            )
        }
    }

    "scanning with a package based storage writer" should {
        "store the scan result" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val scannerWrapper = FakePackageScannerWrapper()
            val writer = spyk(FakePackageBasedStorageWriter())

            val scanner = createScanner(
                storageWriters = listOf(writer),
                packageScannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact), createContext())

            verify(exactly = 1) {
                writer.write(
                    pkgWithArtifact,
                    createNestedScanResult(pkgWithArtifact.artifactProvenance(), scannerWrapper.details)
                )
            }
        }

        "not try to store the scan result if it was retrieved from a storage" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val scannerWrapper = FakePackageScannerWrapper()
            val reader = FakePackageBasedStorageReader(scannerWrapper.details)
            val writer = spyk(FakePackageBasedStorageWriter())

            val scanner = createScanner(
                storageReaders = listOf(reader),
                storageWriters = listOf(writer),
                packageScannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact), createContext())

            verify(exactly = 0) {
                writer.write(any(), any())
            }
        }
    }

    "scanning with a provenance based storage writer" should {
        "store the scan result" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val scannerWrapper = FakePackageScannerWrapper()
            val writer = spyk(FakeProvenanceBasedStorageWriter())

            val scanner = createScanner(
                storageWriters = listOf(writer),
                packageScannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact), createContext())

            verify(exactly = 1) {
                writer.write(createScanResult(pkgWithArtifact.artifactProvenance(), scannerWrapper.details))
            }
        }

        "not try to store the scan result if it was retrieved from a storage" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val scannerWrapper = FakePackageScannerWrapper()
            val reader = FakePackageBasedStorageReader(scannerWrapper.details)
            val writer = spyk(FakeProvenanceBasedStorageWriter())

            val scanner = createScanner(
                storageReaders = listOf(reader),
                storageWriters = listOf(writer),
                packageScannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact), createContext())

            verify(exactly = 0) {
                writer.write(any())
            }
        }
    }

    "scanning with a scanner that does not provide criteria" should {
        "not store the scan results" {
            val pkgWithArtifact = Package.new(name = "artifact").withValidSourceArtifact()
            val scannerWrapper = spyk(FakePackageScannerWrapper())

            every { scannerWrapper.criteria } returns null

            val writer = spyk(FakeProvenanceBasedStorageWriter())

            val scanner = createScanner(
                storageWriters = listOf(writer),
                packageScannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact), createContext())

            verify(exactly = 0) {
                writer.write(any())
            }
        }
    }

    // TODO: Add tests for combinations of different types of storage readers and writers.
    // TODO: Add tests for using multiple types of scanner wrappers at once.
    // TODO: Add tests for a complex example with multiple types of scanner wrappers and storages.
    // TODO: Add tests to verify that scanner details are correctly matched.
    // TODO: Add tests to verify that repository provenance paths are correctly handled.
    // TODO: Add tests for error handling, for example missing provenance, download errors, or scanner errors.
    // TODO: Add tests to verify that scanner criteria are correctly handled.
})

/**
 * An implementation of [PackageScannerWrapper] that creates empty scan results.
 */
@Suppress("RedundantNullableReturnType")
private class FakePackageScannerWrapper(
    val packageProvenanceResolver: PackageProvenanceResolver = FakePackageProvenanceResolver(),
    val sourceCodeOriginPriority: List<SourceCodeOrigin> = listOf(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT),
    name: String = "fake"
) : PackageScannerWrapper {
    override val details = ScannerDetails(name, "1.0.0", "config")
    override val name = details.name
    override val criteria: ScannerCriteria? = ScannerCriteria.forDetails(details)

    override fun scanPackage(pkg: Package, context: ScanContext): ScanResult =
        createScanResult(packageProvenanceResolver.resolveProvenance(pkg, sourceCodeOriginPriority), details)

    override fun filterSecretOptions(options: Options) = options
}

/**
 * An implementation of [ProvenanceScannerWrapper] that creates empty scan results.
 */
private class FakeProvenanceScannerWrapper : ProvenanceScannerWrapper {
    override val details = ScannerDetails("fake", "1.0.0", "config")
    override val name = details.name
    override val criteria = ScannerCriteria.forDetails(details)

    override fun scanProvenance(provenance: KnownProvenance, context: ScanContext): ScanResult =
        createScanResult(provenance, details)

    override fun filterSecretOptions(options: Options) = options
}

/**
 * An implementation of [PathScannerWrapper] that creates scan results with one license finding for each file.
 */
private class FakePathScannerWrapper : PathScannerWrapper {
    override val details = ScannerDetails("fake", "1.0.0", "config")
    override val name = details.name
    override val criteria = ScannerCriteria.forDetails(details)

    override fun scanPath(path: File, context: ScanContext): ScanSummary {
        val licenseFindings = path.walk().filter { it.isFile }.map { file ->
            LicenseFinding("Apache-2.0", TextLocation(file.relativeTo(path).path, 1, 2))
        }.toSortedSet()

        return createScanSummary(licenseFindings = licenseFindings)
    }

    override fun filterSecretOptions(options: Options) = options
}

/**
 * An implementation of [ProvenanceDownloader] that creates a file called [filename] containing the serialized
 * provenance, instead of actually downloading the source code.
 */
private class FakeProvenanceDownloader(val filename: String = "fake.txt") : ProvenanceDownloader {
    override fun download(provenance: KnownProvenance): File {
        val file = createOrtTempDir().resolve(filename)
        file.writeText(yamlMapper.writeValueAsString(provenance))
        return file.parentFile
    }
}

/**
 * An implementation of [PackageProvenanceResolver] that returns the values from the package without performing any
 * validation.
 */
private class FakePackageProvenanceResolver : PackageProvenanceResolver {
    override fun resolveProvenance(pkg: Package, sourceCodeOriginPriority: List<SourceCodeOrigin>): KnownProvenance {
        sourceCodeOriginPriority.forEach { sourceCodeOrigin ->
            when (sourceCodeOrigin) {
                SourceCodeOrigin.ARTIFACT -> {
                    if (pkg.sourceArtifact != RemoteArtifact.EMPTY) {
                        return ArtifactProvenance(pkg.sourceArtifact)
                    }
                }

                SourceCodeOrigin.VCS -> {
                    if (pkg.vcsProcessed != VcsInfo.EMPTY) {
                        return RepositoryProvenance(pkg.vcsProcessed, "resolvedRevision")
                    }
                }
            }
        }

        throw IOException()
    }
}

/**
 * An implementation of [NestedProvenanceResolver] that always returns a non-nested provenance.
 */
private class FakeNestedProvenanceResolver : NestedProvenanceResolver {
    override fun resolveNestedProvenance(provenance: KnownProvenance): NestedProvenance =
        NestedProvenance(root = provenance, subRepositories = emptyMap())
}

/**
 * An implementation of [PackageBasedScanStorageReader] and [PackageBasedScanStorageWriter] that returns scan results
 * with a single license finding for the provided [scannerDetails].
 */
private class FakePackageBasedStorageReader(val scannerDetails: ScannerDetails) : PackageBasedScanStorageReader {
    override fun read(pkg: Package, nestedProvenance: NestedProvenance): List<NestedProvenanceScanResult> {
        val provenance = if (pkg.sourceArtifact != RemoteArtifact.EMPTY) {
            ArtifactProvenance(pkg.sourceArtifact)
        } else {
            RepositoryProvenance(pkg.vcsProcessed, pkg.vcsProcessed.revision)
        }

        return listOf(createStoredNestedScanResult(provenance, scannerDetails))
    }

    override fun read(
        pkg: Package,
        nestedProvenance: NestedProvenance,
        scannerCriteria: ScannerCriteria
    ): List<NestedProvenanceScanResult> = read(pkg, nestedProvenance)
}

private class FakeProvenanceBasedStorageReader(val scannerDetails: ScannerDetails) : ProvenanceBasedScanStorageReader {
    override fun read(provenance: KnownProvenance): List<ScanResult> =
        listOf(createStoredScanResult(provenance, scannerDetails))

    override fun read(provenance: KnownProvenance, scannerCriteria: ScannerCriteria): List<ScanResult> =
        read(provenance)
}

private class FakePackageBasedStorageWriter : PackageBasedScanStorageWriter {
    override fun write(pkg: Package, nestedProvenanceScanResult: NestedProvenanceScanResult) = Unit
}

private class FakeProvenanceBasedStorageWriter : ProvenanceBasedScanStorageWriter {
    override fun write(scanResult: ScanResult) = Unit
}

private fun createContext(
    labels: Map<String, String> = emptyMap(),
    type: PackageType = PackageType.PACKAGE
) = ScanContext(labels, type)

private fun createScanner(
    provenanceDownloader: ProvenanceDownloader = FakeProvenanceDownloader(),
    storageReaders: List<ScanStorageReader> = emptyList(),
    storageWriters: List<ScanStorageWriter> = emptyList(),
    packageProvenanceResolver: PackageProvenanceResolver = FakePackageProvenanceResolver(),
    nestedProvenanceResolver: NestedProvenanceResolver = FakeNestedProvenanceResolver(),
    packageScannerWrappers: List<ScannerWrapper> = emptyList(),
    projectScannerWrappers: List<ScannerWrapper> = emptyList()
) =
    ExperimentalScanner(
        ScannerConfiguration(archive = FileArchiverConfiguration(enabled = false)),
        DownloaderConfiguration(),
        provenanceDownloader,
        storageReaders,
        storageWriters,
        packageProvenanceResolver,
        nestedProvenanceResolver,
        mapOf(
            PackageType.PROJECT to projectScannerWrappers,
            PackageType.PACKAGE to packageScannerWrappers
        )
    )

private fun Package.Companion.new(type: String = "", group: String = "", name: String = "", version: String = "") =
    EMPTY.copy(id = Identifier(type, group, name, version))

private fun Package.artifactProvenance() = ArtifactProvenance(sourceArtifact)
private fun Package.repositoryProvenance() = RepositoryProvenance(vcsProcessed, "resolvedRevision")

private fun Package.withValidSourceArtifact() = copy(sourceArtifact = RemoteArtifact.valid())
private fun Package.withValidVcs() = copy(vcsProcessed = VcsInfo.valid())
private fun Package.withVcsRevision(revision: String) = copy(vcsProcessed = vcsProcessed.copy(revision = revision))

private fun RemoteArtifact.Companion.valid() =
    RemoteArtifact(
        url = "https://jitpack.io/com/github/oss-review-toolkit/ort/cli/f42e41a8fe/cli-f42e41a8fe-sources.jar",
        hash = Hash("7d41355bbe5315daa1414683b301838c", HashAlgorithm.MD5)
    )

private fun VcsInfo.Companion.valid() =
    VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort.git",
        revision = "f42e41a8fedc1e0acd78fab147e91fa047cb2853"
    )

private fun createScanSummary(licenseFindings: Set<LicenseFinding> = emptySet()) =
    ScanSummary(Instant.EPOCH, Instant.EPOCH, "", licenseFindings.toSortedSet(), sortedSetOf())

private fun createScanResult(
    provenance: Provenance,
    scannerDetails: ScannerDetails,
    licenseFindings: SortedSet<LicenseFinding> = sortedSetOf(
        LicenseFinding("Apache-2.0", TextLocation("${scannerDetails.name}.txt", 1, 2))
    )
) =
    ScanResult(
        provenance,
        scannerDetails,
        createScanSummary(licenseFindings)
    )

private fun createNestedScanResult(
    provenance: KnownProvenance,
    scannerDetails: ScannerDetails,
    subRepositories: Map<String, RepositoryProvenance> = emptyMap()
) =
    NestedProvenanceScanResult(
        NestedProvenance(root = provenance, subRepositories = subRepositories),
        scanResults = mapOf(
            provenance to listOf(createScanResult(provenance, scannerDetails))
        ) + subRepositories.values.associateWith { listOf(createScanResult(it, scannerDetails)) }
    )

private fun createStoredScanResult(provenance: Provenance, scannerDetails: ScannerDetails) =
    ScanResult(
        provenance,
        scannerDetails,
        createScanSummary(
            licenseFindings = sortedSetOf(
                LicenseFinding("Apache-2.0", TextLocation("storage.txt", 1, 2))
            )
        )
    )

private fun createStoredNestedScanResult(
    provenance: KnownProvenance,
    scannerDetails: ScannerDetails,
    subRepositories: Map<String, RepositoryProvenance> = emptyMap()
) =
    NestedProvenanceScanResult(
        NestedProvenance(root = provenance, subRepositories = subRepositories),
        scanResults = mapOf(
            provenance to listOf(createStoredScanResult(provenance, scannerDetails))
        ) + subRepositories.values.associateWith { listOf(createStoredScanResult(it, scannerDetails)) }
    )
