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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.AndroidTag
import org.ossreviewtoolkit.utils.test.ExpensiveTag
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class GradleAndroidFunTest : StringSpec() {
    private val projectDir = getAssetFile("projects/synthetic/gradle-android").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Root project dependencies are detected correctly".config(tags = setOf(AndroidTag)) {
            val definitionFile = projectDir.resolve("build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.resolveSibling("gradle-android-expected-output-root.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createGradle().resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "Project dependencies are detected correctly".config(tags = setOf(AndroidTag)) {
            val definitionFile = projectDir.resolve("app/build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.resolveSibling("gradle-android-expected-output-app.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createGradle().resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "External dependencies are detected correctly".config(tags = setOf(AndroidTag)) {
            val definitionFile = projectDir.resolve("lib/build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.resolveSibling("gradle-android-expected-output-lib.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createGradle().resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "Cyclic dependencies over multiple libraries can be handled".config(tags = setOf(AndroidTag, ExpensiveTag)) {
            val cyclicProjectDir = getAssetFile("projects/synthetic/gradle-android-cyclic").absoluteFile
            val definitionFile = cyclicProjectDir.resolve("app/build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.resolveSibling("gradle-android-cyclic-expected-output-app.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision,
                definitionFilePath = definitionFile.absolutePath
            )

            val result = createGradle().resolveDependencies(listOf(definitionFile), emptyMap())

            result.toYaml() shouldBe expectedResult
        }
    }

    private fun createGradle() =
        Gradle("Gradle", USER_DIR, AnalyzerConfiguration(), RepositoryConfiguration())
}
