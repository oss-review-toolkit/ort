/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.here.ort.model.Identifier
import com.here.ort.model.yamlMapper
import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.haveSize
import io.kotlintest.should

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.WordSpec

class RepositoryConfigurationTest : WordSpec() {
    init {
        "RepositoryConfiguration" should {
            "be deserializable with empty excludes" {
                val configuration = """
                    excludes:
                    """.trimIndent()

                val repositoryConfiguration = yamlMapper.readValue(configuration, RepositoryConfiguration::class.java)

                repositoryConfiguration shouldNotBe null
                repositoryConfiguration.excludes shouldBe null
            }

            "be deserializable" {
                val configuration = """
                    excludes:
                      projects:
                      - path: "project1/path"
                        type: "OTHER"
                        reason: "OTHER"
                        comment: "project comment"
                      - path: "project2/path"
                        type: "OTHER"
                        packages:
                        - id: "provider:namespace:name:version"
                          reason: "OTHER"
                          comment: "package comment"
                        scopes:
                        - name: "scope"
                          reason: "OTHER"
                          comment: "scope comment"
                        errors:
                        - message: "error message"
                          reason: "OTHER"
                          comment: "error comment"
                      packages:
                      - id: "provider:namespace:name:version"
                        reason: "OTHER"
                        comment: "package comment"
                      scopes:
                      - name: "scope"
                        reason: "OTHER"
                        comment: "scope comment"
                      errors:
                      - message: "error message"
                        reason: "OTHER"
                        comment: "error comment"
                    """.trimIndent()

                val repositoryConfiguration = yamlMapper.readValue(configuration, RepositoryConfiguration::class.java)

                repositoryConfiguration shouldNotBe null
                repositoryConfiguration.excludes shouldNotBe null

                val projects = repositoryConfiguration.excludes!!.projects
                projects should haveSize(2)

                val project1 = projects[0]
                project1.path shouldBe "project1/path"
                project1.type shouldBe ProjectType.OTHER
                project1.reason shouldBe ExcludeReason.OTHER
                project1.comment shouldBe "project comment"
                project1.exclude shouldBe true
                project1.packages should beEmpty()
                project1.scopes should beEmpty()
                project1.errors should beEmpty()

                val project2 = projects[1]
                project2.path shouldBe "project2/path"
                project2.type shouldBe ProjectType.OTHER
                project2.reason shouldBe null
                project2.comment shouldBe null
                project2.exclude shouldBe false

                project2.packages should haveSize(1)
                project2.packages.first().id shouldBe Identifier.fromString("provider:namespace:name:version")
                project2.packages.first().reason shouldBe ExcludeReason.OTHER
                project2.packages.first().comment shouldBe "package comment"

                project2.scopes should haveSize(1)
                project2.scopes.first().name shouldBe "scope"
                project2.scopes.first().reason shouldBe ExcludeReason.OTHER
                project2.scopes.first().comment shouldBe "scope comment"

                project2.errors should haveSize(1)
                project2.errors.first().message shouldBe "error message"
                project2.errors.first().reason shouldBe ExcludeReason.OTHER
                project2.errors.first().comment shouldBe "error comment"

                val scopes = repositoryConfiguration.excludes!!.scopes
                scopes should haveSize(1)
                scopes.first().name shouldBe "scope"
                scopes.first().reason shouldBe ExcludeReason.OTHER
                scopes.first().comment shouldBe "scope comment"

                val errors = repositoryConfiguration.excludes!!.errors
                errors should haveSize(1)
                errors.first().message shouldBe "error message"
                errors.first().reason shouldBe ExcludeReason.OTHER
                errors.first().comment shouldBe "error comment"
            }
        }
    }
}
