/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.time.Instant

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class NpmVersionUrlFunTest : WordSpec({
    val projectDir = getAssetFile("projects/synthetic/npm-version-urls")
    val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    val vcsUrl = vcsDir.getRemoteUrl()
    val vcsRevision = vcsDir.getRevision()

    "NPM" should {
        "resolve dependencies with URLs as versions correctly" {
            val definitionFile = projectDir.resolve("package.json")
            val config = AnalyzerConfiguration(allowDynamicVersions = true)
            val vcsPath = vcsDir.getPathToRoot(projectDir)
            val expectedResultYaml = patchExpectedResult(
                projectDir.resolveSibling("npm-version-urls-expected-output.yml"),
                definitionFilePath = "$vcsPath/package.json",
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision,
                path = vcsPath
            )
            val expectedResult = yamlMapper.readValue<ProjectAnalyzerResult>(expectedResultYaml)

            val actualResult = createNpm(config).resolveSingleProject(definitionFile, resolveScopes = true)

            actualResult.withInvariantIssues() shouldBe expectedResult.withInvariantIssues()
        }
    }
})

private fun createNpm(config: AnalyzerConfiguration) = Npm("NPM", USER_DIR, config, RepositoryConfiguration())

private fun ProjectAnalyzerResult.withInvariantIssues() =
    // Account for different NPM versions to return issues in different order.
    copy(issues = issues.sortedBy { it.message }.map { it.copy(timestamp = Instant.EPOCH) })
