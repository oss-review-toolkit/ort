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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.time.Instant

import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult2
import org.ossreviewtoolkit.utils.test.toYaml

class CocoaPodsFunTest : WordSpec({
    "resolveSingleProject()" should {
        "determine dependencies from a Podfile without a dependency tree" {
            val definitionFile = getAssetFile("projects/synthetic/cocoapods/regular/Podfile")
            val expectedResultFile = getAssetFile("projects/synthetic/cocoapods-regular-expected-output.yml")

            val result = createCocoaPods().resolveSingleProject(definitionFile)

            result.toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }

        "determine dependencies from a Podfile with a dependency tree" {
            val definitionFile = getAssetFile("projects/synthetic/cocoapods/dep-tree/Podfile")
            val expectedResultFile = getAssetFile("projects/synthetic/cocoapods-dep-tree-expected-output.yml")

            val result = createCocoaPods().resolveSingleProject(definitionFile)

            result.toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }

        "return no dependencies along with an issue if the lockfile is absent" {
            val definitionFile = getAssetFile("projects/synthetic/cocoapods/no-lockfile/Podfile")
            val expectedResultFile = getAssetFile("projects/synthetic/cocoapods-no-lockfile-expected-output.yml")

            val result = createCocoaPods().resolveSingleProject(definitionFile)

            result.replaceIssueTimestamps().toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }
    }
})

private fun createCocoaPods(): CocoaPods =
    CocoaPods.Factory().create(
        analysisRoot = USER_DIR,
        analyzerConfig = AnalyzerConfiguration(),
        repoConfig = RepositoryConfiguration()
    )

private fun ProjectAnalyzerResult.replaceIssueTimestamps(): ProjectAnalyzerResult =
    copy(issues = issues.map { it.copy(timestamp = Instant.EPOCH) })
