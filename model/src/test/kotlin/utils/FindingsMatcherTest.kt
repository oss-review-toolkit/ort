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
import com.here.ort.model.util.FindingsMatcher
import com.here.ort.model.util.FindingsMatcher.Companion.DEFAULT_TOLERANCE_LINES
import com.here.ort.spdx.LicenseFileMatcher

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import kotlin.random.Random
import kotlin.random.nextInt

private fun createLicenseFinding(license: String, path: String, line: Int = Random.nextInt(1..1000)) =
    LicenseFinding(
        license = license,
        location = TextLocation(
            path = path,
            startLine = line,
            endLine = line
        )
    )

private fun createCopyrightFinding(statement: String, path: String, line: Int = Random.nextInt(1..1000)) =
    CopyrightFinding(
        statement = statement,
        location = TextLocation(
            path = path,
            startLine = line,
            endLine = line
        )
    )

private fun Collection<LicenseFindings>.getFindings(license: String) = single { it.license == license }

class FindingsMatcherTest : WordSpec() {
    private val matcher = FindingsMatcher(LicenseFileMatcher("a/LICENSE"))

    init {
        "Given a license finding in a license file and a copyright finding in a file without license, match" should {
            "associate that copyright with the root license" {
                val licenseFindings = listOf(
                    createLicenseFinding(license = "some id", path = "a/LICENSE")
                )
                val copyrightFindings = listOf(
                    createCopyrightFinding(statement = "some stmt", path = "some/other/file")
                )

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.size shouldBe 1
                val findings = result.getFindings("some id")
                findings.locations.map { it.path } shouldContainExactlyInAnyOrder listOf("a/LICENSE")
                findings.copyrights.map { it.statement } shouldContainExactlyInAnyOrder listOf("some stmt")
            }
        }

        "Given no license finding in any license file and a copyright finding in a file without license, match" should {
            "discard that copyright" {
                val licenseFindings = listOf<LicenseFinding>()
                val copyrightFindings = listOf(
                    createCopyrightFinding(statement = "some stmt", path = "some/other/file")
                )

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.size shouldBe 0
            }
        }

        "Given a file with multiple license and copyright findings match" should {
            "associate exactly the statements within the line threshold to the licenses and discard the others" {
                // Use an arbitrary license start line that is clearly larger than DEFAULT_TOLERANCE_LINES.
                val licenseStartLine = Random.nextInt(2 * DEFAULT_TOLERANCE_LINES, 20 * DEFAULT_TOLERANCE_LINES)
                val licenseFindings = listOf(
                    createLicenseFinding("license nearby", "path", licenseStartLine),
                    createLicenseFinding("license far away", "path", licenseStartLine + 100 * DEFAULT_TOLERANCE_LINES)
                )
                val copyrightFindings = listOf(
                    createCopyrightFinding("statement1", "path", licenseStartLine - DEFAULT_TOLERANCE_LINES - 1),
                    createCopyrightFinding("statement2", "path", licenseStartLine - DEFAULT_TOLERANCE_LINES),
                    createCopyrightFinding("statement3", "path", licenseStartLine + DEFAULT_TOLERANCE_LINES),
                    createCopyrightFinding("statement4", "path", licenseStartLine + DEFAULT_TOLERANCE_LINES + 1)
                )

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.size shouldBe 2
                result.getFindings("license nearby")
                    .copyrights.map { it.statement } shouldContainExactlyInAnyOrder listOf(
                        "statement2",
                        "statement3"
                    )
                result.getFindings("license far away").copyrights should beEmpty()
            }
        }
    }
}
