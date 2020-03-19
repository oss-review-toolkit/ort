/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration
import org.ossreviewtoolkit.model.config.FileStorageConfiguration
import org.ossreviewtoolkit.model.config.LocalFileStorageConfiguration
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.LICENSE_FILENAMES
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

private fun generateReport(
    ortResult: OrtResult,
    config: OrtConfiguration = OrtConfiguration(),
    copyrightGarbage: CopyrightGarbage = CopyrightGarbage(),
    preProcessingScript: String? = null
): String =
    ByteArrayOutputStream().also { outputStream ->
        NoticeByPackageReporter().generateReport(
            outputStream,
            ReporterInput(
                ortResult,
                config,
                copyrightGarbage = copyrightGarbage,
                preProcessingScript = preProcessingScript
            )
        )
    }.toString("UTF-8")

class NoticeByPackageReporterTest : WordSpec({
    // TODO: Create a way to reduce the code required to prepare an OrtResult for testing.
    val ortResult = OrtResult(
        repository = Repository(VcsInfo.EMPTY),
        analyzer = AnalyzerRun(
            environment = Environment(),
            config = DEFAULT_ANALYZER_CONFIGURATION,
            result = AnalyzerResult(
                projects = sortedSetOf(
                    Project(
                        id = Identifier("NPM:@ort:project-with-findings:1.0"),
                        definitionFilePath = "project-with-findings/package.json",
                        declaredLicenses = sortedSetOf(),
                        vcs = VcsInfo.EMPTY,
                        homepageUrl = "",
                        scopes = sortedSetOf(
                            Scope(
                                name = "dependencies",
                                dependencies = sortedSetOf(
                                    PackageReference(
                                        id = Identifier("NPM:@ort:no-license-file:1.0")
                                    ),
                                    PackageReference(
                                        id = Identifier("NPM:@ort:license-file:1.0")
                                    ),
                                    PackageReference(
                                        id = Identifier("NPM:@ort:license-file-and-additional-licenses:1.0")
                                    )
                                )
                            )
                        )
                    ),
                    Project(
                        id = Identifier("NPM:@ort:project-without-findings:1.0"),
                        definitionFilePath = "project-without-findings/package.json",
                        declaredLicenses = sortedSetOf(),
                        vcs = VcsInfo.EMPTY,
                        homepageUrl = "",
                        scopes = sortedSetOf(
                            Scope(
                                name = "dependencies",
                                dependencies = sortedSetOf()
                            )
                        )
                    )
                ),
                packages = sortedSetOf(
                    CuratedPackage(
                        pkg = Package(
                            id = Identifier("NPM:@ort:no-license-file:1.0"),
                            declaredLicenses = sortedSetOf(),
                            description = "",
                            homepageUrl = "",
                            binaryArtifact = RemoteArtifact.EMPTY,
                            sourceArtifact = RemoteArtifact.EMPTY,
                            vcs = VcsInfo.EMPTY
                        ),
                        curations = emptyList()
                    ),
                    CuratedPackage(
                        pkg = Package(
                            id = Identifier("NPM:@ort:license-file:1.0"),
                            declaredLicenses = sortedSetOf(),
                            description = "",
                            homepageUrl = "",
                            binaryArtifact = RemoteArtifact.EMPTY,
                            sourceArtifact = RemoteArtifact(
                                url = "https://example.com/license-file-1.0.tgz",
                                hash = Hash(value = "", algorithm = HashAlgorithm.SHA1)
                            ),
                            vcs = VcsInfo.EMPTY
                        ),
                        curations = emptyList()
                    ),
                    CuratedPackage(
                        pkg = Package(
                            id = Identifier("NPM:@ort:license-file-and-additional-licenses:1.0"),
                            declaredLicenses = sortedSetOf(),
                            description = "",
                            homepageUrl = "",
                            binaryArtifact = RemoteArtifact.EMPTY,
                            sourceArtifact = RemoteArtifact(
                                url = "https://example.com/license-file-and-additional-licenses-1.0.tgz",
                                hash = Hash(value = "", algorithm = HashAlgorithm.SHA1)
                            ),
                            vcs = VcsInfo.EMPTY
                        ),
                        curations = emptyList()
                    )
                )
            )
        ),
        scanner = ScannerRun(
            environment = Environment(),
            config = ScannerConfiguration(),
            results = ScanRecord(
                scanResults = sortedSetOf(
                    ScanResultContainer(
                        id = Identifier("NPM:@ort:project-with-findings:1.0"),
                        results = listOf(
                            ScanResult(
                                provenance = Provenance(sourceArtifact = RemoteArtifact.EMPTY),
                                scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                                summary = ScanSummary(
                                    startTime = Instant.EPOCH,
                                    endTime = Instant.EPOCH,
                                    fileCount = 0,
                                    packageVerificationCode = "",
                                    licenseFindings = sortedSetOf(
                                        LicenseFinding(
                                            license = "MIT",
                                            location = TextLocation("project-with-findings/file", 1, 1)
                                        )
                                    ),
                                    copyrightFindings = sortedSetOf(
                                        CopyrightFinding(
                                            statement = "Copyright 1",
                                            location = TextLocation("project-with-findings/file", 1, 1)
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    ScanResultContainer(
                        id = Identifier("NPM:@ort:project-without-findings:1.0"),
                        results = listOf(
                            ScanResult(
                                provenance = Provenance(sourceArtifact = RemoteArtifact.EMPTY),
                                scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                                summary = ScanSummary(
                                    startTime = Instant.EPOCH,
                                    endTime = Instant.EPOCH,
                                    fileCount = 0,
                                    packageVerificationCode = "",
                                    licenseFindings = sortedSetOf(),
                                    copyrightFindings = sortedSetOf()
                                )
                            )
                        )
                    ),
                    ScanResultContainer(
                        id = Identifier("NPM:@ort:no-license-file:1.0"),
                        results = listOf(
                            ScanResult(
                                provenance = Provenance(sourceArtifact = RemoteArtifact.EMPTY),
                                scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                                summary = ScanSummary(
                                    startTime = Instant.EPOCH,
                                    endTime = Instant.EPOCH,
                                    fileCount = 0,
                                    packageVerificationCode = "",
                                    licenseFindings = sortedSetOf(
                                        LicenseFinding(
                                            license = "MIT",
                                            location = TextLocation("file", 1, 1)
                                        )
                                    ),
                                    copyrightFindings = sortedSetOf(
                                        CopyrightFinding(
                                            statement = "Copyright 1",
                                            location = TextLocation("file", 1, 1)
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    ScanResultContainer(
                        id = Identifier("NPM:@ort:no-license-file:1.0"),
                        results = listOf(
                            ScanResult(
                                provenance = Provenance(sourceArtifact = RemoteArtifact.EMPTY),
                                scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                                summary = ScanSummary(
                                    startTime = Instant.EPOCH,
                                    endTime = Instant.EPOCH,
                                    fileCount = 0,
                                    packageVerificationCode = "",
                                    licenseFindings = sortedSetOf(
                                        LicenseFinding(
                                            license = "MIT",
                                            location = TextLocation("file", 1, 1)
                                        )
                                    ),
                                    copyrightFindings = sortedSetOf(
                                        CopyrightFinding(
                                            statement = "Copyright 1",
                                            location = TextLocation("file", 1, 1)
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    ScanResultContainer(
                        id = Identifier("NPM:@ort:license-file:1.0"),
                        results = listOf(
                            ScanResult(
                                provenance = Provenance(
                                    sourceArtifact = RemoteArtifact(
                                        url = "https://example.com/license-file-1.0.tgz",
                                        hash = Hash(value = "", algorithm = HashAlgorithm.SHA1)
                                    )
                                ),
                                scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                                summary = ScanSummary(
                                    startTime = Instant.EPOCH,
                                    endTime = Instant.EPOCH,
                                    fileCount = 0,
                                    packageVerificationCode = "",
                                    licenseFindings = sortedSetOf(
                                        LicenseFinding(
                                            license = "MIT",
                                            location = TextLocation("LICENSE", 1, 1)
                                        ),
                                        LicenseFinding(
                                            license = "MIT",
                                            location = TextLocation("file", 1, 1)
                                        )
                                    ),
                                    copyrightFindings = sortedSetOf(
                                        CopyrightFinding(
                                            statement = "Copyright 1",
                                            location = TextLocation("LICENSE", 1, 1)
                                        ),
                                        CopyrightFinding(
                                            statement = "Copyright 2",
                                            location = TextLocation("file", 1, 1)
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    ScanResultContainer(
                        id = Identifier("NPM:@ort:license-file-and-additional-licenses:1.0"),
                        results = listOf(
                            ScanResult(
                                provenance = Provenance(
                                    sourceArtifact = RemoteArtifact(
                                        url = "https://example.com/license-file-and-additional-licenses-1.0.tgz",
                                        hash = Hash(value = "", algorithm = HashAlgorithm.SHA1)
                                    )
                                ),
                                scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                                summary = ScanSummary(
                                    startTime = Instant.EPOCH,
                                    endTime = Instant.EPOCH,
                                    fileCount = 0,
                                    packageVerificationCode = "",
                                    licenseFindings = sortedSetOf(
                                        LicenseFinding(
                                            license = "MIT",
                                            location = TextLocation("LICENSE", 1, 1)
                                        ),
                                        LicenseFinding(
                                            license = "MIT",
                                            location = TextLocation("file", 1, 1)
                                        ),
                                        LicenseFinding(
                                            license = "BSD-3-Clause",
                                            location = TextLocation("file", 50, 50)
                                        )
                                    ),
                                    copyrightFindings = sortedSetOf(
                                        CopyrightFinding(
                                            statement = "Copyright 1",
                                            location = TextLocation("LICENSE", 1, 1)
                                        ),
                                        CopyrightFinding(
                                            statement = "Copyright 2",
                                            location = TextLocation("file", 1, 1)
                                        ),
                                        CopyrightFinding(
                                            statement = "Copyright 3",
                                            location = TextLocation("file", 50, 50)
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),
                storageStats = AccessStatistics()
            )
        )
    )

    "NoticeByPackageReporter" should {
        "generate the correct license notes" {
            val expectedText = File("src/funTest/assets/notice-by-package-reporter-expected-results").readText()

            val report = generateReport(ortResult)

            report shouldBe expectedText
        }

        "generate the correct license notes with archived license files" {
            val expectedText =
                File("src/funTest/assets/notice-by-package-reporter-expected-results-with-license-files").readText()

            val archiveDir = File("src/funTest/assets/archive")
            val config = OrtConfiguration(
                ScannerConfiguration(
                    archive = FileArchiverConfiguration(
                        patterns = LICENSE_FILENAMES,
                        storage = FileStorageConfiguration(
                            localFileStorage = LocalFileStorageConfiguration(
                                directory = archiveDir,
                                compression = false
                            )
                        )
                    )
                )
            )
            val report = generateReport(ortResult, config)

            report shouldBe expectedText
        }
    }
})
