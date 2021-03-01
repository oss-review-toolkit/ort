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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.spdx.toSpdx

class RepositoryConfigurationTest : WordSpec({
    "RepositoryConfiguration" should {
        "deserialize to a path regex working with double star" {
            val configuration = """
                excludes:
                  paths:
                  - pattern: "android/**build.gradle"
                    reason: "BUILD_TOOL_OF"
                    comment: "project comment"
                """.trimIndent()

            val config = yamlMapper.readValue<RepositoryConfiguration>(configuration)
            config.excludes.paths[0].matches("android/project1/build.gradle") shouldBe true
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
                    reason: "TEST_DEPENDENCY_OF"
                    comment: "scope comment"
                resolutions:
                  issues:
                  - message: "message"
                    reason: "CANT_FIX_ISSUE"
                    comment: "issue comment"
                  rule_violations:
                  - message: "rule message"
                    reason: "PATENT_GRANT_EXCEPTION"
                    comment: "rule comment"
                  vulnerabilities:
                  - id: "vulnerability id"
                    reason: "INEFFECTIVE_VULNERABILITY"
                    comment: "vulnerability comment"
                license_choices:
                  package_license_choices:
                  - package_id: "Maven:com.example:lib:0.0.1"
                    license_choices:
                    - given: MPL-2.0 or EPL-1.0
                      choice: MPL-2.0
                    - choice: MPL-2.0 AND MIT
                """.trimIndent()

            val repositoryConfiguration = yamlMapper.readValue<RepositoryConfiguration>(configuration)

            val paths = repositoryConfiguration.excludes.paths
            paths should haveSize(1)

            val path = paths[0]
            path.pattern shouldBe "project1/path"
            path.reason shouldBe PathExcludeReason.BUILD_TOOL_OF
            path.comment shouldBe "project comment"

            val scopes = repositoryConfiguration.excludes.scopes
            scopes should haveSize(1)
            with(scopes.first()) {
                pattern shouldBe "scope"
                reason shouldBe ScopeExcludeReason.TEST_DEPENDENCY_OF
                comment shouldBe "scope comment"
            }

            val issues = repositoryConfiguration.resolutions.issues
            issues should haveSize(1)
            with(issues.first()) {
                message shouldBe "message"
                reason shouldBe IssueResolutionReason.CANT_FIX_ISSUE
                comment shouldBe "issue comment"
            }

            val ruleViolations = repositoryConfiguration.resolutions.ruleViolations
            ruleViolations should haveSize(1)
            with(ruleViolations.first()) {
                message shouldBe "rule message"
                reason shouldBe RuleViolationResolutionReason.PATENT_GRANT_EXCEPTION
                comment shouldBe "rule comment"
            }

            val vulnerabilities = repositoryConfiguration.resolutions.vulnerabilities
            vulnerabilities should haveSize(1)
            with(vulnerabilities.first()) {
                id shouldBe "vulnerability id"
                reason shouldBe VulnerabilityResolutionReason.INEFFECTIVE_VULNERABILITY
                comment shouldBe "vulnerability comment"
            }

            val packageLicenseChoices = repositoryConfiguration.licenseChoices.packageLicenseChoices
            packageLicenseChoices should haveSize(1)
            with(packageLicenseChoices.first()) {
                packageId shouldBe Identifier("Maven:com.example:lib:0.0.1")
                with(licenseChoices.first()) {
                    given shouldBe "MPL-2.0 or EPL-1.0".toSpdx()
                    choice shouldBe "MPL-2.0".toSpdx()
                }

                with(licenseChoices[1]) {
                    given shouldBe null
                    choice shouldBe "MPL-2.0 AND MIT".toSpdx()
                }
            }
        }
    }
})
