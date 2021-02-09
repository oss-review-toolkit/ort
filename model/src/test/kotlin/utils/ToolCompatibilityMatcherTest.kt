/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.time.LocalDate

import org.ossreviewtoolkit.model.config.ToolCompatibilityConfiguration

class ToolCompatibilityMatcherTest : WordSpec({
    "A tool name" should {
        "match against a null name pattern" {
            val config = ToolCompatibilityConfiguration(
                semanticVersionSpec = "$TOOL_VERSION - 28.10.5"
            )

            val matcher = ToolCompatibilityMatcher(listOf(config))

            matcher.matches(TOOL_NAME, TOOL_VERSION) shouldBe true
        }

        "match against the exact tool name" {
            val config1 = ToolCompatibilityConfiguration(
                namePattern = "foo",
                semanticVersionSpec = "$TOOL_VERSION - 28.10.5"
            )
            val config2 = ToolCompatibilityConfiguration(
                namePattern = TOOL_NAME,
                semanticVersionSpec = ">=3.0.0 <$TOOL_VERSION"
            )

            val matcher = ToolCompatibilityMatcher(listOf(config1, config2))

            matcher.matches(TOOL_NAME, TOOL_VERSION) shouldBe false
        }

        "match against a regular expression pattern" {
            val config1 = ToolCompatibilityConfiguration(
                namePattern = "Scan.+",
                semanticVersionSpec = "$TOOL_VERSION - 28.10.5"
            )
            val config2 = ToolCompatibilityConfiguration(
                namePattern = TOOL_NAME,
                semanticVersionSpec = "3.0.0 - $TOOL_VERSION"
            )

            val matcher = ToolCompatibilityMatcher(listOf(config1, config2))

            matcher.matches(TOOL_NAME, TOOL_VERSION) shouldBe true
        }
    }

    "A semantic version spec" should {
        "accept a tool that is configured as compatible with an exact version" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, semanticVersionSpec = TOOL_VERSION)

            val matcher = ToolCompatibilityMatcher(listOf(config))

            matcher.matches(TOOL_NAME, TOOL_VERSION) shouldBe true
        }

        "not accept a tool whose version is too high" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, semanticVersionSpec = ">=3.0.0 <$TOOL_VERSION")

            val matcher = ToolCompatibilityMatcher(listOf(config))

            matcher.matches(TOOL_NAME, TOOL_VERSION) shouldBe false
        }

        "not accept a tool whose version is too low" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, semanticVersionSpec = "3.2.4 - 4.0.0")

            val matcher = ToolCompatibilityMatcher(listOf(config))

            matcher.matches(TOOL_NAME, TOOL_VERSION) shouldBe false
        }

        "support an undefined upper bound" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, semanticVersionSpec = ">=$TOOL_VERSION")

            val matcher = ToolCompatibilityMatcher(listOf(config))

            matcher.matches(TOOL_NAME, TOOL_VERSION) shouldBe true
            matcher.matches(TOOL_NAME, "3.2.2") shouldBe false
        }

        "support an undefined lower bound" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, semanticVersionSpec = "<3.3.0")

            val matcher = ToolCompatibilityMatcher(listOf(config))

            matcher.matches(TOOL_NAME, TOOL_VERSION) shouldBe true
            matcher.matches(TOOL_NAME, "3.3.0") shouldBe false
        }

        "handle an invalid tool version" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, semanticVersionSpec = ">= 1 <100")

            val matcher = ToolCompatibilityMatcher(listOf(config))

            matcher.matches(TOOL_NAME, "not a semantic version number") shouldBe false
        }
    }

    "A calendar version spec" should {
        "accept a version younger than the threshold" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, calendarVersionSpec = "P1M5D")

            val matcher = ToolCompatibilityMatcher(listOf(config), REFERENCE_DATE)

            matcher.matches(TOOL_NAME, "2021.1.6") shouldBe true
        }

        "not accept a version older than the threshold" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, calendarVersionSpec = "P1M5D")

            val matcher = ToolCompatibilityMatcher(listOf(config), REFERENCE_DATE)

            matcher.matches(TOOL_NAME, "2021.1.5") shouldBe false
        }

        "not accept a version in the future" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, calendarVersionSpec = "P2Y10M17D")

            val matcher = ToolCompatibilityMatcher(listOf(config), REFERENCE_DATE)

            matcher.matches(TOOL_NAME, "2021.2.12") shouldBe false
        }

        "handle an invalid date" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, calendarVersionSpec = "P5Y10M17D")

            val matcher = ToolCompatibilityMatcher(listOf(config), REFERENCE_DATE)

            matcher.matches(TOOL_NAME, "2019.2.29") shouldBe false
        }

        "handle an invalid tool version" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, calendarVersionSpec = "P5Y10M17D")

            val matcher = ToolCompatibilityMatcher(listOf(config), REFERENCE_DATE)

            matcher.matches(TOOL_NAME, "not a calendar version number") shouldBe false
        }
    }

    "A version pattern" should {
        "reject a non-matching version" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, versionPattern = "^Pattern$")

            val matcher = ToolCompatibilityMatcher(listOf(config))

            matcher.matches(TOOL_NAME, "PatternDoesNotMatch") shouldBe false
        }

        "accept a matching version" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME, versionPattern = ".*Summer.*")

            val matcher = ToolCompatibilityMatcher(listOf(config))

            matcher.matches(TOOL_NAME, "Clipper Summer 87") shouldBe true
        }
    }

    "A default configuration creator" should {
        "be applied if no matching configuration for a tool is available" {
            val creator: CompatibilityConfigurationCreator = {
                ToolCompatibilityConfiguration(TOOL_NAME, semanticVersionSpec = "$TOOL_VERSION - 3.2.8")
            }

            val matcher = ToolCompatibilityMatcher(emptyList())

            matcher.matches(TOOL_NAME, TOOL_VERSION, defaultConfigCreator = creator) shouldBe true
            matcher.matches(TOOL_NAME, "3.3", defaultConfigCreator = creator) shouldBe false
        }

        "be available to reject unknown tools" {
            val config = ToolCompatibilityConfiguration(TOOL_NAME)

            val matcher = ToolCompatibilityMatcher(listOf(config))

            matcher.matches("unknownTool", TOOL_VERSION) shouldBe false
        }

        "be available to accept unknown tools" {
            val matcher = ToolCompatibilityMatcher(emptyList())

            matcher.matches(
                TOOL_NAME,
                TOOL_VERSION,
                defaultConfigCreator = ToolCompatibilityMatcher.ACCEPT_UNKNOWN
            ) shouldBe true
        }

        "be applied only if the configuration it returns matches the tool name" {
            val creator: CompatibilityConfigurationCreator = {
                ToolCompatibilityConfiguration(TOOL_NAME + "_other", semanticVersionSpec = ">=1")
            }

            val matcher = ToolCompatibilityMatcher(emptyList())

            matcher.matches(TOOL_NAME, TOOL_VERSION, defaultConfigCreator = creator) shouldBe false
        }
    }
})

private const val TOOL_NAME = "ScanCode"
private const val TOOL_VERSION = "3.2.3"
private val REFERENCE_DATE = LocalDate.of(2021, 2, 11)
