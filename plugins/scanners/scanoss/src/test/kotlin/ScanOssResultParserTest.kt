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

import com.scanoss.utils.JsonUtils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

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
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.readResource

class ScanOssResultParserTest : WordSpec({
    "generateSummary()" should {
        "properly summarize JUnit 4.12 findings" {
            val results = readResource("/scanoss-junit-4.12.json").let {
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
                    path = "junit/4.12/src/site/resources/scripts/hopscotch-0.1.2.min.js",
                    startLine = TextLocation.UNKNOWN_LINE,
                    endLine = TextLocation.UNKNOWN_LINE
                ),
                score = 100.0f
            )

            summary.copyrightFindings should haveSize(7)
            summary.copyrightFindings shouldContain CopyrightFinding(
                statement = "Copyright 2013 LinkedIn Corp.",
                location = TextLocation(
                    path = "junit/4.12/src/site/resources/scripts/hopscotch-0.1.2.min.js",
                    startLine = TextLocation.UNKNOWN_LINE,
                    endLine = TextLocation.UNKNOWN_LINE
                )
            )
        }

        "properly summarize Semver4j 3.1.0 with snippet findings" {
            val results = readResource("/scanoss-semver4j-3.1.0-with-snippet.json").let {
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
                    path = "src/main/java/com/vdurmont/semver4j/Range.java",
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
                                "src/main/java/com/vdurmont/semver4j/Requirement.java",
                                1,
                                710
                            ),
                            RepositoryProvenance(
                                VcsInfo(VcsType.GIT, "https://github.com/vdurmont/semver4j.git", ""),
                                "."
                            ),
                            "pkg:github/vdurmont/semver4j",
                            SpdxExpression.parse("CC-BY-SA-2.0"),
                            mapOf(
                                "file_hash" to "6ff2427335b985212c9b79dfa795799f",
                                "file_url" to "https://osskb.org/api/file_contents/6ff2427335b985212c9b79dfa795799f",
                                "source_hash" to "bd4bff27f540f4f2c9de012acc4b48a3"
                            )
                        )
                    )
                )
            )
        }

        "handle multiple PURLs by extracting first as primary and storing remaining in additionalData" {
            val results = readResource("/scanoss-multiple-purls.json").let {
                JsonUtils.toScanFileResultsFromObject(JsonUtils.toJsonObject(it))
            }

            val time = Instant.now()
            val summary = generateSummary(time, time, results)

            // Verify we have one finding per source location, not per PURL.
            summary.snippetFindings should haveSize(2)

            with(summary.snippetFindings.first()) {
                // Check source location (local file).
                sourceLocation shouldBe TextLocation("hung_task.c", 12, 150)

                // Verify first PURL is extracted as primary identifier.
                snippets should haveSize(1)
                snippets.first().purl shouldBe "pkg:github/kdrag0n/proton_bluecross"

                // Verify related PURLs to be stored as additional data.
                snippets.first().additionalData shouldBe
                    mapOf(
                        "file_hash" to "581734935cfbe570d280a1265aaa2a6b",
                        "file_url" to "https://api.scanoss.com/file_contents/581734935cfbe570d280a1265aaa2a6b",
                        "source_hash" to "45dd1e50621a8a32f88fbe0251a470ab",
                        "related_purls" to "pkg:github/fake/fake_repository"
                    )

                // Check OSS location.
                snippets.first().location shouldBe
                    TextLocation("kernel/hung_task.c", 10, 148)
            }

            // Verify same behavior for second snippet.
            with(summary.snippetFindings.last()) {
                sourceLocation shouldBe TextLocation("hung_task.c", 540, 561)
                snippets.first().purl shouldBe "pkg:github/kdrag0n/proton_bluecross"
                snippets.first().location shouldBe
                    TextLocation("kernel/hung_task.c", 86, 107)
            }
        }

        "combine the same license from different sources into a single expression" {
            // When the same license appears in multiple sources (like scancode and file_header),
            // combine them into a single expression rather than duplicating.
            val results = readResource("/scanoss-snippet-same-license-multiple-sources.json").let {
                JsonUtils.toScanFileResultsFromObject(JsonUtils.toJsonObject(it))
            }

            val time = Instant.now()
            val summary = generateSummary(time, time, results)

            // Verify the snippet finding.
            summary.snippetFindings should haveSize(1)
            val snippet = summary.snippetFindings.first().snippets.first()

            // Consolidate the license into a single expression
            // even though it came from both "scancode" and "file_header" sources.
            snippet.license shouldBe "LGPL-2.1-or-later".toSpdx()

            // Preserve other snippet details correctly.
            with(summary.snippetFindings.first()) {
                sourceLocation.path shouldBe "src/check_error.c"
                sourceLocation.startLine shouldBe 16
                sourceLocation.endLine shouldBe 24
            }
        }

        "handle empty license array with NOASSERTION" {
            val results = readResource("/scanoss-snippet-no-license-data.json").let {
                JsonUtils.toScanFileResultsFromObject(JsonUtils.toJsonObject(it))
            }

            val time = Instant.now()
            val summary = generateSummary(time, time, results)

            // Verify the snippet finding.
            summary.snippetFindings should haveSize(1)
            val snippet = summary.snippetFindings.first().snippets.first()

            // Use NOASSERTION when no licenses are provided.
            snippet.license shouldBe SpdxConstants.NOASSERTION.toSpdx()

            // Preserve other snippet details correctly.
            with(summary.snippetFindings.first()) {
                sourceLocation.path shouldBe "fake_file.c"
                sourceLocation.startLine shouldBe 16
                sourceLocation.endLine shouldBe 24
            }
        }

        "exclude identified snippets from snippet findings" {
            // The scanoss-identified-snippet.json contains two snippets, but one is identified.
            // Only unidentified snippets should be included in the SnippetFindings.
            val results = readResource("/scanoss-identified-snippet.json").let {
                JsonUtils.toScanFileResultsFromObject(JsonUtils.toJsonObject(it))
            }

            val time = Instant.now()
            val summary = generateSummary(time, time, results)

            // Should have only one finding because the identified snippet is excluded
            summary.snippetFindings should haveSize(1)
        }
    }
})
