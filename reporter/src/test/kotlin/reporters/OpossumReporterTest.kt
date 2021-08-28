/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 TNG Technology Consulting GmbH
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

package org.ossreviewtoolkit.reporter.reporters

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.*
import org.ossreviewtoolkit.spdx.toSpdx
import org.ossreviewtoolkit.utils.Environment

import java.io.File
import java.time.Instant

class OpossumReporterTest : WordSpec({
    "generateOpossumInput()" should {
        val result = createOrtResult()
        val opossumInput = OpossumReporter().generateOpossumInput(result)

        "should be somehow valid" {
            opossumInput shouldNot beNull()
            opossumInput.resources shouldNot beNull()
            opossumInput.signals shouldNot beNull()
            opossumInput.pathToSignal shouldNot beNull()
            opossumInput.packageToRoot shouldNot beNull()
            opossumInput.attributionBreakpoints shouldNot beNull()
        }

        val fileList = opossumInput.resources.toFileList()

        "file list should contain some specific files" {
            fileList shouldContain File("")
            fileList shouldContain File("pom.xml/compile/first-package-group/first-package@0.0.1/LICENSE")
            fileList shouldContain File("npm-project/package.json/devDependencies/@something/somepackage@1.2.3/dependencies/@something/somepackage-dep@1.2.3/dependencies/@something/somepackage-dep-dep@1.2.3/dependencies/@something/somepackage-dep-dep-dep@1.2.3/dependencies")
        }

        "file list should contain files from other lists" {
            opossumInput.pathToSignal.forEach { e -> fileList shouldContain e.key }
            opossumInput.attributionBreakpoints.forEach { fileList shouldContain it }
            opossumInput.packageToRoot.forEach { it.value.forEach { fileList shouldContain it } }
        }

        "every package should be found in a signal" {
            result.analyzer?.result?.packages?.forEach {
                val id = it.pkg.id
                opossumInput.signals.find { it.id == id } shouldNot beNull()
            }
        }

        "LICENSE File added by SCANNER report should have signal with license" {
            val signals = opossumInput.getSignalsForFile(
                File("pom.xml/compile/first-package-group/first-package@0.0.1/LICENSE")
            )
            signals.size shouldBe 2
            val signal = signals
                .find { it.source == "ORT-Scanner-SCANNER@1.2.3" }
            signal shouldNot beNull()
            signal!!.license.toString() shouldBe "Apache-2.0"
        }

        "some/file File added by SCANNER report should have signal with copyright" {
            val signals = opossumInput.getSignalsForFile(
                File("pom.xml/compile/first-package-group/first-package@0.0.1/some/file")
            )
            signals.size shouldBe 2
            val signal = signals
                .find { it.source == "ORT-Scanner-SCANNER@1.2.3" }
            signal shouldNot beNull()
            signal!!.copyright shouldContain "Copyright 2020 Some copyright holder in source artifact"
            signal.copyright shouldContain "Copyright 2020 Some other copyright holder in source artifact"
        }


        "all uuids from signals should be assigned" {
            opossumInput.pathToSignal.forEach { e ->
                e.value.forEach { uuid ->
                    opossumInput.signals.find { it.uuid == uuid } shouldNot beNull()
                }
            }
        }

        val opossumInputJson = opossumInput.toJson()
        "opossumInput JSON should have expected top level entries" {
            opossumInputJson.keys shouldContain "metadata"
            (opossumInputJson["metadata"] as Map<*,*>).keys shouldContain "projectId"
            opossumInputJson.keys shouldContain "resources"
            opossumInputJson.keys shouldContain "externalAttributions"
            opossumInputJson.keys shouldContain "resourcesToAttributions"
            opossumInputJson.keys shouldContain "attributionBreakpoints"
        }
    }
})

@Suppress("LongMethod")
private fun createOrtResult(): OrtResult {
    val analyzedVcs = VcsInfo(
        type = VcsType.GIT,
        revision = "master",
        url = "https://github.com/path/first-project.git",
        path = ""
    )

    return OrtResult(
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
            config = AnalyzerConfiguration(ignoreToolVersions = true, allowDynamicVersions = true),
            result = AnalyzerResult(
                projects = sortedSetOf(
                    Project(
                        id = Identifier("Maven:first-project-group:first-project-name:0.0.1"),
                        declaredLicenses = sortedSetOf("MIT"),
                        definitionFilePath = "pom.xml",
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
                    ),
                    Project(
                        id = Identifier("NPM:second-project-group:second-project-name:0.0.1"),
                        declaredLicenses = sortedSetOf("BSD-3-Clause"),
                        definitionFilePath = "npm-project/package.json",
                        homepageUrl = "first project's homepage",
                        scopeDependencies = sortedSetOf(
                            Scope(
                                name = "devDependencies",
                                dependencies = sortedSetOf(
                                    PackageReference(
                                        id = Identifier("NPM:@something:somepackage:1.2.3"),
                                        dependencies = sortedSetOf(
                                            PackageReference(
                                                id = Identifier("NPM:@something:somepackage-dep:1.2.3"),
                                                dependencies = sortedSetOf(
                                                    PackageReference(
                                                        id = Identifier("NPM:@something:somepackage-dep-dep:1.2.3"),
                                                        dependencies = sortedSetOf(
                                                            PackageReference(
                                                                id = Identifier("NPM:@something:somepackage-dep-dep-dep:1.2.3"),
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
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
                ).plus(
                    sortedSetOf(
                        Identifier("NPM:second-project-group:second-project-name:0.0.1"),
                        Identifier("NPM:@something:somepackage:1.2.3"),
                        Identifier("NPM:@something:somepackage-dep:1.2.3"),
                        Identifier("NPM:@something:somepackage-dep-dep:1.2.3"),
                        Identifier("NPM:@something:somepackage-dep-dep-dep:1.2.3"),
                    ).map {
                        CuratedPackage(
                            pkg = Package(
                                id = it,
                                binaryArtifact = RemoteArtifact.EMPTY,
                                declaredLicenses = sortedSetOf("MIT"),
                                description = "Package of ${it}",
                                homepageUrl = "",
                                sourceArtifact = RemoteArtifact.EMPTY,
                                vcs = VcsInfo.EMPTY
                            )
                        )
                    }
                ).toSortedSet()
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
                            scanner = ScannerDetails(
                                name = "SCANNER",
                                version = "1.2.3",
                                configuration = "configuration"
                            ),
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
                            scanner = ScannerDetails(
                                name = "otherSCANNER",
                                version = "1.2.3",
                                configuration = "otherConfiguration"
                            ),
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
}
