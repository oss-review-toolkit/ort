/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.TextLocation

@Suppress("LargeClass")
class ScanCodeResultParserTest : WordSpec({
    "ScanCode 2 results" should {
        "be correctly summarized" {
            val resultFile = File("src/test/assets/mime-types-2.1.18_scancode-2.9.7.json")
            val result = parseScanCodeResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            summary.fileCount shouldBe 10
            summary.packageVerificationCode shouldBe "9e3fdffc51568b300a457228055f8dc8a99fc64b"
        }
    }

    "ScanCode 3 results" should {
        "be correctly summarized" {
            val resultFile = File("src/test/assets/mime-types-2.1.18_scancode-3.0.2.json")
            val result = parseScanCodeResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            summary.fileCount shouldBe 10
            summary.packageVerificationCode shouldBe "8ec22f05b1a7006ae667901ae0853beff197c576"
        }
    }

    "generateSummary()" should {
        "properly summarize the license findings for ScanCode 2.2.1" {
            // TODO: minimize this test case.
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = parseScanCodeResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            summary.licenseFindings should containExactlyInAnyOrder(
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "LICENSE.BSD", startLine = 3, endLine = 21)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "bin/esparse.js", startLine = 5, endLine = 23)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "bin/esvalidate.js", startLine = 5, endLine = 23)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "esprima.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/benchmarks.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/browser-tests.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/check-complexity.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/check-version.js", startLine = 6, endLine = 24)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/downstream.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/grammar-tests.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/profile.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/regression-tests.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/unit-tests.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/utils/create-testcases.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/utils/error-to-object.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "test/utils/evaluate-testcase.js", startLine = 4, endLine = 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation(path = "tools/generate-fixtures.js", startLine = 3, endLine = 19)
                ),
                LicenseFinding(
                    license = "BSD-3-Clause",
                    location = TextLocation(path = "bower.json", startLine = 20, endLine = 20)
                ),
                LicenseFinding(
                    license = "BSD-3-Clause",
                    location = TextLocation(path = "package.json", startLine = 37, endLine = 37)
                ),
                LicenseFinding(
                    license = "BSD-3-Clause",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 1910,
                        endLine = 1910
                    )
                ),
                LicenseFinding(
                    license = "BSD-3-Clause",
                    location = TextLocation(path = "test/3rdparty/yui-3.12.0.js", startLine = 4, endLine = 4)
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation(path = "test/3rdparty/jquery-1.9.1.js", startLine = 10, endLine = 10)
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation(path = "test/3rdparty/jquery.mobile-1.4.2.js", startLine = 8, endLine = 8)
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 233,
                        endLine = 233
                    )
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 832,
                        endLine = 832
                    )
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 1522,
                        endLine = 1523
                    )
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 1538,
                        endLine = 1539
                    )
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 14001,
                        endLine = 14001
                    )
                ),
                LicenseFinding(
                    license = "LGPL-2.0+",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 28, endLine = 28)
                ),
                LicenseFinding(
                    license = "LGPL-2.0+",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 4718, endLine = 4718)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/angular-1.2.5.js", startLine = 4, endLine = 4)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/backbone-1.1.0.js", startLine = 5, endLine = 5)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/benchmark.js", startLine = 6, endLine = 6)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/jquery-1.9.1.js", startLine = 9, endLine = 9)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/jquery-1.9.1.js", startLine = 10, endLine = 10)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/jquery-1.9.1.js", startLine = 3690, endLine = 3690)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/jquery.mobile-1.4.2.js", startLine = 7, endLine = 7)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/jquery.mobile-1.4.2.js", startLine = 8, endLine = 8)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 232,
                        endLine = 232
                    )
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 233,
                        endLine = 233
                    )
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 831,
                        endLine = 831
                    )
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 832,
                        endLine = 832
                    )
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 1522,
                        endLine = 1523
                    )
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 1538,
                        endLine = 1539
                    )
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 1910,
                        endLine = 1910
                    )
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 14000,
                        endLine = 14000
                    )
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 14001,
                        endLine = 14001
                    )
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 21, endLine = 21)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 29, endLine = 29)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 542, endLine = 542)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 723, endLine = 723)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 807, endLine = 807)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 861, endLine = 861)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 991, endLine = 991)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 1202, endLine = 1202)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 1457, endLine = 1457)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 1584, endLine = 1584)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 1701, endLine = 1701)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 1881, endLine = 1881)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 3043, endLine = 3043)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 4103, endLine = 4103)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 4322, endLine = 4322)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 4514, endLine = 4514)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 4715, endLine = 4715)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 4999, endLine = 4999)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 5180, endLine = 5180)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 5350, endLine = 5350)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 5463, endLine = 5463)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 5542, endLine = 5542)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 5657, endLine = 5657)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 5937, endLine = 5937)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 6027, endLine = 6027)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 6110, endLine = 6110)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 6158, endLine = 6158)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 6234, endLine = 6234)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 6341, endLine = 6341)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation(path = "test/3rdparty/underscore-1.5.2.js", startLine = 4, endLine = 4)
                )
            )
        }

        "properly summarize the copyright findings for ScanCode 2.2.1" {
            // TODO: minimize this test case
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = parseScanCodeResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            summary.copyrightFindings should containExactlyInAnyOrder(
                CopyrightFinding(
                    statement = "(c) 2007-2008 Steven Levithan",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 1881, endLine = 1883)
                ),
                CopyrightFinding(
                    statement = "(c) 2009-2013 Jeremy Ashkenas, DocumentCloud and Investigative Reporters & " +
                            "Editors Underscore",
                    location = TextLocation(path = "test/3rdparty/underscore-1.5.2.js", startLine = 2, endLine = 4)
                ),
                CopyrightFinding(
                    statement = "(c) 2010-2011 Jeremy Ashkenas, DocumentCloud Inc.",
                    location = TextLocation(path = "test/3rdparty/backbone-1.1.0.js", startLine = 3, endLine = 6)
                ),
                CopyrightFinding(
                    statement = "(c) 2010-2014 Google, Inc. http://angularjs.org",
                    location = TextLocation(path = "test/3rdparty/angular-1.2.5.js", startLine = 2, endLine = 4)
                ),
                CopyrightFinding(
                    statement = "(c) 2011-2013 Jeremy Ashkenas, DocumentCloud and Investigative Reporters & " +
                            "Editors Backbone",
                    location = TextLocation(path = "test/3rdparty/backbone-1.1.0.js", startLine = 3, endLine = 6)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) 2005-2007 Sam Stephenson",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 27, endLine = 29)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) 2006 Dean Edwards, GNU Lesser General Public",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 27, endLine = 29)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) 2006-2012 Valerio Proietti",
                    location = TextLocation(path = "test/3rdparty/mootools-1.4.5.js", startLine = 23, endLine = 23)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) 2010 Cowboy Ben Alman",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 1521,
                        endLine = 1523
                    )
                ),
                CopyrightFinding(
                    statement = "Copyright (c) 2010 Cowboy Ben Alman",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 1537,
                        endLine = 1539
                    )
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "LICENSE.BSD", startLine = 1, endLine = 1)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "bin/esparse.js", startLine = 3, endLine = 3)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "bin/esvalidate.js", startLine = 3, endLine = 3)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "esprima.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/benchmarks.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/browser-tests.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/check-complexity.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/check-version.js", startLine = 4, endLine = 4)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/downstream.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/grammar-tests.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/profile.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/regression-tests.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/unit-tests.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/utils/create-testcases.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/utils/error-to-object.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "test/utils/evaluate-testcase.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation(path = "tools/generate-fixtures.js", startLine = 2, endLine = 2)
                ),
                CopyrightFinding(
                    statement = "Copyright 2005, 2012 jQuery Foundation, Inc.",
                    location = TextLocation(path = "test/3rdparty/jquery-1.9.1.js", startLine = 8, endLine = 10)
                ),
                CopyrightFinding(
                    statement = "Copyright 2010, 2014 jQuery Foundation, Inc.",
                    location = TextLocation(path = "test/3rdparty/jquery.mobile-1.4.2.js", startLine = 6, endLine = 8)
                ),
                CopyrightFinding(
                    statement = "Copyright 2010-2012 Mathias Bynens",
                    location = TextLocation(path = "test/3rdparty/benchmark.js", startLine = 2, endLine = 6)
                ),
                CopyrightFinding(
                    statement = "Copyright 2012 jQuery Foundation",
                    location = TextLocation(path = "test/3rdparty/jquery-1.9.1.js", startLine = 3688, endLine = 3691)
                ),
                CopyrightFinding(
                    statement = "Copyright 2013 Yahoo! Inc.",
                    location = TextLocation(path = "test/3rdparty/yui-3.12.0.js", startLine = 2, endLine = 3)
                ),
                CopyrightFinding(
                    statement = "Copyright 2013 jQuery Foundation",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 231,
                        endLine = 233
                    )
                ),
                CopyrightFinding(
                    statement = "Copyright 2013 jQuery Foundation",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 830,
                        endLine = 832
                    )
                ),
                CopyrightFinding(
                    statement = "Copyright 2013 jQuery Foundation",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 13999,
                        endLine = 14001
                    )
                ),
                CopyrightFinding(
                    statement = "copyright (c) 2012 Scott Jehl, Paul Irish, Nicholas Zakas.",
                    location = TextLocation(
                        path = "test/3rdparty/jquery.mobile-1.4.2.js",
                        startLine = 1910,
                        endLine = 1911
                    )
                ),
                CopyrightFinding(
                    statement = "copyright Robert Kieffer",
                    location = TextLocation(path = "test/3rdparty/benchmark.js", startLine = 2, endLine = 6)
                )
            )
        }

        "properly summarize license findings for ScanCode 2.9.7" {
            val resultFile = File("src/test/assets/aws-java-sdk-core-1.11.160_scancode-2.9.7.json")
            val result = parseScanCodeResult(resultFile)

            val actualFindings =
            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
                // verification code on an arbitrary file.
                generateSummary(Instant.now(), Instant.now(), resultFile, result)
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

        "properly summarize copyright findings for ScanCode 2.9.7" {
            val resultFile = File("src/test/assets/aws-java-sdk-core-1.11.160_scancode-2.9.7.json")
            val result = parseScanCodeResult(resultFile)

            val actualFindings =
            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
                // verification code on an arbitrary file.
                generateSummary(Instant.now(), Instant.now(), resultFile, result)
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

    "generateDetails" should {
        "parse a ScanCode 2.9.x result file" {
            val result = parseScanCodeResult(File("src/test/assets/mime-types-2.1.18_scancode-2.9.7.json"))

            val details = generateScannerDetails(result)
            details.name shouldBe ScanCode.SCANNER_NAME
            details.version shouldBe "2.9.7"
            details.configuration shouldContain "--copyright true"
            details.configuration shouldContain "--ignore *.ort.yml"
            details.configuration shouldContain "--ignore HERE_NOTICE"
            details.configuration shouldContain "--ignore META-INF/DEPENDENCIES"
            details.configuration shouldContain "--info true"
        }

        "parse a ScanCode 3.x result file" {
            val result = parseScanCodeResult(File("src/test/assets/mime-types-2.1.18_scancode-3.0.2.json"))

            val details = generateScannerDetails(result)
            details.name shouldBe ScanCode.SCANNER_NAME
            details.version shouldBe "3.0.2"
            details.configuration shouldContain "--timeout 300.0"
            details.configuration shouldContain "--processes 3"
        }

        "handle missing option properties gracefully" {
            val result = parseScanCodeResult(File("src/test/assets/mime-types-2.1.18_scancode-3.0.2.json"))
            val headers = result["headers"] as ArrayNode
            val headerObj = headers[0] as ObjectNode
            headerObj.remove("options")

            val details = generateScannerDetails(result)
            details.configuration shouldBe ""
        }

        "handle missing scanner version property gracefully" {
            val result =
                parseScanCodeResult(File("src/test/assets/mime-types-2.1.18_scancode-2.9.7.json")) as ObjectNode
            result.remove("scancode_version")

            val details = generateScannerDetails(result)
            details.version shouldBe ""
        }
    }

    "mapTimeoutErrors()" should {
        "return true for scan results with only timeout errors" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.post277.4d68f9377.json")
            val result = parseScanCodeResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

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
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = parseScanCodeResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            mapTimeoutErrors(summary.issues.toMutableList()) shouldBe false
        }
    }

    "mapUnknownErrors()" should {
        "return true for scan results with only memory errors" {
            val resultFile = File("src/test/assets/very-long-json-lines_scancode-2.2.1.post277.4d68f9377.json")
            val result = parseScanCodeResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            val issues = summary.issues.toMutableList()

            mapUnknownIssues(issues) shouldBe true
            issues.joinToString("\n") { it.message } shouldBe listOf(
                "ERROR: MemoryError while scanning file 'data.json'."
            ).joinToString("\n")
        }

        "return false for scan results with other unknown errors" {
            val resultFile = File("src/test/assets/kotlin-annotation-processing-gradle-1.2.21_scancode.json")
            val result = parseScanCodeResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            val issues = summary.issues.toMutableList()

            mapUnknownIssues(issues) shouldBe false
            issues.joinToString("\n") { it.message } shouldBe listOf(
                "ERROR: AttributeError while scanning file 'compiler/testData/cli/js-dce/withSourceMap.js.map' " +
                        "('NoneType' object has no attribute 'splitlines')."
            ).joinToString("\n")
        }

        "return false for scan results without errors" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = parseScanCodeResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            mapUnknownIssues(summary.issues.toMutableList()) shouldBe false
        }
    }
})
