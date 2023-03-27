/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult2
import org.ossreviewtoolkit.utils.test.toYaml

class ConanFunTest : StringSpec() {
    init {
        "Project dependencies are detected correctly for conanfile.txt" {
            val definitionFile = getAssetFile("projects/synthetic/conan-txt/conanfile.txt")
            val expectedResultFile = getAssetFile("projects/synthetic/conan-expected-output-txt.yml")

            val result = createConanDynamicVersions().resolveSingleProject(definitionFile)

            patchActualResult(result.toYaml()) shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }

        "Project dependencies are detected correctly for conanfile.py" {
            val definitionFile = getAssetFile("projects/synthetic/conan-py/conanfile.py")
            val expectedResultFile = getAssetFile("projects/synthetic/conan-expected-output-py.yml")

            val result = createConanDynamicVersions().resolveSingleProject(definitionFile)

            patchActualResult(result.toYaml()) shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }

        /**
         * This test cannot complete successfully when you run it locally as `package id` differs depending on operating
         * system. The `package id` is set to the one calculated on Linux.
         */
        "Project dependencies are detected correctly with the lockfile".config(enabled = Os.isLinux) {
            val definitionFile = getAssetFile("projects/synthetic/conan-py/conanfile.py")
            val expectedResultFile = getAssetFile("projects/synthetic/conan-expected-output-py.yml")

            val result = createConanWithLockFile().resolveSingleProject(definitionFile)

            patchActualResult(result.toYaml()) shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }
    }

    private fun createConanDynamicVersions() =
        Conan("Conan", USER_DIR, AnalyzerConfiguration(true), RepositoryConfiguration())

    private fun createConanWithLockFile() =
        Conan(
            "Conan",
            USER_DIR,
            AnalyzerConfiguration().copy(
                packageManagers = mapOf(
                    "Conan" to PackageManagerConfiguration(
                        options = mapOf(
                            "lockfileName" to "lockfile.lock"
                        )
                    )
                )
            ),
            RepositoryConfiguration()
        )
}
