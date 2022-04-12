/*
 * Copyright (C) 2020-2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.spdx

import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.reporter.DefaultLicenseTextProvider
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.core.Environment
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.SpdxModelMapper.FileFormat
import org.ossreviewtoolkit.utils.spdx.SpdxModelMapper.fromJson
import org.ossreviewtoolkit.utils.spdx.SpdxModelMapper.fromYaml
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class SpdxDocumentReporterFunTest : WordSpec({
    "Reporting to JSON" should {
        "create a valid document" {
            val jsonMapper = FileFormat.JSON.mapper
            val schema = JsonSchemaFactory
                .builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
                .objectMapper(FileFormat.JSON.mapper)
                .build()
                .getSchema(File("src/funTest/assets/spdx-schema.json").toURI())

            val jsonSpdxDocument = generateReport(ortResult, FileFormat.JSON)
            val errors = schema.validate(jsonMapper.readTree(jsonSpdxDocument))

            errors should beEmpty()
        }

        "create the expected document" {
            val jsonSpdxDocument = generateReport(ortResult, FileFormat.JSON)

            jsonSpdxDocument shouldBe patchExpectedResult(
                "src/funTest/assets/spdx-document-reporter-expected-output.spdx.json",
                fromJson(jsonSpdxDocument)
            )
        }
    }

    "Reporting to YAML" should {
        "create the expected document" {
            val yamlSpdxDocument = generateReport(ortResult, FileFormat.YAML)

            yamlSpdxDocument shouldBe patchExpectedResult(
                "src/funTest/assets/spdx-document-reporter-expected-output.spdx.yml",
                fromYaml(yamlSpdxDocument)
            )
        }
    }
})

private fun TestConfiguration.generateReport(ortResult: OrtResult, format: FileFormat): String {
    val input = ReporterInput(
        ortResult = ortResult,
        licenseTextProvider = DefaultLicenseTextProvider()
    )

    val outputDir = createTestTempDir()

    val reportOptions = mapOf(
        SpdxDocumentReporter.OPTION_CREATION_INFO_COMMENT to "some creation info comment",
        SpdxDocumentReporter.OPTION_DOCUMENT_COMMENT to "some document comment",
        SpdxDocumentReporter.OPTION_DOCUMENT_NAME to "some document name",
        SpdxDocumentReporter.OPTION_OUTPUT_FILE_FORMATS to format.toString()
    )

    return SpdxDocumentReporter().generateReport(input, outputDir, reportOptions).single().readText()
        .normalizeLineBreaks()
}

private fun patchExpectedResult(expectedResultFile: String, actualSpdxDocument: SpdxDocument): String =
    patchExpectedResult(
        File(expectedResultFile),
        mapOf(
            "<REPLACE_LICENSE_LIST_VERSION>" to SpdxLicense.LICENSE_LIST_VERSION.substringBefore("-"),
            "<REPLACE_ORT_VERSION>" to Environment.ORT_VERSION,
            "<REPLACE_CREATION_DATE_AND_TIME>" to actualSpdxDocument.creationInfo.created.toString(),
            "<REPLACE_DOCUMENT_NAMESPACE>" to actualSpdxDocument.documentNamespace
        )
    )

private val analyzedVcs = VcsInfo(
    type = VcsType.GIT,
    revision = "master",
    url = "https://github.com/path/first-project.git",
    path = ""
)

private val ortResult = OrtResult(
    repository = Repository(
        config = RepositoryConfiguration(
            excludes = Excludes(
                scopes = listOf(
                    ScopeExclude(
                        pattern = "test",
                        reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                        comment = "Packages for testing only."
                    )
                )
            )
        ),
        vcs = analyzedVcs,
        vcsProcessed = analyzedVcs
    ),
    analyzer = AnalyzerRun(
        environment = Environment(),
        config = AnalyzerConfiguration(allowDynamicVersions = true),
        result = AnalyzerResult(
            projects = sortedSetOf(
                Project(
                    id = Identifier("Maven:first-project-group:first-project-name:0.0.1"),
                    declaredLicenses = sortedSetOf("MIT"),
                    definitionFilePath = "",
                    homepageUrl = "first project's homepage",
                    scopeDependencies = sortedSetOf(
                        Scope(
                            name = "compile",
                            dependencies = sortedSetOf(
                                PackageReference(
                                    id = Identifier("Maven:first-package-group:first-package:0.0.1")
                                ),
                                PackageReference(
                                    id = Identifier("Maven:second-package-group:second-package:0.0.1")
                                ),
                                PackageReference(
                                    id = Identifier("Maven:third-package-group:third-package:0.0.1")
                                ),
                                PackageReference(
                                    id = Identifier("Maven:fourth-package-group:fourth-package:0.0.1")
                                ),
                                PackageReference(
                                    id = Identifier("Maven:sixth-package-group:sixth-package:0.0.1")
                                )
                            )
                        ),
                        Scope(
                            name = "test",
                            dependencies = sortedSetOf(
                                PackageReference(
                                    id = Identifier("Maven:fifth-package-group:fifth-package:0.0.1")
                                )
                            )
                        )
                    ),
                    vcs = analyzedVcs
                )
            ),
            packages = sortedSetOf(
                CuratedPackage(
                    pkg = Package(
                        id = Identifier("Maven:first-package-group:first-package:0.0.1"),
                        binaryArtifact = RemoteArtifact("https://some-host/first-package.jar", Hash.NONE),
                        concludedLicense = "BSD-2-Clause AND BSD-3-Clause AND MIT".toSpdx(),
                        declaredLicenses = sortedSetOf("BSD-3-Clause", "MIT OR GPL-2.0-only"),
                        description = "A package with all supported attributes set, with a VCS URL containing a " +
                                "user name, and with a scan result containing two copyright finding matched to a " +
                                "license finding.",
                        homepageUrl = "first package's homepage URL",
                        sourceArtifact = RemoteArtifact("https://some-host/first-package-sources.jar", Hash.NONE),
                        vcs = VcsInfo(
                            type = VcsType.GIT,
                            revision = "master",
                            url = "ssh://git@github.com/path/first-package-repo.git",
                            path = "project-path"
                        )
                    )
                ),
                CuratedPackage(
                    pkg = Package(
                        id = Identifier("Maven:second-package-group:second-package:0.0.1"),
                        binaryArtifact = RemoteArtifact.EMPTY,
                        declaredLicenses = sortedSetOf(),
                        description = "A package with minimal attributes set.",
                        homepageUrl = "",
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                    )
                ),
                CuratedPackage(
                    pkg = Package(
                        id = Identifier("Maven:third-package-group:third-package:0.0.1"),
                        binaryArtifact = RemoteArtifact.EMPTY,
                        declaredLicenses = sortedSetOf("unmappable license"),
                        description = "A package with only unmapped declared license.",
                        homepageUrl = "",
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                    )
                ),
                CuratedPackage(
                    pkg = Package(
                        id = Identifier("Maven:fourth-package-group:fourth-package:0.0.1"),
                        binaryArtifact = RemoteArtifact.EMPTY,
                        declaredLicenses = sortedSetOf("unmappable license", "MIT"),
                        description = "A package with partially mapped declared license.",
                        homepageUrl = "",
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                    )
                ),
                CuratedPackage(
                    pkg = Package(
                        id = Identifier("Maven:fifth-package-group:fifth-package:0.0.1"),
                        binaryArtifact = RemoteArtifact.EMPTY,
                        declaredLicenses = sortedSetOf("LicenseRef-scancode-philips-proprietary-notice-2000"),
                        concludedLicense = "LicenseRef-scancode-purdue-bsd".toSpdx(),
                        description = "A package used only from the excluded 'test' scope, with non-SPDX license " +
                                "IDs in the declared and concluded license.",
                        homepageUrl = "",
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                    )
                ),
                CuratedPackage(
                    pkg = Package(
                        id = Identifier("Maven:sixth-package-group:sixth-package:0.0.1"),
                        binaryArtifact = RemoteArtifact.EMPTY,
                        declaredLicenses = sortedSetOf("LicenseRef-scancode-asmus"),
                        concludedLicense = "LicenseRef-scancode-srgb".toSpdx(),
                        description = "A package with non-SPDX license IDs in the declared and concluded license.",
                        homepageUrl = "",
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                    )
                )
            )
        )
    ),
    scanner = ScannerRun(
        environment = Environment(),
        config = ScannerConfiguration(),
        results = ScanRecord(
            scanResults = sortedMapOf(
                Identifier("Maven:first-package-group:first-package:0.0.1") to listOf(
                    ScanResult(
                        provenance = ArtifactProvenance(
                            sourceArtifact = RemoteArtifact(
                                url = "https://some-host/first-package-sources.jar",
                                hash = Hash.NONE
                            )
                        ),
                        scanner = ScannerDetails.EMPTY,
                        summary = ScanSummary(
                            startTime = Instant.MIN,
                            endTime = Instant.MIN,
                            packageVerificationCode = "0000000000000000000000000000000000000000",
                            licenseFindings = sortedSetOf(
                                LicenseFinding(
                                    license = "Apache-2.0",
                                    location = TextLocation("LICENSE", 1)
                                )
                            ),
                            copyrightFindings = sortedSetOf(
                                CopyrightFinding(
                                    statement = "Copyright 2020 Some copyright holder in source artifact",
                                    location = TextLocation("some/file", 1)
                                ),
                                CopyrightFinding(
                                    statement = "Copyright 2020 Some other copyright holder in source artifact",
                                    location = TextLocation("some/file", 7)
                                )
                            )
                        )
                    ),
                    ScanResult(
                        provenance = RepositoryProvenance(
                            vcsInfo = VcsInfo(
                                type = VcsType.GIT,
                                revision = "master",
                                url = "ssh://git@github.com/path/first-package-repo.git",
                                path = "project-path"
                            ),
                            resolvedRevision = "deadbeef"
                        ),
                        scanner = ScannerDetails.EMPTY,
                        summary = ScanSummary(
                            startTime = Instant.MIN,
                            endTime = Instant.MIN,
                            packageVerificationCode = "0000000000000000000000000000000000000000",
                            licenseFindings = sortedSetOf(
                                LicenseFinding(
                                    license = "BSD-2-Clause",
                                    location = TextLocation("LICENSE", 1)
                                )
                            ),
                            copyrightFindings = sortedSetOf(
                                CopyrightFinding(
                                    statement = "Copyright 2020 Some copyright holder in VCS",
                                    location = TextLocation("some/file", 1)
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
