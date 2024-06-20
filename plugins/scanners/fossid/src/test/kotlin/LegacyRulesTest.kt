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

import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType
import org.ossreviewtoolkit.model.Severity

class LegacyRulesTest : WordSpec({
    "compareRules" should {
        "create an issue when a legacy rule is present" {
            val referenceRules = listOf(
                IgnoreRule(-1, RuleType.DIRECTORY, "directory", -1, ""),
                IgnoreRule(-1, RuleType.FILE, "file", -1, "")
            )
            val rulesToTest = listOf(
                IgnoreRule(-1, RuleType.EXTENSION, ".pdf", -1, "")
            )

            val (legacyRules, issues) = rulesToTest.filterLegacyRules(referenceRules)

            issues shouldHaveSize 1
            issues.first() shouldNotBeNull {
                message shouldBe "Rule '.pdf' with type '${RuleType.EXTENSION}' is not present in the .ort.yml path" +
                    " excludes. Add it to the .ort.yml file or remove it from the FossID scan."
                severity shouldBe Severity.HINT
            }
            legacyRules shouldHaveSize 1
            legacyRules shouldBe rulesToTest
        }

        "not create an issue when no legacy rule is present" {
            val referenceRules = listOf(
                IgnoreRule(-1, RuleType.DIRECTORY, "directory", -1, ""),
                IgnoreRule(-1, RuleType.FILE, "file", -1, "")
            )
            val rulesToTest = listOf(
                IgnoreRule(-1, RuleType.DIRECTORY, "directory", -1, "")
            )

            val (legacyRules, issues) = rulesToTest.filterLegacyRules(referenceRules)

            issues should beEmpty()
            legacyRules should beEmpty()
        }
    }
})
