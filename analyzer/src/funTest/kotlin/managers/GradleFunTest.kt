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

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.vcs.Git
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.ExpensiveTag
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class GradleFunTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/gradle").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private val isJava9OrAbove = System.getProperty("java.version").split('.').first().toInt() >= 9

    override suspend fun afterSpec(spec: Spec) {
        // Reset the Gradle wrapper files to the committed state.
        Git().run(projectDir, "checkout", "gradle/", "gradlew*")
    }

    init {
        "Root project dependencies are detected correctly" {
            val packageFile = projectDir.resolve("build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("gradle-expected-output-root.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createGradle().resolveSingleProject(packageFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "Project dependencies are detected correctly" {
            val packageFile = projectDir.resolve("app/build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("gradle-expected-output-app.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createGradle().resolveSingleProject(packageFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val packageFile = projectDir.resolve("lib/build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("gradle-expected-output-lib.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createGradle().resolveSingleProject(packageFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "Unresolved dependencies are detected correctly" {
            val packageFile = projectDir.resolve("lib-without-repo/build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("gradle-expected-output-lib-without-repo.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createGradle().resolveSingleProject(packageFile, resolveScopes = true)

            patchActualResult(result.toYaml()) shouldBe expectedResult
        }

        // Disabled because despite following the example at [1] Gradle says there is "No service of type
        // ToolingModelBuilderRegistry available in GradleScopeServices".
        //
        // [1] https://github.com/gradle/gradle/blob/REL_2.13/subprojects/docs/src/samples/toolingApi/customModel/plugin/src/main/java/org/gradle/sample/plugin/CustomPlugin.java
        "Fails nicely for Gradle version < 2.14".config(enabled = false) {
            val packageFile = projectDir.parentFile.resolve("gradle-unsupported-version/build.gradle")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("gradle-expected-output-unsupported-version.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createGradle().resolveSingleProject(packageFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        // Disabled as it causes hangs and memory issues on CI.
        "Is compatible with Gradle >= 2.14".config(tags = setOf(ExpensiveTag), enabled = false) {
            // See https://blog.gradle.org/java-9-support-update.
            val gradleVersionsThatSupportJava9 = arrayOf(
                row("4.6", ""),
                row("4.5.1", "-3.4"),
                row("4.5", "-3.4"),
                row("4.4.1", "-3.4"),
                row("4.4", "-3.4"),
                row("4.3.1", "-3.4"),
                row("4.3", "-3.4"),
                row("4.2.1", "-3.4")
            )

            val gradleVersionsThatDoNotSupportJava9 = arrayOf(
                row("4.2", "-3.4"),
                row("4.1", "-3.4"),
                row("4.0.2", "-3.4"),
                row("4.0.1", "-3.4"),
                row("4.0", "-3.4"),
                row("3.5.1", "-3.4"),
                row("3.5", "-3.4"),
                row("3.4.1", "-3.4"),
                row("3.4", "-3.4"),
                row("3.3", "-2.14"),
                row("3.2.1", "-2.14"),
                row("3.2", "-2.14"),
                row("3.1", "-2.14"),
                row("3.0", "-2.14"),
                row("2.14.1", "-2.14"),
                row("2.14", "-2.14")
            )

            val gradleVersions = if (isJava9OrAbove) {
                gradleVersionsThatSupportJava9
            } else {
                gradleVersionsThatSupportJava9 + gradleVersionsThatDoNotSupportJava9
            }

            val gradleVersionTable = table(headers("version", "resultsFileSuffix"), *gradleVersions)

            forAll(gradleVersionTable) { version, resultsFileSuffix ->
                installGradleWrapper(version)

                val packageFile = projectDir.resolve("app/build.gradle")
                val expectedResult = patchExpectedResult(
                    projectDir.parentFile.resolve("gradle-expected-output-app$resultsFileSuffix.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
                )

                val result = createGradle().resolveSingleProject(packageFile, resolveScopes = true)

                result.toYaml() shouldBe expectedResult
            }
        }
    }

    private fun installGradleWrapper(version: String) {
        println("Installing Gradle wrapper version $version.")

        val (gradle, wrapper) = if (Os.isWindows) {
            Pair("gradle.bat", projectDir.resolve("gradlew.bat"))
        } else {
            Pair("gradle", projectDir.resolve("gradlew"))
        }

        val command = if (wrapper.isFile) wrapper.absolutePath else gradle

        // When calling Windows batch files directly (without passing them to "cmd" as an argument), Windows requires
        // the absolute path to the batch file to be passed to the underlying ProcessBuilder for some reason.
        ProcessCapture(projectDir, command, "--no-daemon", "wrapper", "--gradle-version", version)
            .requireSuccess()
    }

    private fun createGradle() =
        Gradle("Gradle", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
