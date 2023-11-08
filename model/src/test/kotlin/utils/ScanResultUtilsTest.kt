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

package org.ossreviewtoolkit.model.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.VcsInfo

class ScanResultUtilsTest : WordSpec({
    "filterByVcsPath()" should {
        "return identity if the target path is not a subdirectory of the scan result's VCS path" {
            val scanResult = createScanResult("a/b")

            listOf("", "a").forAll { path ->
                scanResult.filterByVcsPath(path) shouldBe scanResult
            }
        }

        "return filtered scan result if the target path is a subdirectory of the scan result's VCS path" {
            val scanResult = createScanResult("a/b")

            scanResult.filterByPath("a/b/c").provenance
                .shouldBeTypeOf<RepositoryProvenance>().vcsInfo.path shouldBe "a/b/c"
        }
    }
})

private fun createScanResult(vcsPath: String) =
    ScanResult(
        provenance = RepositoryProvenance(
            vcsInfo = VcsInfo.EMPTY.copy(path = vcsPath),
            resolvedRevision = "0000000000000000000000000000000000000000"
        ),
        summary = ScanSummary.EMPTY,
        scanner = ScannerDetails.EMPTY
    )
