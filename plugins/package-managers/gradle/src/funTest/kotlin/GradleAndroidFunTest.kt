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

package org.ossreviewtoolkit.plugins.packagemanagers.gradle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.create
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class GradleAndroidFunTest : StringSpec({
    "Root project dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/gradle-android/build.gradle")
        val expectedResultFile = getAssetFile("projects/synthetic/gradle-android-expected-output-root.yml")

        val result = create("Gradle", "javaVersion" to "17")
            .resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/gradle-android/app/build.gradle")
        val expectedResultFile = getAssetFile("projects/synthetic/gradle-android-expected-output-app.yml")

        val result = create("Gradle", "javaVersion" to "17")
            .resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "External dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/gradle-android/lib/build.gradle")
        val expectedResultFile = getAssetFile("projects/synthetic/gradle-android-expected-output-lib.yml")

        val result = create("Gradle", "javaVersion" to "17")
            .resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Cyclic dependencies over multiple libraries can be handled" {
        val definitionFile = getAssetFile("projects/synthetic/gradle-android-cyclic/app/build.gradle")
        val expectedResultFile = getAssetFile("projects/synthetic/gradle-android-cyclic-expected-output-app.yml")

        val result = create("Gradle", "javaVersion" to "17")
            .resolveDependencies(listOf(definitionFile), emptyMap())

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }
})
