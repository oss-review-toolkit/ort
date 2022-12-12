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
import io.kotest.matchers.file.beRelative
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.readTree
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.test.transformingCollectionMatcher

class ScanCodeResultParserTest : FreeSpec({
    "generateSummary()" - {
        "for ScanCode 3.0.2 should" - {
            "get correct counts" {
                val resultFile = File("src/test/assets/scancode-3.0.2_mime-types-2.1.18.json")
                val result = resultFile.readTree()

                val summary = generateSummary(SpdxConstants.NONE, result)

                summary.licenseFindings.size shouldBe 4
                summary.copyrightFindings.size shouldBe 4
                summary.issues should beEmpty()
            }
        }

        "for ScanCode 3.2.1rc2 should" - {
            "properly parse license expressions" {
                val resultFile = File("src/test/assets/scancode-3.2.1rc2_h2database-1.4.200.json")
                val result = resultFile.readTree()

                val summary = generateSummary(SpdxConstants.NONE, result)

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

                val summary = generateSummary(SpdxConstants.NONE, result)
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

        "for output format 1.0.0 should" - {
            "get correct counts" {
                val resultFile = File("src/test/assets/scancode-output-format-1.0.0_mime-types-2.1.18.json")
                val result = resultFile.readTree()

                val summary = generateSummary(SpdxConstants.NONE, result)

                summary.licenseFindings.size shouldBe 5
                summary.copyrightFindings.size shouldBe 4
                summary.issues should beEmpty()
            }

            "properly summarize license findings" {
                val resultFile = File("src/test/assets/scancode-output-format-1.0.0_mime-types-2.1.18.json")
                val result = resultFile.readTree()

                val summary = generateSummary(SpdxConstants.NONE, result)

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
                val resultFile = File("src/test/assets/scancode-output-format-1.0.0_mime-types-2.1.18.json")
                val result = resultFile.readTree()

                val summary = generateSummary(SpdxConstants.NONE, result)

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

            "associate LLVM-exception findings with Apache-2.0" {
                val resultFile = File("src/test/assets/scancode-output-format-1.0.0_wasi-0.10.2.json")
                val result = resultFile.readTree()

                val summary = generateSummary(SpdxConstants.NONE, result)

                summary.licenseFindings should containExactlyInAnyOrder(
                    LicenseFinding(
                        license = "Apache-2.0",
                        location = TextLocation("Downloads/wasi-0.10.2+wasi-snapshot-preview1/Cargo.toml", 23, 23),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "Apache-2.0",
                        location = TextLocation("Downloads/wasi-0.10.2+wasi-snapshot-preview1/Cargo.toml.orig", 5, 5),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "Apache-2.0",
                        location = TextLocation("Downloads/wasi-0.10.2+wasi-snapshot-preview1/LICENSE-APACHE", 1, 201),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "Apache-2.0 WITH LLVM-exception",
                        location = TextLocation(
                            path = "Downloads/wasi-0.10.2+wasi-snapshot-preview1/" +
                                    "LICENSE-Apache-2.0_WITH_LLVM-exception",
                            startLine = 2,
                            endLine = 219
                        ),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "Apache-2.0",
                        location = TextLocation("Downloads/wasi-0.10.2+wasi-snapshot-preview1/README.md", 85, 88),
                        score = 66.67f
                    ),
                    LicenseFinding(
                        license = "Apache-2.0",
                        location = TextLocation("Downloads/wasi-0.10.2+wasi-snapshot-preview1/README.md", 93, 93),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-free-unknown",
                        location = TextLocation(
                            path = "Downloads/wasi-0.10.2+wasi-snapshot-preview1/ORG_CODE_OF_CONDUCT.md",
                            startLine = 106,
                            endLine = 106
                        ),
                        score = 50.0f
                    ),
                    LicenseFinding(
                        license = "LicenseRef-scancode-unknown-license-reference",
                        location = TextLocation("Downloads/wasi-0.10.2+wasi-snapshot-preview1/README.md", 88, 88),
                        score = 100.0f
                    ),
                    LicenseFinding(
                        license = "MIT",
                        location = TextLocation("Downloads/wasi-0.10.2+wasi-snapshot-preview1/LICENSE-MIT", 1, 23),
                        score = 100.0f
                    )
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

        "for output format 1.0.0 should" - {
            "properly parse details" {
                val result = File("src/test/assets/scancode-output-format-1.0.0_mime-types-2.1.18.json").readTree()

                val details = generateScannerDetails(result)
                details.name shouldBe ScanCode.SCANNER_NAME
                details.version shouldBe "30.1.0"
                details.configuration shouldContain "--timeout 300.0"
                details.configuration shouldContain "--processes 3"
            }
        }

        "for output format 2.0.0 should" - {
            "properly parse details" {
                val result = File("src/test/assets/scancode-output-format-2.0.0_mime-types-2.1.18.json").readTree()

                val details = generateScannerDetails(result)
                details.name shouldBe ScanCode.SCANNER_NAME
                details.version shouldBe "31.2.1"
                details.configuration shouldContain "--timeout 300.0"
                details.configuration shouldContain "--processes 3"
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
