/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.scanoss

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.SnippetChoices
import org.ossreviewtoolkit.model.config.snippet.Choice
import org.ossreviewtoolkit.model.config.snippet.Given
import org.ossreviewtoolkit.model.config.snippet.Provenance
import org.ossreviewtoolkit.model.config.snippet.SnippetChoice
import org.ossreviewtoolkit.model.config.snippet.SnippetChoiceReason

// Sample files in the results.
private const val FILE_1 = "a.java"
private const val FILE_2 = "b.java"

// A sample purl in the results.
private const val PURL_1 = "pkg:github/fakeuser/fakepackage1@1.0.0"

class ScanOssTest : WordSpec({
    "processSnippetChoices()" should {
        "create empty rules when no snippet choices exist" {
            val scanoss = createScanOss(createScanOssConfig())

            val emptySnippetChoices: List<SnippetChoices> = listOf()

            val rules = scanoss.processSnippetChoices(emptySnippetChoices)

            rules.ignoreRules should beEmpty()
            rules.removeRules should beEmpty()
            rules.includeRules should beEmpty()
            rules.replaceRules should beEmpty()
        }

        "create an include rule for snippet choices with ORIGINAL finding" {
            val vcsInfo = createVcsInfo()
            val scanoss = createScanOss(createScanOssConfig())

            val location = TextLocation(FILE_1, 10, 20)
            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(
                    location,
                    PURL_1,
                    "This is an original finding"
                )
            )

            val rules = scanoss.processSnippetChoices(snippetChoices)

            rules.includeRules.shouldBeSingleton { rule ->
                rule.purl shouldBe PURL_1
                rule.path shouldBe FILE_1
            }

            rules.removeRules should beEmpty()
            rules.ignoreRules should beEmpty()
            rules.replaceRules should beEmpty()
        }

        "create a remove rule for snippet choices with NOT_FINDING reason" {
            val vcsInfo = createVcsInfo()

            val scanoss = createScanOss(createScanOssConfig())

            val location = TextLocation(FILE_2, 15, 30)
            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(
                    location,
                    null, // null PURL for NOT_FINDING.
                    "This is not a relevant finding"
                )
            )

            val rules = scanoss.processSnippetChoices(snippetChoices)

            rules.removeRules.shouldBeSingleton { rule ->
                rule.path shouldBe FILE_2
                rule.startLine shouldBe 15
                rule.endLine shouldBe 30
            }

            rules.includeRules should beEmpty()
            rules.ignoreRules should beEmpty()
            rules.replaceRules should beEmpty()
        }

        "handle multiple snippet choices with different reasons correctly" {
            val vcsInfo = createVcsInfo()
            val scanoss = createScanOss(createScanOssConfig())

            val location1 = TextLocation(FILE_1, 10, 20)
            val location2 = TextLocation(FILE_2, 15, 30)

            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(
                    location1,
                    PURL_1,
                    "This is an original finding"
                ),
                createSnippetChoice(
                    location2,
                    null,
                    "This is not a relevant finding"
                )
            )

            val rules = scanoss.processSnippetChoices(snippetChoices)

            rules.includeRules.shouldBeSingleton { rule ->
                rule.purl shouldBe PURL_1
                rule.path shouldBe FILE_1
            }

            rules.removeRules.shouldBeSingleton { rule ->
                rule.path shouldBe FILE_2
                rule.startLine shouldBe 15
                rule.endLine shouldBe 30
            }

            rules.ignoreRules should beEmpty()
            rules.replaceRules should beEmpty()
        }

        "create a remove rule without line ranges when snippet choice has UNKNOWN_LINE (-1) values" {
            val vcsInfo = createVcsInfo()
            val scanoss = createScanOss(createScanOssConfig())

            // Create a TextLocation with -1 for start and end lines.
            val location = TextLocation(FILE_2, TextLocation.UNKNOWN_LINE, TextLocation.UNKNOWN_LINE)
            val snippetChoices = createSnippetChoices(
                vcsInfo.url,
                createSnippetChoice(
                    location,
                    null, // null PURL for NOT_FINDING.
                    "This is a not relevant finding with no line ranges"
                )
            )

            val rules = scanoss.processSnippetChoices(snippetChoices)

            rules.removeRules.shouldBeSingleton { rule ->
                rule.path shouldBe FILE_2
                rule.startLine shouldBe null
                rule.endLine shouldBe null
            }

            rules.includeRules should beEmpty()
            rules.ignoreRules should beEmpty()
            rules.replaceRules should beEmpty()
        }
    }
})

private fun createSnippetChoices(provenanceUrl: String, vararg snippetChoices: SnippetChoice) =
    listOf(SnippetChoices(Provenance(provenanceUrl), snippetChoices.asList()))

private fun createSnippetChoice(location: TextLocation, purl: String? = null, comment: String) =
    SnippetChoice(
        Given(
            location
        ),
        Choice(
            purl,
            if (purl == null) SnippetChoiceReason.NO_RELEVANT_FINDING else SnippetChoiceReason.ORIGINAL_FINDING,
            comment
        )
    )
