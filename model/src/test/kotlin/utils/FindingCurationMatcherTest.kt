/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.model.utils

import com.here.ort.model.LicenseFinding
import com.here.ort.model.TextLocation
import com.here.ort.model.config.LicenseFindingCuration
import com.here.ort.model.config.LicenseFindingCurationReason.INCORRECT
import com.here.ort.model.util.FindingCurationMatcher

import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import kotlin.random.Random

class FindingCurationMatcherTest : WordSpec() {
    private val matcher = FindingCurationMatcher()
    private lateinit var finding: LicenseFinding
    private lateinit var curation: LicenseFindingCuration

    override fun isInstancePerTest() = true

    private fun setupFinding(license: String, path: String, startLine: Int, endLine: Int) {
        finding = LicenseFinding(
            license = license,
            location = TextLocation(path, startLine, endLine)
        )
    }

    private fun setupCuration(
        license: String?,
        path: String,
        startLines: List<Int>,
        lineCount: Int?,
        concludedLicense: String = "concluded_license_${Random.nextInt()}",
        comment: String = "comment_${Random.nextInt()}"
    ) {
        curation = LicenseFindingCuration(
            path = path,
            startLines = startLines,
            lineCount = lineCount,
            detectedLicense = license,
            concludedLicense = concludedLicense,
            reason = INCORRECT,
            comment = comment
        )
    }

    init {
        "Given exactly matching curation, matches" should {
            "return true" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(license = "MIT", path = "a/path", startLines = listOf(8), lineCount = 6)

                matcher.matches(finding, curation) shouldBe true
            }
        }

        "Given matching curation with empty startLines, matches" should {
            "return true" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(license = "MIT", path = "a/path", startLines = listOf(), lineCount = 6)

                matcher.matches(finding, curation) shouldBe true
            }
        }

        "Given matching curation with multiple startLines, matches" should {
            "return true" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(license = "MIT", path = "a/path", startLines = listOf(7, 8), lineCount = 6)

                matcher.matches(finding, curation) shouldBe true
            }
        }

        "Given matching curation with 'null' as lineCount, matches" should {
            "return true" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(license = "MIT", path = "a/path", startLines = listOf(8), lineCount = null)

                matcher.matches(finding, curation) shouldBe true
            }
        }

        "Given matching curation with wildcard in path glob, matches" should {
            "return true" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(license = "MIT", path = "**/path", startLines = listOf(8), lineCount = 6)

                matcher.matches(finding, curation) shouldBe true
            }
        }

        "Given matching curation with null detected license, matches" should {
            "return true" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(license = null, path = "a/path", startLines = listOf(8), lineCount = 6)

                matcher.matches(finding, curation) shouldBe true
            }
        }

        "Given matching curation with maximal broad matchers, matches" should {
            "return true" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(license = "MIT", path = "**", startLines = emptyList(), lineCount = null)

                matcher.matches(finding, curation) shouldBe true
            }
        }

        "Given curation with only license not matching, matches" should {
            "return false" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(license = "Apache-2.0", path = "**", startLines = emptyList(), lineCount = null)

                matcher.matches(finding, curation) shouldBe false
            }
        }

        "Given curation with only path not matching, matches" should {
            "return false" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(license = "MIT", path = "other/path", startLines = emptyList(), lineCount = null)

                matcher.matches(finding, curation) shouldBe false
            }
        }

        "Given curation with only startLines not matching, matches" should {
            "return false" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(license = "MIT", path = "**", startLines = listOf(6, 7, 9), lineCount = null)

                matcher.matches(finding, curation) shouldBe false
            }
        }

        "Given curation with only lineCount not matching, matches" should {
            "return false" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(license = "MIT", path = "**", startLines = emptyList(), lineCount = 1)

                matcher.matches(finding, curation) shouldBe false
            }
        }

        "Given matching curation, apply" should {
            "return that finding with the license set to the concluded license" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(
                    license = "MIT",
                    path = "**",
                    startLines = emptyList(),
                    lineCount = null,
                    concludedLicense = "Apache-2.0"
                )

                val curatedFinding = matcher.apply(finding, curation)!!

                curatedFinding.license shouldBe "Apache-2.0"
                curatedFinding.location shouldBe finding.location
            }
        }

        "Given matching curation with NONE as concluded license, apply" should {
            "return null" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(
                    license = "MIT",
                    path = "**",
                    startLines = emptyList(),
                    lineCount = null,
                    concludedLicense = "NONE"
                )

                matcher.apply(finding, curation) shouldBe null
            }
        }

        "Given non-matching curation, apply" should {
            "return the original finding" {
                setupFinding(license = "MIT", path = "a/path", startLine = 8, endLine = 13)
                setupCuration(
                    license = "Non matching license",
                    path = "**",
                    startLines = emptyList(),
                    lineCount = null,
                    concludedLicense = "Apache-2.0"
                )

                matcher.apply(finding, curation) shouldBe finding
            }
        }

        "Given multiple curations and findings, applyAll" should {
            "apply a curation to all matching license findings" {
                val findings = listOf(
                    LicenseFinding(
                        license = "MIT",
                        location = TextLocation(path = "some/path", startLine = 1, endLine = 1)
                    ),
                    LicenseFinding(
                        license = "MIT",
                        location = TextLocation(path = "another/path", startLine = 2, endLine = 2)
                    ),
                    LicenseFinding(
                        license = "MIT",
                        location = TextLocation(path = "some/other/path", startLine = 3, endLine = 3)
                    )
                )
                val curations = listOf(
                    LicenseFindingCuration(
                        path = "some/path",
                        detectedLicense = "MIT",
                        reason = INCORRECT,
                        concludedLicense = "Apache-2.0"
                    ),
                    LicenseFindingCuration(
                        path = "another/path",
                        detectedLicense = "MIT",
                        reason = INCORRECT,
                        concludedLicense = "BSD-3-Clause"
                    )
                )

                val result = matcher.applyAll(findings, curations)

                result shouldContainExactlyInAnyOrder listOf(
                    LicenseFinding(
                        license = "Apache-2.0",
                        location = TextLocation(path = "some/path", startLine = 1, endLine = 1)
                    ),
                    LicenseFinding(
                        license = "BSD-3-Clause",
                        location = TextLocation(path = "another/path", startLine = 2, endLine = 2)
                    ),
                    LicenseFinding(
                        license = "MIT",
                        location = TextLocation(path = "some/other/path", startLine = 3, endLine = 3)
                    )
                )
            }
        }
    }
}
