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

import org.ossreviewtoolkit.model.FileList.Entry
import org.ossreviewtoolkit.model.utils.alignRevisions
import org.ossreviewtoolkit.model.utils.clearVcsPath

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
            val ex = shouldThrow<IllegalArgumentException> {
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
            }

            val expectedMessage = buildString {
                appendLine("Found multiple scan results for the same provenance and scanner.")
                appendLine("Scanner:")
                appendLine(scanner.toYaml())
                appendLine("Provenance:")
                append(provenance.toYaml())
            }.trimEnd()

            ex.message!!.trimEnd() shouldBe expectedMessage

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

            val ex = shouldThrow<IllegalArgumentException> {
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
            }

            val expectedMessage = buildString {
                appendLine("Found multiple file lists for the same provenance:")
                append(provenance.toYaml())
            }.trimEnd()

            ex.message!!.trimEnd() shouldBe expectedMessage
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
})
