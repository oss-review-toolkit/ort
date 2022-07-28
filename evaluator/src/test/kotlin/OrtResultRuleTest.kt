/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

package org.ossreviewtoolkit.evaluator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.test.createSpecTempDir

class OrtResultRuleTest : WordSpec({
    "sourceTreeHasFile()" should {
        "return true if at least one file matches the given glob pattern" {
            val dir = createSpecTempDir().apply {
                addFiles(
                    "README.md",
                    "module/docs/LICENSE.txt"
                )
            }
            val rule = createOrtResultRule(dir)

            with(rule) {
                sourceTreeHasFile("README.md").matches() shouldBe true
                sourceTreeHasFile("**/README.md").matches() shouldBe true
                sourceTreeHasFile("**/LICENSE*").matches() shouldBe true
                sourceTreeHasFile("**/*.txt").matches() shouldBe true
            }
        }

        "return false if only a directory matches the given glob pattern" {
            val dir = createSpecTempDir().apply {
                addDirs("README.md")
            }
            val rule = createOrtResultRule(dir)

            rule.sourceTreeHasFile("README.md").matches() shouldBe false
        }

        "return false if neither any file nor directory matches the given glob pattern" {
            val dir = createSpecTempDir()
            val rule = createOrtResultRule(dir)

            rule.sourceTreeHasFile("README.md").matches() shouldBe false
        }
    }
})

private fun createOrtResultRule(projectSourcesDir: File) =
    OrtResultRule(
        ruleSet = ruleSet(
            ortResult = OrtResult.EMPTY,
            projectSourceTree = SourceTree.forLocalDir(projectSourcesDir, VcsType.GIT)
        ),
        name = "RULE_NAME"
    )

private fun File.addFiles(vararg paths: String) {
    require(isDirectory)

    paths.forEach { path ->
        resolve(path).apply {
            parentFile.mkdirs()
            createNewFile()
        }
    }
}

private fun File.addDirs(vararg paths: String) {
    require(isDirectory)

    paths.forEach { path ->
        resolve(path).mkdirs()
    }
}
