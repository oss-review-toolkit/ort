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
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should

import java.io.File
import java.time.Instant

import kotlinx.serialization.json.decodeFromStream

import org.ossreviewtoolkit.clients.scanoss.FullScanResponse
import org.ossreviewtoolkit.clients.scanoss.ScanOssService
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

class ScanOssResultParserTest : WordSpec({
    "generateSummary()" should {
        "properly summarize JUnit 4.12 findings" {
            val result = File("src/test/assets/scanoss-junit-4.12.json").inputStream().use {
                ScanOssService.JSON.decodeFromStream<FullScanResponse>(it)
            }

            val time = Instant.now()
            val summary = generateSummary(time, time, SpdxConstants.NONE, result)

            summary.licenses.map { it.toString() } should containExactlyInAnyOrder(
                "Apache-2.0",
                "EPL-1.0",
                "MIT",
                "LicenseRef-scancode-free-unknown",
                "LicenseRef-scanoss-SSPL"
            )

            summary.licenseFindings should haveSize(201)
            summary.licenseFindings should containAll(
                LicenseFinding(
                    license = "Apache-2.0",
                    location = TextLocation(
                        path = "hopscotch-rails-0.1.2.1/vendor/assets/javascripts/hopscotch.js",
                        startLine = TextLocation.UNKNOWN_LINE,
                        endLine = TextLocation.UNKNOWN_LINE
                    )
                )
            )

            summary.copyrightFindings should haveSize(7)
            summary.copyrightFindings should containAll(
                CopyrightFinding(
                    statement = "Copyright 2013 LinkedIn Corp.",
                    location = TextLocation(
                        path = "hopscotch-rails-0.1.2.1/vendor/assets/javascripts/hopscotch.js",
                        startLine = TextLocation.UNKNOWN_LINE,
                        endLine = TextLocation.UNKNOWN_LINE
                    )
                )
            )
        }
    }
})
