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
                                    TextLocation("esprima.js", 4, 22),
                                    TextLocation("LICENSE.BSD", 3, 21),
                                    TextLocation("bin/esvalidate.js", 5, 23),
                                    TextLocation("bin/esparse.js", 5, 23),
                                    TextLocation("tools/generate-fixtures.js", 3, 19),
                                    TextLocation("test/check-complexity.js", 4, 22),
                                    TextLocation("test/regression-tests.js", 4, 22),
                                    TextLocation("test/downstream.js", 4, 22),
                                    TextLocation("test/browser-tests.js", 4, 22),
                                    TextLocation("test/profile.js", 4, 22),
                                    TextLocation("test/unit-tests.js", 4, 22),
                                    TextLocation("test/check-version.js", 6, 24),
                                    TextLocation("test/grammar-tests.js", 4, 22),
                                    TextLocation("test/benchmarks.js", 4, 22),
                                    TextLocation("test/utils/evaluate-testcase.js", 4, 22),
                                    TextLocation("test/utils/create-testcases.js", 4, 22),
                                    TextLocation("test/utils/error-to-object.js", 4, 22)
                            ),
                            sortedSetOf(
                                    "Copyright (c) jQuery Foundation, Inc. and Contributors"
                            )
                    ),
                    LicenseFinding(
                            "BSD-3-Clause",
                            sortedSetOf(
                                    TextLocation("package.json", 37, 37),
                                    TextLocation("bower.json", 20, 20),
                                    TextLocation("test/3rdparty/yui-3.12.0.js", 4, 4),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1910, 1910)
                            ),
                            sortedSetOf(
                                    "copyright (c) 2012 Scott Jehl, Paul Irish, Nicholas Zakas.",
                                    "Copyright 2013 Yahoo! Inc."
                            )
                    ),
                    LicenseFinding(
                            "GPL-1.0+",
                            sortedSetOf(
                                    TextLocation("test/3rdparty/jquery-1.9.1.js", 10, 10),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 8, 8),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 233, 233),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 832, 832),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1522, 1523),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1538, 1539),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 14001, 14001)
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
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 28, 28),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4718, 4718)
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
                                    TextLocation("test/3rdparty/benchmark.js", 6, 6),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 21, 21),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 29, 29),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 542, 542),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 723, 723),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 807, 807),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 861, 861),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 991, 991),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 1202, 1202),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 1457, 1457),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 1584, 1584),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 1701, 1701),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 1881, 1881),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 3043, 3043),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4103, 4103),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4322, 4322),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4514, 4514),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4715, 4715),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 4999, 4999),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5180, 5180),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5350, 5350),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5463, 5463),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5542, 5542),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5657, 5657),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 5937, 5937),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 6027, 6027),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 6110, 6110),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 6158, 6158),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 6234, 6234),
                                    TextLocation("test/3rdparty/mootools-1.4.5.js", 6341, 6341),
                                    TextLocation("test/3rdparty/angular-1.2.5.js", 4, 4),
                                    TextLocation("test/3rdparty/jquery-1.9.1.js", 9, 9),
                                    TextLocation("test/3rdparty/jquery-1.9.1.js", 10, 10),
                                    TextLocation("test/3rdparty/jquery-1.9.1.js", 3690, 3690),
                                    TextLocation("test/3rdparty/underscore-1.5.2.js", 4, 4),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 7, 7),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 8, 8),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 232, 232),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 233, 233),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 831, 831),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 832, 832),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1522, 1523),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1538, 1539),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1910, 1910),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 14000, 14000),
                                    TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 14001, 14001),
                                    TextLocation("test/3rdparty/backbone-1.1.0.js", 5, 5)
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
                    "--copyright --license --ignore *.ort.yml --info --strip-root --timeout 300 --json-pp " +
                    "--license-diag"
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
                    match("--copyright --license --ignore \\*.ort.yml --info --strip-root --timeout 300 " +
                            "--processes \\d+ --license-diag --verbose")
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
