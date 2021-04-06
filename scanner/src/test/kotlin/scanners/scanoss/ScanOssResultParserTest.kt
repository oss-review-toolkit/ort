/*
 * Copyright (C) 2020-2021 SCANOSS TECNOLOGIAS SL
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

package org.ossreviewtoolkit.scanner.scanners.scanoss

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.readJsonFile
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

class ScanOssResultParserTest : WordSpec({
    "generateSummary()" should {
        "properly summarize pigz.c findings" {
            val resultFile = File("src/test/assets/pigz-c-scanoss.json")
            val result = readJsonFile(resultFile)

            val summary = generateSummary(Instant.now(), Instant.now(), SpdxConstants.NONE, result)

            summary.licenseFindings should containExactlyInAnyOrder(
                    LicenseFinding(
                            license = "Zlib",
                            location = TextLocation(
                                path = "pigz-2.4/pigz.c",
                                startLine = TextLocation.UNKNOWN_LINE,
                                endLine = TextLocation.UNKNOWN_LINE
                            )
                    )
            )
            summary.copyrightFindings should containExactlyInAnyOrder(
                    CopyrightFinding(
                            statement = "Copyright (C) 2007-2017 Mark Adler",
                            location = TextLocation(
                                path = "pigz-2.4/pigz.c",
                                startLine = TextLocation.UNKNOWN_LINE,
                                endLine = TextLocation.UNKNOWN_LINE
                            )
                    )
            )
        }
    }
})
