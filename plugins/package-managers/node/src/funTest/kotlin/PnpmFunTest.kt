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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.create
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.plugins.packagemanagers.node.pnpm.Pnpm
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.patchActualResult

class PnpmFunTest : WordSpec({
    "Pnpm" should {
        "resolve dependencies for a project with lockfile correctly" {
            val definitionFile = getAssetFile("projects/synthetic/pnpm/project-with-lockfile/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/pnpm/project-with-lockfile-expected-output.yml")

            val result = create("PNPM").resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "resolve dependencies correctly in a workspaces project" {
            val definitionFile = getAssetFile("projects/synthetic/pnpm/workspaces/packages.json")
            val expectedResultFile = getAssetFile("projects/synthetic/pnpm/workspaces-expected-output.yml")

            val result = analyze(definitionFile.parentFile, packageManagers = setOf(Pnpm.Factory()))

            patchActualResult(result.toYaml(), patchStartAndEndTime = true) should
                matchExpectedResult(expectedResultFile, definitionFile)
        }
    }
})
