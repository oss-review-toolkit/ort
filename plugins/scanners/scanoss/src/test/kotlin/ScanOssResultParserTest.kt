/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.scanoss.dto.LicenseDetails
import com.scanoss.utils.JsonUtils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.Snippet
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseIdExpression

class ScanOssResultParserTest : WordSpec({
    "getUniqueLicenseDetails()" should {
        "deduplicate complex license expressions" {
            val uniqueLicenses = getUniqueLicenseExpression(
                listOf(
                    LicenseDetails.builder().name("MIT").build(),
                    LicenseDetails.builder().name("MIT").build(),
                    LicenseDetails.builder().name("GPL-2.0-only").build(),
                    LicenseDetails.builder().name("GPL-2.0-only WITH Linux-syscall-note").build(),
                    LicenseDetails.builder().name("GPL-2.0-only AND MIT").build()
                )
            )

            val decomposed = uniqueLicenses.decompose().toList()

            val expressionStrings = decomposed.map { it.toString() }

            // Check that each license appears exactly once
            expressionStrings.count { it == "MIT" } shouldBe 1
            expressionStrings.count { it == "GPL-2.0-only" } shouldBe 1
            expressionStrings.count { it == "GPL-2.0-only WITH Linux-syscall-note" } shouldBe 1

            // Ensure no unexpected elements
            expressionStrings.size shouldBe 3
        }

        "handle empty license list" {
            val emptyLicenses = getUniqueLicenseExpression(listOf())

            // Verify empty license list returns NOASSERTION
            emptyLicenses shouldBe SpdxLicenseIdExpression(SpdxConstants.NOASSERTION)
        }
    }

    "generateSummary()" should {
        "properly summarize JUnit 4.12 findings" {
            val results = File("src/test/assets/scanoss-junit-4.12.json").readText().let {
                JsonUtils.toScanFileResultsFromObject(JsonUtils.toJsonObject(it))
            }

            val time = Instant.now()
            val summary = generateSummary(time, time, results)

            summary.licenses.map { it.toString() } should containExactlyInAnyOrder(
                "Apache-2.0",
                "EPL-1.0",
                "MIT",
                "LicenseRef-scancode-free-unknown",
                "LicenseRef-scanoss-SSPL"
            )

            summary.licenseFindings should haveSize(201)
            summary.licenseFindings shouldContain LicenseFinding(
                license = "Apache-2.0",
                location = TextLocation(
                    path = "hopscotch-rails-0.1.2.1/vendor/assets/javascripts/hopscotch.js",
                    startLine = TextLocation.UNKNOWN_LINE,
                    endLine = TextLocation.UNKNOWN_LINE
                ),
                score = 100.0f
            )

            summary.copyrightFindings should haveSize(7)
            summary.copyrightFindings shouldContain CopyrightFinding(
                statement = "Copyright 2013 LinkedIn Corp.",
                location = TextLocation(
                    path = "hopscotch-rails-0.1.2.1/vendor/assets/javascripts/hopscotch.js",
                    startLine = TextLocation.UNKNOWN_LINE,
                    endLine = TextLocation.UNKNOWN_LINE
                )
            )
        }

        "properly summarize Semver4j 3.1.0 with snippet findings" {
            val results = File("src/test/assets/scanoss-semver4j-3.1.0-with-snippet.json").readText().let {
                JsonUtils.toScanFileResultsFromObject(JsonUtils.toJsonObject(it))
            }

            val time = Instant.now()
            val summary = generateSummary(time, time, results)

            summary.licenses.map { it.toString() } should containExactlyInAnyOrder(
                "Apache-2.0",
                "BSD-2-Clause",
                "EPL-2.0",
                "LicenseRef-scanoss-SSPL",
                "MIT"
            )

            summary.licenseFindings should haveSize(11)
            summary.licenseFindings shouldContain LicenseFinding(
                license = "Apache-2.0",
                location = TextLocation(
                    path = "com/vdurmont/semver4j/Range.java",
                    startLine = TextLocation.UNKNOWN_LINE,
                    endLine = TextLocation.UNKNOWN_LINE
                ),
                score = 100.0f
            )

            summary.snippetFindings should haveSize(1)
            summary.snippetFindings should containExactly(
                SnippetFinding(
                    TextLocation("src/main/java/com/vdurmont/semver4j/Requirement.java", 1, 710),
                    setOf(
                        Snippet(
                            98.0f,
                            TextLocation(
                                "https://osskb.org/api/file_contents/6ff2427335b985212c9b79dfa795799f",
                                1,
                                710
                            ),
                            RepositoryProvenance(
                                VcsInfo(VcsType.GIT, "https://github.com/vdurmont/semver4j.git", ""),
                                "."
                            ),
                            "pkg:github/vdurmont/semver4j",
                            SpdxExpression.parse("CC-BY-SA-2.0"),
                            additionalData = mapOf(
                                "release_date" to "2019-09-13",
                                "all_purls" to "pkg:github/vdurmont/semver4j"
                            )
                        )
                    )
                )
            )
        }

        "should handle multiple PURLs by selecting first as primary and preserving all in metadata" {
            val results = File("src/test/assets/scanoss-multiple-purls.json").readText().let {
                JsonUtils.toScanFileResultsFromObject(JsonUtils.toJsonObject(it))
            }

            val time = Instant.now()
            val summary = generateSummary(time, time, results)

            // Should have one finding per source location, not per PURL.
            summary.snippetFindings should haveSize(2)

            with(summary.snippetFindings.first()) {
                // Check source location (local file).
                sourceLocation shouldBe TextLocation("hung_task.c", 12, 150)

                // Should use first PURL as primary identifier.
                snippets should haveSize(1)
                snippets.first().purl shouldBe "pkg:github/kdrag0n/proton_bluecross"

                // Should preserve all PURLs in additionalData.
                snippets.first().additionalData["all_purls"] shouldBe
                    "pkg:github/kdrag0n/proton_bluecross pkg:github/fake/fake_repository"

                // Check OSS location.
                snippets.first().location shouldBe
                    TextLocation("https://api.scanoss.com/file_contents/581734935cfbe570d280a1265aaa2a6b", 10, 148)
            }

            // Verify same behavior for second snippet.
            with(summary.snippetFindings.last()) {
                sourceLocation shouldBe TextLocation("hung_task.c", 540, 561)
                snippets.first().purl shouldBe "pkg:github/kdrag0n/proton_bluecross"
                snippets.first().location shouldBe
                    TextLocation("https://api.scanoss.com/file_contents/581734935cfbe570d280a1265aaa2a6b", 86, 107)
            }
        }
    }
})
