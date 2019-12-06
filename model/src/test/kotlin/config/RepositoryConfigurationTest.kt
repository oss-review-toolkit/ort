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

import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.WordSpec

class RepositoryConfigurationTest : WordSpec() {
    init {
        "RepositoryConfiguration" should {
            "be deserializable with empty excludes and resolutions" {
                val configuration = """
                    excludes:
                    resolutions:
                    """.trimIndent()

                val repositoryConfiguration = yamlMapper.readValue<RepositoryConfiguration>(configuration)

                repositoryConfiguration shouldNotBe null
                repositoryConfiguration.excludes shouldBe null
            }

            "deserialize to a path regex working with double star" {
                val configuration = """
                    excludes:
                      paths:
                      - pattern: "android/**build.gradle"
                        reason: "BUILD_TOOL_OF"
                        comment: "project comment"
                    """.trimIndent()

                val config = yamlMapper.readValue<RepositoryConfiguration>(configuration)
                config.excludes!!.paths[0].matches("android/project1/build.gradle") shouldBe true
            }

            "be deserializable" {
                val configuration = """
                    excludes:
                      paths:
                      - pattern: "project1/path"
                        reason: "BUILD_TOOL_OF"
                        comment: "project comment"
                      scopes:
                      - name: "scope"
                        reason: "TEST_TOOL_OF"
                        comment: "scope comment"
                    resolutions:
                      errors:
                      - message: "message"
                        reason: "CANT_FIX_ISSUE"
                        comment: "error comment"
                      rule_violations:
                      - message: "rule message"
                        reason: "PATENT_GRANT_EXCEPTION"
                        comment: "rule comment"
                    """.trimIndent()

                val repositoryConfiguration = yamlMapper.readValue<RepositoryConfiguration>(configuration)

                repositoryConfiguration shouldNotBe null
                repositoryConfiguration.excludes shouldNotBe null

                val paths = repositoryConfiguration.excludes!!.paths
                paths should haveSize(1)

                val path = paths[0]
                path.pattern shouldBe "project1/path"
                path.reason shouldBe PathExcludeReason.BUILD_TOOL_OF
                path.comment shouldBe "project comment"

                val scopes = repositoryConfiguration.excludes!!.scopes
                scopes should haveSize(1)
                with(scopes.first()) {
                    pattern shouldBe "scope"
                    reason shouldBe ScopeExcludeReason.TEST_TOOL_OF
                    comment shouldBe "scope comment"
                }

                val errors = repositoryConfiguration.resolutions!!.errors
                errors should haveSize(1)
                with(errors.first()) {
                    message shouldBe "message"
                    reason shouldBe ErrorResolutionReason.CANT_FIX_ISSUE
                    comment shouldBe "error comment"
                }

                val evalErrors = repositoryConfiguration.resolutions!!.ruleViolations
                evalErrors should haveSize(1)
                with(evalErrors.first()) {
                    message shouldBe "rule message"
                    reason shouldBe RuleViolationResolutionReason.PATENT_GRANT_EXCEPTION
                    comment shouldBe "rule comment"
                }
            }
        }
    }
}
