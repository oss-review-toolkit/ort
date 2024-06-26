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

package org.ossreviewtoolkit.plugins.scanners.fossid

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlinx.coroutines.flow.flowOf

import org.ossreviewtoolkit.clients.fossid.PolymorphicList
import org.ossreviewtoolkit.clients.fossid.model.result.MatchType
import org.ossreviewtoolkit.clients.fossid.model.result.MatchedLines
import org.ossreviewtoolkit.clients.fossid.model.result.Snippet
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.TextLocation

class FossIdSnippetMappingTest : WordSpec({
    "mapSnippetFindings" should {
        "group snippets by source file location" {
            val issues = mutableListOf<Issue>()
            val listSnippets = flowOf(
                "src/main/java/Tokenizer.java" to
                    setOf(
                        createSnippet(
                            1,
                            MatchType.FULL,
                            "pkg:github/vdurmont/semver4j@3.1.0",
                            "MIT",
                            "src/main/java/com/vdurmont/semver4j/Tokenizer.java"
                        ),
                        createSnippet(
                            2,
                            MatchType.FULL,
                            "pkg:maven/com.vdurmont/semver4j@3.1.0",
                            "MIT",
                            "com/vdurmont/semver4j/Tokenizer.java"
                        )
                    ),
                "src/main/java/com/vdurmont/semver4j/Requirement.java" to
                    setOf(
                        createSnippet(
                            3,
                            MatchType.PARTIAL,
                            "pkg:github/vdurmont/semver4j@3.1.0",
                            "MIT",
                            "com/vdurmont/semver4j/Requirement.java"
                        )
                    )
            )
            val localFile = ((1..24) + (45..675)).toPolymorphicList()
            val remoteFile = (1..655).toPolymorphicList()
            // There is no matched line information for full match snippets.
            val snippetMatchedLines = mapOf(3 to MatchedLines(localFile, remoteFile))
            val rawResults = RawResults(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                listSnippets,
                snippetMatchedLines
            )

            val mappedSnippets = mapSnippetFindings(
                rawResults,
                500,
                issues,
                emptyMap(),
                emptyList(),
                mutableSetOf()
            )

            issues should beEmpty()
            mappedSnippets shouldHaveSize 3
            mappedSnippets.first().apply {
                sourceLocation shouldBe TextLocation("src/main/java/Tokenizer.java", TextLocation.UNKNOWN_LINE)
                snippets shouldHaveSize 2
                snippets.map { it.purl } should containExactly(
                    "pkg:github/vdurmont/semver4j@3.1.0",
                    "pkg:maven/com.vdurmont/semver4j@3.1.0"
                )
            }

            mappedSnippets.elementAtOrNull(1) shouldNotBeNull {
                sourceLocation shouldBe TextLocation("src/main/java/com/vdurmont/semver4j/Requirement.java", 1, 24)
                snippets.map { it.purl } should containExactly("pkg:github/vdurmont/semver4j@3.1.0")
            }

            mappedSnippets.elementAtOrNull(2) shouldNotBeNull {
                sourceLocation shouldBe TextLocation("src/main/java/com/vdurmont/semver4j/Requirement.java", 45, 675)
                snippets.map { it.purl } should containExactly("pkg:github/vdurmont/semver4j@3.1.0")
            }
        }
    }
})

private fun createSnippet(id: Int, matchType: MatchType, purl: String, license: String, file: String) =
    Snippet(
        id = id,
        created = "",
        scanId = 1,
        scanFileId = 1,
        fileId = 1,
        matchType = matchType,
        reason = null,
        author = null,
        artifact = null,
        version = null,
        purl = purl,
        artifactLicense = license,
        artifactLicenseCategory = null,
        releaseDate = null,
        mirror = null,
        file = file,
        fileLicense = null,
        url = "",
        hits = null,
        size = null,
        updated = null,
        cpe = null,
        score = "1.0",
        matchFileId = null,
        classification = null,
        highlighting = null
    )

private fun IntRange.toPolymorphicList() = toList().toPolymorphicList()
private fun List<Int>.toPolymorphicList() = PolymorphicList(this)
