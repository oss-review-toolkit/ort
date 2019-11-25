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

package com.here.ort.reporter.reporters

import com.here.ort.model.AccessStatistics
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.AnalyzerRun
import com.here.ort.model.CopyrightFinding
import com.here.ort.model.CuratedPackage
import com.here.ort.model.Environment
import com.here.ort.model.Hash
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.LicenseFinding
import com.here.ort.model.OrtResult
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.Provenance
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Repository
import com.here.ort.model.ScanRecord
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScanSummary
import com.here.ort.model.ScannerDetails
import com.here.ort.model.ScannerRun
import com.here.ort.model.Scope
import com.here.ort.model.TextLocation
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.config.FileArchiverConfiguration
import com.here.ort.model.config.FileStorageConfiguration
import com.here.ort.model.config.LocalFileStorageConfiguration
import com.here.ort.model.config.OrtConfiguration
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.spdx.LICENSE_FILENAMES
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

private fun generateReport(
    ortResult: OrtResult,
    config: OrtConfiguration = OrtConfiguration(),
    copyrightGarbage: CopyrightGarbage = CopyrightGarbage(),
    postProcessingScript: String? = null
): String =
    ByteArrayOutputStream().also { outputStream ->
        NoticeByPackageReporter().generateReport(
            outputStream,
            ortResult,
            config,
            copyrightGarbage = copyrightGarbage,
            postProcessingScript = postProcessingScript
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
                scopes = sortedSetOf(),
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
                                archiveDir
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
