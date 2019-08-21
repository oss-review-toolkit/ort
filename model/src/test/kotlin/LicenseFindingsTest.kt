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

class LicenseFindingsTest : StringSpec({
    val licenseFinding = LicenseFindings(
        "license",
        sortedSetOf(
            TextLocation("path 1", 1, 2),
            TextLocation("path 2", 3, 4)
        ),
        sortedSetOf(
            CopyrightFinding("copyright 1", sortedSetOf()),
            CopyrightFinding("copyright 2", sortedSetOf())
        )
    )

    "can be serialized and deserialized" {
        val serializedLicenseFinding = yamlMapper.writeValueAsString(licenseFinding)
        val deserializedLicenseFinding = yamlMapper.readValue<LicenseFindings>(serializedLicenseFinding)

        deserializedLicenseFinding shouldBe licenseFinding
    }

    "is serialized as expected" {
        val yaml = yamlMapper.writeValueAsString(licenseFinding)

        yaml.trim() shouldBe """
            ---
            license: "license"
            locations:
            - path: "path 1"
              start_line: 1
              end_line: 2
            - path: "path 2"
              start_line: 3
              end_line: 4
            copyrights:
            - statement: "copyright 1"
              locations: []
            - statement: "copyright 2"
              locations: []
            """.trimIndent()
    }

    "can be deserialized from a single license string" {
        val yaml = """
            ---
            "license"
            """.trimIndent()

        val deserializedLicenseFinding = yamlMapper.readValue<LicenseFindings>(yaml)

        deserializedLicenseFinding shouldBe LicenseFindings("license", sortedSetOf(), sortedSetOf())
    }

    "can be deserialized from a license finding without locations" {
        val yaml = """
            ---
            license: "license"
            copyrights:
            - "copyright 1"
            - "copyright 2"
            """.trimIndent()

        val deserializedLicenseFinding = yamlMapper.readValue<LicenseFindings>(yaml)

        deserializedLicenseFinding shouldBe LicenseFindings(
            "license",
            sortedSetOf(),
            sortedSetOf(
                CopyrightFinding("copyright 1", sortedSetOf()),
                CopyrightFinding("copyright 2", sortedSetOf())
            )
        )
    }
})
