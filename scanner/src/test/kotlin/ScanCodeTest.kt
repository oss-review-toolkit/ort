/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.scanner.scanners.ScanCode

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
                    LicenseFinding("Apache-2.0", sortedSetOf("Copyright (c) 2017-2018 HERE Europe B.V."))
            )
        }

        "properly associate licenses to copyrights" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = scanner.getResult(resultFile)

            val expectedFindings = sortedSetOf(
                    LicenseFinding(
                            "BSD-2-Clause",
                            sortedSetOf(
                                    "Copyright (c) jQuery Foundation, Inc. and Contributors"
                            )
                    ),
                    LicenseFinding(
                            "BSD-3-Clause",
                            sortedSetOf(
                                    "copyright (c) 2012 Scott Jehl, Paul Irish, Nicholas Zakas.",
                                    "Copyright 2013 Yahoo! Inc."
                            )
                    ),
                    LicenseFinding(
                            "GPL-1.0+",
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
                                    "Copyright (c) 2005-2007 Sam Stephenson",
                                    "Copyright (c) 2006 Dean Edwards, GNU Lesser General Public",
                                    "Copyright (c) 2006-2012 Valerio Proietti"
                            )
                    ),
                    LicenseFinding(
                            "MIT",
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
    }
})
