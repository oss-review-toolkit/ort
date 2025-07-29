/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.time.Instant

import org.ossreviewtoolkit.model.FileList.Entry
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.utils.alignRevisions
import org.ossreviewtoolkit.model.utils.clearVcsPath
import org.ossreviewtoolkit.utils.ort.Environment

class ScannerRunTest : WordSpec({
    "init" should {
        "error on duplicate provenance and scanner scan results" {
            val provenance = RepositoryProvenance(
                VcsInfo(type = VcsType.GIT, url = "https://github.com/example.git", revision = "revision"),
                "revision"
            )
            val otherProvenance = RepositoryProvenance(
                VcsInfo(type = VcsType.GIT, url = "https://github.com/example.git", revision = "other_revision"),
                "other_revision"
            )
            val provenances = setOf(
                ProvenanceResolutionResult(
                    id = Identifier("maven::example:1.0"),
                    packageProvenance = provenance
                ),
                ProvenanceResolutionResult(
                    id = Identifier("maven::other_example:1.0"),
                    packageProvenance = otherProvenance
                )
            )

            val scanner = ScannerDetails("scanner", "1.0.0", "configuration")
            val otherScanner = ScannerDetails("other-scanner", "1.0.0", "configuration")

            // Shared provenance and scanner.
            shouldThrow<IllegalArgumentException> {
                ScannerRun.EMPTY.copy(
                    provenances = provenances,
                    scanResults = setOf(
                        ScanResult(
                            provenance = provenance,
                            scanner = scanner,
                            summary = ScanSummary.EMPTY.copy(
                                licenseFindings = setOf(
                                    LicenseFinding("MIT", TextLocation("file1.txt", 1, 1))
                                )
                            )
                        ),
                        ScanResult(
                            provenance = provenance,
                            scanner = scanner,
                            summary = ScanSummary.EMPTY.copy(
                                licenseFindings = setOf(
                                    LicenseFinding("MIT", TextLocation("file2.txt", 1, 1))
                                )
                            )
                        )
                    )
                )
            }.message shouldBe buildString {
                appendLine("Found multiple scan results for the same provenance and scanner.")
                appendLine("Scanner:")
                appendLine(scanner.toYaml())
                appendLine("Provenance:")
                append(provenance.toYaml())
            }

            // Shared provenance and different scanners.
            ScannerRun.EMPTY.copy(
                provenances = provenances,
                scanResults = setOf(
                    ScanResult(
                        provenance = provenance,
                        scanner = scanner,
                        summary = ScanSummary.EMPTY.copy(
                            licenseFindings = setOf(
                                LicenseFinding("MIT", TextLocation("file1.txt", 1, 1))
                            )
                        )
                    ),
                    ScanResult(
                        provenance = provenance,
                        scanner = otherScanner,
                        summary = ScanSummary.EMPTY.copy(
                            licenseFindings = setOf(
                                LicenseFinding("MIT", TextLocation("file2.txt", 1, 1))
                            )
                        )
                    )
                )
            )

            // Different provenance and shared scanner.
            ScannerRun.EMPTY.copy(
                provenances = provenances,
                scanResults = setOf(
                    ScanResult(
                        provenance = provenance,
                        scanner = scanner,
                        summary = ScanSummary.EMPTY.copy(
                            licenseFindings = setOf(
                                LicenseFinding("MIT", TextLocation("file1.txt", 1, 1))
                            )
                        )
                    ),
                    ScanResult(
                        provenance = otherProvenance,
                        scanner = scanner,
                        summary = ScanSummary.EMPTY.copy(
                            licenseFindings = setOf(
                                LicenseFinding("MIT", TextLocation("file2.txt", 1, 1))
                            )
                        )
                    )
                )
            )
        }

        "error on duplicate provenance file lists" {
            val provenance = RepositoryProvenance(
                VcsInfo(type = VcsType.GIT, url = "https://github.com/example.git", revision = "revision"),
                "revision"
            )

            shouldThrow<IllegalArgumentException> {
                ScannerRun.EMPTY.copy(
                    provenances = setOf(
                        ProvenanceResolutionResult(
                            id = Identifier("maven::other_example:1.0"),
                            packageProvenance = provenance
                        )
                    ),
                    files = setOf(
                        FileList(
                            provenance = provenance,
                            files = setOf(
                                Entry(
                                    path = "vcs/path/file1.txt",
                                    sha1 = "1111111111111111111111111111111111111111"
                                )
                            )
                        ),
                        FileList(
                            provenance = provenance,
                            files = setOf(
                                Entry(
                                    path = "some/dir/file2.txt",
                                    sha1 = "2222222222222222222222222222222222222222"
                                )
                            )
                        )
                    )
                )
            }.message shouldBe "Found multiple file lists for the same provenance:\n" +
                provenance.toYaml()
        }
    }

    "getFileList()" should {
        "filter by VCS path and merge sub-repository lists as expected" {
            val id = Identifier("a:b:c:1.0.0")

            val packageProvenance = RepositoryProvenance(
                vcsInfo = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://example.org/some/project.git",
                    revision = "master",
                    path = "vcs/path"
                ),
                resolvedRevision = "000000000000000000000000000000000000"
            )

            val subRepositoryVcsInfo = VcsInfo(
                type = VcsType.GIT,
                url = "https://example.org/some/subrepository.git",
                revision = "1111111111111111111111111111111111111111"
            )

            val run = ScannerRun.EMPTY.copy(
                provenances = setOf(
                    ProvenanceResolutionResult(
                        id = id,
                        packageProvenance = packageProvenance,
                        subRepositories = mapOf("vcs/path/sub/repository" to subRepositoryVcsInfo)
                    )
                ),
                files = setOf(
                    FileList(
                        provenance = packageProvenance.clearVcsPath().alignRevisions(),
                        files = setOf(
                            Entry(
                                path = "vcs/path/file1.txt",
                                sha1 = "1111111111111111111111111111111111111111"
                            ),
                            Entry(
                                path = "other/path/file2.txt",
                                sha1 = "2222222222222222222222222222222222222222"
                            ),
                            Entry(
                                path = "file3.txt",
                                sha1 = "3333333333333333333333333333333333333333"
                            )
                        )
                    ),
                    FileList(
                        provenance = RepositoryProvenance(
                            vcsInfo = subRepositoryVcsInfo,
                            resolvedRevision = subRepositoryVcsInfo.revision
                        ),
                        files = setOf(
                            Entry(
                                path = "some/dir/file4.txt",
                                sha1 = "4444444444444444444444444444444444444444"
                            )
                        )
                    )
                )
            )

            run.getFileList(id) shouldNotBeNull {
                files should containExactlyInAnyOrder(
                    Entry("vcs/path/file1.txt", "1111111111111111111111111111111111111111"),
                    Entry("vcs/path/sub/repository/some/dir/file4.txt", "4444444444444444444444444444444444444444")
                )
            }
        }
    }

    "getAllIssues()" should {
        "combine issues from scan results and issues map" {
            val provenance1 = RepositoryProvenance(
                VcsInfo(type = VcsType.GIT, url = "https://github.com/example.git", revision = "revision"),
                "revision"
            )

            val issue1 = Issue(source = "Scanner", message = "Issue 1")
            val issue2 = Issue(source = "Scanner", message = "Issue 2")
            val issue3 = Issue(source = "Scanner", message = "Issue 3")

            val run = ScannerRun.EMPTY.copy(
                provenances = setOf(
                    ProvenanceResolutionResult(
                        id = Identifier("maven::example:1.0"),
                        packageProvenance = provenance1
                    ),
                    ProvenanceResolutionResult(
                        id = Identifier("maven::example2:1.0"),
                        packageProvenance = RepositoryProvenance(
                            VcsInfo(type = VcsType.GIT, url = "https://github.com/example2.git", revision = "revision"),
                            "revision"
                        )
                    )
                ),
                scanResults = setOf(
                    ScanResult(
                        provenance1,
                        ScannerDetails("scanner", "1.0.0", "configuration"),
                        ScanSummary.EMPTY.copy(issues = listOf(issue1))
                    )
                ),
                issues = mapOf(
                    Identifier("maven::example:1.0") to setOf(issue3),
                    Identifier("maven::example2:1.0") to setOf(issue2)
                ),
                scanners = mapOf(Identifier("maven::example:1.0") to setOf("scanner"))
            )

            run.getAllIssues() should containExactly(
                Identifier("maven::example:1.0") to setOf(issue1, issue3),
                Identifier("maven::example2:1.0") to setOf(issue2)
            )
        }
    }

    "+" should {
        "not merge runs with different config" {
            val config1 = ScannerConfiguration(skipConcluded = true)
            val config2 = ScannerConfiguration(skipConcluded = false)

            val run1 = ScannerRun.EMPTY.copy(
                config = config1
            )

            val run2 = ScannerRun.EMPTY.copy(
                config = config2
            )

            shouldThrow<IllegalArgumentException> {
                run1 + run2
            }.message shouldBe "Cannot merge ScannerRuns with different configurations: $config1 != $config2."
        }

        "not merge runs with conflicting environments" {
            val env1 = Environment("1.0.0", "x86_64")
            val env2 = Environment("2.0.0", "x86_64")

            val run1 = ScannerRun.EMPTY.copy(
                environment = env1
            )

            val run2 = ScannerRun.EMPTY.copy(
                environment = env2
            )

            shouldThrow<IllegalArgumentException> {
                run1 + run2
            }.message shouldBe
                "Cannot merge Environments with different ORT versions: '${env1.ortVersion}' != '${env2.ortVersion}'."
        }

        "combine start and end time" {
            val run1 = ScannerRun.EMPTY.copy(
                startTime = Instant.parse("2023-01-01T00:00:00Z"),
                endTime = Instant.parse("2023-01-01T01:00:00Z")
            )

            val run2 = ScannerRun.EMPTY.copy(
                startTime = Instant.parse("2023-01-01T00:30:00Z"),
                endTime = Instant.parse("2023-01-01T02:00:00Z")
            )

            val mergedRun = run1 + run2

            mergedRun.startTime shouldBe Instant.parse("2023-01-01T00:00:00Z")
            mergedRun.endTime shouldBe Instant.parse("2023-01-01T02:00:00Z")
        }

        "combine provenances" {
            val provenance = RepositoryProvenance(
                VcsInfo(type = VcsType.GIT, url = "https://github.com/example.git", revision = "revision"),
                "revision"
            )
            val provenanceResult1 = ProvenanceResolutionResult(
                id = Identifier("maven::example:1.0"),
                packageProvenance = provenance
            )
            val provenanceResult2 = ProvenanceResolutionResult(
                id = Identifier("maven::example2:1.0"),
                packageProvenance = provenance
            )

            val run1 = ScannerRun.EMPTY.copy(
                provenances = setOf(provenanceResult1)
            )
            val run2 = ScannerRun.EMPTY.copy(
                provenances = setOf(provenanceResult2)
            )

            val mergedRun = run1 + run2

            mergedRun.provenances should containExactlyInAnyOrder(
                ProvenanceResolutionResult(
                    id = Identifier("maven::example:1.0"),
                    packageProvenance = provenance
                ),
                ProvenanceResolutionResult(
                    id = Identifier("maven::example2:1.0"),
                    packageProvenance = provenance
                )
            )
        }

        "combine issues" {
            val issue1 = Issue(
                source = "Scanner",
                message = "Issue 1"
            )
            val issue2 = Issue(
                source = "Scanner",
                message = "Issue 2"
            )
            val issue3 = Issue(
                source = "Scanner",
                message = "Issue 3"
            )

            val issues1 = mapOf(
                Identifier("maven::example:1.0") to setOf(issue1),
                Identifier("maven::example2:1.0") to setOf(issue2)
            )
            val issues2 = mapOf(
                Identifier("maven::example:1.0") to setOf(issue3),
                Identifier("maven::example2:1.0") to setOf(issue2)
            )

            val run1 = ScannerRun.EMPTY.copy(
                issues = issues1
            )
            val run2 = ScannerRun.EMPTY.copy(
                issues = issues2
            )

            val mergedRun = run1 + run2

            mergedRun.issues should containExactly(
                Identifier("maven::example:1.0") to setOf(issue1, issue3),
                Identifier("maven::example2:1.0") to setOf(issue2)
            )
        }

        "combine scanners" {
            val scanners1 = mapOf(
                Identifier("maven::example:1.0") to setOf("scanner1"),
                Identifier("maven::example2:1.0") to setOf("scanner2")
            )
            val scanners2 = mapOf(
                Identifier("maven::example:1.0") to setOf("scanner3"),
                Identifier("maven::example2:1.0") to setOf("scanner2")
            )

            val run1 = ScannerRun.EMPTY.copy(
                scanners = scanners1
            )
            val run2 = ScannerRun.EMPTY.copy(
                scanners = scanners2
            )

            val mergedRun = run1 + run2

            mergedRun.scanners should containExactly(
                Identifier("maven::example:1.0") to setOf("scanner1", "scanner3"),
                Identifier("maven::example2:1.0") to setOf("scanner2")
            )
        }

        "combine file lists" {
            val provenance1 = RepositoryProvenance(
                VcsInfo(type = VcsType.GIT, url = "https://github.com/example.git", revision = "revision"),
                "revision"
            )
            val provenance2 = RepositoryProvenance(
                VcsInfo(type = VcsType.GIT, url = "https://github.com/example.git", revision = "other_revision"),
                "other_revision"
            )

            val provenances = setOf(
                ProvenanceResolutionResult(
                    id = Identifier("maven::example:1.0"),
                    packageProvenance = provenance1
                ),
                ProvenanceResolutionResult(
                    id = Identifier("maven::other_example:1.0"),
                    packageProvenance = provenance2
                )
            )

            val fileList1 = FileList(
                provenance = provenance1,
                files = setOf(
                    Entry(
                        path = "vcs/path/file1.txt",
                        sha1 = "1111111111111111111111111111111111111111"
                    )
                )
            )
            val fileList2 = FileList(
                provenance = provenance2,
                files = setOf(
                    Entry(
                        path = "some/dir/file2.txt",
                        sha1 = "2222222222222222222222222222222222222222"
                    )
                )
            )
            val fileList3 = FileList(
                provenance = provenance1,
                files = setOf(
                    Entry(
                        path = "some/dir/file3.txt",
                        sha1 = "3333333333333333333333333333333333333333"
                    )
                )
            )

            val run1 = ScannerRun.EMPTY.copy(
                provenances = provenances,
                files = setOf(fileList1, fileList2)
            )
            val run2 = ScannerRun.EMPTY.copy(
                provenances = provenances,
                files = setOf(fileList2, fileList3)
            )

            val mergedRun = run1 + run2

            mergedRun.files should containExactlyInAnyOrder(
                FileList(
                    provenance = provenance1,
                    files = setOf(
                        Entry("vcs/path/file1.txt", "1111111111111111111111111111111111111111"),
                        Entry("some/dir/file3.txt", "3333333333333333333333333333333333333333")
                    )
                ),
                FileList(
                    provenance = provenance2,
                    files = setOf(
                        Entry("some/dir/file2.txt", "2222222222222222222222222222222222222222")
                    )
                )
            )
        }

        "combine scan results" {
            val provenance1 = RepositoryProvenance(
                VcsInfo(type = VcsType.GIT, url = "https://github.com/example.git", revision = "revision"),
                "revision"
            )
            val provenance2 = RepositoryProvenance(
                VcsInfo(type = VcsType.GIT, url = "https://github.com/example.git", revision = "other_revision"),
                "other_revision"
            )

            val provenances = setOf(
                ProvenanceResolutionResult(
                    id = Identifier("maven::example:1.0"),
                    packageProvenance = provenance1
                ),
                ProvenanceResolutionResult(
                    id = Identifier("maven::other_example:1.0"),
                    packageProvenance = provenance2
                )
            )

            val scanner1 = ScannerDetails("scanner1", "1.0.0", "configuration")
            val scanner2 = ScannerDetails("scanner2", "1.0.0", "configuration")

            val scanResult1 = ScanResult(
                provenance = provenance1,
                scanner = scanner1,
                summary = ScanSummary.EMPTY.copy(
                    licenseFindings = setOf(
                        LicenseFinding("MIT", TextLocation("file1.txt", 1, 1))
                    )
                )
            )
            val scanResult2 = ScanResult(
                provenance = provenance2,
                scanner = scanner2,
                summary = ScanSummary.EMPTY.copy(
                    licenseFindings = setOf(
                        LicenseFinding("Apache-2.0", TextLocation("file2.txt", 1, 1))
                    )
                )
            )
            val scanResult3 = ScanResult(
                provenance = provenance1,
                scanner = scanner1,
                summary = ScanSummary.EMPTY.copy(
                    licenseFindings = setOf(
                        LicenseFinding("GPL-3.0", TextLocation("file3.txt", 1, 1))
                    )
                )
            )

            val run1 = ScannerRun.EMPTY.copy(
                provenances = provenances,
                scanResults = setOf(scanResult1, scanResult2)
            )
            val run2 = ScannerRun.EMPTY.copy(
                provenances = provenances,
                scanResults = setOf(scanResult2, scanResult3)
            )

            val mergedRun = run1 + run2

            mergedRun.scanResults should containExactlyInAnyOrder(
                ScanResult(
                    provenance = provenance1,
                    scanner = scanner1,
                    summary = ScanSummary.EMPTY.copy(
                        licenseFindings = setOf(
                            LicenseFinding("MIT", TextLocation("file1.txt", 1, 1)),
                            LicenseFinding("GPL-3.0", TextLocation("file3.txt", 1, 1))
                        )
                    )
                ),
                ScanResult(
                    provenance = provenance2,
                    scanner = scanner2,
                    summary = ScanSummary.EMPTY.copy(
                        licenseFindings = setOf(
                            LicenseFinding("Apache-2.0", TextLocation("file2.txt", 1, 1))
                        )
                    )
                )
            )
        }
    }
})
