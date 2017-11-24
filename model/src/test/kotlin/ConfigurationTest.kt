/*
 * Copyright (c) 2017 HERE Europe B.V.
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

package com.here.ort.util

import com.fasterxml.jackson.databind.exc.MismatchedInputException

import com.here.ort.model.Configuration

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.StringSpec

class ConfigurationTest : StringSpec() {
    init {
        "Complete configuration can be parsed" {
            val apiToken = "myApiToken"
            val url = "https://my.artifactory.url"

            val configuration = Configuration.parse("""
                    scanner:
                      cache:
                        artifactory:
                          apiToken: "$apiToken"
                          url: "$url"
                    """.trimIndent())

            Configuration.value shouldBe configuration

            configuration.scanner shouldNotBe null
            configuration.scanner?.cache shouldNotBe null
            configuration.scanner?.cache?.artifactory shouldNotBe null
            configuration.scanner?.cache?.artifactory?.apiToken shouldBe apiToken
            configuration.scanner?.cache?.artifactory?.url shouldBe url
        }

        "Does not fail if no scanner cache is configured" {
            val configuration = Configuration.parse("""
                    scanner:
                      cache:
                    """.trimIndent())

            configuration.scanner shouldNotBe null
            configuration.scanner?.cache shouldBe null
        }

        "Does fail if configuration file is empty" {
            shouldThrow<MismatchedInputException> {
                Configuration.parse("")
            }
        }
    }
}
