/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.random.Random

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.LicenseFindingCurationReason.INCORRECT
import org.ossreviewtoolkit.model.licenses.LicenseFindingCurationResult
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdxexpression.toSpdx

class FindingCurationMatcherTest : WordSpec({
    val matcher = FindingCurationMatcher()
    lateinit var finding: LicenseFinding
    lateinit var curation: LicenseFindingCuration

    fun setupFinding(license: String, path: String, startLine: Int, endLine: Int) {
        finding = LicenseFinding(
            license = license,
            location = TextLocation(path, startLine, endLine)
        )
    }

    fun setupCuration(
        license: String?,
        path: String,
        startLines: List<Int>,
        lineCount: Int?,
        concludedLicense: String = "concluded-license-${Random.nextInt()}",
        comment: String = "comment_${Random.nextInt()}"
    ) {
        curation = LicenseFindingCuration(
            path = path,
            startLines = startLines,
            lineCount = lineCount,
            detectedLicense = license?.toSpdx(),
            concludedLicense = concludedLicense.toSpdx(),
            reason = INCORRECT,
            comment = comment
        )
    }

    "matches()" should {
        "return true for an exactly matching curations" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(license = "MIT", path = "a/path", startLines = listOf(8), lineCount = 6)

            matcher.matches(finding, curation) shouldBe true
        }

        "return true for a matching curation with empty startLines" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(license = "MIT", path = "a/path", startLines = listOf(), lineCount = 6)

            matcher.matches(finding, curation) shouldBe true
        }

        "return true for a matching curation with multiple startLines" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(license = "MIT", path = "a/path", startLines = listOf(7, 8), lineCount = 6)

            matcher.matches(finding, curation) shouldBe true
        }

        "return true for a matching curation with 'null' as lineCount" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(license = "MIT", path = "a/path", startLines = listOf(8), lineCount = null)

            matcher.matches(finding, curation) shouldBe true
        }

        "return true for a matching curation with wildcard in path glob" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(license = "MIT", path = "**/path", startLines = listOf(8), lineCount = 6)

            matcher.matches(finding, curation) shouldBe true
        }

        "return true for a matching curation with null detected license" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(license = null, path = "a/path", startLines = listOf(8), lineCount = 6)

            matcher.matches(finding, curation) shouldBe true
        }

        "return true for a matching curation with maximal broad matchers" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(license = "MIT", path = "**", startLines = emptyList(), lineCount = null)

            matcher.matches(finding, curation) shouldBe true
        }

        "return false for a curation with only license not matching" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(license = "Apache-2.0", path = "**", startLines = emptyList(), lineCount = null)

            matcher.matches(finding, curation) shouldBe false
        }

        "return false for a curation with only path not matching" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(license = "MIT", path = "other/path", startLines = emptyList(), lineCount = null)

            matcher.matches(finding, curation) shouldBe false
        }

        "return false for a curation with only startLines not matching" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(license = "MIT", path = "**", startLines = listOf(6, 7, 9), lineCount = null)

            matcher.matches(finding, curation) shouldBe false
        }

        "return false for a curation with only lineCount not matching" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(license = "MIT", path = "**", startLines = emptyList(), lineCount = 1)

            matcher.matches(finding, curation) shouldBe false
        }
    }

    "apply()" should {
        "return the finding with the license set to the concluded license of the matching curation" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(
                license = "MIT",
                path = "**",
                startLines = emptyList(),
                lineCount = null,
                concludedLicense = "Apache-2.0"
            )

            val curatedFinding = matcher.apply(finding, curation)

            curatedFinding shouldNotBeNull {
                license shouldBe "Apache-2.0".toSpdx()
                location shouldBe finding.location
            }
        }

        "return null if the matching curation has NONE as concluded license" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(
                license = "MIT",
                path = "**",
                startLines = emptyList(),
                lineCount = null,
                concludedLicense = "NONE"
            )

            matcher.apply(finding, curation) should beNull()
        }

        "return the original finding if the curation does not match" {
            setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
            setupCuration(
                license = "Non-matching-license",
                path = "**",
                startLines = emptyList(),
                lineCount = null,
                concludedLicense = "Apache-2.0"
            )

            matcher.apply(finding, curation) shouldBe finding
        }
    }

    "applyAll()" should {
        "apply a curation to all matching license findings" {
            val findings = listOf(
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("some/path", 1)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("another/path", 2)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("some/other/path", 3)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("one/more/path", 4)
                )
            )
            val curations = listOf(
                LicenseFindingCuration(
                    path = "some/path",
                    detectedLicense = "MIT".toSpdx(),
                    reason = INCORRECT,
                    concludedLicense = "Apache-2.0".toSpdx()
                ),
                LicenseFindingCuration(
                    path = "another/path",
                    detectedLicense = "MIT".toSpdx(),
                    reason = INCORRECT,
                    concludedLicense = "BSD-3-Clause".toSpdx()
                ),
                LicenseFindingCuration(
                    path = "one/more/path",
                    detectedLicense = "MIT".toSpdx(),
                    reason = INCORRECT,
                    concludedLicense = SpdxConstants.NONE.toSpdx()
                )
            )

            val result = matcher.applyAll(findings, curations)

            result should containExactlyInAnyOrder(
                LicenseFindingCurationResult(
                    curatedFinding = LicenseFinding(
                        license = "Apache-2.0",
                        location = TextLocation("some/path", 1)
                    ),
                    originalFindings = listOf(Pair(findings[0], curations[0]))
                ),
                LicenseFindingCurationResult(
                    curatedFinding = LicenseFinding(
                        license = "BSD-3-Clause",
                        location = TextLocation("another/path", 2)
                    ),
                    originalFindings = listOf(Pair(findings[1], curations[1]))
                ),
                LicenseFindingCurationResult(
                    curatedFinding = LicenseFinding(
                        license = "MIT",
                        location = TextLocation("some/other/path", 3)
                    ),
                    originalFindings = emptyList()
                ),
                LicenseFindingCurationResult(
                    curatedFinding = null,
                    originalFindings = listOf(Pair(findings[3], curations[2]))
                )
            )
        }

        "apply only the first out of multiple matching curations with different concluded licenses to a finding" {
            val findings = listOf(
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("some/path", 1)
                )
            )
            val curations = listOf(
                LicenseFindingCuration(
                    path = "some/path",
                    detectedLicense = "MIT".toSpdx(),
                    reason = INCORRECT,
                    concludedLicense = "MIT-old-style".toSpdx()
                ),
                LicenseFindingCuration(
                    path = "some/path",
                    detectedLicense = "MIT".toSpdx(),
                    reason = INCORRECT,
                    concludedLicense = "Apache-2.0".toSpdx()
                )
            )

            val result = matcher.applyAll(findings, curations)

            result should containExactlyInAnyOrder(
                LicenseFindingCurationResult(
                    curatedFinding = LicenseFinding(
                        license = "MIT-old-style",
                        location = TextLocation("some/path", 1)
                    ),
                    originalFindings = listOf(
                        Pair(findings.first(), curations[0])
                    )
                )
            )
        }
    }
})
