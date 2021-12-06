/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.yamlMapper

class AdvisorConfigurationTest : WordSpec({
    "NexusIqConfiguration" should {
        "support a serialization round-trip via an ObjectMapper" {
            val originalConfig = loadAdvisorConfig()
            val rereadConfig = rereadAdvisorConfig(originalConfig)

            val expectedNexusIqConfig = originalConfig.nexusIq.shouldNotBeNull()
            val actualNexusIqConfiguration = rereadConfig.nexusIq.shouldNotBeNull()

            actualNexusIqConfiguration.serverUrl shouldBe expectedNexusIqConfig.serverUrl
            actualNexusIqConfiguration.username shouldBe expectedNexusIqConfig.username
        }

        "not serialize credentials" {
            val rereadConfig = rereadAdvisorConfig(loadAdvisorConfig()).nexusIq.shouldNotBeNull()

            rereadConfig.password.shouldBeNull()
        }
    }

    "VulnerableCodeConfiguration" should {
        "support a serialization round-trip via an ObjectMapper" {
            val originalConfig = loadAdvisorConfig()
            val rereadConfig = rereadAdvisorConfig(originalConfig)

            val expectedVCConfig = originalConfig.vulnerableCode.shouldNotBeNull()
            val actualVCConfig = rereadConfig.vulnerableCode.shouldNotBeNull()

            actualVCConfig shouldBe expectedVCConfig
        }
    }

    "GitHubDefectsConfiguration" should {
        "support a serialization round-trip via an ObjectMapper" {
            val originalConfig = loadAdvisorConfig()
            val rereadConfig = rereadAdvisorConfig(originalConfig)

            val expectedGHConfig = originalConfig.gitHubDefects.shouldNotBeNull()
            val actualGHConfig = rereadConfig.gitHubDefects.shouldNotBeNull()

            actualGHConfig.endpointUrl shouldBe expectedGHConfig.endpointUrl
            actualGHConfig.labelFilter shouldBe expectedGHConfig.labelFilter
            actualGHConfig.maxNumberOfIssuesPerRepository shouldBe expectedGHConfig.maxNumberOfIssuesPerRepository
            actualGHConfig.parallelRequests shouldBe expectedGHConfig.parallelRequests
        }

        "not serialize credentials" {
            val rereadConfig = rereadAdvisorConfig(loadAdvisorConfig()).gitHubDefects.shouldNotBeNull()

            rereadConfig.token.shouldBeNull()
        }
    }

    "generic advisor options" should {
        "not be serialized as they might contain sensitive information" {
            rereadAdvisorConfig(loadAdvisorConfig()).options.shouldBeNull()
        }
    }
})

/**
 * Load the ORT reference configuration and extract the advisor configuration.
 */
private fun loadAdvisorConfig(): AdvisorConfiguration =
    OrtConfiguration.load(file = File("src/main/resources/reference.conf")).advisor

/**
 * Perform a serialization round-trip of the given advisor [config] and return the result. This is used to check
 * whether serialization and deserialization of advisor configurations work as expected.
 */
private fun rereadAdvisorConfig(config: AdvisorConfiguration): AdvisorConfiguration {
    val yaml = yamlMapper.writeValueAsString(config)

    return yamlMapper.readValue(yaml)
}
