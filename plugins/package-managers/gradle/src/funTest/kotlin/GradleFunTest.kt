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

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.plugins.versioncontrolsystems.git.GitCommand
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.patchActualResult

class GradleFunTest : StringSpec() {
    private val projectDir = getAssetFile("projects/synthetic/gradle")
    private val isJava9OrAbove = Environment.JAVA_VERSION.split('.').first().toInt() >= 9

    private fun installGradleWrapper(version: String) {
        val (gradle, wrapper) = if (Os.isWindows) {
            Pair("gradle.bat", projectDir / "gradlew.bat")
        } else {
            Pair("gradle", projectDir / "gradlew")
        }

        val command = if (wrapper.isFile) wrapper.absolutePath else gradle

        // When calling Windows batch files directly (without passing them to "cmd" as an argument), Windows requires
        // the absolute path to the batch file to be passed to the underlying ProcessBuilder for some reason.
        ProcessCapture(projectDir, command, "--no-daemon", "wrapper", "--gradle-version", version)
            .requireSuccess()
    }

    override suspend fun afterSpec(spec: Spec) {
        // Reset the Gradle wrapper files to the committed state.
        GitCommand.run(projectDir, "checkout", "gradle/", "gradlew*").requireSuccess()
    }

    init {
        "Root project dependencies are detected correctly" {
            val definitionFile = getAssetFile("projects/synthetic/gradle/build.gradle")
            val expectedResultFile = getAssetFile("projects/synthetic/gradle-expected-output-root.yml")

            val result = GradleFactory.create(javaVersion = "17")
                .resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "Project dependencies are detected correctly" {
            val definitionFile = getAssetFile("projects/synthetic/gradle/app/build.gradle")
            val expectedResultFile = getAssetFile("projects/synthetic/gradle-expected-output-app.yml")

            val result = GradleFactory.create(javaVersion = "17")
                .resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "External dependencies are detected correctly" {
            val definitionFile = getAssetFile("projects/synthetic/gradle/lib/build.gradle")
            val expectedResultFile = getAssetFile("projects/synthetic/gradle-expected-output-lib.yml")

            val result = GradleFactory.create(javaVersion = "17")
                .resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "Unresolved dependencies are detected correctly" {
            val definitionFile = getAssetFile("projects/synthetic/gradle/lib-without-repo/build.gradle")
            val expectedResultFile = getAssetFile("projects/synthetic/gradle-expected-output-lib-without-repo.yml")

            val result = GradleFactory.create(javaVersion = "17")
                .resolveSingleProject(definitionFile, resolveScopes = true)

            patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "Scopes are correctly excluded from the dependency graph" {
            val definitionFile = getAssetFile("projects/synthetic/gradle/app/build.gradle")
            val expectedResultFile = getAssetFile("projects/synthetic/gradle-expected-output-scopes-excludes.yml")

            val result = GradleFactory.create(javaVersion = "17")
                .resolveSingleProject(definitionFile, excludedScopes = setOf("test.*"), resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        // Disabled because despite following the example at [1] Gradle says there is "No service of type
        // ToolingModelBuilderRegistry available in GradleScopeServices".
        //
        // [1] https://github.com/gradle/gradle/blob/REL_2.13/subprojects/docs/src/samples/toolingApi/customModel/plugin/src/main/java/org/gradle/sample/plugin/CustomPlugin.java
        "Fails nicely for Gradle version < 2.14".config(enabled = false) {
            val definitionFile = getAssetFile("projects/synthetic/gradle-unsupported-version/build.gradle")
            val expectedResultFile = getAssetFile("projects/synthetic/gradle-expected-output-unsupported-version.yml")

            val result = GradleFactory.create(javaVersion = "17")
                .resolveSingleProject(definitionFile, resolveScopes = true)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        // Disabled as it causes hangs and memory issues on CI.
        "Is compatible with Gradle >= 2.14".config(enabled = false) {
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

            forAll(gradleVersionTable) { version, suffix ->
                installGradleWrapper(version)

                val definitionFile = getAssetFile("projects/synthetic/app/build.gradle")
                val expectedResultFile = getAssetFile("projects/synthetic/gradle-expected-output-app$suffix.yml")

                val result = GradleFactory.create(javaVersion = "17")
                    .resolveSingleProject(definitionFile, resolveScopes = true)

                result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
            }
        }
    }
}
