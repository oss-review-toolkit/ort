/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.collateMultipleProjects
import org.ossreviewtoolkit.analyzer.withResolvedScopes
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class TychoFunTest : StringSpec({
    "Project dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/tycho/pom.xml")
        val expectedResultFile = getAssetFile("projects/synthetic/tycho-expected-output.yml")

        val result = TychoFactory.create().collateMultipleProjects(definitionFile).withResolvedScopes()

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Scopes can be excluded" {
        val definitionFile = getAssetFile("projects/synthetic/tycho/pom.xml")
        val expectedResultFile = getAssetFile("projects/synthetic/tycho-expected-output-scope-excludes.yml")

        val result = TychoFactory.create()
            .collateMultipleProjects(definitionFile, excludedScopes = setOf("test.*")).withResolvedScopes()

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }
})
