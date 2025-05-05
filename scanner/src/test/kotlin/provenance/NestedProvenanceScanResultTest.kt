/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.provenance

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo

class NestedProvenanceScanResultTest : WordSpec({
    "filterByVcsPath" should {
        "not change the result if the path is empty" {
            nestedProvenanceScanResult.filterByVcsPath("") shouldBe nestedProvenanceScanResult
        }

        "remove repositories which are on a different branch than the provided path" {
            val filteredResult = nestedProvenanceScanResult.filterByVcsPath("submodules/a")
            val repositories = filteredResult.nestedProvenance.getRepositoryUrls()

            repositories should containExactlyInAnyOrder(
                provenanceRoot.vcsInfo.url,
                provenanceSubmoduleA.vcsInfo.url
            )
        }

        "keep a nested repository that contains the provided path" {
            val filteredResult = nestedProvenanceScanResult.filterByVcsPath("submodules/a/dir")
            val repositories = filteredResult.nestedProvenance.getRepositoryUrls()

            repositories should containExactlyInAnyOrder(
                provenanceRoot.vcsInfo.url,
                provenanceSubmoduleA.vcsInfo.url
            )
        }

        "update the VCS paths of the provenances" {
            val filteredResult = nestedProvenanceScanResult.filterByVcsPath("submodules/a/dir")

            (filteredResult.nestedProvenance.root as RepositoryProvenance).vcsInfo.path shouldBe "submodules/a/dir"
            filteredResult.nestedProvenance.subRepositories.getValue("submodules/a").vcsInfo.path shouldBe "dir"

            filteredResult.nestedProvenance.allProvenances.forEach {
                filteredResult.scanResults.getValue(it).single().provenance shouldBe it
            }
        }

        "filter findings correctly" {
            nestedProvenanceScanResult.filterByVcsPath("submodules").let { filteredResult ->
                with(filteredResult.scanResults.getValue(filteredResult.nestedProvenance.root).single()) {
                    summary.licenseFindings shouldNotContain LicenseFinding("Apache-2.0", TextLocation("file", 1))

                    summary.licenseFindings should containExactly(
                        LicenseFinding("Apache-2.0", TextLocation("submodules/file", 1))
                    )
                }

                filteredResult.scanResults.getValue(provenanceSubmoduleA).single() shouldBe scanResultSubmoduleA
                filteredResult.scanResults.getValue(provenanceSubmoduleB).single() shouldBe scanResultSubmoduleB
            }
        }
    }
})

private val provenanceRoot = RepositoryProvenance(
    vcsInfo = VcsInfo.EMPTY.copy(url = "https://example.com/root.git"),
    resolvedRevision = "revision"
)

private val provenanceSubmoduleA = RepositoryProvenance(
    vcsInfo = VcsInfo.EMPTY.copy(url = "https://example.com/submoduleA.git"),
    resolvedRevision = "revision"
)

private val provenanceSubmoduleB = RepositoryProvenance(
    vcsInfo = VcsInfo.EMPTY.copy(url = "https://example.com/submoduleB.git"),
    resolvedRevision = "revision"
)

private val nestedProvenance = NestedProvenance(
    root = provenanceRoot,
    subRepositories = mapOf(
        "submodules/a" to provenanceSubmoduleA,
        "submodules/b" to provenanceSubmoduleB
    )
)

private val scannerDetails = ScannerDetails(name = "scanner", version = "1.0.0", configuration = "")

private val scanResultRoot = ScanResult(
    provenance = provenanceRoot,
    scanner = scannerDetails,
    summary = ScanSummary.EMPTY.copy(
        licenseFindings = setOf(
            LicenseFinding("Apache-2.0", TextLocation("file", 1)),
            LicenseFinding("Apache-2.0", TextLocation("submodules/file", 1))
        ),
        copyrightFindings = setOf(
            CopyrightFinding("Copyright", TextLocation("file", 1)),
            CopyrightFinding("Copyright", TextLocation("submodules/file", 1))
        )
    )
)

private val scanResultSubmoduleA = ScanResult(
    provenance = provenanceSubmoduleA,
    scanner = scannerDetails,
    summary = ScanSummary.EMPTY.copy(
        licenseFindings = setOf(
            LicenseFinding("Apache-2.0", TextLocation("fileA", 1)),
            LicenseFinding("Apache-2.0", TextLocation("dir/fileA", 1))
        ),
        copyrightFindings = setOf(
            CopyrightFinding("Copyright", TextLocation("fileA", 1)),
            CopyrightFinding("Copyright", TextLocation("dir/fileA", 1))
        )
    )
)

private val scanResultSubmoduleB = ScanResult(
    provenance = provenanceSubmoduleA,
    scanner = scannerDetails,
    summary = ScanSummary.EMPTY.copy(
        licenseFindings = setOf(
            LicenseFinding("Apache-2.0", TextLocation("fileB", 1)),
            LicenseFinding("Apache-2.0", TextLocation("dir/fileB", 1))
        ),
        copyrightFindings = setOf(
            CopyrightFinding("Copyright", TextLocation("fileB", 1)),
            CopyrightFinding("Copyright", TextLocation("dir/fileB", 1))
        )
    )
)

private val nestedProvenanceScanResult = NestedProvenanceScanResult(
    nestedProvenance = nestedProvenance,
    scanResults = mapOf(
        provenanceRoot to listOf(scanResultRoot),
        provenanceSubmoduleA to listOf(scanResultSubmoduleA),
        provenanceSubmoduleB to listOf(scanResultSubmoduleB)
    )
)

private fun NestedProvenance.getRepositoryUrls() = allProvenances.map { (it as RepositoryProvenance).vcsInfo.url }
