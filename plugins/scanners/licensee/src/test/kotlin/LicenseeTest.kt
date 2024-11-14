/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.licensee

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.scanner.ScannerWrapperConfig

class LicenseeTest : StringSpec({
    val scanner = Licensee("Licensee", ScannerWrapperConfig.EMPTY)

    "Parsing details from results succeeds" {
        val result = """
            {
              "version": "9.12.0",
              "parameters": [
                "--json",
                "--no-readme"
              ],
              "output": {
                "contentType": "application/json",
                "content": {
                  "licenses": [],
                  "matched_files": []
                }
              }
            }
        """.trimIndent()

        scanner.parseDetails(result) shouldBe ScannerDetails(
            name = "Licensee",
            version = "9.12.0",
            configuration = "--json --no-readme"
        )
    }
})
