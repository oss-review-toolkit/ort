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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.managers.create
import org.ossreviewtoolkit.analyzer.managers.resolveSingleProject
import org.ossreviewtoolkit.analyzer.managers.withInvariantIssues
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.fromYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class NpmVersionUrlFunTest : WordSpec({
    "NPM" should {
        "resolve dependencies with URLs as versions correctly" {
            val definitionFile = getAssetFile("projects/synthetic/npm-version-urls/package.json")
            val expectedResultFile = getAssetFile("projects/synthetic/npm-version-urls-expected-output.yml")
            val expectedResult = patchExpectedResult(expectedResultFile, definitionFile)
                .fromYaml<ProjectAnalyzerResult>()

            val actualResult = create("NPM", allowDynamicVersions = true)
                .resolveSingleProject(definitionFile, resolveScopes = true)

            actualResult.withInvariantIssues() shouldBe expectedResult.withInvariantIssues()
        }
    }
})
