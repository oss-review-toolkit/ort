/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.spdx

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.CopyrightFinding
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
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.scannerRunOf

private val ANALYZED_VCS = VcsInfo(
    type = VcsType.GIT,
    revision = "master",
    url = "https://github.com/path/proj1-repo.git"
)

val ORT_RESULT = OrtResult(
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
        vcs = ANALYZED_VCS,
        vcsProcessed = ANALYZED_VCS
    ),
    analyzer = AnalyzerRun.EMPTY.copy(
        result = AnalyzerResult(
            projects = setOf(
                Project(
                    id = Identifier("Maven:proj1-grp:proj1:0.0.1"),
                    declaredLicenses = setOf("MIT"),
                    definitionFilePath = "",
                    homepageUrl = "https://example.com/proj1/homepage",
                    scopeDependencies = setOf(
                        Scope(
                            name = "compile",
                            dependencies = setOf(
                                PackageReference(
                                    id = Identifier("Maven:pkg1-grp:pkg1:0.0.1"),
                                    dependencies = setOf(
                                        PackageReference(
                                            id = Identifier("Maven:pkg2-grp:pkg2:0.0.1")
                                        ),
                                        PackageReference(
                                            id = Identifier("Maven:pkg3-grp:pkg3:0.0.1"),
                                            dependencies = setOf(
                                                PackageReference(
                                                    id = Identifier("Maven:pkg6-grp:pkg6:0.0.1")
                                                )
                                            )
                                        )
                                    )
                                ),
                                PackageReference(
                                    id = Identifier("Maven:pkg4-grp:pkg4:0.0.1"),
                                    dependencies = setOf(
                                        PackageReference(
                                            id = Identifier("Maven:pkg7-grp:pkg7:0.0.1")
                                        )
                                    )
                                ),
                                PackageReference(
                                    id = Identifier("Go::gopkg.in/yaml.v3:3.0.1")
                                )
                            )
                        ),
                        Scope(
                            name = "test",
                            dependencies = setOf(
                                PackageReference(
                                    id = Identifier("Maven:pkg-grp:pkg5:0.0.1")
                                )
                            )
                        )
                    ),
                    vcs = ANALYZED_VCS
                )
            ),
            packages = setOf(
                // A package with all supported attributes set, with a VCS URL containing a user name, and with two scan
                // results for the VCS containing copyright findings matched to a license finding.
                Package(
                    id = Identifier("Maven:pkg1-grp:pkg1:0.0.1"),
                    binaryArtifact = RemoteArtifact(
                        url = "https://example.com/pkg1.jar",
                        hash = Hash.create("0000000000000000000000000000000000000000")
                    ),
                    concludedLicense = "BSD-2-Clause AND BSD-3-Clause AND MIT".toSpdx(),
                    declaredLicenses = setOf("BSD-3-Clause", "MIT OR GPL-2.0-only"),
                    description = "Description of pkg1.",
                    homepageUrl = "https://example.com/pkg1/homepage",
                    sourceArtifact = RemoteArtifact(
                        url = "https://example.com/pkg1-sources.jar",
                        hash = Hash.create("0000000000000000000000000000000000000000")
                    ),
                    vcs = VcsInfo(
                        type = VcsType.GIT,
                        revision = "master",
                        url = "ssh://git@github.com/path/pkg1-repo.git",
                        path = "project-path"
                    )
                ),
                // A package with minimal attributes set.
                Package(
                    id = Identifier("Maven:pkg2-grp:pkg2:0.0.1"),
                    binaryArtifact = RemoteArtifact.EMPTY,
                    declaredLicenses = emptySet(),
                    description = "",
                    homepageUrl = "",
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY
                ),
                // A package with only unmapped declared license.
                Package(
                    id = Identifier("Maven:pkg3-grp:pkg3:0.0.1"),
                    binaryArtifact = RemoteArtifact.EMPTY,
                    declaredLicenses = setOf("unmappable license"),
                    description = "",
                    homepageUrl = "",
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY
                ),
                // A package with partially mapped declared license.
                Package(
                    id = Identifier("Maven:pkg4-grp:pkg4:0.0.1"),
                    binaryArtifact = RemoteArtifact.EMPTY,
                    declaredLicenses = setOf("unmappable license", "MIT"),
                    description = "",
                    homepageUrl = "",
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY
                ),
                // A package used only from the excluded 'test' scope, with non-SPDX license IDs in the declared and
                // concluded license.
                Package(
                    id = Identifier("Maven:pkg5-grp:pkg5:0.0.1"),
                    binaryArtifact = RemoteArtifact.EMPTY,
                    declaredLicenses = setOf("LicenseRef-scancode-philips-proprietary-notice-2000"),
                    concludedLicense = "LicenseRef-scancode-purdue-bsd".toSpdx(),
                    description = "",
                    homepageUrl = "",
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY
                ),
                // A package with non-SPDX license IDs in the declared and concluded license.
                Package(
                    id = Identifier("Maven:pkg6-grp:pkg6:0.0.1"),
                    binaryArtifact = RemoteArtifact.EMPTY,
                    declaredLicenses = setOf("LicenseRef-scancode-asmus"),
                    concludedLicense = "LicenseRef-scancode-srgb".toSpdx(),
                    description = "",
                    homepageUrl = "",
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY
                ),
                // A package with a source artifact scan result.
                Package(
                    id = Identifier("Maven:pkg7-grp:pkg7:0.0.1"),
                    binaryArtifact = RemoteArtifact.EMPTY,
                    declaredLicenses = setOf(),
                    description = "",
                    homepageUrl = "",
                    sourceArtifact = RemoteArtifact(
                        url = "https://example.com/pkg7-sources.jar",
                        hash = Hash.create("0000000000000000000000000000000000000000")
                    ),
                    vcs = VcsInfo.EMPTY
                ),
                // A package without a declared license, but with license findings in file matching the license file
                // patterns, which targets testing the inclusion of the main license in the output. This covers
                // the standard use case in the Go ecosystem where packages do not have a declared license.
                Package(
                    id = Identifier("Go::gopkg.in/yaml.v3:3.0.1"),
                    purl = "pkg:golang/gopkg.in/yaml.v3@3.0.1",
                    declaredLicenses = emptySet(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo(
                        type = VcsType.GIT,
                        url = "https://gopkg.in/yaml.v3",
                        revision = "f6f7691b1fdeb513f56608cd2c32c51f8194bf51"
                    )
                )
            )
        )
    ),
    scanner = scannerRunOf(
        Identifier("Maven:pkg1-grp:pkg1:0.0.1") to listOf(
            ScanResult(
                provenance = RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        revision = "master",
                        url = "ssh://git@github.com/path/pkg1-repo.git",
                        path = "project-path"
                    ),
                    resolvedRevision = "deadbeef"
                ),
                scanner = ScannerDetails.EMPTY.copy(name = "scanner1"),
                summary = ScanSummary.EMPTY.copy(
                    licenseFindings = setOf(
                        LicenseFinding(
                            license = "Apache-2.0",
                            location = TextLocation("LICENSE", 1)
                        )
                    ),
                    copyrightFindings = setOf(
                        CopyrightFinding(
                            statement = "Copyright 2020 Some copyright holder in source artifact",
                            location = TextLocation("project-path/some/file", 1)
                        ),
                        CopyrightFinding(
                            statement = "Copyright 2020 Some other copyright holder in source artifact",
                            location = TextLocation("project-path/some/file", 7)
                        )
                    )
                )
            ),
            ScanResult(
                provenance = RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        revision = "master",
                        url = "ssh://git@github.com/path/pkg1-repo.git",
                        path = "project-path"
                    ),
                    resolvedRevision = "deadbeef"
                ),
                scanner = ScannerDetails.EMPTY.copy(name = "scanner2"),
                summary = ScanSummary.EMPTY.copy(
                    licenseFindings = setOf(
                        LicenseFinding(
                            license = "BSD-2-Clause",
                            location = TextLocation("LICENSE", 1)
                        )
                    ),
                    copyrightFindings = setOf(
                        CopyrightFinding(
                            statement = "Copyright 2020 Some copyright holder in VCS",
                            location = TextLocation("project-path/some/file", 1)
                        )
                    )
                )
            )
        ),
        Identifier("Maven:pkg7-grp:pkg7:0.0.1") to listOf(
            ScanResult(
                provenance = ArtifactProvenance(
                    sourceArtifact = RemoteArtifact(
                        url = "https://example.com/pkg7-sources.jar",
                        hash = Hash.create("0000000000000000000000000000000000000000")
                    )
                ),
                scanner = ScannerDetails.EMPTY,
                summary = ScanSummary.EMPTY.copy(
                    licenseFindings = setOf(
                        LicenseFinding(
                            license = "GPL-2.0-only WITH NOASSERTION",
                            location = TextLocation("LICENSE", 1)
                        )
                    ),
                    copyrightFindings = setOf(
                        CopyrightFinding(
                            statement = "Copyright 2020 Some copyright holder in source artifact",
                            location = TextLocation("some/file", 1)
                        )
                    )
                )
            )
        ),
        Identifier("Go::gopkg.in/yaml.v3:3.0.1") to listOf(
            ScanResult(
                provenance = RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        url = "https://gopkg.in/yaml.v3",
                        revision = "f6f7691b1fdeb513f56608cd2c32c51f8194bf51"
                    ),
                    resolvedRevision = "f6f7691b1fdeb513f56608cd2c32c51f8194bf51"
                ),
                scanner = ScannerDetails.EMPTY,
                summary = ScanSummary.EMPTY.copy(
                    licenseFindings = setOf(
                        LicenseFinding(
                            license = "Apache-2.0",
                            location = TextLocation("LICENSE", 34)
                        ),
                        LicenseFinding(
                            license = "Apache-2.0",
                            location = TextLocation("LICENSE", 36)
                        ),
                        LicenseFinding(
                            license = "MIT",
                            location = TextLocation("LICENSE", 38)
                        )
                    ),
                    copyrightFindings = setOf(
                        CopyrightFinding(
                            statement = "Copyright (c) 2006-2010 Kirill Simonov",
                            location = TextLocation("readerc.go", 1)
                        )
                    )
                )
            )
        )
    )
)
