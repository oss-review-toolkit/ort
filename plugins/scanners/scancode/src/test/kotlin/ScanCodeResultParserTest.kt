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

package org.ossreviewtoolkit.plugins.scanners.scancode

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.test.transformingCollectionMatcher

class ScanCodeResultParserTest : FreeSpec({
    "generateSummary()" - {
        "for ScanCode 3.0.2 should" - {
            "get correct counts" {
                val resultFile = getAssetFile("scancode-3.0.2_mime-types-2.1.18.json")

                val summary = parseResult(resultFile).toScanSummary()

                summary.licenseFindings shouldHaveSize 4
                summary.copyrightFindings shouldHaveSize 4
                summary.issues should beEmpty()
            }
        }

        "for ScanCode 3.2.1rc2 should" - {
            "properly parse license expressions" {
                val resultFile = getAssetFile("scancode-3.2.1rc2_h2database-1.4.200.json")

                val summary = parseResult(resultFile).toScanSummary()

                summary.licenseFindings should containExactlyInAnyOrder(
                    LicenseFinding(
                        license = "(MPL-2.0 OR EPL-1.0) AND LicenseRef-scancode-proprietary-license",
                        location = TextLocation("h2/src/main/org/h2/table/Column.java", 2, 3),
                        score = 20.37f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-public-domain",
                        location = TextLocation("h2/src/main/org/h2/table/Column.java", 317),
                        score = 70.0f
                    )
                )
            }
        }

        "for ScanCode 32.0.8 should" - {
            "get license mappings even without '--license-references'" {
                val resultFile = getAssetFile("scancode-32.0.8_spdx-expression-parse_no-license-references.json")

                val summary = parseResult(resultFile).toScanSummary()

                with(summary.licenseFindings) {
                    shouldHaveSize(18)
                    find { it.location == TextLocation("README.md", 100) && it.score == 100.0f }
                        ?.license.toString() shouldBe "GPL-2.0-only WITH GCC-exception-2.0"
                }
            }

            "get file-level findings with the 'preferFileLicense' option" {
                val resultFile = getAssetFile("scancode-32.0.8_spdx-expression-parse_no-license-references.json")

                val summary = parseResult(resultFile).toScanSummary(preferFileLicense = true)

                summary.licenseFindings.map { it.license.toString() }.shouldContainExactlyInAnyOrder(
                    "LicenseRef-scancode-generic-cla AND MIT",
                    "MIT",
                    "MIT",
                    "JSON AND BSD-2-Clause AND GPL-2.0-only WITH GCC-exception-2.0 AND CC-BY-3.0 AND MIT",
                    "BSD-3-Clause AND GPL-2.0-only WITH GCC-exception-2.0 AND GPL-2.0-only WITH GCC-exception-2.0"
                )
            }
        }

        "for ScanCode 32.1.0 should" - {
            "contain findings that stem from referenced files" {
                val resultFile = getAssetFile("scancode-32.1.0_from_file-reference.json")

                val summary = parseResult(resultFile).toScanSummary()

                summary.licenseFindings should containExactlyInAnyOrder(
                    LicenseFinding(
                        license = "LicenseRef-scancode-public-domain",
                        location = TextLocation("COPYING", 9),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-public-domain",
                        location = TextLocation("COPYING", 11, 12),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "LGPL-2.1-or-later",
                        location = TextLocation("COPYING", 13, 14),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "GPL-2.0-or-later",
                        location = TextLocation("COPYING", 17, 18),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-public-domain",
                        location = TextLocation("COPYING", 21, 22),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-public-domain",
                        location = TextLocation("COPYING", 24),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-public-domain AND GPL-2.0-or-later AND GPL-3.0-or-later",
                        location = TextLocation("COPYING", 26, 27),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-public-domain",
                        location = TextLocation("COPYING", 31),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-public-domain",
                        location = TextLocation("COPYING", 33),
                        score = 70.0f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-other-permissive AND LicenseRef-scancode-other-copyleft",
                        location = TextLocation("COPYING", 33, 34),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-public-domain-disclaimer",
                        location = TextLocation("COPYING", 36, 50),
                        score = 96.69f
                    ),
                    LicenseFinding(
                        license = "LGPL-2.1-only AND GPL-2.0-only AND GPL-3.0-only",
                        location = TextLocation("COPYING", 52, 55),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-public-domain",
                        location = TextLocation("COPYING", 59),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "LGPL-2.1-only",
                        location = TextLocation("COPYING.LGPLv2.1", 1, 502),
                        score = 100.0f
                    )
                )
            }
        }

        for (version in 1..MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION) {
            val resultFile = getAssetFile("scancode-output-format-$version.0.0_mime-types-2.1.18.json")
            val summary = parseResult(resultFile).toScanSummary()

            "for output format $version.0.0 should" - {
                "get correct counts" {
                    summary.licenseFindings shouldHaveSize 5
                    summary.copyrightFindings shouldHaveSize 4
                    summary.issues shouldHaveSize 1
                }

                "properly summarize license findings" {
                    summary should containLicensesExactly("MIT")
                    summary should containLocationsForLicenseExactly(
                        "MIT",
                        TextLocation("LICENSE", 1),
                        TextLocation("LICENSE", 6, 23),
                        TextLocation("README.md", 95, 97),
                        TextLocation("index.js", 5),
                        TextLocation("package.json", 10, 10)
                    )
                }

                "properly summarize copyright findings" {
                    summary should containCopyrightsExactly(
                        "Copyright (c) 2014 Jonathan Ong" to
                            listOf(TextLocation("index.js", 3)),
                        "Copyright (c) 2014 Jonathan Ong <me@jongleberry.com>" to
                            listOf(TextLocation("LICENSE", 3)),
                        "Copyright (c) 2015 Douglas Christopher Wilson" to
                            listOf(TextLocation("index.js", 4)),
                        "Copyright (c) 2015 Douglas Christopher Wilson <doug@somethingdoug.com>" to
                            listOf(TextLocation("LICENSE", 4))
                    )
                }

                "properly map the timeout issue" {
                    val issue = summary.issues.single()

                    issue.message shouldStartWith
                        "ERROR: Timeout after (?<timeout>\\d+) seconds while scanning file '.eslintignore'\\.".toRegex()
                    issue.affectedPath shouldBe ".eslintignore"
                }
            }
        }

        "for output format 4.0.0 should" - {
            "create an issue about an unsupported version" {
                val headers = """
                    {
                      "headers": [
                        {
                          "tool_name": "scancode-toolkit",
                          "tool_version": "some future version",
                          "options": {
                            "input": [
                              "."
                            ],
                            "--copyright": true,
                            "--info": true,
                            "--json-pp": "scancode.json",
                            "--license": true,
                            "--processes": "3",
                            "--strip-root": true,
                            "--timeout": "300.0"
                          },
                          "start_timestamp": "2022-12-12T065635.691832",
                          "end_timestamp": "2022-12-12T065637.770792",
                          "output_format_version": "4.0.0"
                        }
                      ],
                      "files": [
                      ]
                    }
                """.trimIndent()

                val summary = parseResult(headers).toScanSummary()

                summary.issues.map { it.copy(timestamp = Instant.EPOCH) } shouldHaveSingleElement Issue(
                    timestamp = Instant.EPOCH,
                    source = ScanCode.SCANNER_NAME,
                    message = "The output format version 4.0.0 exceeds the supported major version " +
                        "$MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION. Results may be incomplete or incorrect.",
                    severity = Severity.WARNING
                )
            }
        }
    }
})

private fun containLicensesExactly(vararg licenses: String): Matcher<ScanSummary?> =
    transformingCollectionMatcher(expected = licenses.toList(), matcher = ::containExactlyInAnyOrder) { summary ->
        summary.licenseFindings.map { it.license.toString() }.toSet()
    }

private fun containLocationsForLicenseExactly(license: String, vararg locations: TextLocation): Matcher<ScanSummary?> =
    transformingCollectionMatcher(expected = locations.toList(), matcher = ::containExactlyInAnyOrder) { summary ->
        summary.licenseFindings.filter { it.license.toString() == license }.map { it.location }
    }

private fun containCopyrightsExactly(vararg copyrights: Pair<String, List<TextLocation>>): Matcher<ScanSummary?> =
    transformingCollectionMatcher(expected = copyrights.toList(), matcher = ::containExactlyInAnyOrder) { summary ->
        summary.copyrightFindings.groupBy { it.statement }.entries
            .map { (key, value) -> key to value.map { it.location } }
    }

private fun getAssetFile(path: String): File = File("src/test/assets", path).absoluteFile
