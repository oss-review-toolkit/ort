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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class OptionParsersTest : WordSpec({
    "parseBooleanOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "booleanOption",
                description = "A boolean option.",
                type = PluginOptionType.BOOLEAN,
                defaultValue = "false",
                aliases = emptyList(),
                isNullable = false,
                isRequired = false
            )
        )

        "return the correct boolean value from the options" {
            factory.parseBooleanOption(
                "booleanOption",
                PluginConfig(options = mapOf("booleanOption" to "true"))
            ) shouldBe true
        }

        "return the default value if the option is not set" {
            factory.parseBooleanOption("booleanOption", PluginConfig.EMPTY) shouldBe false
        }

        "throw an exception for an invalid boolean value" {
            shouldThrow<IllegalArgumentException> {
                factory.parseBooleanOption(
                    "booleanOption",
                    PluginConfig(options = mapOf("booleanOption" to "notABoolean"))
                )
            }
        }
    }

    "parseNullableBooleanOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "nullableBooleanOption",
                description = "A nullable boolean option.",
                type = PluginOptionType.BOOLEAN,
                defaultValue = null,
                aliases = emptyList(),
                isNullable = true,
                isRequired = false
            )
        )

        "return the correct boolean value from the options" {
            factory.parseNullableBooleanOption(
                "nullableBooleanOption",
                PluginConfig(options = mapOf("nullableBooleanOption" to "true"))
            ) shouldBe true
        }

        "return null if the option is not set" {
            factory.parseNullableBooleanOption("nullableBooleanOption", PluginConfig.EMPTY) shouldBe null
        }

        "throw an exception for an invalid boolean value" {
            shouldThrow<IllegalArgumentException> {
                factory.parseNullableBooleanOption(
                    "nullableBooleanOption",
                    PluginConfig(options = mapOf("nullableBooleanOption" to "notABoolean"))
                )
            }
        }
    }

    "parseIntegerOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "integerOption",
                description = "An integer option.",
                type = PluginOptionType.INTEGER,
                defaultValue = "42",
                aliases = emptyList(),
                isNullable = false,
                isRequired = false
            )
        )

        "return the correct integer value from the options" {
            factory.parseIntegerOption(
                "integerOption",
                PluginConfig(options = mapOf("integerOption" to "100"))
            ) shouldBe 100
        }

        "return the default value if the option is not set" {
            factory.parseIntegerOption("integerOption", PluginConfig.EMPTY) shouldBe 42
        }

        "throw an exception for an invalid integer value" {
            shouldThrow<NumberFormatException> {
                factory.parseIntegerOption(
                    "integerOption",
                    PluginConfig(options = mapOf("integerOption" to "notAnInteger"))
                )
            }
        }
    }

    "parseNullableIntegerOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "nullableIntegerOption",
                description = "A nullable integer option.",
                type = PluginOptionType.INTEGER,
                defaultValue = null,
                aliases = emptyList(),
                isNullable = true,
                isRequired = false
            )
        )

        "return the correct integer value from the options" {
            factory.parseNullableIntegerOption(
                "nullableIntegerOption",
                PluginConfig(options = mapOf("nullableIntegerOption" to "100"))
            ) shouldBe 100
        }

        "return null if the option is not set" {
            factory.parseNullableIntegerOption("nullableIntegerOption", PluginConfig.EMPTY) shouldBe null
        }

        "throw an exception for an invalid integer value" {
            shouldThrow<NumberFormatException> {
                factory.parseNullableIntegerOption(
                    "nullableIntegerOption",
                    PluginConfig(options = mapOf("nullableIntegerOption" to "notAnInteger"))
                )
            }
        }
    }

    "parseLongOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "longOption",
                description = "A long option.",
                type = PluginOptionType.LONG,
                defaultValue = "42000000000",
                aliases = emptyList(),
                isNullable = false,
                isRequired = false
            )
        )

        "return the correct long value from the options" {
            factory.parseLongOption(
                "longOption",
                PluginConfig(options = mapOf("longOption" to "100000000000"))
            ) shouldBe 100000000000L
        }

        "return the default value if the option is not set" {
            factory.parseLongOption("longOption", PluginConfig.EMPTY) shouldBe 42000000000L
        }

        "throw an exception for an invalid long value" {
            shouldThrow<NumberFormatException> {
                factory.parseLongOption(
                    "longOption",
                    PluginConfig(options = mapOf("longOption" to "notALong"))
                )
            }
        }
    }

    "parseNullableLongOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "nullableLongOption",
                description = "A nullable long option.",
                type = PluginOptionType.LONG,
                defaultValue = null,
                aliases = emptyList(),
                isNullable = true,
                isRequired = false
            )
        )

        "return the correct long value from the options" {
            factory.parseNullableLongOption(
                "nullableLongOption",
                PluginConfig(options = mapOf("nullableLongOption" to "100000000000"))
            ) shouldBe 100000000000L
        }

        "return null if the option is not set" {
            factory.parseNullableLongOption("nullableLongOption", PluginConfig.EMPTY) shouldBe null
        }

        "throw an exception for an invalid long value" {
            shouldThrow<NumberFormatException> {
                factory.parseNullableLongOption(
                    "nullableLongOption",
                    PluginConfig(options = mapOf("nullableLongOption" to "notALong"))
                )
            }
        }
    }

    "parseSecretOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "secretOption",
                description = "A secret option.",
                type = PluginOptionType.SECRET,
                defaultValue = "defaultSecret",
                aliases = emptyList(),
                isNullable = false,
                isRequired = false
            )
        )

        "return the correct secret value from the options" {
            factory.parseSecretOption(
                "secretOption",
                PluginConfig(secrets = mapOf("secretOption" to "mySecretValue"))
            ) shouldBe Secret("mySecretValue")
        }

        "return the default value if the option is not set" {
            factory.parseSecretOption("secretOption", PluginConfig.EMPTY) shouldBe Secret("defaultSecret")
        }
    }

    "parseNullableSecretOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "nullableSecretOption",
                description = "A nullable secret option.",
                type = PluginOptionType.SECRET,
                defaultValue = null,
                aliases = emptyList(),
                isNullable = true,
                isRequired = false
            )
        )

        "return the correct secret value from the options" {
            factory.parseNullableSecretOption(
                "nullableSecretOption",
                PluginConfig(secrets = mapOf("nullableSecretOption" to "mySecretValue"))
            ) shouldBe Secret("mySecretValue")
        }

        "return null if the option is not set" {
            factory.parseNullableSecretOption("nullableSecretOption", PluginConfig.EMPTY) shouldBe null
        }
    }

    "parseStringOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "stringOption",
                description = "A string option.",
                type = PluginOptionType.STRING,
                defaultValue = "defaultString",
                aliases = emptyList(),
                isNullable = false,
                isRequired = false
            )
        )

        "return the correct string value from the options" {
            factory.parseStringOption(
                "stringOption",
                PluginConfig(options = mapOf("stringOption" to "customString"))
            ) shouldBe "customString"
        }

        "return the default value if the option is not set" {
            factory.parseStringOption("stringOption", PluginConfig.EMPTY) shouldBe "defaultString"
        }
    }

    "parseNullableStringOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "nullableStringOption",
                description = "A nullable string option.",
                type = PluginOptionType.STRING,
                defaultValue = null,
                aliases = emptyList(),
                isNullable = true,
                isRequired = false
            )
        )

        "return the correct string value from the options" {
            factory.parseNullableStringOption(
                "nullableStringOption",
                PluginConfig(options = mapOf("nullableStringOption" to "customString"))
            ) shouldBe "customString"
        }

        "return null if the option is not set" {
            factory.parseNullableStringOption("nullableStringOption", PluginConfig.EMPTY) shouldBe null
        }
    }

    "parseStringListOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "stringListOption",
                description = "A string list option.",
                type = PluginOptionType.STRING_LIST,
                defaultValue = "a,b,c",
                aliases = emptyList(),
                isNullable = true,
                isRequired = false
            )
        )

        "return the correct string list from the options" {
            factory.parseStringListOption(
                "stringListOption",
                PluginConfig(options = mapOf("stringListOption" to "x,y,z"))
            ) shouldBe listOf("x", "y", "z")
        }

        "return the default value if the option is not set" {
            factory.parseStringListOption("stringListOption", PluginConfig.EMPTY) shouldBe listOf("a", "b", "c")
        }

        "trim values and filter out empty strings" {
            factory.parseStringListOption(
                "stringListOption",
                PluginConfig(options = mapOf("stringListOption" to " a , , b , c , "))
            ) shouldBe listOf("a", "b", "c")
        }

        "return an empty list for an empty default value" {
            createFactory(
                PluginOption(
                    name = "stringListOption",
                    description = "A string list option.",
                    type = PluginOptionType.STRING_LIST,
                    defaultValue = "",
                    aliases = emptyList(),
                    isNullable = false,
                    isRequired = true
                )
            ).parseStringListOption("stringListOption", PluginConfig.EMPTY) should beEmpty()
        }
    }

    "parseNullableStringListOption()" should {
        val factory = createFactory(
            PluginOption(
                name = "nullableStringListOption",
                description = "A nullable string list option.",
                type = PluginOptionType.STRING_LIST,
                defaultValue = null,
                aliases = emptyList(),
                isNullable = true,
                isRequired = false
            )
        )

        "return the correct string list from the options" {
            factory.parseNullableStringListOption(
                "nullableStringListOption",
                PluginConfig(options = mapOf("nullableStringListOption" to "x,y,z"))
            ) shouldBe listOf("x", "y", "z")
        }

        "return null if the option is not set" {
            factory.parseNullableStringListOption("nullableStringListOption", PluginConfig.EMPTY) shouldBe null
        }

        "trim values and filter out empty strings" {
            factory.parseNullableStringListOption(
                "nullableStringListOption",
                PluginConfig(options = mapOf("nullableStringListOption" to " a , , b , c , "))
            ) shouldBe listOf("a", "b", "c")
        }
    }
})

private fun createFactory(option: PluginOption) =
    object : PluginFactory<Plugin> {
        private val pluginDescriptor = PluginDescriptor(
            id = "test",
            displayName = "Test",
            description = "Test plugin",
            options = listOf(option)
        )

        override val descriptor = pluginDescriptor

        override fun create(config: PluginConfig) =
            object : Plugin {
                override val descriptor = pluginDescriptor
            }
    }
