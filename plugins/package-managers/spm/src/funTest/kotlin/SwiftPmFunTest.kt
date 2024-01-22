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

package org.ossreviewtoolkit.plugins.packagemanagers.swiftpm

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.managers.create
import org.ossreviewtoolkit.analyzer.managers.resolveSingleProject
import org.ossreviewtoolkit.analyzer.managers.withInvariantIssues
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class SwiftPmFunTest : WordSpec({
    "Parsing 'Package.resolved' dependencies" should {
        "return the correct result" {
            val definitionFile = getAssetFile("projects/synthetic/spm-app/Package.resolved")
            val expectedResultFile = getAssetFile("projects/synthetic/spm-expected-output-app.yml")

            val result = create(PROJECT_TYPE).resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Parsing 'Package.swift' dependencies" should {
        "return the correct result" {
            val definitionFile = getAssetFile("projects/synthetic/spm-lib/Package.swift")
            val expectedResultFile = getAssetFile("projects/synthetic/spm-expected-output-lib.yml")

            val result = create(PROJECT_TYPE, AnalyzerConfiguration(allowDynamicVersions = true))
                .resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "show an error if 'allowDynamicVersions' is disabled" {
            val definitionFile = getAssetFile("projects/synthetic/spm-lib/Package.swift")
            val expectedResultFile = getAssetFile("projects/synthetic/spm-expected-output-lib-no-lockfile.yml")

            val result = create(PROJECT_TYPE).resolveSingleProject(definitionFile, resolveScopes = true)

            result.withInvariantIssues().toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }
})
