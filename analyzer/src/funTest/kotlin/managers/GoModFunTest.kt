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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.toYaml

class GoModFunTest : StringSpec({
    val testDir = getAssetFile("projects/synthetic")

    "Project dependencies are detected correctly" {
        val definitionFile = testDir.resolve("gomod/go.mod")
        val expectedResultFile = testDir.resolve("gomod-expected-output.yml")

        val result = createGoMod().resolveSingleProject(definitionFile)

        result.toYaml() shouldBe patchExpectedResult(definitionFile, expectedResultFile)
    }

    "Project dependencies are detected correctly if the main package does not contain any code" {
        val definitionFile = testDir.resolve("gomod-subpkg/go.mod")
        val expectedResultFile = testDir.resolve("gomod-subpkg-expected-output.yml")

        val result = createGoMod().resolveSingleProject(definitionFile)

        result.toYaml() shouldBe patchExpectedResult(definitionFile, expectedResultFile)
    }

    "Project dependencies are detected correctly if there are no dependencies" {
        val definitionFile = testDir.resolve("gomod-no-deps/go.mod")
        val expectedResultFile = testDir.resolve("gomod-no-deps-expected-output.yml")

        val result = createGoMod().resolveSingleProject(definitionFile)

        result.toYaml() shouldBe patchExpectedResult(definitionFile, expectedResultFile)
    }

    "Unused dependencies are not contained in the result" {
        val definitionFile = testDir.resolve("gomod-unused-deps/go.mod")
        val expectedResultFile = testDir.resolve("gomod-unused-deps-expected-output.yml")

        val result = createGoMod().resolveSingleProject(definitionFile)

        result.toYaml() shouldBe patchExpectedResult(definitionFile, expectedResultFile)
    }
})

private fun createGoMod() = GoMod("GoMod", USER_DIR, AnalyzerConfiguration(), RepositoryConfiguration())

private fun patchExpectedResult(
    definitionFile: File,
    expectedResultFile: File
): String {
    val projectDir = definitionFile.parentFile
    val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    val vcsUrl = vcsDir.getRemoteUrl()
    val vcsRevision = vcsDir.getRevision()
    val vcsPath = vcsDir.getPathToRoot(projectDir)

    return patchExpectedResult(
        expectedResultFile,
        definitionFilePath = "$vcsPath/go.mod",
        path = vcsPath,
        revision = vcsRevision,
        url = normalizeVcsUrl(vcsUrl)
    )
}
