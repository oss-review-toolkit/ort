/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.storages.utils

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File

import org.ossreviewtoolkit.model.readTree
import org.ossreviewtoolkit.scanner.scanners.scancode.MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION
import org.ossreviewtoolkit.scanner.scanners.scancode.ScanCode

class ScanCodeDetailsParserTest : FreeSpec({
    "generateDetails()" - {
        "for ScanCode 3.0.2 should" - {
            "properly parse details" {
                val result = File("src/test/assets/scancode-3.0.2_mime-types-2.1.18.json").readTree()

                val details = getScanCodeDetails(result)
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

                val details = getScanCodeDetails(result)
                details.configuration shouldBe ""
            }
        }

        for (version in 1..MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION) {
            "for output format $version.0.0 should" - {
                "properly parse details" {
                    val filename = "scancode-output-format-$version.0.0_mime-types-2.1.18.json"
                    val result = File("src/test/assets/$filename").readTree()

                    val details = getScanCodeDetails(result)
                    details.name shouldBe ScanCode.SCANNER_NAME
                    details.configuration shouldContain "--timeout 300.0"
                    details.configuration shouldContain "--processes 3"
                }
            }
        }
    }
})
