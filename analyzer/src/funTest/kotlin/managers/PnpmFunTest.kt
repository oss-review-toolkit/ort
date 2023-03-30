/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult2
import org.ossreviewtoolkit.utils.test.toYaml

class PnpmFunTest : WordSpec({
    "Pnpm" should {
        "resolve dependencies correctly in a simple project" {
            val definitionFile = getAssetFile("projects/synthetic/pnpm/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/pnpm-expected-output.yml")

            val result = createPnpm().resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }

        "resolve dependencies correctly in a workspaces project" {
            val definitionFile = getAssetFile("projects/synthetic/pnpm-workspaces/packages.json")
            val expectedResultFile = getAssetFile("projects/synthetic/pnpm-workspaces-expected-output.yml")
            val expectedResult = patchExpectedResult2(expectedResultFile, definitionFile)

            val ortResult = analyze(definitionFile.parentFile, packageManagers = setOf(Pnpm.Factory()))

            patchActualResult(ortResult, patchStartAndEndTime = true) shouldBe expectedResult
        }
    }
})

private fun createPnpm() = Pnpm("PNPM", USER_DIR, AnalyzerConfiguration(), RepositoryConfiguration())
