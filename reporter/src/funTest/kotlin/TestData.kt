/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter

import java.net.URI
import java.time.Instant

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorRecord
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION

// TODO: Create a way to reduce the code required to prepare an OrtResult for testing.
val ORT_RESULT = OrtResult(
    repository = Repository(
        vcs = VcsInfo.EMPTY,
        config = RepositoryConfiguration(
            excludes = Excludes(
                paths = listOf(
                    PathExclude(
                        pattern = "excluded-project/**",
                        reason = PathExcludeReason.OTHER
                    )
                ),
                scopes = listOf(
                    ScopeExclude(
                        pattern = "devDependencies",
                        reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF
                    )
                )
            )
        )
    ),
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
                    homepageUrl = "https://github.com/oss-review-toolkit/ort",
                    scopeDependencies = sortedSetOf(
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
                                ),
                                PackageReference(
                                    id = Identifier("NPM:@ort:concluded-license:1.0")
                                ),
                                PackageReference(
                                    id = Identifier("NPM:@ort:declared-license:1.0")
                                )
                            )
                        ),
                        Scope(
                            name = "devDependencies",
                            dependencies = sortedSetOf(
                                PackageReference(
                                    id = Identifier("NPM:@ort:declared-license:1.0")
                                ),
                            )
                        )
                    )
                ),
                Project(
                    id = Identifier("NPM:@ort:project-without-findings:1.0"),
                    definitionFilePath = "project-without-findings/package.json",
                    declaredLicenses = sortedSetOf(),
                    vcs = VcsInfo.EMPTY,
                    homepageUrl = "https://github.com/oss-review-toolkit/ort",
                    scopeDependencies = sortedSetOf(
                        Scope(
                            name = "dependencies",
                            dependencies = sortedSetOf()
                        )
                    )
                ),
                Project(
                    id = Identifier("NPM:@ort:excluded-project:1.0"),
                    definitionFilePath = "excluded-project/package.json",
                    declaredLicenses = sortedSetOf("BSD-2-Clause"),
                    vcs = VcsInfo.EMPTY,
                    homepageUrl = "https://github.com/oss-review-toolkit/ort",
                    scopeDependencies = sortedSetOf()
                )
            ),
            packages = sortedSetOf(
                CuratedPackage(
                    pkg = Package(
                        id = Identifier("NPM:@ort:no-license-file:1.0"),
                        declaredLicenses = sortedSetOf(),
                        description = "",
                        homepageUrl = "https://github.com/oss-review-toolkit/ort",
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                    )
                ),
                CuratedPackage(
                    pkg = Package(
                        id = Identifier("NPM:@ort:license-file:1.0"),
                        declaredLicenses = sortedSetOf(),
                        description = "",
                        homepageUrl = "https://github.com/oss-review-toolkit/ort",
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = RemoteArtifact(
                            url = "https://example.com/license-file-1.0.tgz",
                            hash = Hash(
                                value = "0000000000000000000000000000000000000000",
                                algorithm = HashAlgorithm.SHA1
                            )
                        ),
                        vcs = VcsInfo.EMPTY
                    )
                ),
                CuratedPackage(
                    pkg = Package(
                        id = Identifier("NPM:@ort:license-file-and-additional-licenses:1.0"),
                        declaredLicenses = sortedSetOf(),
                        description = "",
                        homepageUrl = "https://github.com/oss-review-toolkit/ort",
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = RemoteArtifact(
                            url = "https://example.com/license-file-and-additional-licenses-1.0.tgz",
                            hash = Hash(
                                value = "0000000000000000000000000000000000000000",
                                algorithm = HashAlgorithm.SHA1
                            )
                        ),
                        vcs = VcsInfo.EMPTY
                    )
                ),
                CuratedPackage(
                    pkg = Package(
                        id = Identifier("NPM:@ort:concluded-license:1.0"),
                        declaredLicenses = sortedSetOf("BSD-3-Clause"),
                        concludedLicense = "MIT AND MIT WITH Libtool-exception".toSpdx(),
                        description = "",
                        homepageUrl = "https://github.com/oss-review-toolkit/ort",
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = RemoteArtifact(
                            url = "https://example.com/concluded-license-1.0.tgz",
                            hash = Hash(
                                value = "0000000000000000000000000000000000000000",
                                algorithm = HashAlgorithm.SHA1
                            )
                        ),
                        vcs = VcsInfo.EMPTY
                    )
                ),
                CuratedPackage(
                    pkg = Package(
                        id = Identifier("NPM:@ort:declared-license:1.0"),
                        declaredLicenses = sortedSetOf("MIT"),
                        description = "",
                        homepageUrl = "https://github.com/oss-review-toolkit/ort",
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = RemoteArtifact(
                            url = "https://example.com/declared-license-1.0.tgz",
                            hash = Hash(
                                value = "0000000000000000000000000000000000000000",
                                algorithm = HashAlgorithm.SHA1
                            )
                        ),
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
                Identifier("NPM:@ort:project-with-findings:1.0") to listOf(
                    ScanResult(
                        provenance = UnknownProvenance,
                        scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                        summary = ScanSummary(
                            startTime = Instant.EPOCH,
                            endTime = Instant.EPOCH,
                            packageVerificationCode = "",
                            licenseFindings = sortedSetOf(
                                LicenseFinding(
                                    license = "MIT",
                                    location = TextLocation("project-with-findings/file", 1)
                                )
                            ),
                            copyrightFindings = sortedSetOf(
                                CopyrightFinding(
                                    statement = "Copyright 1",
                                    location = TextLocation("project-with-findings/file", 1)
                                )
                            )
                        )
                    )
                ),
                Identifier("NPM:@ort:project-without-findings:1.0") to listOf(
                    ScanResult(
                        provenance = UnknownProvenance,
                        scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                        summary = ScanSummary(
                            startTime = Instant.EPOCH,
                            endTime = Instant.EPOCH,
                            packageVerificationCode = "",
                            licenseFindings = sortedSetOf(),
                            copyrightFindings = sortedSetOf()
                        )
                    )
                ),
                Identifier("NPM:@ort:no-license-file:1.0") to listOf(
                    ScanResult(
                        provenance = UnknownProvenance,
                        scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                        summary = ScanSummary(
                            startTime = Instant.EPOCH,
                            endTime = Instant.EPOCH,
                            packageVerificationCode = "",
                            licenseFindings = sortedSetOf(
                                LicenseFinding(
                                    license = "MIT",
                                    location = TextLocation("file", 1)
                                )
                            ),
                            copyrightFindings = sortedSetOf(
                                CopyrightFinding(
                                    statement = "Copyright 1",
                                    location = TextLocation("file", 1)
                                )
                            )
                        )
                    )
                ),
                Identifier("NPM:@ort:license-file:1.0") to listOf(
                    ScanResult(
                        provenance = ArtifactProvenance(
                            sourceArtifact = RemoteArtifact(
                                url = "https://example.com/license-file-1.0.tgz",
                                hash = Hash(value = "", algorithm = HashAlgorithm.SHA1)
                            )
                        ),
                        scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                        summary = ScanSummary(
                            startTime = Instant.EPOCH,
                            endTime = Instant.EPOCH,
                            packageVerificationCode = "",
                            licenseFindings = sortedSetOf(
                                LicenseFinding(
                                    license = "MIT",
                                    location = TextLocation("LICENSE", 1)
                                ),
                                LicenseFinding(
                                    license = "MIT",
                                    location = TextLocation("file", 1)
                                )
                            ),
                            copyrightFindings = sortedSetOf(
                                CopyrightFinding(
                                    statement = "Copyright 1",
                                    location = TextLocation("LICENSE", 1)
                                ),
                                CopyrightFinding(
                                    statement = "Copyright 2",
                                    location = TextLocation("file", 1)
                                )
                            )
                        )
                    )
                ),
                Identifier("NPM:@ort:license-file-and-additional-licenses:1.0") to listOf(
                    ScanResult(
                        provenance = ArtifactProvenance(
                            sourceArtifact = RemoteArtifact(
                                url = "https://example.com/license-file-and-additional-licenses-1.0.tgz",
                                hash = Hash(value = "", algorithm = HashAlgorithm.SHA1)
                            )
                        ),
                        scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                        summary = ScanSummary(
                            startTime = Instant.EPOCH,
                            endTime = Instant.EPOCH,
                            packageVerificationCode = "",
                            licenseFindings = sortedSetOf(
                                LicenseFinding(
                                    license = "MIT",
                                    location = TextLocation("LICENSE", 1)
                                ),
                                LicenseFinding(
                                    license = "MIT",
                                    location = TextLocation("file", 1)
                                ),
                                LicenseFinding(
                                    license = "BSD-3-Clause",
                                    location = TextLocation("file", 50)
                                )
                            ),
                            copyrightFindings = sortedSetOf(
                                CopyrightFinding(
                                    statement = "Copyright 1",
                                    location = TextLocation("LICENSE", 1)
                                ),
                                CopyrightFinding(
                                    statement = "Copyright 2",
                                    location = TextLocation("file", 1)
                                ),
                                CopyrightFinding(
                                    statement = "Copyright 3",
                                    location = TextLocation("file", 50)
                                )
                            )
                        )
                    )
                ),
                Identifier("NPM:@ort:concluded-license:1.0") to listOf(
                    ScanResult(
                        provenance = ArtifactProvenance(
                            sourceArtifact = RemoteArtifact(
                                url = "https://example.com/concluded-license-1.0.tgz",
                                hash = Hash(value = "", algorithm = HashAlgorithm.SHA1)
                            )
                        ),
                        scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                        summary = ScanSummary(
                            startTime = Instant.EPOCH,
                            endTime = Instant.EPOCH,
                            packageVerificationCode = "",
                            licenseFindings = sortedSetOf(
                                LicenseFinding(
                                    license = "MIT",
                                    location = TextLocation("file1", 1)
                                ),
                                LicenseFinding(
                                    license = "BSD-2-Clause",
                                    location = TextLocation("file2", 1)
                                )
                            ),
                            copyrightFindings = sortedSetOf(
                                CopyrightFinding(
                                    statement = "Copyright 1",
                                    location = TextLocation("file1", 1)
                                ),
                                CopyrightFinding(
                                    statement = "Copyright 2",
                                    location = TextLocation("file2", 1)
                                )
                            )
                        )
                    )
                ),
                Identifier("NPM:@ort:declared-license:1.0") to listOf(
                    ScanResult(
                        provenance = ArtifactProvenance(
                            sourceArtifact = RemoteArtifact(
                                url = "https://example.com/declared-license-1.0.tgz",
                                hash = Hash(value = "", algorithm = HashAlgorithm.SHA1)
                            )
                        ),
                        scanner = ScannerDetails(name = "scanner", version = "1.0", configuration = ""),
                        summary = ScanSummary(
                            startTime = Instant.EPOCH,
                            endTime = Instant.EPOCH,
                            packageVerificationCode = "",
                            licenseFindings = sortedSetOf(
                                LicenseFinding(
                                    license = "BSD-3-Clause",
                                    location = TextLocation("LICENSE", 1)
                                )
                            ),
                            copyrightFindings = sortedSetOf(
                                CopyrightFinding(
                                    statement = "Copyright 1",
                                    location = TextLocation("LICENSE", 1)
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

val VULNERABILITY = Vulnerability(
    id = "CVE-2021-1234",
    references = listOf(
        VulnerabilityReference(URI("https://cves.example.org/cve1"), "Cvss2", "MEDIUM")
    )
)

val ADVISOR_WITH_VULNERABILITIES = AdvisorRun(
    startTime = Instant.now(),
    endTime = Instant.now(),
    environment = Environment(),
    config = AdvisorConfiguration(),
    results = AdvisorRecord(
        sortedMapOf(
            Identifier("NPM:@ort:declared-license:1.0") to listOf(
                AdvisorResult(
                    advisor = AdvisorDetails("VulnerableCode", enumSetOf(AdvisorCapability.VULNERABILITIES)),
                    summary = AdvisorSummary(Instant.now(), Instant.now()),
                    vulnerabilities = listOf(VULNERABILITY)
                )
            )
        )
    )
)

val ORT_RESULT_WITH_VULNERABILITIES = ORT_RESULT.copy(advisor = ADVISOR_WITH_VULNERABILITIES)
