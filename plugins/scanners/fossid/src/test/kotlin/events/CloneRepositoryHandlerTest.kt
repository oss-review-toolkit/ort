/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid.events

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType

class CloneRepositoryHandlerTest : WordSpec({
    "deduplicateAndNormalizeIgnoreRules" should {
        "deduplicate and normalize ignore rules" {
            val ignoredDirectories = listOf("node_modules", ".git/*", "test/*", ".git").map { pattern ->
                createDirectoryIgnoreRule(pattern)
            }

            val expectedIgnoredDirectories = listOf("node_modules", "test", ".git").map { pattern ->
                createDirectoryIgnoreRule(pattern)
            }

            val ignoredFiles = listOf("gradlew", "gradlew.bat", ".gitignore", "Doxyfile", ".gitignore").map { pattern ->
                createFileIgnoreRule(pattern)
            }

            val expectedIgnoredFiles = listOf("gradlew", "gradlew.bat", ".gitignore", "Doxyfile").map { pattern ->
                createFileIgnoreRule(pattern)
            }

            val deduplicatedAndNormalizedIgnoreRules = deduplicateAndNormalizeIgnoreRules(
                ignoredDirectories + ignoredFiles
            )

            deduplicatedAndNormalizedIgnoreRules shouldContainExactlyInAnyOrder
                expectedIgnoredDirectories + expectedIgnoredFiles
        }
    }
})

private fun createDirectoryIgnoreRule(pattern: String) =
    IgnoreRule(
        id = 0,
        type = RuleType.DIRECTORY,
        value = pattern,
        scanId = 0,
        updated = "2025-01-01 12:34:56"
    )

private fun createFileIgnoreRule(pattern: String) =
    IgnoreRule(
        id = 0,
        type = RuleType.FILE,
        value = pattern,
        scanId = 0,
        updated = "2025-01-01 12:34:56"
    )
