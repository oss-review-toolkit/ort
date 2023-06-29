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

package org.ossreviewtoolkit.scanner.scanners.scancode

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

import io.kotest.core.spec.style.FreeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.Matcher
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.file.beRelative
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.readTree
import org.ossreviewtoolkit.utils.test.transformingCollectionMatcher

class ScanCodeResultParserTest : FreeSpec({
    "generateSummary()" - {
        "for ScanCode 3.0.2 should" - {
            "get correct counts" {
                val resultFile = File("src/test/assets/scancode-3.0.2_mime-types-2.1.18.json")
                val result = resultFile.readTree()

                val summary = generateSummary(result)

                summary.licenseFindings.size shouldBe 4
                summary.copyrightFindings.size shouldBe 4
                summary.issues should beEmpty()
            }
        }

        "for ScanCode 3.2.1rc2 should" - {
            "properly parse license expressions" {
                val resultFile = File("src/test/assets/scancode-3.2.1rc2_h2database-1.4.200.json")
                val result = resultFile.readTree()

                val summary = generateSummary(result)

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

            "properly parse absolute paths" {
                val resultFile = File("src/test/assets/scancode-3.2.1rc2_spring-javaformat-checkstyle-0.0.15.json")
                val result = resultFile.readTree()

                val summary = generateSummary(result)
                val fileExtensions = listOf("html", "java", "txt")

                summary.licenseFindings.forAll {
                    val file = File(it.location.path)
                    file should beRelative()
                    file.extension shouldBeIn fileExtensions
                }

                summary.copyrightFindings.forAll {
                    val file = File(it.location.path)
                    file should beRelative()
                    file.extension shouldBeIn fileExtensions
                }
            }
        }

        for (version in 1..MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION) {
            "for output format $version.0.0 should" - {
                "get correct counts" {
                    val filename = "scancode-output-format-$version.0.0_mime-types-2.1.18.json"
                    val resultFile = File("src/test/assets/$filename")
                    val result = resultFile.readTree()

                    val summary = generateSummary(result)

                    summary.licenseFindings.size shouldBe 5
                    summary.copyrightFindings.size shouldBe 4
                    summary.issues should beEmpty()
                }

                "properly summarize license findings" {
                    val resultFile = File("src/test/assets/scancode-output-format-$version.0.0_mime-types-2.1.18.json")
                    val result = resultFile.readTree()

                    val summary = generateSummary(result)

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
                    val resultFile = File("src/test/assets/scancode-output-format-$version.0.0_mime-types-2.1.18.json")
                    val result = resultFile.readTree()

                    val summary = generateSummary(result)

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
            }
        }

        "for output format 3.0.0 should" - {
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
                          "output_format_version": "3.0.0"
                        }
                      ]
                    }
                """.trimIndent()

                val result = jsonMapper.readTree(headers)

                val summary = generateSummary(result)

                summary.issues.map { it.copy(timestamp = Instant.EPOCH) } shouldHaveSingleElement Issue(
                    timestamp = Instant.EPOCH,
                    source = ScanCode.SCANNER_NAME,
                    message = "The output format version 3.0.0 exceeds the supported major version " +
                            "$MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION. Results may be incomplete or incorrect.",
                    severity = Severity.WARNING
                )
            }
        }
    }

    "generateDetails()" - {
        "for ScanCode 3.0.2 should" - {
            "properly parse details" {
                val result = File("src/test/assets/scancode-3.0.2_mime-types-2.1.18.json").readTree()

                val details = generateScannerDetails(result)
                details.name shouldBe ScanCode.SCANNER_NAME
                details.version shouldBe "3.0.2"
                details.configuration shouldContain "--timeout 300.0"
                details.configuration shouldContain "--processes 3"
            }

            "handle a missing option property gracefully" {
                val result = File("src/test/assets/scancode-3.0.2_mime-types-2.1.18.json").readTree()
                val headers = result["headers"] as ArrayNode
                val headerObj = headers.first() as ObjectNode
                headerObj.remove("options")

                val details = generateScannerDetails(result)
                details.configuration shouldBe ""
            }
        }

        for (version in 1..MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION) {
            "for output format $version.0.0 should" - {
                "properly parse details" {
                    val filename = "scancode-output-format-$version.0.0_mime-types-2.1.18.json"
                    val result = File("src/test/assets/$filename").readTree()

                    val details = generateScannerDetails(result)
                    details.name shouldBe ScanCode.SCANNER_NAME
                    details.configuration shouldContain "--timeout 300.0"
                    details.configuration shouldContain "--processes 3"
                }
            }
        }
    }

    "replaceLicenseKeys() should" - {
        "properly handle redundant replacements" {
            val expression = "public-domain"
            val replacements = listOf(
                LicenseKeyReplacement("public-domain", "LicenseRef-scancode-public-domain"),
                LicenseKeyReplacement("public-domain", "LicenseRef-scancode-public-domain")
            )

            val result = replaceLicenseKeys(expression, replacements)

            result shouldBe "LicenseRef-scancode-public-domain"
        }

        "properly replace the same license multiple times" {
            val expression = "gpl-2.0 AND (gpl-2.0 OR gpl-2.0-plus)"
            val replacements = listOf(
                LicenseKeyReplacement("gpl-2.0", "GPL-2.0-only"),
                LicenseKeyReplacement("gpl-2.0-plus", "GPL-2.0-or-later")
            )

            val result = replaceLicenseKeys(expression, replacements)

            result shouldBe "GPL-2.0-only AND (GPL-2.0-only OR GPL-2.0-or-later)"
        }

        "properly handle replacements with a license key being a suffix of another" {
            val expression = "agpl-3.0-openssl"
            val replacements = listOf(
                LicenseKeyReplacement("agpl-3.0-openssl", "LicenseRef-scancode-agpl-3.0-openssl"),
                LicenseKeyReplacement("openssl", "LicenseRef-scancode-openssl")
            )

            val result = replaceLicenseKeys(expression, replacements)

            result shouldBe "LicenseRef-scancode-agpl-3.0-openssl"
        }

        "properly handle braces" {
            val expression = "((public-domain AND openssl) OR mit)"
            val replacements = listOf(
                LicenseKeyReplacement("public-domain", "LicenseRef-scancode-public-domain"),
                LicenseKeyReplacement("openssl", "LicenseRef-scancode-openssl"),
                LicenseKeyReplacement("mit", "MIT")
            )

            val result = replaceLicenseKeys(expression, replacements)

            result shouldBe "((LicenseRef-scancode-public-domain AND LicenseRef-scancode-openssl) OR MIT)"
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
