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

package com.here.ort.model

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.time.Instant

class ScannerRunTest : StringSpec() {
    init {
        "ScannerRun without timestamps can be deserialized" {
            val yaml = """
                ---
                environment:
                  os: "Linux"
                  tool_versions: {}
                config:
                  scanner: null
                results:
                  scopes: []
                  scan_results: []
                  storage_stats:
                    num_reads: 0
                    num_hits: 0
                  has_errors: false
            """.trimIndent()

            val scannerRun = yamlMapper.readValue<ScannerRun>(yaml)

            scannerRun.startTime shouldBe Instant.EPOCH
            scannerRun.endTime shouldBe Instant.EPOCH
        }

        "ScannerRun with timestamps can be deserialized" {
            val yaml = """
                ---
                start_time: "1970-01-01T00:00:10Z"
                end_time: "1970-01-01T00:00:10Z"
                environment:
                  os: "Linux"
                  tool_versions: {}
                config:
                  scanner: null
                results:
                  scopes: []
                  scan_results: []
                  storage_stats:
                    num_reads: 0
                    num_hits: 0
                  has_errors: false
            """.trimIndent()

            val scannerRun = yamlMapper.readValue<ScannerRun>(yaml)

            scannerRun.startTime shouldBe Instant.ofEpochSecond(10)
            scannerRun.endTime shouldBe Instant.ofEpochSecond(10)
        }
    }
}
