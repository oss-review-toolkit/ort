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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should

import org.ossreviewtoolkit.utils.spdx.SpdxExpression

class ScanSummaryTest : WordSpec({
    "filterByPaths()" should {
        val summary = createSummaryWithFindingPaths(
            "a/file.txt",
            "b/c/file.txt",
            "d/file.txt"
        )

        val filteredSummary = summary.filterByPaths(listOf("a", "b/c"))

        "filter copyright findings" {
            filteredSummary.copyrightFindings.map { it.location.path } should
                containExactly("a/file.txt", "b/c/file.txt")
        }

        "filter license findings" {
            filteredSummary.licenseFindings.map { it.location.path } should
                containExactly("a/file.txt", "b/c/file.txt")
        }

        "filter snippet findings" {
            filteredSummary.snippetFindings.map { it.sourceLocation.path } should
                containExactly("a/file.txt", "b/c/file.txt")
        }
    }
})

private fun createSummaryWithFindingPaths(vararg paths: String): ScanSummary {
    fun textLocation(path: String) = TextLocation(path = path, startLine = 1, endLine = 2)

    return ScanSummary.EMPTY.copy(
        licenseFindings = paths.mapTo(mutableSetOf()) { path ->
            LicenseFinding(
                license = SpdxExpression.parse("MIT"),
                location = textLocation(path)
            )
        },
        copyrightFindings = paths.mapTo(mutableSetOf()) { path ->
            CopyrightFinding(
                statement = "Some statement",
                location = textLocation(path)
            )
        },
        snippetFindings = paths.mapTo(mutableSetOf()) { path ->
            SnippetFinding(
                sourceLocation = textLocation(path),
                snippets = emptySet()
            )
        }
    )
}
