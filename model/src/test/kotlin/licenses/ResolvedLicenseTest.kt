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

package org.ossreviewtoolkit.model.licenses

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.TextLocation

class ResolvedLicenseTest : WordSpec({
    "Copyright findings across different files" should {
        "be consolidated without processing if their statements are exactly the same" {
            val originalFindings = listOf(
                ResolvedCopyrightFinding(
                    statement = "Copyright (C) 2022 The ORT Project Authors",
                    location = TextLocation(
                        path = "/path/to/file/A",
                        line = 2
                    ),
                    matchingPathExcludes = emptyList()
                ),
                ResolvedCopyrightFinding(
                    statement = "Copyright (C) 2022 The ORT Project Authors",
                    location = TextLocation(
                        path = "/path/to/file/B",
                        line = 2
                    ),
                    matchingPathExcludes = emptyList()
                )
            )

            val resolvedCopyrights = originalFindings.toResolvedCopyrights(process = false)

            resolvedCopyrights shouldHaveSize 1
            with(resolvedCopyrights.first()) {
                statement shouldBe "Copyright (C) 2022 The ORT Project Authors"
                findings.map { it.location.path } should containExactlyInAnyOrder("/path/to/file/A", "/path/to/file/B")
            }
        }

        "be consolidated with processing if their statements are afterwards the same" {
            val originalFindings = listOf(
                ResolvedCopyrightFinding(
                    statement = "Copyright (C) 2022 The ORT Project Authors",
                    location = TextLocation(
                        path = "/path/to/file/A",
                        line = 2
                    ),
                    matchingPathExcludes = emptyList()
                ),
                ResolvedCopyrightFinding(
                    // Note the "." at the end.
                    statement = "Copyright (C) 2022 The ORT Project Authors.",
                    location = TextLocation(
                        path = "/path/to/file/B",
                        line = 2
                    ),
                    matchingPathExcludes = emptyList()
                )
            )

            val resolvedCopyrights = originalFindings.toResolvedCopyrights(process = true)

            resolvedCopyrights shouldHaveSize 1
            with(resolvedCopyrights.first()) {
                statement shouldBe "Copyright (C) 2022 The ORT Project Authors"
                findings.map { it.location.path } should containExactlyInAnyOrder("/path/to/file/A", "/path/to/file/B")
            }
        }
    }
})
