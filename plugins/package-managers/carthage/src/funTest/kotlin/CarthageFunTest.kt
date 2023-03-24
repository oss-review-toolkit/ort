/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.carthage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.managers.resolveSingleProject
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.toYaml

class CarthageFunTest : StringSpec() {
    private val projectDir = getAssetFile("projects/synthetic/carthage")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private val normalizedVcsUrl = normalizeVcsUrl(vcsUrl)
    private val gitHubProject = normalizedVcsUrl.split('/', '.').dropLast(1).takeLast(2).joinToString(":")

    init {
        "Project dependencies are detected correctly" {
            val cartfileResolved = projectDir.resolve("Cartfile.resolved")
            val vcsPath = vcsDir.getPathToRoot(projectDir)
            val expectedResult = patchExpectedResult(
                projectDir.resolveSibling("carthage-expected-output.yml"),
                definitionFilePath = "$vcsPath/Cartfile.resolved",
                path = vcsPath,
                revision = vcsRevision,
                url = normalizedVcsUrl,
                custom = mapOf("<REPLACE_GITHUB_PROJECT>" to gitHubProject)
            )

            val result = createCarthage().resolveSingleProject(cartfileResolved)

            result.toYaml() shouldBe expectedResult
        }
    }

    private fun createCarthage() =
        Carthage("Carthage", USER_DIR, AnalyzerConfiguration(), RepositoryConfiguration())
}
