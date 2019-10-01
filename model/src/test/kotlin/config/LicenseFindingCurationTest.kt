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

package com.here.ort.model.config

import com.fasterxml.jackson.module.kotlin.readValue
import com.here.ort.model.yamlMapper

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class LicenseFindingCurationTest : WordSpec({
    "A License finding curation" should {
        "deserialize startLines as expected" {
            val yaml = """
                path: "a/path"
                start_lines: "1,2,3,5"
                detected_license: "MIT"
                reason: "INCORRECT"
                concluded_license: "Apache-2.0"
                """.trimIndent()

            val curation = yamlMapper
                .readValue<LicenseFindingCuration>(yaml)
                .let { yamlMapper.writeValueAsString(it) }
                .let { yamlMapper.readValue<LicenseFindingCuration>(it) }

            curation.startLines shouldBe listOf(1, 2, 3, 5)
        }
    }
})
