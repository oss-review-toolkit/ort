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

package org.ossreviewtoolkit.reporter.reporters.opossum

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import java.time.Instant

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class OpossumReporterTest : WordSpec({
    "resolvePath()" should {
        "work with basic cases" {
            resolvePath("/") shouldBe "/"
            resolvePath("/", "test") shouldBe "/test"
            resolvePath("/", "/test") shouldBe "/test"
            resolvePath("/", "/test/") shouldBe "/test/"
            resolvePath("/tost", "test") shouldBe "/tost/test"
            resolvePath("/tost", "/test") shouldBe "/tost/test"
            resolvePath("/tost", "/test/") shouldBe "/tost/test/"
            resolvePath("/tost", "./test/") shouldBe "/tost/test/"
        }

        "work with lists" {
            resolvePath(listOf("/", "test/to/path", "/something/else/")) shouldBe "/test/to/path/something/else/"
        }

        "work for more cases" {
            resolvePath("/") shouldBe "/"
            resolvePath("/", isDirectory = true) shouldBe "/"
            resolvePath("/", isDirectory = false) shouldBe "/"
            resolvePath("path/to/thing") shouldBe "/path/to/thing"
            resolvePath("path/to/thing/") shouldBe "/path/to/thing/"
            resolvePath("/path/to/thing/") shouldBe "/path/to/thing/"
            resolvePath("/path/./to/thing/") shouldBe "/path/to/thing/"
            resolvePath("path/to/thing/", isDirectory = true) shouldBe "/path/to/thing/"
            resolvePath("path/to/thing/", isDirectory = false) shouldBe "/path/to/thing"
            resolvePath("path/to/thing", isDirectory = true) shouldBe "/path/to/thing/"
            resolvePath("path/to/thing", isDirectory = false) shouldBe "/path/to/thing"
            resolvePath("path/./to/thing", isDirectory = false) shouldBe "/path/to/thing"
            resolvePath("/path/to/thing/", isDirectory = true) shouldBe "/path/to/thing/"
            resolvePath("/path/to/thing/", isDirectory = false) shouldBe "/path/to/thing"
            resolvePath("/path/to/thing", isDirectory = true) shouldBe "/path/to/thing/"
            resolvePath("/path/to/thing", isDirectory = false) shouldBe "/path/to/thing"
            resolvePath("/path/./to/thing", isDirectory = false) shouldBe "/path/to/thing"
        }
    }

    "generateOpossumInput()" should {
        val result = createOrtResult()
        val opossumInput = OpossumReporter().generateOpossumInput(result)

        "create input that is somehow valid" {
            opossumInput shouldNotBeNull {
                resources shouldNot beNull()
                signals shouldNot beNull()
                pathToSignal shouldNot beNull()
                packageToRoot shouldNot beNull()
                attributionBreakpoints shouldNot beNull()
            }
        }

        val fileList = opossumInput.resources.toFileList()

        "create a file list that contains some specific files" {
            fileList shouldContain "/"
            fileList shouldContain "/pom.xml/compile/first-package-group/first-package@0.0.1/LICENSE"
            fileList shouldContain "/npm-project/package.json/devDependencies/@something/somepackage@1.2.3/" +
                    "dependencies/@something/somepackage-dep@1.2.3/dependencies/" +
                    "@something/somepackage-dep-dep@1.2.3/dependencies/@something/somepackage-dep-dep-dep@1.2.3"

            opossumInput.attributionBreakpoints shouldContain "/npm-project/package.json/devDependencies/" +
                    "@something/somepackage@1.2.3/dependencies/@something/somepackage-dep@1.2.3/dependencies/" +
                    "@something/somepackage-dep-dep@1.2.3/dependencies/"
        }

        "create a file list that contains files from other lists" {
            opossumInput.pathToSignal.keys.forAll { path -> fileList shouldContain resolvePath(path) }

            opossumInput.attributionBreakpoints.map { it.replace(Regex("/$"), "") }.forAll { path ->
                fileList shouldContain resolvePath(path)
            }

            opossumInput.packageToRoot.values.forAll { levelForPath ->
                levelForPath.keys.forAll { path ->
                    fileList shouldContain resolvePath(path)
                }
            }
        }

        "create a result that contains all packages in its signals" {
            result.getPackages().forAll { pkg ->
                opossumInput.signals.find { it.id == pkg.metadata.id } shouldNot beNull()
            }
        }

        "create a signal with license containing LICENSE File added by SCANNER report" {
            val signals = opossumInput.getSignalsForFile(
                "/pom.xml/compile/first-package-group/first-package@0.0.1/LICENSE"
            )
            signals.size shouldBe 2
            signals.find { it.source == "ORT-Scanner-SCANNER@1.2.3" } shouldNotBeNull {
                license.toString() shouldBe "Apache-2.0"
            }
        }

        "create a signal with copyright if some file is added by SCANNER report" {
            val signals =
                opossumInput.getSignalsForFile("/pom.xml/compile/first-package-group/first-package@0.0.1/some/file")
            signals.size shouldBe 2
            signals.find { it.source == "ORT-Scanner-SCANNER@1.2.3" } shouldNotBeNull {
                copyright shouldContain "Copyright 2020 Some copyright holder in source artifact"
                copyright shouldContain "Copyright 2020 Some other copyright holder in source artifact"
            }
        }

        "create signals with all uuids being assigned" {
            opossumInput.pathToSignal.values.forAll { signal ->
                signal.forAll { uuid ->
                    opossumInput.signals.find { it.uuid == uuid } shouldNot beNull()
                }
            }
        }

        "create an opossumInput JSON with expected top level entries" {
            val opossumInputJson = opossumInput.toJson()

            opossumInputJson.keys should containExactlyInAnyOrder(
                "attributionBreakpoints",
                "baseUrlsForSources",
                "externalAttributionSources",
                "externalAttributions",
                "filesWithChildren",
                "frequentLicenses",
                "metadata",
                "resources",
                "resourcesToAttributions"
            )
            (opossumInputJson["metadata"] as Map<*, *>).keys shouldContain "projectId"
        }

        "create frequentLicenses" {
            opossumInput.frequentLicenses.map { it.shortName } shouldContain "MIT"
        }

        "create correct filesWithChildren" {
            opossumInput.filesWithChildren shouldContain "/pom.xml/"
        }

        "create valid baseUrlsForSources" {
            opossumInput.baseUrlsForSources["/"] shouldBe "https://github.com/path/first-project/" +
                    "tree/master/sub/path/{path}"
            opossumInput.baseUrlsForSources["/pom.xml/compile/first-package-group/first-package@0.0.1/"] shouldBe
                    "https://github.com/path/first-package-repo/tree/master/project-path/{path}"
        }

        "create issues containing all issues" {
            val issuesFromFirstPackage =
                opossumInput.getSignalsForFile("/pom.xml/compile/first-package-group/first-package@0.0.1")
                    .filter { it.comment?.contains(Regex("Source-.*Message-")) == true }
            issuesFromFirstPackage.size shouldBe 4
            issuesFromFirstPackage.forAll {
                it.followUp shouldBe true
                it.excludeFromNotice shouldBe true
            }

            val issuesAttachedToFallbackPath = opossumInput.getSignalsForFile("/")
            issuesAttachedToFallbackPath.size shouldBe 1
            issuesAttachedToFallbackPath.forAll {
                it.followUp shouldBe true
                it.excludeFromNotice shouldBe true
                it.comment shouldContain Regex("Source-.*Message-")
            }
        }
    }

    "generateOpossumInput() with excluded scopes" should {
        val result = createOrtResult().setScopeExcludes("devDependencies")
        val opossumInputWithExcludedScopes = OpossumReporter().generateOpossumInput(result)
        val fileListWithExcludedScopes = opossumInputWithExcludedScopes.resources.toFileList()

        "exclude scopes" {
            fileListWithExcludedScopes shouldNotContain "/npm-project/package.json/devDependencies/@something/" +
                    "somepackage@1.2.3/dependencies/@something/somepackage-dep@1.2.3/dependencies/@something/" +
                    "somepackage-dep-dep@1.2.3/dependencies/@something/somepackage-dep-dep-dep@1.2.3"
        }
    }
})

@Suppress("LongMethod")
private fun createOrtResult(): OrtResult {
    val analyzedVcs = VcsInfo(
        type = VcsType.GIT,
        revision = "master",
        url = "https://github.com/path/first-project.git",
        path = "sub/path"
    )

    return OrtResult(
        repository = Repository(
            vcs = analyzedVcs,
            vcsProcessed = analyzedVcs
        ),
        analyzer = AnalyzerRun.EMPTY.copy(
            config = AnalyzerConfiguration(allowDynamicVersions = true),
            result = AnalyzerResult(
                projects = setOf(
                    Project(
                        id = Identifier("Maven:first-project-group:first-project-name:0.0.1"),
                        declaredLicenses = setOf("MIT"),
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
                        declaredLicenses = setOf("BSD-3-Clause"),
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
                                                                id = Identifier(
                                                                    "NPM:@something:" +
                                                                            "somepackage-dep-dep-dep:1.2.3"
                                                                ),
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
                packages = setOf(
                    Package(
                        id = Identifier("Maven:first-package-group:first-package:0.0.1"),
                        binaryArtifact = RemoteArtifact("https://some-host/first-package.jar", Hash.NONE),
                        concludedLicense = "BSD-2-Clause AND BSD-3-Clause AND MIT".toSpdx(),
                        declaredLicenses = setOf("BSD-3-Clause", "MIT OR GPL-2.0-only"),
                        description = "A package with all supported attributes set, with a VCS URL containing a user " +
                                "name, and with a scan result containing two copyright finding matched to a license " +
                                "finding.",
                        homepageUrl = "first package's homepage URL",
                        sourceArtifact = RemoteArtifact("https://some-host/first-package-sources.jar", Hash.NONE),
                        vcs = VcsInfo(
                            type = VcsType.GIT,
                            revision = "master",
                            url = "ssh://git@github.com/path/first-package-repo.git",
                            path = "project-path"
                        )
                    ),
                    Package(
                        id = Identifier("Maven:second-package-group:second-package:0.0.1"),
                        binaryArtifact = RemoteArtifact.EMPTY,
                        declaredLicenses = emptySet(),
                        description = "A package with minimal attributes set.",
                        homepageUrl = "",
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                    ),
                    Package(
                        id = Identifier("Maven:third-package-group:third-package:0.0.1"),
                        binaryArtifact = RemoteArtifact.EMPTY,
                        declaredLicenses = setOf("unmappable license"),
                        description = "A package with only unmapped declared license.",
                        homepageUrl = "",
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                    ),
                    Package(
                        id = Identifier("Maven:fourth-package-group:fourth-package:0.0.1"),
                        binaryArtifact = RemoteArtifact.EMPTY,
                        declaredLicenses = setOf("unmappable license", "MIT"),
                        description = "A package with partially mapped declared license.",
                        homepageUrl = "",
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                    ),
                    Package(
                        id = Identifier("Maven:fifth-package-group:fifth-package:0.0.1"),
                        binaryArtifact = RemoteArtifact.EMPTY,
                        declaredLicenses = setOf("LicenseRef-scancode-philips-proprietary-notice-2000"),
                        concludedLicense = "LicenseRef-scancode-purdue-bsd".toSpdx(),
                        description = "A package used only from the excluded 'test' scope, with non-SPDX license IDs " +
                                "in the declared and concluded license.",
                        homepageUrl = "",
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY

                    ),
                    Package(
                        id = Identifier("Maven:sixth-package-group:sixth-package:0.0.1"),
                        binaryArtifact = RemoteArtifact.EMPTY,
                        declaredLicenses = setOf("LicenseRef-scancode-asmus"),
                        concludedLicense = "LicenseRef-scancode-srgb".toSpdx(),
                        description = "A package with non-SPDX license IDs in the declared and concluded license.",
                        homepageUrl = "",
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                    )
                ).plus(
                    setOf(
                        Identifier("NPM:second-project-group:second-project-name:0.0.1"),
                        Identifier("NPM:@something:somepackage:1.2.3"),
                        Identifier("NPM:@something:somepackage-dep:1.2.3"),
                        Identifier("NPM:@something:somepackage-dep-dep:1.2.3"),
                        Identifier("NPM:@something:somepackage-dep-dep-dep:1.2.3"),
                    ).map {
                        Package(
                            id = it,
                            binaryArtifact = RemoteArtifact.EMPTY,
                            declaredLicenses = setOf("MIT"),
                            description = "Package of $it",
                            homepageUrl = "",
                            sourceArtifact = RemoteArtifact.EMPTY,
                            vcs = VcsInfo.EMPTY
                        )
                    }
                ),
                issues = mapOf(
                    Identifier("Maven:first-package-group:first-package:0.0.1") to listOf(
                        Issue(
                            source = "Source-1",
                            message = "Message-1"
                        ),
                        Issue(
                            source = "Source-2",
                            message = "Message-2"
                        )
                    ),
                    Identifier("unknown-identifier") to listOf(
                        Issue(
                            source = "Source-3",
                            message = "Message-3"
                        )
                    )
                ),
            ),
        ),
        scanner = ScannerRun.EMPTY.copy(
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
                        summary = ScanSummary.EMPTY.copy(
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
                        summary = ScanSummary.EMPTY.copy(
                            startTime = Instant.EPOCH,
                            endTime = Instant.EPOCH,
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
                            ),
                            issues = listOf(
                                Issue(
                                    source = "Source-4",
                                    message = "Message-4"
                                ),
                                Issue(
                                    source = "Source-5",
                                    message = "Message-5"
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

private fun OrtResult.setScopeExcludes(vararg patterns: String): OrtResult {
    val config = repository.config.copy(
        excludes = repository.config.excludes.copy(
            scopes = patterns.map { pattern ->
                ScopeExclude(pattern, ScopeExcludeReason.DEV_DEPENDENCY_OF)
            }
        )
    )

    return replaceConfig(config)
}
