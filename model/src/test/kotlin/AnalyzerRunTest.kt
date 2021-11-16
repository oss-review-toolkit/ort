/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.time.Instant

class AnalyzerRunTest : StringSpec({
    "AnalyzerRun without timestamps can be deserialized" {
        val yaml = """
            ---
            environment:
              os: "Linux"
              tool_versions: {}
            config:
              allow_dynamic_versions: false
            result:
              projects: []
              packages: []
              has_issues: false
        """.trimIndent()

        val analyzerRun = yamlMapper.readValue<AnalyzerRun>(yaml)

        analyzerRun.startTime shouldBe Instant.EPOCH
        analyzerRun.endTime shouldBe Instant.EPOCH
    }

    "AnalyzerRun with timestamps can be deserialized" {
        val yaml = """
            ---
            start_time: "1970-01-01T00:00:10Z"
            end_time: "1970-01-01T00:00:20Z"
            environment:
              os: "Linux"
              tool_versions: {}
            config:
              allow_dynamic_versions: false
            result:
              projects: []
              packages: []
              has_issues: false
        """.trimIndent()

        val analyzerRun = yamlMapper.readValue<AnalyzerRun>(yaml)

        analyzerRun.startTime shouldBe Instant.ofEpochSecond(10)
        analyzerRun.endTime shouldBe Instant.ofEpochSecond(20)
    }
})
