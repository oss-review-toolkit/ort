/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class GradleLibraryFunTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/gradle-library").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Root project dependencies are detected correctly" {
            val packageFile = projectDir.resolve("build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("gradle-library-expected-output-root.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createGradle().resolveSingleProject(packageFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "Project dependencies are detected correctly" {
            val packageFile = projectDir.resolve("app/build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("gradle-library-expected-output-app.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createGradle().resolveSingleProject(packageFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val packageFile = projectDir.resolve("lib/build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("gradle-library-expected-output-lib.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createGradle().resolveSingleProject(packageFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }
    }

    private fun createGradle() =
        Gradle("Gradle", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
