/*
 * Copyright (C) 2021 HERE Europe B.V.
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
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.utils.test.containExactly

class ExperimentalScannerTest : WordSpec({
    "Creating the experimental scanner" should {
        "throw an exception if no scanner wrappers are provided" {
            shouldThrow<IllegalArgumentException> {
                createScanner()
            }
        }
    }

    "Scanning with a package based remote scanner" should {
        "return a scan result for each package" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(id = "repository").withValidVcs()
            val scannerWrapper = spyk(FakePackageBasedRemoteScannerWrapper())
            val scanner = createScanner(scannerWrappers = listOf(scannerWrapper))

            every { scannerWrapper.scanPackage(pkgWithArtifact) } returns
                    createRemoteScanResult(pkgWithArtifact.artifactProvenance(), scannerWrapper.details)
            every { scannerWrapper.scanPackage(pkgWithVcs) } returns
                    createRemoteScanResult(pkgWithVcs.repositoryProvenance(), scannerWrapper.details)

            val result = scanner.scan(setOf(pkgWithArtifact, pkgWithVcs))

            result should containExactly(
                pkgWithArtifact to createRemoteNestedScanResult(
                    provenance = pkgWithArtifact.artifactProvenance(),
                    scannerDetails = scannerWrapper.details
                ),
                pkgWithVcs to createRemoteNestedScanResult(
                    provenance = pkgWithVcs.repositoryProvenance(),
                    scannerDetails = scannerWrapper.details
                )
            )

            verify(exactly = 1) {
                scannerWrapper.scanPackage(pkgWithArtifact)
                scannerWrapper.scanPackage(pkgWithVcs)
            }
        }

        "not try to download the source code" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(id = "repository").withValidVcs()
            val provenanceDownloader = mockk<ProvenanceDownloader>()
            val scannerWrapper = FakePackageBasedRemoteScannerWrapper()
            val scanner = createScanner(
                provenanceDownloader = provenanceDownloader,
                scannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact, pkgWithVcs))

            verify(exactly = 0) { provenanceDownloader.download(any(), any()) }
        }
    }

    "Scanning with a provenance based remote scanner" should {
        "return a scan result for each provenance" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(id = "repository").withValidVcs()
            val scannerWrapper = spyk(FakeProvenanceBasedRemoteScannerWrapper())
            val scanner = createScanner(scannerWrappers = listOf(scannerWrapper))

            val result = scanner.scan(setOf(pkgWithArtifact, pkgWithVcs))

            result should containExactly(
                pkgWithArtifact to createRemoteNestedScanResult(
                    provenance = pkgWithArtifact.artifactProvenance(),
                    scannerDetails = scannerWrapper.details
                ),
                pkgWithVcs to createRemoteNestedScanResult(
                    provenance = pkgWithVcs.repositoryProvenance(),
                    scannerDetails = scannerWrapper.details
                )
            )

            verify(exactly = 1) {
                scannerWrapper.scanProvenance(pkgWithArtifact.artifactProvenance())
                scannerWrapper.scanProvenance(pkgWithVcs.repositoryProvenance())
            }
        }

        "not try to download the source code" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(id = "repository").withValidVcs()
            val provenanceDownloader = mockk<ProvenanceDownloader>()
            val scannerWrapper = FakeProvenanceBasedRemoteScannerWrapper()
            val scanner = createScanner(
                provenanceDownloader = provenanceDownloader,
                scannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact, pkgWithVcs))

            verify(exactly = 0) { provenanceDownloader.download(any(), any()) }
        }
    }

    "scanning with a local scanner" should {
        "return a scan result for each provenance" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(id = "repository").withValidVcs()
            val scannerWrapper = spyk(FakeLocalScannerWrapper())
            val scanner = createScanner(scannerWrappers = listOf(scannerWrapper))

            val result = scanner.scan(setOf(pkgWithArtifact, pkgWithVcs))

            result should containExactly(
                pkgWithArtifact to createLocalNestedScanResult(
                    provenance = pkgWithArtifact.artifactProvenance(),
                    scannerDetails = scannerWrapper.details
                ),
                pkgWithVcs to createLocalNestedScanResult(
                    provenance = pkgWithVcs.repositoryProvenance(),
                    scannerDetails = scannerWrapper.details
                )
            )

            verify(exactly = 2) {
                scannerWrapper.scanPath(any())
            }
        }

        "try to download the source code" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val pkgWithVcs = Package.new(id = "repository").withValidVcs()
            val provenanceDownloader = spyk(FakeProvenanceDownloader())
            val scannerWrapper = FakeLocalScannerWrapper()
            val scanner = createScanner(
                provenanceDownloader = provenanceDownloader,
                scannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact, pkgWithVcs))

            verify(exactly = 1) {
                provenanceDownloader.download(pkgWithArtifact.artifactProvenance(), any())
                provenanceDownloader.download(pkgWithVcs.repositoryProvenance(), any())
            }
        }
    }

    "scanning with a package based storage reader" should {
        "prefer a stored result over a new scan" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val scannerWrapper = spyk(FakePackageBasedRemoteScannerWrapper())
            val reader = spyk(FakePackageBasedStorageReader(scannerWrapper.details))

            every { reader.read(pkgWithArtifact) } returns listOf(
                createStoredNestedScanResult(pkgWithArtifact.artifactProvenance(), scannerWrapper.details)
            )

            val scanner = createScanner(
                storageReaders = listOf(reader),
                scannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgWithArtifact))

            result should containExactly(
                pkgWithArtifact to createStoredNestedScanResult(
                    provenance = pkgWithArtifact.artifactProvenance(),
                    scannerDetails = scannerWrapper.details
                )
            )

            verify(exactly = 0) {
                scannerWrapper.scanPackage(pkgWithArtifact)
            }
        }

        "start a scan if no stored result is available" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val scannerWrapper = spyk(FakePackageBasedRemoteScannerWrapper())
            val reader = spyk(FakePackageBasedStorageReader(scannerWrapper.details))

            every { reader.read(any()) } returns emptyList()

            val scanner = createScanner(
                storageReaders = listOf(reader),
                scannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgWithArtifact))

            result should containExactly(
                pkgWithArtifact to createRemoteNestedScanResult(
                    provenance = pkgWithArtifact.artifactProvenance(),
                    scannerDetails = scannerWrapper.details
                )
            )

            verify(exactly = 1) {
                reader.read(pkgWithArtifact)
                scannerWrapper.scanPackage(pkgWithArtifact)
            }
        }

        "scan only the parts of a nested provenance for which no stored result is available" {
            val pkgCompletelyScanned = Package.new(id = "completely").withValidVcs().withVcsRevision("revision1")
            val pkgPartlyScanned = Package.new(id = "partly").withValidVcs().withVcsRevision("revision2")

            val scannerWrapper = spyk(FakeProvenanceBasedRemoteScannerWrapper())

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

            val nestedScanResultCompletelyScanned = createRemoteNestedScanResult(
                provenance = nestedProvenanceCompletelyScanned.root,
                scannerDetails = scannerWrapper.details,
                subRepositories = nestedProvenanceCompletelyScanned.subRepositories
            )
            val nestedScanResultPartlyScanned = createRemoteNestedScanResult(
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

            every { reader.read(pkgCompletelyScanned) } returns listOf(nestedScanResultCompletelyScanned)

            val scanner = createScanner(
                storageReaders = listOf(reader),
                nestedProvenanceResolver = nestedProvenanceResolver,
                scannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgCompletelyScanned, pkgPartlyScanned))

            result should containExactly(
                pkgCompletelyScanned to nestedScanResultCompletelyScanned,
                pkgPartlyScanned to nestedScanResultPartlyScanned
            )

            verify(exactly = 0) {
                scannerWrapper.scanProvenance(scannedSubRepository)
            }

            verify(exactly = 1) {
                scannerWrapper.scanProvenance(unscannedSubRepository)
            }
        }
    }

    "scanning with a provenance based storage reader" should {
        "prefer a stored result over a new scan" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val scannerWrapper = spyk(FakeProvenanceBasedRemoteScannerWrapper())
            val reader = spyk(FakeProvenanceBasedStorageReader(scannerWrapper.details))

            every { reader.read(pkgWithArtifact.artifactProvenance()) } returns listOf(
                createStoredScanResult(pkgWithArtifact.artifactProvenance(), scannerWrapper.details)
            )

            val scanner = createScanner(
                storageReaders = listOf(reader),
                scannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgWithArtifact))

            result should containExactly(
                pkgWithArtifact to createStoredNestedScanResult(
                    pkgWithArtifact.artifactProvenance(),
                    scannerWrapper.details
                )
            )

            verify(exactly = 0) {
                scannerWrapper.scanProvenance(pkgWithArtifact.artifactProvenance())
            }
        }

        "start a scan if no stored result is available" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val scannerWrapper = spyk(FakeProvenanceBasedRemoteScannerWrapper())
            val reader = spyk(FakeProvenanceBasedStorageReader(scannerWrapper.details))

            every { reader.read(any()) } returns emptyList()

            val scanner = createScanner(
                storageReaders = listOf(reader),
                scannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgWithArtifact))

            result should containExactly(
                pkgWithArtifact to createRemoteNestedScanResult(
                    pkgWithArtifact.artifactProvenance(),
                    scannerWrapper.details
                )
            )

            verify(exactly = 1) {
                reader.read(pkgWithArtifact.artifactProvenance())
                scannerWrapper.scanProvenance(pkgWithArtifact.artifactProvenance())
            }
        }

        "scan only the parts of a nested provenance for which no stored result is available" {
            val pkgCompletelyScanned = Package.new(id = "completely").withValidVcs().withVcsRevision("revision1")
            val pkgPartlyScanned = Package.new(id = "partly").withValidVcs().withVcsRevision("revision2")

            val scannerWrapper = spyk(FakeProvenanceBasedRemoteScannerWrapper())

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
                        createRemoteScanResult(unscannedSubRepository, scannerWrapper.details)
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
                scannerWrappers = listOf(scannerWrapper)
            )

            val result = scanner.scan(setOf(pkgCompletelyScanned, pkgPartlyScanned))

            result should containExactly(
                pkgCompletelyScanned to nestedScanResultCompletelyScanned,
                pkgPartlyScanned to nestedScanResultPartlyScanned
            )

            verify(exactly = 0) {
                scannerWrapper.scanProvenance(scannedSubRepository)
            }

            verify(exactly = 1) {
                scannerWrapper.scanProvenance(unscannedSubRepository)
            }
        }
    }

    "scanning with a package based storage writer" should {
        "store the scan result" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val scannerWrapper = FakePackageBasedRemoteScannerWrapper()
            val writer = spyk(FakePackageBasedStorageWriter())

            val scanner = createScanner(
                storageWriters = listOf(writer),
                scannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact))

            verify(exactly = 1) {
                writer.write(
                    pkgWithArtifact,
                    createRemoteNestedScanResult(pkgWithArtifact.artifactProvenance(), scannerWrapper.details)
                )
            }
        }

        "not try to store the scan result if it was retrieved from a storage" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val scannerWrapper = FakePackageBasedRemoteScannerWrapper()
            val reader = FakePackageBasedStorageReader(scannerWrapper.details)
            val writer = spyk(FakePackageBasedStorageWriter())

            val scanner = createScanner(
                storageReaders = listOf(reader),
                storageWriters = listOf(writer),
                scannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact))

            verify(exactly = 0) {
                writer.write(any(), any())
            }
        }
    }

    "scanning with a provenance based storage writer" should {
        "store the scan result" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val scannerWrapper = FakePackageBasedRemoteScannerWrapper()
            val writer = spyk(FakeProvenanceBasedStorageWriter())

            val scanner = createScanner(
                storageWriters = listOf(writer),
                scannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact))

            verify(exactly = 1) {
                writer.write(createRemoteScanResult(pkgWithArtifact.artifactProvenance(), scannerWrapper.details))
            }
        }

        "not try to store the scan result if it was retrieved from a storage" {
            val pkgWithArtifact = Package.new(id = "artifact").withValidSourceArtifact()
            val scannerWrapper = FakePackageBasedRemoteScannerWrapper()
            val reader = FakePackageBasedStorageReader(scannerWrapper.details)
            val writer = spyk(FakeProvenanceBasedStorageWriter())

            val scanner = createScanner(
                storageReaders = listOf(reader),
                storageWriters = listOf(writer),
                scannerWrappers = listOf(scannerWrapper)
            )

            scanner.scan(setOf(pkgWithArtifact))

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
 * An implementation of [PackageBasedRemoteScannerWrapper] that creates empty scan results.
 */
private class FakePackageBasedRemoteScannerWrapper(
    val packageProvenanceResolver: PackageProvenanceResolver = FakePackageProvenanceResolver(),
    val sourceCodeOriginPriority: List<SourceCodeOrigin> = listOf(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT)
) : PackageBasedRemoteScannerWrapper {
    override val details: ScannerDetails = ScannerDetails("fake", "1.0.0", "config")
    override val name: String = details.name
    override val criteria: ScannerCriteria = details.toCriteria()

    override fun scanPackage(pkg: Package): ScanResult =
        createRemoteScanResult(packageProvenanceResolver.resolveProvenance(pkg, sourceCodeOriginPriority), details)
}

/**
 * An implementation of [ProvenanceBasedRemoteScannerWrapper] that creates empty scan results.
 */
private class FakeProvenanceBasedRemoteScannerWrapper : ProvenanceBasedRemoteScannerWrapper {
    override val details: ScannerDetails = ScannerDetails("fake", "1.0.0", "config")
    override val name: String = details.name
    override val criteria: ScannerCriteria = details.toCriteria()

    override fun scanProvenance(provenance: KnownProvenance): ScanResult =
        createRemoteScanResult(provenance, details)
}

/**
 * An implementation of [LocalScannerWrapper] that creates scan results with one license finding for each file.
 */
private class FakeLocalScannerWrapper : LocalScannerWrapper {
    override val details: ScannerDetails = ScannerDetails("fake", "1.0.0", "config")
    override val name: String = details.name
    override val criteria: ScannerCriteria = details.toCriteria()

    override fun scanPath(path: File): ScanSummary {
        val licenseFindings = path.walk().filter { it.isFile }.map { file ->
            LicenseFinding("Apache-2.0", TextLocation(file.relativeTo(path).path, 1, 2))
        }.toSortedSet()

        return createScanSummary(licenseFindings = licenseFindings)
    }
}

/**
 * An implementation of [ProvenanceDownloader] that creates a file called "provenance.txt" containing the serialized
 * provenance, instead of actually downloading the source code.
 */
private class FakeProvenanceDownloader : ProvenanceDownloader {
    override fun download(provenance: KnownProvenance, downloadDir: File) {
        // TODO: Should downloadDir be created if it does not exist?
        val file = downloadDir.resolve("provenance.txt")
        file.writeText(yamlMapper.writeValueAsString(provenance))
    }
}

/**
 * An implementation of [PackageProvenanceResolver] that returns the values from the package without performing any
 * validation.
 */
private class FakePackageProvenanceResolver : PackageProvenanceResolver {
    override fun resolveProvenance(pkg: Package, sourceCodeOriginPriority: List<SourceCodeOrigin>): Provenance {
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

        return UnknownProvenance
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
    override fun read(pkg: Package): List<NestedProvenanceScanResult> {
        val provenance = if (pkg.sourceArtifact != RemoteArtifact.EMPTY) {
            ArtifactProvenance(pkg.sourceArtifact)
        } else {
            RepositoryProvenance(pkg.vcsProcessed, pkg.vcsProcessed.revision)
        }

        return listOf(createStoredNestedScanResult(provenance, scannerDetails))
    }

    override fun read(pkg: Package, scannerCriteria: ScannerCriteria): List<NestedProvenanceScanResult> = read(pkg)
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

@Suppress("LongParameterList")
private fun createScanner(
    scannerConfig: ScannerConfiguration = ScannerConfiguration(),
    downloaderConfig: DownloaderConfiguration = DownloaderConfiguration(),
    provenanceDownloader: ProvenanceDownloader = FakeProvenanceDownloader(),
    storageReaders: List<ScanStorageReader> = emptyList(),
    storageWriters: List<ScanStorageWriter> = emptyList(),
    packageProvenanceResolver: PackageProvenanceResolver = FakePackageProvenanceResolver(),
    nestedProvenanceResolver: NestedProvenanceResolver = FakeNestedProvenanceResolver(),
    scannerWrappers: List<ScannerWrapper> = emptyList()
) =
    ExperimentalScanner(
        scannerConfig,
        downloaderConfig,
        provenanceDownloader,
        storageReaders,
        storageWriters,
        packageProvenanceResolver,
        nestedProvenanceResolver,
        scannerWrappers
    )

private fun ScannerDetails.toCriteria() =
    ScannerCriteria(
        regScannerName = name,
        minVersion = Semver(version),
        maxVersion = Semver(version),
        configMatcher = { true }
    )

private fun Package.Companion.new(type: String = "", group: String = "", id: String = "", version: String = "") =
    EMPTY.copy(id = Identifier(type, group, id, version))

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

private fun createRemoteScanResult(provenance: Provenance, scannerDetails: ScannerDetails) =
    ScanResult(
        provenance,
        scannerDetails,
        createScanSummary(
            licenseFindings = sortedSetOf(
                LicenseFinding("Apache-2.0", TextLocation("remote.txt", 1, 2))
            )
        )
    )

private fun createRemoteNestedScanResult(
    provenance: KnownProvenance,
    scannerDetails: ScannerDetails,
    subRepositories: Map<String, RepositoryProvenance> = emptyMap()
) =
    NestedProvenanceScanResult(
        NestedProvenance(root = provenance, subRepositories = subRepositories),
        scanResults = mapOf(
            provenance to listOf(createRemoteScanResult(provenance, scannerDetails))
        ) + subRepositories.values.associateWith { listOf(createRemoteScanResult(it, scannerDetails)) }
    )

private fun createLocalScanResult(provenance: Provenance, scannerDetails: ScannerDetails) =
    ScanResult(
        provenance,
        scannerDetails,
        createScanSummary(
            licenseFindings = setOf(
                LicenseFinding("Apache-2.0", TextLocation("provenance.txt", 1, 2))
            )
        )
    )

private fun createLocalNestedScanResult(provenance: KnownProvenance, scannerDetails: ScannerDetails) =
    NestedProvenanceScanResult(
        NestedProvenance(root = provenance, subRepositories = emptyMap()),
        scanResults = mapOf(
            provenance to listOf(createLocalScanResult(provenance, scannerDetails))
        )
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
