/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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
import java.time.Instant

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

private val SYNTHETIC_PROJECTS_DIR = File("src/funTest/assets/projects/synthetic")

class CocoaPodsFunTest : WordSpec({
    "resolveSingleProject()" should {
        "determine dependencies from a Podfile without a dependency tree".config(enabled = !Os.isWindows) {
            val definitionFile = SYNTHETIC_PROJECTS_DIR.resolve("cocoapods/regular/Podfile").absoluteFile
            val expectedResult = getExpectedResult(
                definitionFile = definitionFile,
                expectedResultFile = SYNTHETIC_PROJECTS_DIR.resolve("cocoapods-regular-expected-output.yml")
            )

            val result = createCocoaPods().resolveSingleProject(definitionFile)

            result.toYaml() shouldBe expectedResult
        }

        "determine dependencies from a Podfile with a dependency tree".config(enabled = !Os.isWindows) {
            val definitionFile = SYNTHETIC_PROJECTS_DIR.resolve("cocoapods/dep-tree/Podfile").absoluteFile
            val expectedResult = getExpectedResult(
                definitionFile = definitionFile,
                expectedResultFile = SYNTHETIC_PROJECTS_DIR.resolve("cocoapods-dep-tree-expected-output.yml")
            )

            val result = createCocoaPods().resolveSingleProject(definitionFile)

            result.toYaml() shouldBe expectedResult
        }

        "return no dependencies along with an issue if the lockfile is absent".config(enabled = !Os.isWindows) {
            val definitionFile = SYNTHETIC_PROJECTS_DIR.resolve("cocoapods/no-lockfile/Podfile").absoluteFile
            val expectedResult = getExpectedResult(
                definitionFile = definitionFile,
                expectedResultFile = SYNTHETIC_PROJECTS_DIR.resolve("cocoapods-no-lockfile-expected-output.yml")
            )

            val result = createCocoaPods().resolveSingleProject(definitionFile)

            result.replaceIssueTimestamps().toYaml() shouldBe expectedResult
        }
    }
})

private fun createCocoaPods(): CocoaPods =
    CocoaPods.Factory().create(
        analysisRoot = USER_DIR,
        analyzerConfig = DEFAULT_ANALYZER_CONFIGURATION,
        repoConfig = DEFAULT_REPOSITORY_CONFIGURATION
    )

private fun getExpectedResult(definitionFile: File, expectedResultFile: File): String {
    val projectDir = definitionFile.parentFile
    val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    val vcsRevision = vcsDir.getRevision()
    val vcsUrl = vcsDir.getRemoteUrl()
    val vcsPath = vcsDir.getPathToRoot(projectDir)

    return patchExpectedResult(
        expectedResultFile,
        definitionFilePath = "$vcsPath/${definitionFile.name}",
        path = vcsPath,
        revision = vcsRevision,
        url = normalizeVcsUrl(vcsUrl)
    )
}

private fun ProjectAnalyzerResult.replaceIssueTimestamps(): ProjectAnalyzerResult =
    copy(issues = issues.map { it.copy(timestamp = Instant.EPOCH) })
