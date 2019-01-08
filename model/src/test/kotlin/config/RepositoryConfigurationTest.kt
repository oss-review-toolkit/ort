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

import io.kotlintest.matchers.beEmpty
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

            "deserialize to a path regex working with escaped dots" {
                val configuration = """
                    excludes:
                      projects:
                      - path: "android/.*build\\.gradle"
                        reason: "BUILD_TOOL_OF"
                        comment: "project comment"
                    """.trimIndent()

                val config = yamlMapper.readValue<RepositoryConfiguration>(configuration)
                config.excludes!!.projects[0].path.matches("android/project1/build.gradle") shouldBe true
            }

            "be deserializable" {
                val configuration = """
                    excludes:
                      projects:
                      - path: "project1/path"
                        reason: "BUILD_TOOL_OF"
                        comment: "project comment"
                      - path: "project2/path"
                        scopes:
                        - name: "scope"
                          reason: "PROVIDED_BY"
                          comment: "scope comment"
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
                        reason: "APPROVED"
                        comment: "rule comment"
                    """.trimIndent()

                val repositoryConfiguration = yamlMapper.readValue<RepositoryConfiguration>(configuration)

                repositoryConfiguration shouldNotBe null
                repositoryConfiguration.excludes shouldNotBe null

                val projects = repositoryConfiguration.excludes!!.projects
                projects should haveSize(2)

                val project1 = projects[0]
                project1.path.pattern shouldBe "project1/path"
                project1.reason shouldBe ProjectExcludeReason.BUILD_TOOL_OF
                project1.comment shouldBe "project comment"
                project1.isWholeProjectExcluded shouldBe true
                project1.scopes should beEmpty()

                val project2 = projects[1]
                project2.path.pattern shouldBe "project2/path"
                project2.reason shouldBe null
                project2.comment shouldBe null
                project2.isWholeProjectExcluded shouldBe false

                project2.scopes should haveSize(1)
                project2.scopes.first().name.pattern shouldBe "scope"
                project2.scopes.first().reason shouldBe ScopeExcludeReason.PROVIDED_BY
                project2.scopes.first().comment shouldBe "scope comment"

                val scopes = repositoryConfiguration.excludes!!.scopes
                scopes should haveSize(1)
                scopes.first().name.pattern shouldBe "scope"
                scopes.first().reason shouldBe ScopeExcludeReason.TEST_TOOL_OF
                scopes.first().comment shouldBe "scope comment"

                val errors = repositoryConfiguration.resolutions!!.errors
                errors should haveSize(1)
                val error = errors.first()
                error.message shouldBe "message"
                error.reason shouldBe ErrorResolutionReason.CANT_FIX_ISSUE
                error.comment shouldBe "error comment"

                val evalErrors = repositoryConfiguration.resolutions!!.ruleViolations
                evalErrors should haveSize(1)
                val evalError = evalErrors.first()
                evalError.message shouldBe "rule message"
                evalError.reason shouldBe RuleViolationResolutionReason.APPROVED
                evalError.comment shouldBe "rule comment"
            }
        }
    }
}
