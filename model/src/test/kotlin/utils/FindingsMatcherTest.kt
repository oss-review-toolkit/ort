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
import com.here.ort.model.TextLocation
import com.here.ort.model.util.FindingsMatcher
import com.here.ort.model.util.FindingsMatcher.Companion.DEFAULT_TOLERANCE_LINES
import com.here.ort.spdx.LicenseFileMatcher

import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
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

class FindingsMatcherTest : WordSpec() {
    private val matcher = FindingsMatcher(LicenseFileMatcher("a/LICENSE"))

    init {
        "getRootLicense()" should {
            "return the license of the file matched by the license file matcher" {
                val licenseFindings = listOf(
                    createLicenseFinding(license = "abc", path = "a/LICENSE")
                )

                matcher.getRootLicense(licenseFindings) shouldBe "abc"
            }

            "return an empty string when no file is matched by the license file matcher" {
                val licenseFindings = listOf(
                    createLicenseFinding(license = "abc", path = "b/LICENSE")
                )

                matcher.getRootLicense(licenseFindings) shouldBe ""
            }
        }

        "getClosestCopyrightStatements()" should {
            "return exactly the statements within the line threshold" {
                // Use an arbitrary license start line that is larger than DEFAULT_TOLERANCE_LINES.
                val licenseStartLine = 20
                val copyrightFindings = listOf(
                    createCopyrightFinding("statement1", "path", licenseStartLine - DEFAULT_TOLERANCE_LINES - 1),
                    createCopyrightFinding("statement2", "path", licenseStartLine - DEFAULT_TOLERANCE_LINES),
                    createCopyrightFinding("statement3", "path", licenseStartLine + DEFAULT_TOLERANCE_LINES),
                    createCopyrightFinding("statement4", "path", licenseStartLine + DEFAULT_TOLERANCE_LINES + 1)
                )

                val result = matcher.getClosestCopyrightStatements(copyrightFindings, licenseStartLine)
                    .map { it.statement }

                result shouldContainExactlyInAnyOrder listOf("statement2", "statement3")
            }
        }
    }
}
