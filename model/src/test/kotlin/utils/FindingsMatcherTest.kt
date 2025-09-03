/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.maps.beEmpty as beEmptyMap
import io.kotest.matchers.maps.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.random.Random

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.utils.FindingsMatcher.Companion.DEFAULT_EXPAND_TOLERANCE_LINES
import org.ossreviewtoolkit.model.utils.FindingsMatcher.Companion.DEFAULT_TOLERANCE_LINES
import org.ossreviewtoolkit.utils.spdx.toSpdx

private fun FindingsMatcherResult.getFindings(license: String): Pair<Set<LicenseFinding>, Set<CopyrightFinding>> =
    matchedFindings.filter { it.key.license == license.toSpdx() }.let {
        Pair(it.keys, it.values.flatten().toSet())
    }

private fun FindingsMatcherResult.getCopyrights(license: String): Set<CopyrightFinding> = getFindings(license).second

class FindingsMatcherTest : WordSpec() {
    private val matcher = FindingsMatcher()
    private val licenseFindings = mutableSetOf<LicenseFinding>()
    private val copyrightFindings = mutableSetOf<CopyrightFinding>()

    // Use InstancePerRoot to work around https://github.com/kotest/kotest/issues/5021.
    override fun isolationMode() = IsolationMode.InstancePerRoot

    private fun setupLicenseFinding(license: String, path: String, line: Int = 1) {
        licenseFindings += LicenseFinding(
            license = license,
            location = TextLocation(
                path = path,
                startLine = line,
                endLine = line
            )
        )
    }

    private fun setupCopyrightFinding(statement: String, path: String, line: Int = 1) {
        copyrightFindings += CopyrightFinding(
            statement = statement,
            location = TextLocation(
                path = path,
                startLine = line,
                endLine = line
            )
        )
    }

    init {
        "Given an applicable root license finding and a copyright finding in a file without license, match" should {
            "associate that copyright with the root license" {
                setupLicenseFinding(license = "some-id", path = "LICENSE")
                setupCopyrightFinding(statement = "some stmt", path = "some/other/file")

                val result = matcher.match(licenseFindings, copyrightFindings)
                val (licenseFindings, copyrightFindings) = result.getFindings("some-id")

                licenseFindings.map { it.location.path } should containExactly("LICENSE")
                copyrightFindings.map { it.statement } should containExactly("some stmt")
            }
        }

        "Given a license finding in a license file and a file with copyright and license findings, match" should {
            "not associate that copyright with the root license" {
                setupLicenseFinding(license = "some-id", path = "a/LICENSE")
                setupLicenseFinding(license = "some-other-id", path = "some/other/file")
                setupCopyrightFinding(statement = "some stmt", path = "some/other/file")

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.getCopyrights("some-id") should beEmpty()
            }
        }

        "Given license findings in two license files and a copyright finding in a file without license, match" should {
            "associate that copyright finding with all applicable root licenses" {
                setupLicenseFinding(license = "license-a1", path = "LICENSE")
                setupLicenseFinding(license = "license-a2", path = "UNLICENSE")
                setupLicenseFinding(license = "license-b1", path = "b/LICENSE")
                setupCopyrightFinding(statement = "some stmt", path = "some/file")

                val result = matcher.match(licenseFindings, copyrightFindings)

                with(result) {
                    matchedFindings should haveSize(3)
                    getCopyrights("license-a1").map { it.statement } should containExactly("some stmt")
                    getCopyrights("license-a2").map { it.statement } should containExactly("some stmt")
                }
            }
        }

        "Given a file with multiple license findings and 7 copyrights above one of them, match" should {
            "associate all except the top copyright finding" {
                setupLicenseFinding(license = "license-nearby", path = "some/file", line = 16)
                setupLicenseFinding(license = "license-far-away", path = "some/file", line = 1000)
                setupCopyrightFinding(statement = "stmt 5", path = "some/file", line = 5)
                setupCopyrightFinding(statement = "stmt 8", path = "some/file", line = 8)
                setupCopyrightFinding(statement = "stmt 10", path = "some/file", line = 10)
                setupCopyrightFinding(statement = "stmt 11", path = "some/file", line = 11)
                setupCopyrightFinding(statement = "stmt 12", path = "some/file", line = 12)
                setupCopyrightFinding(statement = "stmt 13", path = "some/file", line = 13)
                setupCopyrightFinding(statement = "stmt 14", path = "some/file", line = 14)
                setupCopyrightFinding(statement = "stmt 15", path = "some/file", line = 15)

                val result = matcher.match(licenseFindings, copyrightFindings)

                with(result) {
                    matchedFindings should haveSize(2)
                    matchedFindings.values.flatten().filter { it.statement == "stmt 1" } should beEmpty()
                    getCopyrights("license-nearby").map { it.statement } should containExactlyInAnyOrder(
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
        }

        "Given no license finding in any license file and a copyright finding in a file without license, match" should {
            "discard that copyright" {
                setupCopyrightFinding(statement = "some stmt", path = "some/other/file")

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.matchedFindings should beEmptyMap()
            }
        }

        "Given license findings in non-license files and a copyright finding in a file without license, match" should {
            "discard that copyright" {
                setupLicenseFinding(license = "some-license", path = "some/file")
                setupCopyrightFinding(statement = "some stmt", path = "some/other/file")

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.matchedFindings should haveSize(1)
                result.matchedFindings.values.flatten() should beEmpty()
            }
        }

        "Given a copyright finding within line tolerance of two license findings, match" should {
            "associate that copyright with both licenses" {
                setupLicenseFinding(license = "some-id", path = "some/file", line = 5)
                setupLicenseFinding(license = "some-other-id", path = "some/file", line = 6)
                setupCopyrightFinding(statement = "some stmt", path = "some/file", line = 4)

                val result = matcher.match(licenseFindings, copyrightFindings)

                with(result) {
                    matchedFindings should haveSize(2)
                    getCopyrights("some-id").map { it.statement } should containExactly("some stmt")
                    getCopyrights("some-other-id").map { it.statement } should containExactly("some stmt")
                }
            }
        }

        "Given a file with multiple license and copyright findings, match" should {
            "associate the statements to the license-nearby but not to the license-far-away" {
                // Use an arbitrary license start line that is clearly larger than DEFAULT_TOLERANCE_LINES.
                val licenseStartLine = Random.nextInt(2 * DEFAULT_TOLERANCE_LINES, 20 * DEFAULT_TOLERANCE_LINES)
                setupLicenseFinding("license-nearby", "path", licenseStartLine)
                setupLicenseFinding("license-far-away", "path", licenseStartLine + 100 * DEFAULT_TOLERANCE_LINES)
                setupCopyrightFinding(
                    "statement1",
                    "path",
                    licenseStartLine - DEFAULT_TOLERANCE_LINES - DEFAULT_EXPAND_TOLERANCE_LINES - 1
                )
                setupCopyrightFinding("statement2", "path", licenseStartLine - DEFAULT_TOLERANCE_LINES)
                setupCopyrightFinding("statement3", "path", licenseStartLine + DEFAULT_TOLERANCE_LINES)
                setupCopyrightFinding("statement4", "path", licenseStartLine + DEFAULT_TOLERANCE_LINES + 1)

                val result = matcher.match(licenseFindings, copyrightFindings)

                with(result) {
                    getCopyrights("license-nearby").map { it.statement } should containExactlyInAnyOrder(
                        "statement2",
                        "statement3"
                    )
                    getCopyrights("license-far-away") should beEmpty()
                }
            }
        }

        "Given a file with multiple license and a not nearby copyright finding and a root license, match" should {
            "associate that finding with the root license" {
                setupLicenseFinding("license-1", "path", 1)
                setupLicenseFinding("license-2", "path", 2)
                setupCopyrightFinding("statement 1", "path", 2 + DEFAULT_TOLERANCE_LINES + 1)
                setupLicenseFinding("root-license-1", "LICENSE", 102)

                val result = matcher.match(licenseFindings, copyrightFindings)

                result.getCopyrights("root-license-1").map { it.statement } should containExactly("statement 1")
            }
        }

        "associateLicensesWithExceptions()" should {
            "merge with the nearest license" {
                associateLicensesWithExceptions(
                    listOf(
                        LicenseFinding("Apache-2.0", TextLocation("file", 1)),
                        LicenseFinding("Apache-2.0", TextLocation("file", 100)),
                        LicenseFinding("LLVM-exception", TextLocation("file", 5))
                    )
                ) should containExactlyInAnyOrder(
                    LicenseFinding("Apache-2.0 WITH LLVM-exception", TextLocation("file", 1, 5)),
                    LicenseFinding("Apache-2.0", TextLocation("file", 100))
                )
            }

            "omit exceptions that are already contained in existing findings" {
                associateLicensesWithExceptions(
                    listOf(
                        LicenseFinding("GPL-1.0-or-later", TextLocation("pom.xml", 45)),
                        LicenseFinding("GPL-2.0-only WITH Classpath-exception-2.0", TextLocation("pom.xml", 46, 48)),
                        LicenseFinding("Classpath-exception-2.0", TextLocation("pom.xml", 47))
                    )
                ) should containExactlyInAnyOrder(
                    LicenseFinding("GPL-1.0-or-later", TextLocation("pom.xml", 45)),
                    LicenseFinding("GPL-2.0-only WITH Classpath-exception-2.0", TextLocation("pom.xml", 46, 48))
                )
            }

            "associate orphan exceptions by NOASSERTION" {
                associateLicensesWithExceptions(
                    listOf(
                        LicenseFinding("GPL-2.0-only", TextLocation("file", 1)),
                        LicenseFinding("389-exception", TextLocation("file", 100))
                    )
                ) should containExactlyInAnyOrder(
                    LicenseFinding("GPL-2.0-only", TextLocation("file", 1)),
                    LicenseFinding("NOASSERTION WITH 389-exception", TextLocation("file", 100))
                )
            }

            "not associate licenses and exceptions that do not belong together" {
                associateLicensesWithExceptions(
                    listOf(
                        LicenseFinding("LicenseRef-scancode-unknown", TextLocation("file", 1)),
                        LicenseFinding("LLVM-exception", TextLocation("file", 5))
                    )
                ) should containExactlyInAnyOrder(
                    LicenseFinding("LicenseRef-scancode-unknown", TextLocation("file", 1)),
                    LicenseFinding("NOASSERTION WITH LLVM-exception", TextLocation("file", 5))
                )
            }

            "not associate findings from different files" {
                associateLicensesWithExceptions(
                    listOf(
                        LicenseFinding("Apache-2.0", TextLocation("fileA", 1)),
                        LicenseFinding("LLVM-exception", TextLocation("fileB", 5))
                    )
                ) should containExactlyInAnyOrder(
                    LicenseFinding("Apache-2.0", TextLocation("fileA", 1)),
                    LicenseFinding("NOASSERTION WITH LLVM-exception", TextLocation("fileB", 5))
                )
            }

            "associate licenses and exceptions from the same expression" {
                assertSoftly {
                    associateLicensesWithExceptions(
                        "MIT OR (GPL-2.0-only AND CC-BY-3.0 AND GCC-exception-2.0)".toSpdx()
                    ) shouldBe "MIT OR (GPL-2.0-only WITH GCC-exception-2.0 AND CC-BY-3.0)".toSpdx()

                    associateLicensesWithExceptions(
                        "MIT OR (0BSD AND CC-BY-3.0 AND GCC-exception-2.0)".toSpdx()
                    ) shouldBe "MIT OR (0BSD AND CC-BY-3.0 AND NOASSERTION WITH GCC-exception-2.0)".toSpdx()

                    associateLicensesWithExceptions(
                        @Suppress("MaxLineLength")
                        "(BSD-3-Clause AND GPL-2.0-only WITH GCC-exception-2.0) AND (GPL-2.0-only AND GCC-exception-2.0)".toSpdx()
                    ) shouldBe "BSD-3-Clause AND GPL-2.0-only WITH GCC-exception-2.0".toSpdx()

                    associateLicensesWithExceptions(
                        "GPL-2.0-only AND GPL-3.0-only AND Bootloader-exception AND Classpath-exception-2.0".toSpdx()
                    ) shouldBe (
                        "GPL-2.0-only WITH Bootloader-exception AND " +
                            "GPL-3.0-only WITH Bootloader-exception AND " +
                            "GPL-2.0-only WITH Classpath-exception-2.0 AND " +
                            "GPL-3.0-only WITH Classpath-exception-2.0"
                        ).toSpdx()

                    associateLicensesWithExceptions(
                        @Suppress("MaxLineLength")
                        "(CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0) AND GPL-2.0-only AND Classpath-exception-2.0".toSpdx()
                    ) shouldBe (
                        "(CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0) AND " +
                            "GPL-2.0-only WITH Classpath-exception-2.0"
                        ).toSpdx()
                }
            }
        }
    }
}
