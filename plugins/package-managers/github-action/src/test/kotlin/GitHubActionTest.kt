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

package org.ossreviewtoolkit.plugins.packagemanagers.githubaction

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKeys
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify

import java.io.IOException

import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.utils.common.div

class GitHubActionTest : WordSpec({
    "resolveDependencies()" should {
        "only fail for composite actions that use remote actions if abom fails" {
            val projectDir = tempdir()
            val remote = (projectDir / "remote" / "action.yml").apply {
                parentFile.mkdirs()
                writeText(compositeAction("actions/checkout@v6"))
            }

            val otherRemote = (projectDir / "other-remote" / "action.yml").apply {
                parentFile.mkdirs()
                writeText(compositeAction("actions/cache@v5"))
            }

            val local = (projectDir / "local" / "action.yml").apply {
                parentFile.mkdirs()
                writeText(compositeAction("./other"))
            }

            val docker = (projectDir / "docker" / "action.yml").apply {
                parentFile.mkdirs()
                writeText("name: Docker\nruns:\n  using: docker\n  image: Dockerfile\n")
            }

            val definitionFiles = listOf(remote, otherRemote, local, docker)
            val analyzerConfig = AnalyzerConfiguration()

            mockkObject(ABomCommand)
            try {
                every {
                    ABomCommand.run(*anyVararg(), workingDir = any(), environment = any())
                } throws IOException("abom failed")

                val gitHubAction = GitHubActionFactory.create()
                gitHubAction.beforeResolution(projectDir, definitionFiles, analyzerConfig)
                val results = gitHubAction.resolveDependencies(
                    projectDir,
                    definitionFiles,
                    Excludes.EMPTY,
                    Includes.EMPTY,
                    analyzerConfig,
                    emptyMap()
                ).projectResults

                results.shouldContainKeys(*definitionFiles.toTypedArray())

                listOf(remote, otherRemote).forEach { file ->
                    results.getValue(file).single().issues.map { it.severity } shouldBe listOf(Severity.ERROR)
                }

                with(results.getValue(local).single()) {
                    project.id.name shouldBe "Composite"
                    issues shouldHaveSize 1
                    issues.single().severity shouldBe Severity.WARNING
                }

                with(results.getValue(docker).single()) {
                    project.id.name shouldBe "Docker"
                    issues should beEmpty()
                }

                verify(exactly = 1) { ABomCommand.run(*anyVararg(), workingDir = any(), environment = any()) }
            } finally {
                unmockkObject(ABomCommand)
            }
        }
    }
})

private fun compositeAction(uses: String) =
    """
    name: Composite
    runs:
      using: composite
      steps:
        - uses: $uses
    """.trimIndent()
