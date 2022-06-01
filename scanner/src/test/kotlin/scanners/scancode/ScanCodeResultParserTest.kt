/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2020 Bosch.IO GmbH
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
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.file.beRelative
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.readTree
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.test.transformingCollectionMatcher

@Suppress("LargeClass")
class ScanCodeResultParserTest : FreeSpec({
    "generateSummary()" - {
        "for ScanCode 2.2.1 should" - {
            "properly summarize license findings" {
                val resultFile = File("src/test/assets/scancode-2.2.1_esprima-2.7.3.json")
                val result = resultFile.readTree()

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

                summary should containLicensesExactly(
                    "BSD-2-Clause",
                    "BSD-3-Clause",
                    "GPL-1.0+",
                    "LGPL-2.0+",
                    "MIT"
                )

                summary should containLocationsForLicenseExactly(
                    "BSD-2-Clause",
                    TextLocation("LICENSE.BSD", 3, 21),
                    TextLocation("bin/esparse.js", 5, 23),
                    TextLocation("bin/esvalidate.js", 5, 23),
                    TextLocation("esprima.js", 4, 22),
                    TextLocation("test/benchmarks.js", 4, 22),
                    TextLocation("test/browser-tests.js", 4, 22),
                    TextLocation("test/check-complexity.js", 4, 22),
                    TextLocation("test/check-version.js", 6, 24),
                    TextLocation("test/downstream.js", 4, 22),
                    TextLocation("test/grammar-tests.js", 4, 22),
                    TextLocation("test/profile.js", 4, 22),
                    TextLocation("test/regression-tests.js", 4, 22),
                    TextLocation("test/unit-tests.js", 4, 22),
                    TextLocation("test/utils/create-testcases.js", 4, 22),
                    TextLocation("test/utils/error-to-object.js", 4, 22),
                    TextLocation("test/utils/evaluate-testcase.js", 4, 22),
                    TextLocation("tools/generate-fixtures.js", 3, 19)
                )

                summary should containLocationsForLicenseExactly(
                    "BSD-3-Clause",
                    TextLocation("bower.json", 20, 20),
                    TextLocation("package.json", 37, 37),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1910),
                    TextLocation("test/3rdparty/yui-3.12.0.js", 4)
                )

                summary should containLocationsForLicenseExactly(
                    "GPL-1.0+",
                    TextLocation("test/3rdparty/jquery-1.9.1.js", 10),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 8),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 233),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 832),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1522, 1523),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1538, 1539),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 14001)
                )

                summary should containLocationsForLicenseExactly(
                    "LGPL-2.0+",
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 28),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4718)
                )

                summary should containLocationsForLicenseExactly(
                    "MIT",
                    TextLocation("test/3rdparty/angular-1.2.5.js", 4),
                    TextLocation("test/3rdparty/backbone-1.1.0.js", 5),
                    TextLocation("test/3rdparty/benchmark.js", 6),
                    TextLocation("test/3rdparty/jquery-1.9.1.js", 9),
                    TextLocation("test/3rdparty/jquery-1.9.1.js", 10),
                    TextLocation("test/3rdparty/jquery-1.9.1.js", 3690),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 7),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 8),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 232),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 233),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 831),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 832),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1522, 1523),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1538, 1539),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1910),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 14000),
                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 14001),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 21),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 29),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 542),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 723),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 807),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 861),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 991),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 1202),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 1457),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 1584),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 1701),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 1881),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 3043),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4103),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4322),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4514),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4715),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4999),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5180),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5350),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5463),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5542),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5657),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5937),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 6027),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 6110),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 6158),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 6234),
                    TextLocation("test/3rdparty/mootools-1.4.5.js", 6341),
                    TextLocation("test/3rdparty/underscore-1.5.2.js", 4)
                )
            }

            "properly summarize copyright findings" {
                val resultFile = File("src/test/assets/scancode-2.2.1_esprima-2.7.3.json")
                val result = resultFile.readTree()

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

                summary should containCopyrightsExactly(
                    "(c) 2007-2008 Steven Levithan" to
                            listOf(TextLocation("test/3rdparty/mootools-1.4.5.js", 1881, 1883)),
                    "(c) 2009-2013 Jeremy Ashkenas, DocumentCloud and Investigative Reporters & Editors Underscore" to
                            listOf(TextLocation("test/3rdparty/underscore-1.5.2.js", 2, 4)),
                    "(c) 2010-2011 Jeremy Ashkenas, DocumentCloud Inc." to
                            listOf(TextLocation("test/3rdparty/backbone-1.1.0.js", 3, 6)),
                    "(c) 2010-2014 Google, Inc. http://angularjs.org" to
                            listOf(TextLocation("test/3rdparty/angular-1.2.5.js", 2, 4)),
                    "(c) 2011-2013 Jeremy Ashkenas, DocumentCloud and Investigative Reporters & Editors Backbone" to
                            listOf(TextLocation("test/3rdparty/backbone-1.1.0.js", 3, 6)),
                    "Copyright (c) 2005-2007 Sam Stephenson" to
                            listOf(TextLocation("test/3rdparty/mootools-1.4.5.js", 27, 29)),
                    "Copyright (c) 2006 Dean Edwards, GNU Lesser General Public" to
                            listOf(TextLocation("test/3rdparty/mootools-1.4.5.js", 27, 29)),
                    "Copyright (c) 2006-2012 Valerio Proietti" to
                            listOf(TextLocation("test/3rdparty/mootools-1.4.5.js", 23)),
                    "Copyright (c) 2010 Cowboy Ben Alman" to listOf(
                        TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1521, 1523),
                        TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1537, 1539)
                    ),
                    "Copyright (c) jQuery Foundation, Inc. and Contributors" to listOf(
                        TextLocation("LICENSE.BSD", 1),
                        TextLocation("bin/esparse.js", 3),
                        TextLocation("bin/esvalidate.js", 3),
                        TextLocation("esprima.js", 2),
                        TextLocation("test/benchmarks.js", 2),
                        TextLocation("test/browser-tests.js", 2),
                        TextLocation("test/check-complexity.js", 2),
                        TextLocation("test/check-version.js", 4),
                        TextLocation("test/downstream.js", 2),
                        TextLocation("test/grammar-tests.js", 2),
                        TextLocation("test/profile.js", 2),
                        TextLocation("test/regression-tests.js", 2),
                        TextLocation("test/unit-tests.js", 2),
                        TextLocation("test/utils/create-testcases.js", 2),
                        TextLocation("test/utils/error-to-object.js", 2),
                        TextLocation("test/utils/evaluate-testcase.js", 2),
                        TextLocation("tools/generate-fixtures.js", 2)
                    ),
                    "Copyright 2005, 2012 jQuery Foundation, Inc." to
                            listOf(TextLocation("test/3rdparty/jquery-1.9.1.js", 8, 10)),
                    "Copyright 2010, 2014 jQuery Foundation, Inc." to
                            listOf(TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 6, 8)),
                    "Copyright 2010-2012 Mathias Bynens" to
                            listOf(TextLocation("test/3rdparty/benchmark.js", 2, 6)),
                    "Copyright 2012 jQuery Foundation" to
                            listOf(TextLocation("test/3rdparty/jquery-1.9.1.js", 3688, 3691)),
                    "Copyright 2013 Yahoo! Inc." to
                            listOf(TextLocation("test/3rdparty/yui-3.12.0.js", 2, 3)),
                    "Copyright 2013 jQuery Foundation" to listOf(
                        TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 231, 233),
                        TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 830, 832),
                        TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 13999, 14001)
                    ),
                    "copyright (c) 2012 Scott Jehl, Paul Irish, Nicholas Zakas." to
                            listOf(TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1910, 1911)),
                    "copyright Robert Kieffer" to
                            listOf(TextLocation("test/3rdparty/benchmark.js", 2, 6))
                )
            }
        }

        "for ScanCode 2.9.7 should" - {
            "get correct counts" {
                val resultFile = File("src/test/assets/scancode-2.9.7_mime-types-2.1.18.json")
                val result = resultFile.readTree()

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

                summary.licenseFindings.size shouldBe 4
                summary.copyrightFindings.size shouldBe 4
                summary.issues should beEmpty()
            }

            "properly summarize license findings" {
                val resultFile = File("src/test/assets/scancode-2.9.7_aws-java-sdk-core-1.11.160.json")
                val result = resultFile.readTree()

                val actualFindings = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)
                    .licenseFindings

                actualFindings.distinctBy { it.license } should haveSize(1)
                actualFindings should haveSize(517)
                actualFindings.toList().subList(0, 10).map { it.location } should containExactlyInAnyOrder(
                    TextLocation("com/amazonaws/AbortedException.java", 4, 13),
                    TextLocation("com/amazonaws/AmazonClientException.java", 4, 13),
                    TextLocation("com/amazonaws/AmazonServiceException.java", 4, 13),
                    TextLocation("com/amazonaws/AmazonWebServiceClient.java", 4, 13),
                    TextLocation("com/amazonaws/AmazonWebServiceRequest.java", 4, 13),
                    TextLocation("com/amazonaws/AmazonWebServiceResponse.java", 4, 13),
                    TextLocation("com/amazonaws/AmazonWebServiceResult.java", 4, 13),
                    TextLocation("com/amazonaws/ApacheHttpClientConfig.java", 4, 13),
                    TextLocation("com/amazonaws/ClientConfiguration.java", 4, 13),
                    TextLocation("com/amazonaws/ClientConfigurationFactory.java", 4, 13)
                )
            }

            "properly summarize copyright findings" {
                val resultFile = File("src/test/assets/scancode-2.9.7_aws-java-sdk-core-1.11.160.json")
                val result = resultFile.readTree()

                val actualFindings = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)
                    .copyrightFindings

                actualFindings.mapTo(mutableSetOf()) { it.statement } should containExactlyInAnyOrder(
                    "Copyright (c) 2016 Amazon.com, Inc.",
                    "Copyright (c) 2016. Amazon.com, Inc.",
                    "Copyright 2010-2017 Amazon.com, Inc.",
                    "Copyright 2011-2017 Amazon Technologies, Inc.",
                    "Copyright 2011-2017 Amazon.com, Inc.",
                    "Copyright 2012-2017 Amazon Technologies, Inc.",
                    "Copyright 2012-2017 Amazon.com, Inc.",
                    "Copyright 2013-2017 Amazon Technologies, Inc.",
                    "Copyright 2013-2017 Amazon.com, Inc.",
                    "Copyright 2014-2017 Amazon Technologies, Inc.",
                    "Copyright 2014-2017 Amazon.com, Inc.",
                    "Copyright 2015-2017 Amazon Technologies, Inc.",
                    "Copyright 2015-2017 Amazon.com, Inc.",
                    "Copyright 2016-2017 Amazon.com, Inc.",
                    "Portions copyright 2006-2009 James Murty."
                )
            }
        }

        "for ScanCode 3.0.2 should" - {
            "get correct counts" {
                val resultFile = File("src/test/assets/scancode-3.0.2_mime-types-2.1.18.json")
                val result = resultFile.readTree()

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

                summary.licenseFindings.size shouldBe 4
                summary.copyrightFindings.size shouldBe 4
                summary.issues should beEmpty()
            }
        }

        "for ScanCode 3.2.1rc2 should" - {
            "properly parse license expressions" {
                val resultFile = File("src/test/assets/scancode-3.2.1rc2_h2database-1.4.200.json")
                val result = resultFile.readTree()

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

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

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)
                val fileExtensions = listOf("html", "java", "txt")

                summary.licenseFindings.forAll {
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

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

                summary.licenseFindings.size shouldBe 5
                summary.copyrightFindings.size shouldBe 4
                summary.issues should beEmpty()
            }

            "properly summarize license findings" {
                val resultFile = File("src/test/assets/scancode-output-format-1.0.0_mime-types-2.1.18.json")
                val result = resultFile.readTree()

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

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

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

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

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

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
        "for ScanCode 2.9.7 should" - {
            "properly parse details" {
                val result = File("src/test/assets/scancode-2.9.7_mime-types-2.1.18.json").readTree()

                val details = generateScannerDetails(result)
                details.name shouldBe ScanCode.SCANNER_NAME
                details.version shouldBe "2.9.7"
                details.configuration shouldContain "--copyright true"
                details.configuration shouldContain "--ignore *.ort.yml"
                details.configuration shouldContain "--ignore META-INF/DEPENDENCIES"
                details.configuration shouldContain "--info true"
            }

            "handle a missing scanner version property gracefully" {
                val result = File("src/test/assets/scancode-2.9.7_mime-types-2.1.18.json").readTree()
                    as ObjectNode
                result.remove("scancode_version")

                val details = generateScannerDetails(result)
                details.version shouldBe ""
            }
        }

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
                val headerObj = headers[0] as ObjectNode
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
    }

    "mapTimeoutErrors()" - {
        "for ScanCode 2.2.1 should" - {
            "return true for scan results with only timeout errors" {
                val resultFile = File("src/test/assets/scancode-2.2.1.post277.4d68f9377_esprima-2.7.3.json")
                val result = resultFile.readTree()

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

                val issues = summary.issues.toMutableList()

                mapTimeoutErrors(issues) shouldBe true
                issues.joinToString("\n") { it.message } shouldBe listOf(
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/angular-1.2.5.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery-1.9.1.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery.mobile-1.4.2.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/mootools-1.4.5.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/underscore-1.5.2.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/yui-3.12.0.tokens'.",

                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/angular-1.2.5.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery-1.9.1.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery.mobile-1.4.2.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/mootools-1.4.5.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/underscore-1.5.2.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/yui-3.12.0.json'."
                ).joinToString("\n")
            }

            "return false for scan results without errors" {
                val resultFile = File("src/test/assets/scancode-2.2.1_esprima-2.7.3.json")
                val result = resultFile.readTree()

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

                mapTimeoutErrors(summary.issues.toMutableList()) shouldBe false
            }
        }
    }

    "mapUnknownErrors()" - {
        "for ScanCode 2.2.1 should" - {
            "return true for scan results with only memory errors" {
                val resultFile = File("src/test/assets/scancode-2.2.1.post277.4d68f9377_very-long-json-lines.json")
                val result = resultFile.readTree()

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

                val issues = summary.issues.toMutableList()

                mapUnknownIssues(issues) shouldBe true
                issues.joinToString("\n") { it.message } shouldBe listOf(
                    "ERROR: MemoryError while scanning file 'data.json'."
                ).joinToString("\n")
            }

            "return false for scan results with other unknown errors" {
                val resultFile = File(
                    "src/test/assets/scancode-2.2.1.post277.4d68f9377_kotlin-annotation-processing-gradle-1.2.21.json"
                )
                val result = resultFile.readTree()

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

                val issues = summary.issues.toMutableList()

                mapUnknownIssues(issues) shouldBe false
                issues.joinToString("\n") { it.message } shouldBe listOf(
                    "ERROR: AttributeError while scanning file 'compiler/testData/cli/js-dce/withSourceMap.js.map' " +
                            "('NoneType' object has no attribute 'splitlines')."
                ).joinToString("\n")
            }

            "return false for scan results without errors" {
                val resultFile = File("src/test/assets/scancode-2.2.1_esprima-2.7.3.json")
                val result = resultFile.readTree()

                val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

                mapUnknownIssues(summary.issues.toMutableList()) shouldBe false
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
