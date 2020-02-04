/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer.managers

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class YarnTest : WordSpec() {
    private fun getExpectedResult(projectDir: File, expectedResultTemplateFile: String): String {
        val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
        val vcsUrl = vcsDir.getRemoteUrl()
        val vcsPath = vcsDir.getPathToRoot(projectDir)
        val vcsRevision = vcsDir.getRevision()
        val expectedOutputTemplate = projectDir.parentFile.resolve(expectedResultTemplateFile)

        return patchExpectedResult(
            result = expectedOutputTemplate,
            definitionFilePath = "$vcsPath/package.json",
            url = normalizeVcsUrl(vcsUrl),
            revision = vcsRevision,
            path = vcsPath
        )
    }

    private fun resolveDependencies(projectDir: File): String {
        val packageFile = File(projectDir, "package.json")
        val result = createYarn().resolveDependencies(listOf(packageFile))[packageFile]
        return yamlMapper.writeValueAsString(result)
    }

    init {
        "yarn" should {
            "resolve dependencies correctly" {
                val projectDir = File("src/funTest/assets/projects/synthetic/yarn").absoluteFile

                val result = resolveDependencies(projectDir)

                val expectedResult = getExpectedResult(projectDir, "yarn-expected-output.yml")
                result shouldBe expectedResult
            }

            "resolve workspace dependencies correctly" {
                // This test case illustrates the lack of Yarn workspaces support, in particular not all workspace
                // dependencies get assigned to a scope.
                val projectDir = File("src/funTest/assets/projects/synthetic/yarn-workspaces").absoluteFile

                val result = resolveDependencies(projectDir)

                val expectedResult = getExpectedResult(projectDir, "yarn-workspaces-expected-output.yml")
                result shouldBe expectedResult
            }
        }
    }

    private fun createYarn() =
        Yarn("Yarn", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
