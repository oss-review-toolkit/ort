/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.model.jsonMapper
import com.here.ort.scanner.scanners.ScanCode

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File
import java.time.Instant

class ScanCodeTest : WordSpec({
    "mapTimeoutErrors()" should {
        "return true for scan results with only timeout errors" {
            val resultFileName = "/esprima-2.7.3_scancode-2.2.1.post277.4d68f9377.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = ScanCode.getResult(resultFile)
            val summary = ScanCode.generateSummary(Instant.now(), Instant.now(), result)

            ScanCode.mapTimeoutErrors(summary.errors) shouldBe true
            summary.errors.joinToString("\n") shouldBe sortedSetOf(
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/angular-1.2.5.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/angular-1.2.5.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery-1.9.1.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery-1.9.1.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery.mobile-1.4.2.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery.mobile-1.4.2.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/mootools-1.4.5.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/mootools-1.4.5.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/underscore-1.5.2.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/underscore-1.5.2.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/yui-3.12.0.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/yui-3.12.0.tokens'."
            ).joinToString("\n")
        }

        "return false for scan results without errors" {
            val resultFileName = "/esprima-2.7.3_scancode-2.2.1.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = ScanCode.getResult(resultFile)
            val summary = ScanCode.generateSummary(Instant.now(), Instant.now(), result)

            ScanCode.mapTimeoutErrors(summary.errors) shouldBe false
        }
    }

    "mapUnknownErrors()" should {
        "return true for scan results with only memory errors" {
            val resultFileName = "/very-long-json-lines_scancode-2.2.1.post277.4d68f9377.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = ScanCode.getResult(resultFile)
            val summary = ScanCode.generateSummary(Instant.now(), Instant.now(), result)

            ScanCode.mapUnknownErrors(summary.errors) shouldBe true
            summary.errors.joinToString("\n") shouldBe sortedSetOf(
                    "ERROR: MemoryError while scanning file 'data.json'."
            ).joinToString("\n")
        }

        "return false for scan results with other unknown errors" {
            val resultFileName = "/kotlin-annotation-processing-gradle-1.2.21_scancode.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = ScanCode.getResult(resultFile)
            val summary = ScanCode.generateSummary(Instant.now(), Instant.now(), result)

            ScanCode.mapUnknownErrors(summary.errors) shouldBe false
            summary.errors.joinToString("\n") shouldBe sortedSetOf(
                    "ERROR: AttributeError while scanning file 'compiler/testData/cli/js-dce/withSourceMap.js.map' " +
                            "('NoneType' object has no attribute 'splitlines')."
            ).joinToString("\n")
        }

        "return false for scan results without errors" {
            val resultFileName = "/esprima-2.7.3_scancode-2.2.1.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = ScanCode.getResult(resultFile)
            val summary = ScanCode.generateSummary(Instant.now(), Instant.now(), result)

            ScanCode.mapUnknownErrors(summary.errors) shouldBe false
        }
    }

    "getRootLicense()" should {
        "succeed for a result containing a LICENSE file" {
            val resultFileName = "/oss-review-toolkit-license-and-readme_scancode-2.9.2.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = jsonMapper.readTree(resultFile)

            ScanCode.getRootLicense(result) shouldBe "Apache-2.0"
        }

        "succeed for a result containing a LICENSE.BSD file" {
            val resultFileName = "/esprima-2.7.3_scancode-2.2.1.post277.4d68f9377.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = jsonMapper.readTree(resultFile)

            ScanCode.getRootLicense(result) shouldBe "BSD-2-Clause"
        }
    }

    "getClosestCopyrightStatements()" should {
        "properly return closest jquery copyright statements" {
            val resultFileName = "/esprima-2.7.3_scancode-2.2.1.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = jsonMapper.readTree(resultFile)
            val copyrights = result["files"].find {
                it["path"].asText() == "test/3rdparty/jquery-1.9.1.js"
            }!!.get("copyrights")

            ScanCode.getClosestCopyrightStatements(copyrights, 5) shouldBe
                    sortedSetOf("Copyright 2005, 2012 jQuery Foundation, Inc.")
            ScanCode.getClosestCopyrightStatements(copyrights, 3690) shouldBe
                    sortedSetOf("Copyright 2012 jQuery Foundation")
        }

        "properly return closest mootools copyright statements" {
            val resultFileName = "/esprima-2.7.3_scancode-2.2.1.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = jsonMapper.readTree(resultFile)
            val copyrights = result["files"].find {
                it["path"].asText() == "test/3rdparty/mootools-1.4.5.js"
            }!!.get("copyrights")

            ScanCode.getClosestCopyrightStatements(copyrights, 28) shouldBe sortedSetOf(
                    "Copyright (c) 2005-2007 Sam Stephenson",
                    "Copyright (c) 2006 Dean Edwards, GNU Lesser General Public",
                    "Copyright (c) 2006-2012 Valerio Proietti"
            )
        }
    }

    "associateFindings()" should {
        "properly associate a separate copyright to a root license" {
            val resultFileName = "/oss-review-toolkit-license-and-readme_scancode-2.9.2.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = jsonMapper.readTree(resultFile)

            ScanCode.associateFindings(result) shouldBe sortedMapOf("Apache-2.0" to
                    sortedSetOf("Copyright (c) 2017-2018 HERE Europe B.V."))
        }

        "properly associate licenses to copyrights" {
            val resultFileName = "/esprima-2.7.3_scancode-2.2.1.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = ScanCode.getResult(resultFile)

            val expectedFindings = sortedMapOf(
                    "BSD-2-Clause" to sortedSetOf(
                            "Copyright (c) jQuery Foundation, Inc. and Contributors"
                    ),
                    "BSD-3-Clause" to sortedSetOf(
                            "copyright (c) 2012 Scott Jehl, Paul Irish, Nicholas Zakas.",
                            "Copyright 2013 Yahoo! Inc."
                    ),
                    "GPL-1.0+" to sortedSetOf(
                            "Copyright (c) 2010 Cowboy Ben Alman",
                            "Copyright 2005, 2012 jQuery Foundation, Inc.",
                            "Copyright 2010, 2014 jQuery Foundation, Inc.",
                            "Copyright 2013 jQuery Foundation"
                    ),
                    "LGPL-2.0+" to sortedSetOf(
                            "Copyright (c) 2005-2007 Sam Stephenson",
                            "Copyright (c) 2006 Dean Edwards, GNU Lesser General Public",
                            "Copyright (c) 2006-2012 Valerio Proietti"
                    ),
                    "MIT" to sortedSetOf(
                            "(c) 2007-2008 Steven Levithan",
                            "(c) 2009-2013 Jeremy Ashkenas, DocumentCloud and Investigative Reporters & Editors Underscore",
                            "(c) 2010-2011 Jeremy Ashkenas, DocumentCloud Inc.",
                            "(c) 2010-2014 Google, Inc. http://angularjs.org",
                            "(c) 2011-2013 Jeremy Ashkenas, DocumentCloud and Investigative Reporters & Editors Backbone",
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
            val actualFindings = ScanCode.associateFindings(result)

            actualFindings shouldBe expectedFindings
        }
    }
})
