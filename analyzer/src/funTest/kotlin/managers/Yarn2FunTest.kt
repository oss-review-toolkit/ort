/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.toYaml

class Yarn2FunTest : WordSpec({
    "Yarn 2" should {
        "resolve dependencies correctly" {
            val projectDir = getAssetFile("projects/synthetic/yarn2")

            val result = resolveDependencies(projectDir)

            val expectedResult = getExpectedResult(projectDir, "yarn2-expected-output.yml")
            result shouldBe expectedResult
        }

        "exclude scopes if configured" {
            val projectDir = getAssetFile("projects/synthetic/yarn2")

            val analyzerConfig = AnalyzerConfiguration(skipExcluded = true)
            val scopeExclude = ScopeExclude("devDependencies", ScopeExcludeReason.TEST_DEPENDENCY_OF)
            val excludes = Excludes(scopes = listOf(scopeExclude))
            val repositoryConfig = RepositoryConfiguration(excludes = excludes)

            val result = resolveDependencies(projectDir, analyzerConfig, repositoryConfig)

            val expectedResult = getExpectedResult(projectDir, "yarn2-expected-output-scope-excludes.yml")
            result shouldBe expectedResult
        }

        "resolve workspace dependencies correctly" {
            val projectDir = getAssetFile("projects/synthetic/yarn2-workspaces")

            val result = resolveMultipleDependencies(projectDir)

            val expectedResult = getExpectedResult(
                projectDir,
                "yarn2-workspaces-expected-output.yml"
            )
            result shouldBe expectedResult
        }
    }
})

private fun getExpectedResult(projectDir: File, expectedResultTemplateFile: String): String {
    val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    val vcsUrl = vcsDir.getRemoteUrl()
    val vcsPath = vcsDir.getPathToRoot(projectDir)
    val vcsRevision = vcsDir.getRevision()
    val expectedOutputTemplate = projectDir.resolveSibling(expectedResultTemplateFile)

    return patchExpectedResult(
        result = expectedOutputTemplate,
        definitionFilePath = "$vcsPath/package.json",
        url = normalizeVcsUrl(vcsUrl),
        revision = vcsRevision,
        path = vcsPath,
        custom = mapOf("<REPLACE_RAW_URL>" to vcsUrl)
    )
}

private fun resolveDependencies(
    projectDir: File,
    analyzerConfig: AnalyzerConfiguration = AnalyzerConfiguration(),
    repositoryConfig: RepositoryConfiguration = RepositoryConfiguration()
): String {
    val definitionFile = projectDir.resolve("package.json")
    val result = createYarn2(analyzerConfig, repositoryConfig)
        .resolveSingleProject(definitionFile, resolveScopes = true)
    return result.toYaml()
}

private fun resolveMultipleDependencies(projectDir: File): String {
    val definitionFile = projectDir.resolve("package.json")
    val result = createYarn2().collateMultipleProjects(definitionFile)
    // Remove the dependency graph and add scope information.
    return result.withResolvedScopes().toYaml()
}

private fun createYarn2(
    analyzerConfig: AnalyzerConfiguration = AnalyzerConfiguration(),
    repositoryConfig: RepositoryConfiguration = RepositoryConfiguration()
) =
    Yarn2("Yarn2", USER_DIR, analyzerConfig, repositoryConfig)
