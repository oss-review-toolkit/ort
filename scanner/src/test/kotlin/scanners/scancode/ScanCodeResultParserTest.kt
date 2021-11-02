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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
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
import org.ossreviewtoolkit.model.readJsonFile
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

@Suppress("LargeClass")
class ScanCodeResultParserTest : WordSpec({
    "ScanCode 2 results" should {
        "be correctly summarized" {
            val resultFile = File("src/test/assets/scancode-2.9.7_mime-types-2.1.18.json")
            val result = readJsonFile(resultFile)

            val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

            summary.licenseFindings.size shouldBe 4
            summary.copyrightFindings.size shouldBe 4
            summary.issues should beEmpty()
        }
    }

    "ScanCode 3 results" should {
        "be correctly summarized" {
            val resultFile = File("src/test/assets/scancode-3.0.2_mime-types-2.1.18.json")
            val result = readJsonFile(resultFile)

            val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

            summary.licenseFindings.size shouldBe 4
            summary.copyrightFindings.size shouldBe 4
            summary.issues should beEmpty()
        }
    }

    "generateSummary()" should {
        "properly summarize the license findings for ScanCode 2.2.1" {
            // TODO: minimize this test case.
            val resultFile = File("src/test/assets/scancode-2.2.1_esprima-2.7.3.json")
            val result = readJsonFile(resultFile)

            val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

            summary.licenseFindings should containExactlyInAnyOrder(
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("LICENSE.BSD", 3, 21)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("bin/esparse.js", 5, 23)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("bin/esvalidate.js", 5, 23)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("esprima.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/benchmarks.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/browser-tests.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/check-complexity.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/check-version.js", 6, 24)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/downstream.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/grammar-tests.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/profile.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/regression-tests.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/unit-tests.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/utils/create-testcases.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/utils/error-to-object.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("test/utils/evaluate-testcase.js", 4, 22)
                ),
                LicenseFinding(
                    license = "BSD-2-Clause",
                    location = TextLocation("tools/generate-fixtures.js", 3, 19)
                ),
                LicenseFinding(
                    license = "BSD-3-Clause",
                    location = TextLocation("bower.json", 20, 20)
                ),
                LicenseFinding(
                    license = "BSD-3-Clause",
                    location = TextLocation("package.json", 37, 37)
                ),
                LicenseFinding(
                    license = "BSD-3-Clause",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1910)
                ),
                LicenseFinding(
                    license = "BSD-3-Clause",
                    location = TextLocation("test/3rdparty/yui-3.12.0.js", 4)
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation("test/3rdparty/jquery-1.9.1.js", 10)
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 8)
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 233)
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 832)
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1522, 1523)
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1538, 1539)
                ),
                LicenseFinding(
                    license = "GPL-1.0+",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 14001)
                ),
                LicenseFinding(
                    license = "LGPL-2.0+",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 28)
                ),
                LicenseFinding(
                    license = "LGPL-2.0+",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 4718)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/angular-1.2.5.js", 4)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/backbone-1.1.0.js", 5)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/benchmark.js", 6)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery-1.9.1.js", 9)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery-1.9.1.js", 10)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery-1.9.1.js", 3690)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 7)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 8)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 232)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 233)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 831)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 832)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1522, 1523)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1538, 1539)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1910)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 14000)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 14001)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 21)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 29)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 542)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 723)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 807)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 861)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 991)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 1202)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 1457)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 1584)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 1701)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 1881)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 3043)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 4103)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 4322)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 4514)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 4715)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 4999)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 5180)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 5350)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 5463)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 5542)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 5657)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 5937)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 6027)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 6110)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 6158)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 6234)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 6341)
                ),
                LicenseFinding(
                    license = "MIT",
                    location = TextLocation("test/3rdparty/underscore-1.5.2.js", 4)
                )
            )
        }

        "properly parse license expressions for ScanCode 3.2.1" {
            val resultFile = File("src/test/assets/scancode-3.2.1_h2database-1.4.200.json")
            val result = readJsonFile(resultFile)

            val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

            summary.licenseFindings should containExactlyInAnyOrder(
                LicenseFinding(
                    license = "(MPL-2.0 OR EPL-1.0) AND LicenseRef-scancode-proprietary-license",
                    location = TextLocation("h2/src/main/org/h2/table/Column.java", 2, 3)
                ),
                LicenseFinding(
                    license = "LicenseRef-scancode-public-domain",
                    location = TextLocation("h2/src/main/org/h2/table/Column.java", 317)
                )
            )
        }

        "properly summarize the copyright findings for ScanCode 2.2.1" {
            // TODO: minimize this test case
            val resultFile = File("src/test/assets/scancode-2.2.1_esprima-2.7.3.json")
            val result = readJsonFile(resultFile)

            val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

            summary.copyrightFindings should containExactlyInAnyOrder(
                CopyrightFinding(
                    statement = "(c) 2007-2008 Steven Levithan",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 1881, 1883)
                ),
                CopyrightFinding(
                    statement = "(c) 2009-2013 Jeremy Ashkenas, DocumentCloud and Investigative Reporters & " +
                            "Editors Underscore",
                    location = TextLocation("test/3rdparty/underscore-1.5.2.js", 2, 4)
                ),
                CopyrightFinding(
                    statement = "(c) 2010-2011 Jeremy Ashkenas, DocumentCloud Inc.",
                    location = TextLocation("test/3rdparty/backbone-1.1.0.js", 3, 6)
                ),
                CopyrightFinding(
                    statement = "(c) 2010-2014 Google, Inc. http://angularjs.org",
                    location = TextLocation("test/3rdparty/angular-1.2.5.js", 2, 4)
                ),
                CopyrightFinding(
                    statement = "(c) 2011-2013 Jeremy Ashkenas, DocumentCloud and Investigative Reporters & " +
                            "Editors Backbone",
                    location = TextLocation("test/3rdparty/backbone-1.1.0.js", 3, 6)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) 2005-2007 Sam Stephenson",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 27, 29)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) 2006 Dean Edwards, GNU Lesser General Public",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 27, 29)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) 2006-2012 Valerio Proietti",
                    location = TextLocation("test/3rdparty/mootools-1.4.5.js", 23)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) 2010 Cowboy Ben Alman",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1521, 1523)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) 2010 Cowboy Ben Alman",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1537, 1539)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("LICENSE.BSD", 1)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("bin/esparse.js", 3)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("bin/esvalidate.js", 3)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("esprima.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/benchmarks.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/browser-tests.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/check-complexity.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/check-version.js", 4)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/downstream.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/grammar-tests.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/profile.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/regression-tests.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/unit-tests.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/utils/create-testcases.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/utils/error-to-object.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("test/utils/evaluate-testcase.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright (c) jQuery Foundation, Inc. and Contributors",
                    location = TextLocation("tools/generate-fixtures.js", 2)
                ),
                CopyrightFinding(
                    statement = "Copyright 2005, 2012 jQuery Foundation, Inc.",
                    location = TextLocation("test/3rdparty/jquery-1.9.1.js", 8, 10)
                ),
                CopyrightFinding(
                    statement = "Copyright 2010, 2014 jQuery Foundation, Inc.",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 6, 8)
                ),
                CopyrightFinding(
                    statement = "Copyright 2010-2012 Mathias Bynens",
                    location = TextLocation("test/3rdparty/benchmark.js", 2, 6)
                ),
                CopyrightFinding(
                    statement = "Copyright 2012 jQuery Foundation",
                    location = TextLocation("test/3rdparty/jquery-1.9.1.js", 3688, 3691)
                ),
                CopyrightFinding(
                    statement = "Copyright 2013 Yahoo! Inc.",
                    location = TextLocation("test/3rdparty/yui-3.12.0.js", 2, 3)
                ),
                CopyrightFinding(
                    statement = "Copyright 2013 jQuery Foundation",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 231, 233)
                ),
                CopyrightFinding(
                    statement = "Copyright 2013 jQuery Foundation",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 830, 832)
                ),
                CopyrightFinding(
                    statement = "Copyright 2013 jQuery Foundation",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 13999, 14001)
                ),
                CopyrightFinding(
                    statement = "copyright (c) 2012 Scott Jehl, Paul Irish, Nicholas Zakas.",
                    location = TextLocation("test/3rdparty/jquery.mobile-1.4.2.js", 1910, 1911)
                ),
                CopyrightFinding(
                    statement = "copyright Robert Kieffer",
                    location = TextLocation("test/3rdparty/benchmark.js", 2, 6)
                )
            )
        }

        "properly summarize license findings for ScanCode 2.9.7" {
            val resultFile = File("src/test/assets/scancode-2.9.7_aws-java-sdk-core-1.11.160.json")
            val result = readJsonFile(resultFile)

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

        "properly summarize copyright findings for ScanCode 2.9.7" {
            val resultFile = File("src/test/assets/scancode-2.9.7_aws-java-sdk-core-1.11.160.json")
            val result = readJsonFile(resultFile)

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

    "generateDetails()" should {
        "parse a ScanCode 2.9.x result file" {
            val result = readJsonFile(File("src/test/assets/scancode-2.9.7_mime-types-2.1.18.json"))

            val details = generateScannerDetails(result)
            details.name shouldBe ScanCode.SCANNER_NAME
            details.version shouldBe "2.9.7"
            details.configuration shouldContain "--copyright true"
            details.configuration shouldContain "--ignore *.ort.yml"
            details.configuration shouldContain "--ignore META-INF/DEPENDENCIES"
            details.configuration shouldContain "--info true"
        }

        "parse a ScanCode 3.x result file" {
            val result = readJsonFile(File("src/test/assets/scancode-3.0.2_mime-types-2.1.18.json"))

            val details = generateScannerDetails(result)
            details.name shouldBe ScanCode.SCANNER_NAME
            details.version shouldBe "3.0.2"
            details.configuration shouldContain "--timeout 300.0"
            details.configuration shouldContain "--processes 3"
        }

        "handle missing option properties gracefully" {
            val result = readJsonFile(File("src/test/assets/scancode-3.0.2_mime-types-2.1.18.json"))
            val headers = result["headers"] as ArrayNode
            val headerObj = headers[0] as ObjectNode
            headerObj.remove("options")

            val details = generateScannerDetails(result)
            details.configuration shouldBe ""
        }

        "handle missing scanner version property gracefully" {
            val result =
                readJsonFile(File("src/test/assets/scancode-2.9.7_mime-types-2.1.18.json")) as ObjectNode
            result.remove("scancode_version")

            val details = generateScannerDetails(result)
            details.version shouldBe ""
        }
    }

    "mapTimeoutErrors()" should {
        "return true for scan results with only timeout errors" {
            val resultFile = File("src/test/assets/scancode-2.2.1.post277.4d68f9377_esprima-2.7.3.json")
            val result = readJsonFile(resultFile)

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
            val result = readJsonFile(resultFile)

            val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

            mapTimeoutErrors(summary.issues.toMutableList()) shouldBe false
        }
    }

    "mapUnknownErrors()" should {
        "return true for scan results with only memory errors" {
            val resultFile = File("src/test/assets/scancode-2.2.1.post277.4d68f9377_very-long-json-lines.json")
            val result = readJsonFile(resultFile)

            val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

            val issues = summary.issues.toMutableList()

            mapUnknownIssues(issues) shouldBe true
            issues.joinToString("\n") { it.message } shouldBe listOf(
                "ERROR: MemoryError while scanning file 'data.json'."
            ).joinToString("\n")
        }

        "return false for scan results with other unknown errors" {
            val resultFile = File("src/test/assets/scancode-2.2.1.post277.4d68f9377_" +
                    "kotlin-annotation-processing-gradle-1.2.21.json")
            val result = readJsonFile(resultFile)

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
            val result = readJsonFile(resultFile)

            val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

            mapUnknownIssues(summary.issues.toMutableList()) shouldBe false
        }
    }

    "replaceLicenseKeys()" should {
        "Properly handle redundant replacements" {
            val expression = "public-domain"
            val replacements = listOf(
                LicenseKeyReplacement("public-domain", "LicenseRef-scancode-public-domain"),
                LicenseKeyReplacement("public-domain", "LicenseRef-scancode-public-domain")
            )

            val result = replaceLicenseKeys(expression, replacements)

            result shouldBe "LicenseRef-scancode-public-domain"
        }

        "Properly handle replacements with a license key being a suffix of another" {
            val expression = "agpl-3.0-openssl"
            val replacements = listOf(
                LicenseKeyReplacement("agpl-3.0-openssl", "LicenseRef-scancode-agpl-3.0-openssl"),
                LicenseKeyReplacement("openssl", "LicenseRef-scancode-openssl")
            )

            val result = replaceLicenseKeys(expression, replacements)

            result shouldBe "LicenseRef-scancode-agpl-3.0-openssl"
        }

        "Properly handle braces" {
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
