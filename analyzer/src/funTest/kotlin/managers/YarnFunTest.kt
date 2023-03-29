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

import java.io.File

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult2
import org.ossreviewtoolkit.utils.test.toYaml

class YarnFunTest : WordSpec() {
    private fun resolveDependencies(definitionFile: File): String {
        val result = createYarn().resolveSingleProject(definitionFile, resolveScopes = true)
        return result.toYaml()
    }

    init {
        "yarn" should {
            "resolve dependencies correctly" {
                val definitionFile = getAssetFile("projects/synthetic/yarn/package.json")
                val expectedResultFile = getAssetFile("projects/synthetic/yarn-expected-output.yml")

                val result = resolveDependencies(definitionFile)

                result shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
            }

            "resolve workspace dependencies correctly" {
                // This test case illustrates the lack of Yarn workspaces support, in particular not all workspace
                // dependencies get assigned to a scope.
                val definitionFile = getAssetFile("projects/synthetic/yarn-workspaces/package.json")
                val expectedResultFile = getAssetFile("projects/synthetic/yarn-workspaces-expected-output.yml")

                val result = resolveDependencies(definitionFile)

                result shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
            }
        }
    }

    private fun createYarn() =
        Yarn("Yarn", USER_DIR, AnalyzerConfiguration(), RepositoryConfiguration())
}
