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

package org.ossreviewtoolkit.plugins.packagemanagers.gradleinspector

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.create
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class GradleKotlinScriptFunTest : StringSpec({
    "root project dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/multi-kotlin-project/build.gradle.kts").toGradle()
        val expectedResultFile = getAssetFile("projects/synthetic/multi-kotlin-project-expected-output-root.yml")

        val result = create("GradleInspector", "javaVersion" to "17")
            .resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "core project dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/multi-kotlin-project/core/build.gradle.kts").toGradle()
        val expectedResultFile = getAssetFile("projects/synthetic/multi-kotlin-project-expected-output-core.yml")

        val result = create("GradleInspector", "javaVersion" to "17")
            .resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "cli project dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/multi-kotlin-project/cli/build.gradle.kts").toGradle()
        val expectedResultFile = getAssetFile("projects/synthetic/multi-kotlin-project-expected-output-cli.yml")

        val result = create("GradleInspector", "javaVersion" to "17")
            .resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }
})
