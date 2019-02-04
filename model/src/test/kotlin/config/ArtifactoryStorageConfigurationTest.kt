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

class ArtifactoryStorageConfigurationTest : WordSpec() {
    init {
        "ArtifactoryStorageConfiguration" should {
            "be deserializable" {
                val yaml = """
                    ---
                    url: "url"
                    repository: "repository"
                    api_token: "apiToken"
                    rewrite_artifactory_cache: false""".trimIndent()

                val artifactoryStorageConfiguration = yamlMapper.readValue<ArtifactoryStorageConfiguration>(yaml)

                artifactoryStorageConfiguration.url shouldBe "url"
                artifactoryStorageConfiguration.repository shouldBe "repository"
                artifactoryStorageConfiguration.apiToken shouldBe "apiToken"
                artifactoryCacheConfiguration.rewriteArtifactoryCache shouldBe false

            }

            "not serialize the ApiToken" {
                val artifactoryStorageConfiguration = ArtifactoryStorageConfiguration("url", "repository", "apiToken")

                val yaml = yamlMapper.writeValueAsString(artifactoryStorageConfiguration).trim()

                yaml shouldBe """
                    ---
                    url: "url"
                    repository: "repository"
                    rewrite_artifactory_cache: false
                    """.trimIndent()
            }

            "be deserializable without apiToken" {
                val yaml = """
                    ---
                    url: "url"
                    repository: "repository"
                    """.trimIndent()

                val artifactoryStorageConfiguration = yamlMapper.readValue<ArtifactoryStorageConfiguration>(yaml)

                artifactoryStorageConfiguration.url shouldBe "url"
                artifactoryStorageConfiguration.repository shouldBe "repository"
                artifactoryStorageConfiguration.apiToken shouldBe ""
            }
        }
    }
}
