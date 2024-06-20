/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason

class MapIgnoreRulesTest : WordSpec({
    "convertRules" should {
        "map rule with directory with **" {
            val exclude = Excludes(listOf(PathExclude("directory/**", PathExcludeReason.OTHER)))

            val (ignoreRules, issues) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe "directory/**"
                type shouldBe RuleType.DIRECTORY
            }

            issues should beEmpty()
        }

        "map rule with directory containing subdirectories with **" {
            val exclude = Excludes(listOf(PathExclude("directory/sub1/sub2/**", PathExcludeReason.OTHER)))

            val (ignoreRules, issues) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe "directory/sub1/sub2/**"
                type shouldBe RuleType.DIRECTORY
            }

            issues should beEmpty()
        }

        "map rule with directory containing a dot" {
            val exclude = Excludes(listOf(PathExclude(".git/", PathExcludeReason.OTHER)))

            val (ignoreRules, _) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe ".git"
                type shouldBe RuleType.DIRECTORY
            }
        }

        "map rule with directory containing subdirectories with a dot" {
            val exclude = Excludes(listOf(PathExclude("src/example.test/templates/", PathExcludeReason.OTHER)))

            val (ignoreRules, issues) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe "src/example.test/templates"
                type shouldBe RuleType.DIRECTORY
            }

            issues should beEmpty()
        }

        "map rule with directory containing a dash" {
            val exclude = Excludes(listOf(PathExclude("test-prod/", PathExcludeReason.OTHER)))

            val (ignoreRules, _) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe "test-prod"
                type shouldBe RuleType.DIRECTORY
            }
        }

        "map rule with directory containing subdirectories with a dash" {
            val exclude = Excludes(listOf(PathExclude("src/test-prod/templates/", PathExcludeReason.OTHER)))

            val (ignoreRules, issues) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe "src/test-prod/templates"
                type shouldBe RuleType.DIRECTORY
            }

            issues should beEmpty()
        }

        "map rule with directory" {
            val exclude = Excludes(listOf(PathExclude("directory/", PathExcludeReason.OTHER)))

            val (ignoreRules, issues) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe "directory"
                type shouldBe RuleType.DIRECTORY
            }

            issues should beEmpty()
        }

        "map rule with directory containing subdirectories" {
            val exclude = Excludes(listOf(PathExclude("directory/sub1/sub2/", PathExcludeReason.OTHER)))

            val (ignoreRules, issues) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe "directory/sub1/sub2"
                type shouldBe RuleType.DIRECTORY
            }

            issues should beEmpty()
        }

        "map rule with file extensions" {
            val exclude = Excludes(listOf(PathExclude("*.pdf", PathExcludeReason.OTHER)))

            val (ignoreRules, issues) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe ".pdf"
                type shouldBe RuleType.EXTENSION
            }

            issues should beEmpty()
        }

        "map rule with file" {
            val exclude = Excludes(listOf(PathExclude("file.txt", PathExcludeReason.OTHER)))

            val (ignoreRules, issues) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe "file.txt"
                type shouldBe RuleType.FILE
            }

            issues should beEmpty()
        }

        "map rule with files (with '.' in their names)" {
            val exclude = Excludes(listOf(PathExclude("file.old.txt", PathExcludeReason.OTHER)))

            val (ignoreRules, issues) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe "file.old.txt"
                type shouldBe RuleType.FILE
            }

            issues should beEmpty()
        }

        "map rule with files (with '-' in their names)" {
            val exclude = Excludes(listOf(PathExclude("package-lock.json", PathExcludeReason.OTHER)))

            val (ignoreRules, issues) = convertRules(exclude)

            ignoreRules shouldHaveSize 1
            ignoreRules.first() shouldNotBeNull {
                value shouldBe "package-lock.json"
                type shouldBe RuleType.FILE
            }

            issues should beEmpty()
        }

        "add an issue when the pattern cannot be mapped" {
            val exclude = Excludes(listOf(PathExclude("directory/**/test/*", PathExcludeReason.OTHER)))

            val (ignoreRules, issues) = convertRules(exclude)

            ignoreRules should beEmpty()

            issues shouldHaveSize 1
            issues.first() shouldNotBeNull {
                message shouldBe "Path exclude 'directory/**/test/*' cannot be converted to an ignore rule."
                severity shouldBe Severity.HINT
            }
        }
    }
})
