/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.evaluator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryConfiguration

class SnippetRuleTest : WordSpec({
    "isExcluded()" should {
        "return false if the snippet is not excluded" {
            val matcher = createSnippetRule(
                snippetFindingFindingPath = "path/to/snippet.py",
                isExcluded = false
            ).isExcluded()

            matcher.matches() shouldBe false
        }

        "return true if the snippet is excluded" {
            val matcher = createSnippetRule(
                snippetFindingFindingPath = "path/to/snippet.py",
                isExcluded = true
            ).isExcluded()

            matcher.matches() shouldBe true
        }
    }

    "isFromScanner()" should {
        val rule = createSnippetRule(
            snippetFindingFindingPath = "path/to/snippet.py",
            scannerName = "ScanOss"
        )

        "return true if the given scanner name matches" {
            rule.isFromScanner("ScanOss").matches() shouldBe true
        }

        "return false if the given scanner name does not match" {
            rule.isFromScanner("FossId").matches() shouldBe false
        }
    }
})

private fun createSnippetRule(
    snippetFindingFindingPath: String,
    isExcluded: Boolean = false,
    scannerName: String = "ScanOss"
): SnippetRule {
    val ortResult = OrtResult(
        repository = Repository(
            vcs = VcsInfo.EMPTY,
            config = RepositoryConfiguration(
                excludes = Excludes(
                    paths = if (isExcluded) {
                        listOf(PathExclude(snippetFindingFindingPath, PathExcludeReason.TEST_OF))
                    } else {
                        emptyList()
                    }
                )
            )
        )
    )

    return SnippetRule(
        ruleSet = ruleSet(ortResult),
        name = "SNIPPET_RULE",
        scannerName = scannerName,
        snippetFinding = SnippetFinding(
            sourceLocation = TextLocation(snippetFindingFindingPath, 1, 2),
            snippets = emptySet()
        )
    )
}
