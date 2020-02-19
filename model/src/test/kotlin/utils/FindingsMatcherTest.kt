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

import com.here.ort.model.CopyrightFinding
import com.here.ort.model.LicenseFinding
import com.here.ort.model.LicenseFindings
import com.here.ort.model.TextLocation
import com.here.ort.model.utils.FindingsMatcher.Companion.DEFAULT_EXPAND_TOLERANCE_LINES
import com.here.ort.model.utils.FindingsMatcher.Companion.DEFAULT_TOLERANCE_LINES
import com.here.ort.utils.FileMatcher

import io.kotlintest.IsolationMode
import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import kotlin.random.Random

private fun Collection<LicenseFindings>.getFindings(license: String) = single { it.license == license }

private fun Collection<LicenseFindings>.getAllStatements() = flatMap { it.copyrights.map { it.statement } }

private const val NESTED_LICENSE_FILE_A = "a/LICENSE"
private const val NESTED_LICENSE_FILE_B = "b/LICENSE"

class FindingsMatcherTest : WordSpec() {
    private val matcher = FindingsMatcher(FileMatcher(NESTED_LICENSE_FILE_A, NESTED_LICENSE_FILE_B))
    private val licenseFindings = mutableListOf<LicenseFinding>()
    private val copyrightFindings = mutableListOf<CopyrightFinding>()

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun setupLicenseFinding(license: String, path: String, line: Int = 1) {
        licenseFindings.add(
            LicenseFinding(
                license = license,
                location = TextLocation(
                    path = path,
                    startLine = line,
                    endLine = line
                )
            )
        )
    }

    private fun setupCopyrightFinding(statement: String, path: String, line: Int = 1) {
        copyrightFindings.add(
            CopyrightFinding(
                statement = statement,
                location = TextLocation(
                    path = path,
                    startLine = line,
                    endLine = line
                )
            )
        )
    }

    init {
        "Given a license finding in a license file and a copyright finding in a file without license, match" should {
            "associate that copyright with the root license" {
                setupLicenseFinding(license = "some id", path = NESTED_LICENSE_FILE_A)
                setupCopyrightFinding(statement = "some stmt", path = "some/other/file")

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.size shouldBe 1
                val findings = result.getFindings("some id")
                findings.locations.map { it.path } shouldContainExactlyInAnyOrder listOf(NESTED_LICENSE_FILE_A)
                findings.copyrights.map { it.statement } shouldContainExactlyInAnyOrder listOf("some stmt")
            }
        }

        "Given a license finding in a license file and a file with copyright and license findings, match" should {
            "not associate that copyright with the root license" {
                setupLicenseFinding(license = "some id", path = NESTED_LICENSE_FILE_A)
                setupLicenseFinding(license = "some other id", path = "some/other/file")
                setupCopyrightFinding(statement = "some stmt", path = "some/other/file")

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.getFindings("some id").copyrights should beEmpty()
            }
        }

        "Given license findings in two license files and a copyright finding in a file without license, match" should {
            "associate that copyright finding with all root licenses" {
                setupLicenseFinding(license = "license-a1", path = NESTED_LICENSE_FILE_A)
                setupLicenseFinding(license = "license-a2", path = NESTED_LICENSE_FILE_A)
                setupLicenseFinding(license = "license-b1", path = NESTED_LICENSE_FILE_B)
                setupCopyrightFinding(statement = "some stmt", path = "some/file")

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.size shouldBe 3
                result.getFindings("license-a1").copyrights.map { it.statement } shouldBe listOf("some stmt")
                result.getFindings("license-a2").copyrights.map { it.statement } shouldBe listOf("some stmt")
                result.getFindings("license-b1").copyrights.map { it.statement } shouldBe listOf("some stmt")
            }
        }

        "Given a file with multiple license findings and 7 copyrights above one of them, match" should {
            "associate all except the top copyright finding" {
                setupLicenseFinding(license = "license nearby", path = "some/file", line = 16)
                setupLicenseFinding(license = "license far away", path = "some/file", line = 1000)
                setupCopyrightFinding(statement = "stmt 5", path = "some/file", line = 5)
                setupCopyrightFinding(statement = "stmt 8", path = "some/file", line = 8)
                setupCopyrightFinding(statement = "stmt 10", path = "some/file", line = 10)
                setupCopyrightFinding(statement = "stmt 11", path = "some/file", line = 11)
                setupCopyrightFinding(statement = "stmt 12", path = "some/file", line = 12)
                setupCopyrightFinding(statement = "stmt 13", path = "some/file", line = 13)
                setupCopyrightFinding(statement = "stmt 14", path = "some/file", line = 14)
                setupCopyrightFinding(statement = "stmt 15", path = "some/file", line = 15)

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.size shouldBe 2
                result.flatMap { it.copyrights.filter { it.statement == "stmt 1" } } should beEmpty()
                result.getFindings("license nearby").copyrights.map { it.statement } shouldContainExactlyInAnyOrder
                        listOf(
                            "stmt 8",
                            "stmt 10",
                            "stmt 11",
                            "stmt 12",
                            "stmt 13",
                            "stmt 14",
                            "stmt 15"
                        )
            }
        }

        "Given no license finding in any license file and a copyright finding in a file without license, match" should {
            "discard that copyright" {
                setupCopyrightFinding(statement = "some stmt", path = "some/other/file")

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.size shouldBe 0
            }
        }

        "Given license findings in non-license files and a copyright finding in a file without license, match" should {
            "discard that copyright" {
                setupLicenseFinding(license = "some license", path = "some/file")
                setupCopyrightFinding(statement = "some stmt", path = "some/other/file")

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.size shouldBe 1
                result.flatMap { it.copyrights } should beEmpty()
            }
        }

        "Given a copyright finding within line tolerance of two license findings, match" should {
            "associate that copyright with both licenses" {
                setupLicenseFinding(license = "some id", path = "some/file", line = 5)
                setupLicenseFinding(license = "some other id", path = "some/file", line = 6)
                setupCopyrightFinding(statement = "some stmt", path = "some/file", line = 4)

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.size shouldBe 2
                result.getFindings("some id").copyrights.map { it.statement } shouldBe listOf("some stmt")
                result.getFindings("some other id").copyrights.map { it.statement } shouldBe listOf("some stmt")
            }
        }

        "Given a file with multiple license and copyright findings, match" should {
            "associate the statements to the license nearby but not to the license far away" {
                // Use an arbitrary license start line that is clearly larger than DEFAULT_TOLERANCE_LINES.
                val licenseStartLine = Random.nextInt(2 * DEFAULT_TOLERANCE_LINES, 20 * DEFAULT_TOLERANCE_LINES)
                setupLicenseFinding("license nearby", "path", licenseStartLine)
                setupLicenseFinding("license far away", "path", licenseStartLine + 100 * DEFAULT_TOLERANCE_LINES)
                setupCopyrightFinding("statement1", "path", licenseStartLine - DEFAULT_TOLERANCE_LINES -
                        DEFAULT_EXPAND_TOLERANCE_LINES - 1)
                setupCopyrightFinding("statement2", "path", licenseStartLine - DEFAULT_TOLERANCE_LINES)
                setupCopyrightFinding("statement3", "path", licenseStartLine + DEFAULT_TOLERANCE_LINES)
                setupCopyrightFinding("statement4", "path", licenseStartLine + DEFAULT_TOLERANCE_LINES + 1)

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.getFindings("license nearby")
                    .copyrights.map { it.statement } shouldContainExactlyInAnyOrder listOf(
                        "statement2",
                        "statement3"
                    )
                result.getFindings("license far away").copyrights should beEmpty()
            }
        }

        "Given a file with multiple license and a not nearby copyright finding and a root license, match" should {
            "associate that finding with the root license" {
                setupLicenseFinding("license 1", "path", 1)
                setupLicenseFinding("license 2", "path", 2)
                setupCopyrightFinding("statement 1", "path", 2 + DEFAULT_TOLERANCE_LINES + 1)
                setupLicenseFinding("root license 1", NESTED_LICENSE_FILE_A, 102)

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.getFindings("root license 1").copyrights.map { it.statement } shouldBe listOf("statement 1")
            }
        }
    }
}
