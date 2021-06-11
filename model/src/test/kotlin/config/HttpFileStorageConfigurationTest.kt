/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.yamlMapper

class HttpFileStorageConfigurationTest : StringSpec({
    "Header values should be masked in serialization" {
        val config = HttpFileStorageConfiguration("url", headers = mapOf("key1" to "value1", "key2" to "value2"))

        val yaml = yamlMapper.writeValueAsString(config).trim()

        yaml shouldBe """
            ---
            url: "url"
            headers:
              key1: "***"
              key2: "***"
            """.trimIndent()
    }

    "Query string should be masked in serialization" {
        val config = HttpFileStorageConfiguration("url", "?query=value", emptyMap())

        val yaml = yamlMapper.writeValueAsString(config).trim()

        yaml shouldBe """
            ---
            url: "url"
            query: "***"
            """.trimIndent()
    }
})
