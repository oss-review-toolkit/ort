/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.advisor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.advisor.advisors.VulnerableCode
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.VulnerableCodeConfiguration

class AbstractAdviceProviderFactoryTest : WordSpec({
    "forProvider" should {
        "return the configuration for the selected provider" {
            val advisorConfig = AdvisorConfiguration(vulnerableCode = VULNERABLE_CODE_CONFIG)

            val factory = object : AbstractAdviceProviderFactory<AdviceProvider>(PROVIDER_NAME) {
                override fun create(config: AdvisorConfiguration): AdviceProvider {
                    config.forProvider { vulnerableCode } shouldBe VULNERABLE_CODE_CONFIG
                    return VulnerableCode(providerName, VULNERABLE_CODE_CONFIG)
                }
            }

            factory.create(advisorConfig)
        }

        "throw an exception if no configuration for the selected provider is defined" {
            val factory = object : AbstractAdviceProviderFactory<AdviceProvider>(PROVIDER_NAME) {
                override fun create(config: AdvisorConfiguration): AdviceProvider {
                    val exception = shouldThrow<IllegalArgumentException> {
                        config.forProvider { vulnerableCode }
                    }

                    exception.message shouldContain PROVIDER_NAME

                    return VulnerableCode(providerName, VULNERABLE_CODE_CONFIG)
                }
            }

            factory.create(AdvisorConfiguration())
        }
    }

    "providerOptions" should {
        "return the specific options for the selected provider" {
            val providerOptions = mapOf("foo" to "bar")
            val advisorConfig = AdvisorConfiguration(options = mapOf(PROVIDER_NAME to providerOptions))

            val factory = object : AbstractAdviceProviderFactory<AdviceProvider>(PROVIDER_NAME) {
                override fun create(config: AdvisorConfiguration): AdviceProvider {
                    config.providerOptions() shouldBe providerOptions

                    return VulnerableCode(providerName, VULNERABLE_CODE_CONFIG)
                }
            }

            factory.create(advisorConfig)
        }

        "return an empty map if no options for the selected provider are available" {
            val options = mapOf("anotherProvider" to mapOf("someOption" to "someValue"))
            val advisorConfig = AdvisorConfiguration(options = options)

            val factory = object : AbstractAdviceProviderFactory<AdviceProvider>(PROVIDER_NAME) {
                override fun create(config: AdvisorConfiguration): AdviceProvider {
                    config.providerOptions() should beEmpty()

                    return VulnerableCode(providerName, VULNERABLE_CODE_CONFIG)
                }
            }

            factory.create(advisorConfig)
        }

        "return an empty map if no options are defined at all" {
            val factory = object : AbstractAdviceProviderFactory<AdviceProvider>(PROVIDER_NAME) {
                override fun create(config: AdvisorConfiguration): AdviceProvider {
                    config.providerOptions() should beEmpty()

                    return VulnerableCode(providerName, VULNERABLE_CODE_CONFIG)
                }
            }

            factory.create(AdvisorConfiguration())
        }
    }
})

private const val PROVIDER_NAME = "testAdviceProvider"

private val VULNERABLE_CODE_CONFIG = VulnerableCodeConfiguration("https://example.org/vc")
