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

package com.here.ort.scanner

import com.here.ort.model.LicenseFinding
import com.here.ort.model.TextLocation
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.scanner.scanners.ScanCode

import io.kotlintest.matchers.haveSize
import io.kotlintest.matchers.match
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File
import java.time.Instant

class ScanCodeTest : WordSpec({
    val scanner = ScanCode(ScannerConfiguration())

    "mapTimeoutErrors()" should {
        "return true for scan results with only timeout errors" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.post277.4d68f9377.json")
            val result = scanner.getResult(resultFile)
            val summary = scanner.generateSummary(Instant.now(), Instant.now(), result)
            val errors = summary.errors.toMutableList()

            scanner.mapTimeoutErrors(errors) shouldBe true
            errors.joinToString("\n") { it.message } shouldBe listOf(
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
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = scanner.getResult(resultFile)
            val summary = scanner.generateSummary(Instant.now(), Instant.now(), result)

            scanner.mapTimeoutErrors(summary.errors.toMutableList()) shouldBe false
        }
    }

    "mapUnknownErrors()" should {
        "return true for scan results with only memory errors" {
            val resultFile = File("src/test/assets/very-long-json-lines_scancode-2.2.1.post277.4d68f9377.json")
            val result = scanner.getResult(resultFile)
            val summary = scanner.generateSummary(Instant.now(), Instant.now(), result)
            val errors = summary.errors.toMutableList()

            scanner.mapUnknownErrors(errors) shouldBe true
            errors.joinToString("\n") { it.message } shouldBe listOf(
                    "ERROR: MemoryError while scanning file 'data.json'."
            ).joinToString("\n")
        }

        "return false for scan results with other unknown errors" {
            val resultFile = File("src/test/assets/kotlin-annotation-processing-gradle-1.2.21_scancode.json")
            val result = scanner.getResult(resultFile)
            val summary = scanner.generateSummary(Instant.now(), Instant.now(), result)
            val errors = summary.errors.toMutableList()

            scanner.mapUnknownErrors(errors) shouldBe false
            errors.joinToString("\n") { it.message } shouldBe listOf(
                    "ERROR: AttributeError while scanning file 'compiler/testData/cli/js-dce/withSourceMap.js.map' " +
                            "('NoneType' object has no attribute 'splitlines')."
            ).joinToString("\n")
        }

        "return false for scan results without errors" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = scanner.getResult(resultFile)
            val summary = scanner.generateSummary(Instant.now(), Instant.now(), result)

            scanner.mapUnknownErrors(summary.errors.toMutableList()) shouldBe false
        }
    }

    "getRootLicense()" should {
        "succeed for a result containing a LICENSE file" {
            val resultFile = File("src/test/assets/oss-review-toolkit-license-and-readme_scancode-2.9.2.json")
            val result = jsonMapper.readTree(resultFile)

            scanner.getRootLicense(result) shouldBe "Apache-2.0"
        }

        "succeed for a result containing a LICENSE.BSD file" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.post277.4d68f9377.json")
            val result = jsonMapper.readTree(resultFile)

            scanner.getRootLicense(result) shouldBe "BSD-2-Clause"
        }
    }

    "getClosestCopyrightStatements()" should {
        "properly return closest jquery copyright statements" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = jsonMapper.readTree(resultFile)
            val copyrights = result["files"].find {
                it["path"].asText() == "test/3rdparty/jquery-1.9.1.js"
            }!!.get("copyrights")

            scanner.getClosestCopyrightStatements(copyrights, 5) shouldBe
                    sortedSetOf("Copyright 2005, 2012 jQuery Foundation, Inc.")
            scanner.getClosestCopyrightStatements(copyrights, 3690) shouldBe
                    sortedSetOf("Copyright 2012 jQuery Foundation")
        }

        "properly return closest mootools copyright statements" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = jsonMapper.readTree(resultFile)
            val copyrights = result["files"].find {
                it["path"].asText() == "test/3rdparty/mootools-1.4.5.js"
            }!!.get("copyrights")

            scanner.getClosestCopyrightStatements(copyrights, 28) shouldBe sortedSetOf(
                    "Copyright (c) 2005-2007 Sam Stephenson",
                    "Copyright (c) 2006 Dean Edwards, GNU Lesser General Public",
                    "Copyright (c) 2006-2012 Valerio Proietti"
            )
        }
    }

    "associateFindings()" should {
        "properly associate a separate copyright to a root license" {
            val resultFile = File("src/test/assets/oss-review-toolkit-license-and-readme_scancode-2.9.2.json")
            val result = jsonMapper.readTree(resultFile)

            scanner.associateFindings(result) shouldBe sortedSetOf(
                    LicenseFinding(
                            "Apache-2.0",
                            sortedSetOf(TextLocation("LICENSE", 1, 201)),
                            sortedSetOf("Copyright (C) 2017-2019 HERE Europe B.V.")
                    )
            )
        }

        "properly associate licenses to locations and copyrights" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = scanner.getResult(resultFile)

            val expectedFindings = sortedSetOf(
                    LicenseFinding(
                            "BSD-2-Clause",
                            sortedSetOf(
                                    TextLocation(path="esprima.js", startLine=4, endLine=22),
                                    TextLocation(path="LICENSE.BSD", startLine=3, endLine=21),
                                    TextLocation(path="bin/esvalidate.js", startLine=5, endLine=23),
                                    TextLocation(path="bin/esparse.js", startLine=5, endLine=23),
                                    TextLocation(path="tools/generate-fixtures.js", startLine=3, endLine=19),
                                    TextLocation(path="test/check-complexity.js", startLine=4, endLine=22),
                                    TextLocation(path="test/regression-tests.js", startLine=4, endLine=22),
                                    TextLocation(path="test/downstream.js", startLine=4, endLine=22),
                                    TextLocation(path="test/browser-tests.js", startLine=4, endLine=22),
                                    TextLocation(path="test/profile.js", startLine=4, endLine=22),
                                    TextLocation(path="test/unit-tests.js", startLine=4, endLine=22),
                                    TextLocation(path="test/check-version.js", startLine=6, endLine=24),
                                    TextLocation(path="test/grammar-tests.js", startLine=4, endLine=22),
                                    TextLocation(path="test/benchmarks.js", startLine=4, endLine=22),
                                    TextLocation(path="test/utils/evaluate-testcase.js", startLine=4, endLine=22),
                                    TextLocation(path="test/utils/create-testcases.js", startLine=4, endLine=22),
                                    TextLocation(path="test/utils/error-to-object.js", startLine=4, endLine=22)
                            ),
                            sortedSetOf(
                                    "Copyright (c) jQuery Foundation, Inc. and Contributors"
                            )
                    ),
                    LicenseFinding(
                            "BSD-3-Clause",
                            sortedSetOf(
                                    TextLocation(path="package.json", startLine=37, endLine=37),
                                    TextLocation(path="bower.json", startLine=20, endLine=20),
                                    TextLocation(path="test/3rdparty/yui-3.12.0.js", startLine=4, endLine=4),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=1910, endLine=1910)
                            ),
                            sortedSetOf(
                                    "copyright (c) 2012 Scott Jehl, Paul Irish, Nicholas Zakas.",
                                    "Copyright 2013 Yahoo! Inc."
                            )
                    ),
                    LicenseFinding(
                            "GPL-1.0+",
                            sortedSetOf(
                                    TextLocation(path="test/3rdparty/jquery-1.9.1.js", startLine=10, endLine=10),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=8, endLine=8),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=233, endLine=233),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=832, endLine=832),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=1522, endLine=1523),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=1538, endLine=1539),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=14001, endLine=14001)
                            ),
                            sortedSetOf(
                                    "Copyright (c) 2010 Cowboy Ben Alman",
                                    "Copyright 2005, 2012 jQuery Foundation, Inc.",
                                    "Copyright 2010, 2014 jQuery Foundation, Inc.",
                                    "Copyright 2013 jQuery Foundation"
                            )
                    ),
                    LicenseFinding(
                            "LGPL-2.0+",
                            sortedSetOf(
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=28, endLine=28),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=4718, endLine=4718)
                            ),
                            sortedSetOf(
                                    "Copyright (c) 2005-2007 Sam Stephenson",
                                    "Copyright (c) 2006 Dean Edwards, GNU Lesser General Public",
                                    "Copyright (c) 2006-2012 Valerio Proietti"
                            )
                    ),
                    LicenseFinding(
                            "MIT",
                            sortedSetOf(
                                    TextLocation(path="test/3rdparty/benchmark.js", startLine=6, endLine=6),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=21, endLine=21),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=29, endLine=29),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=542, endLine=542),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=723, endLine=723),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=807, endLine=807),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=861, endLine=861),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=991, endLine=991),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=1202, endLine=1202),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=1457, endLine=1457),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=1584, endLine=1584),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=1701, endLine=1701),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=1881, endLine=1881),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=3043, endLine=3043),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=4103, endLine=4103),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=4322, endLine=4322),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=4514, endLine=4514),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=4715, endLine=4715),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=4999, endLine=4999),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=5180, endLine=5180),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=5350, endLine=5350),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=5463, endLine=5463),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=5542, endLine=5542),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=5657, endLine=5657),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=5937, endLine=5937),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=6027, endLine=6027),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=6110, endLine=6110),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=6158, endLine=6158),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=6234, endLine=6234),
                                    TextLocation(path="test/3rdparty/mootools-1.4.5.js", startLine=6341, endLine=6341),
                                    TextLocation(path="test/3rdparty/angular-1.2.5.js", startLine=4, endLine=4),
                                    TextLocation(path="test/3rdparty/jquery-1.9.1.js", startLine=9, endLine=9),
                                    TextLocation(path="test/3rdparty/jquery-1.9.1.js", startLine=10, endLine=10),
                                    TextLocation(path="test/3rdparty/jquery-1.9.1.js", startLine=3690, endLine=3690),
                                    TextLocation(path="test/3rdparty/underscore-1.5.2.js", startLine=4, endLine=4),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=7, endLine=7),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=8, endLine=8),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=232, endLine=232),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=233, endLine=233),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=831, endLine=831),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=832, endLine=832),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=1522, endLine=1523),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=1538, endLine=1539),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=1910, endLine=1910),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=14000, endLine=14000),
                                    TextLocation(path="test/3rdparty/jquery.mobile-1.4.2.js", startLine=14001, endLine=14001),
                                    TextLocation(path="test/3rdparty/backbone-1.1.0.js", startLine=5, endLine=5)
                            ),
                            sortedSetOf(
                                    "(c) 2007-2008 Steven Levithan",
                                    "(c) 2009-2013 Jeremy Ashkenas, DocumentCloud and Investigative Reporters & " +
                                            "Editors Underscore",
                                    "(c) 2010-2011 Jeremy Ashkenas, DocumentCloud Inc.",
                                    "(c) 2010-2014 Google, Inc. http://angularjs.org",
                                    "(c) 2011-2013 Jeremy Ashkenas, DocumentCloud and Investigative Reporters & " +
                                            "Editors Backbone",
                                    "Copyright (c) 2005-2007 Sam Stephenson",
                                    "Copyright (c) 2006 Dean Edwards, GNU Lesser General Public",
                                    "Copyright (c) 2006-2012 Valerio Proietti",
                                    "Copyright (c) 2010 Cowboy Ben Alman",
                                    "copyright (c) 2012 Scott Jehl, Paul Irish, Nicholas Zakas.",
                                    "Copyright 2005, 2012 jQuery Foundation, Inc.",
                                    "Copyright 2010, 2014 jQuery Foundation, Inc.",
                                    "Copyright 2010-2012 Mathias Bynens",
                                    "Copyright 2012 jQuery Foundation",
                                    "Copyright 2013 jQuery Foundation",
                                    "copyright Robert Kieffer"
                            )
                    )
            )
            val actualFindings = scanner.associateFindings(result)

            actualFindings shouldBe expectedFindings
        }

        "properly associate licenses to locations and copyrights for the new output format" {
            val resultFile = File("src/test/assets/aws-java-sdk-core-1.11.160_scancode-2.9.7.json")
            val result = scanner.getResult(resultFile)

            val actualFindings = scanner.associateFindings(result)

            actualFindings should haveSize(1)

            val finding = actualFindings.first()
            finding.license shouldBe "Apache-2.0"

            // Only compare the first 10 elements because the result contains too many locations to list them all.
            finding.locations should haveSize(517)
            finding.locations.toList().subList(0, 10) shouldBe listOf(
                    TextLocation(path="com/amazonaws/AbortedException.java", startLine=4, endLine=13),
                    TextLocation(path="com/amazonaws/AmazonClientException.java", startLine=4, endLine=13),
                    TextLocation(path="com/amazonaws/AmazonServiceException.java", startLine=4, endLine=13),
                    TextLocation(path="com/amazonaws/AmazonWebServiceClient.java", startLine=4, endLine=13),
                    TextLocation(path="com/amazonaws/AmazonWebServiceRequest.java", startLine=4, endLine=13),
                    TextLocation(path="com/amazonaws/AmazonWebServiceResponse.java", startLine=4, endLine=13),
                    TextLocation(path="com/amazonaws/AmazonWebServiceResult.java", startLine=4, endLine=13),
                    TextLocation(path="com/amazonaws/ApacheHttpClientConfig.java", startLine=4, endLine=13),
                    TextLocation(path="com/amazonaws/ClientConfiguration.java", startLine=4, endLine=13),
                    TextLocation(path="com/amazonaws/ClientConfigurationFactory.java", startLine=4, endLine=13)
            )

            finding.copyrights shouldBe sortedSetOf(
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

    "getConfiguration()" should {
        "return the default values if the scanner configuration is empty" {
            val scanCode = ScanCode(ScannerConfiguration())

            scanCode.getConfiguration() shouldBe
                    "--copyright --license --info --strip-root --timeout 300 --json-pp --license-diag"
        }

        "return the non config values from the scanner configuration" {
            val scanCode = ScanCode(ScannerConfiguration(scanner = mapOf(
                    "ScanCode" to mapOf(
                            "commandLine" to "--command --line",
                            "commandLineNonConfig" to "--commandLineNonConfig",
                            "debugCommandLine" to "--debug --commandLine",
                            "debugCommandLineNonConfig" to "--debugCommandLineNonConfig"
                    )
            )))

            scanCode.getConfiguration() shouldBe "--command --line --json-pp --debug --commandLine"
        }
    }

    "commandLineOptions" should {
        "contain the default values if the scanner configuration is empty" {
            val scanCode = ScanCode(ScannerConfiguration())

            scanCode.commandLineOptions.joinToString(" ") should
                    match("--copyright --license --info --strip-root --timeout 300 --processes \\d+ --license-diag " +
                            "--verbose")
        }

        "contain the values from the scanner configuration" {
            val scanCode = ScanCode(ScannerConfiguration(scanner = mapOf(
                    "ScanCode" to mapOf(
                            "commandLine" to "--command --line",
                            "commandLineNonConfig" to "--commandLineNonConfig",
                            "debugCommandLine" to "--debug --commandLine",
                            "debugCommandLineNonConfig" to "--debugCommandLineNonConfig"
                    )
            )))

            scanCode.commandLineOptions.joinToString(" ") shouldBe
                    "--command --line --commandLineNonConfig --debug --commandLine --debugCommandLineNonConfig"
        }
    }
})
