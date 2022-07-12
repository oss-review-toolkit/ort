/*
 * Copyright (C) 2022 Bosch.IO GmbH
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
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class Yarn2FunTest : WordSpec({
    "Yarn 2" should {
        "resolve dependencies correctly" {
            val projectDir = File("src/funTest/assets/projects/synthetic/yarn2").absoluteFile

            val result = resolveDependencies(projectDir)

            val expectedResult = getExpectedResult(projectDir, "yarn2-expected-output.yml")
            result shouldBe expectedResult
        }

        "resolve workspace dependencies correctly" {
            val projectDir = File("src/funTest/assets/projects/synthetic/yarn2-workspaces").absoluteFile

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
    val expectedOutputTemplate = projectDir.parentFile.resolve(expectedResultTemplateFile)

    return patchExpectedResult(
        result = expectedOutputTemplate,
        definitionFilePath = "$vcsPath/package.json",
        url = normalizeVcsUrl(vcsUrl),
        revision = vcsRevision,
        path = vcsPath,
        custom = mapOf("<REPLACE_RAW_URL>" to vcsUrl)
    )
}

private fun resolveDependencies(projectDir: File): String {
    val packageFile = projectDir.resolve("package.json")
    val result = createYarn2().resolveSingleProject(packageFile, resolveScopes = true)
    return result.toYaml()
}

private fun resolveMultipleDependencies(projectDir: File): String {
    val packageFile = projectDir.resolve("package.json")
    val result = createYarn2().collateMultipleProjects(packageFile)
    // Remove the dependency graph and add scope information.
    return result.withResolvedScopes().toYaml()
}

private fun createYarn2() =
    Yarn2("Yarn2", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
