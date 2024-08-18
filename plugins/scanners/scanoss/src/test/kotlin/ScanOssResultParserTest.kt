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
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should

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
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

class ScanOssResultParserTest : WordSpec({
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

            summary.snippetFindings shouldHaveSize (1)
            summary.snippetFindings.shouldContainExactly(
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
                            SpdxExpression.parse("CC-BY-SA-2.0")
                        )
                    )
                )
            )
        }
    }
})
