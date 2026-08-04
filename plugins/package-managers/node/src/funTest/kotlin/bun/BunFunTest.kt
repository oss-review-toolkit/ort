/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.bun

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.patchActualResult

@Tags("RequiresExternalTool")
class BunFunTest : StringSpec({
    "Resolve dependencies for a project with lockfile correctly" {
        val definitionFile = getAssetFile("projects/synthetic/bun/project-with-lockfile/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/bun/project-with-lockfile-expected-output.yml")

        val result = BunFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Exclude scopes if configured" {
        val definitionFile = getAssetFile("projects/synthetic/bun/project-with-lockfile/package.json")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/bun/project-with-lockfile-skip-excluded-scopes-expected-output.yml"
        )

        val result = BunFactory.create()
            .resolveSingleProject(definitionFile, excludedScopes = setOf("devDependencies"), resolveScopes = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a workspaces project correctly" {
        val definitionFile = getAssetFile("projects/synthetic/bun/workspaces/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/bun/workspaces-expected-output.yml")

        val result = BunFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(
            expectedResultFile,
            definitionFile,
            custom = mapOf(
                "<REPLACE_PROJECT_DIR>" to definitionFile.parentFile.invariantSeparatorsPath
            )
        )
    }

    "Resolve dependencies for a project with a legacy binary lockfile correctly" {
        val definitionFile = getAssetFile("projects/synthetic/bun/binary-lockfile/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/bun/binary-lockfile-expected-output.yml")

        val result = BunFactory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Show an error if no lockfile is present" {
        val workingDir = tempdir()
        val definitionFile = workingDir.resolve("package.json").apply {
            writeText(
                """
                {
                  "name": "bun-no-lockfile",
                  "version": "1.0.0",
                  "dependencies": {
                    "long": "3.2.0"
                  }
                }
                """.trimIndent()
            )
        }

        val result = BunFactory.create().resolveSingleProject(definitionFile)

        result.issues.forAtLeastOne {
            it.source shouldBe "Bun"
            it.severity shouldBe Severity.ERROR
            it.message shouldContain "No lockfile found"
        }
    }
})
