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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn2

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.getAnalyzerResult
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class Yarn2FunTest : StringSpec({
    "Resolve dependencies for a project with lockfile correctly" {
        val definitionFile = getAssetFile("projects/synthetic/yarn2/project-with-lockfile/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/yarn2/project-with-lockfile-expected-output.yml")

        val result = Yarn2Factory.create().resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a project with lockfile correctly and skip excluded scopes" {
        val definitionFile = getAssetFile("projects/synthetic/yarn2/project-with-lockfile/package.json")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/yarn2/project-with-lockfile-skip-excluded-scopes-expected-output.yml"
        )

        val result = Yarn2Factory.create()
            .resolveSingleProject(definitionFile, excludedScopes = setOf("devDependencies"), resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve dependencies for a workspaces project correctly" {
        val definitionFile = getAssetFile("projects/synthetic/yarn2/workspaces/package.json")
        val expectedResultFile = getAssetFile("projects/synthetic/yarn2/workspaces-expected-output.yml")

        val result = analyze(definitionFile.parentFile, packageManagers = setOf(Yarn2Factory())).getAnalyzerResult()

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }
})
