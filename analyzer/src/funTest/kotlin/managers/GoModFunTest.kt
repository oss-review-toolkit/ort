/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class GoModFunTest : StringSpec() {
    private val testDir = File("src/funTest/assets/projects/synthetic").absoluteFile

    init {
        "Project dependencies are detected correctly" {
            val projectDir = testDir.resolve("gomod")
            val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
            val vcsUrl = vcsDir.getRemoteUrl()
            val vcsRevision = vcsDir.getRevision()
            val definitionFile = projectDir.resolve("go.mod")
            val vcsPath = vcsDir.getPathToRoot(projectDir)
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("gomod-expected-output.yml"),
                definitionFilePath = "$vcsPath/go.mod",
                path = vcsPath,
                revision = vcsRevision,
                url = normalizeVcsUrl(vcsUrl)
            )

            val result = createGoMod().resolveSingleProject(definitionFile)

            result.toYaml() shouldBe expectedResult
        }

        "Project dependencies are detected correctly if the main package does not contain any code" {
            val projectDir = testDir.resolve("gomod-subpkg")
            val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
            val vcsUrl = vcsDir.getRemoteUrl()
            val vcsRevision = vcsDir.getRevision()
            val definitionFile = projectDir.resolve("go.mod")
            val vcsPath = vcsDir.getPathToRoot(projectDir)
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("gomod-subpkg-expected-output.yml"),
                definitionFilePath = "$vcsPath/go.mod",
                path = vcsPath,
                revision = vcsRevision,
                url = normalizeVcsUrl(vcsUrl)
            )

            val result = createGoMod().resolveSingleProject(definitionFile)

            result.toYaml() shouldBe expectedResult
        }
    }

    private fun createGoMod() =
        GoMod("GoMod", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
