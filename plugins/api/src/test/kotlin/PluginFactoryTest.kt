/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.api

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

private val testDescriptor = PluginDescriptor(
    id = "test",
    displayName = "Test",
    description = "Test plugin",
    options = listOf(
        PluginOption(
            name = "stringList",
            description = "A string list option",
            type = PluginOptionType.STRING_LIST,
            defaultValue = "",
            aliases = emptyList(),
            isNullable = false,
            isRequired = false
        )
    )
)

private val testFactory = object : PluginFactory<Plugin> {
    override val descriptor = testDescriptor

    override fun create(config: PluginConfig) =
        object : Plugin {
            override val descriptor = testDescriptor
        }
}

class PluginFactoryTest : WordSpec({
    "parseStringListOption()" should {
        "return an empty list for an empty default value" {
            val result = testFactory.parseStringListOption("stringList", PluginConfig.EMPTY)

            result should beEmpty()
        }

        "return a list with trimmed values" {
            val config = PluginConfig(options = mapOf("stringList" to " a , b , c "))

            val result = testFactory.parseStringListOption("stringList", config)

            result shouldBe listOf("a", "b", "c")
        }

        "filter out empty strings from the result" {
            val config = PluginConfig(options = mapOf("stringList" to "a,,b, ,c"))

            val result = testFactory.parseStringListOption("stringList", config)

            result shouldBe listOf("a", "b", "c")
        }
    }
})
