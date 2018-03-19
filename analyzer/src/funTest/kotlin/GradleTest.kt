/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer

import com.here.ort.analyzer.managers.Gradle
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.downloader.vcs.Git
import com.here.ort.utils.ExpensiveTag
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.yamlMapper

import io.kotlintest.Spec
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.properties.forAll
import io.kotlintest.properties.headers
import io.kotlintest.properties.row
import io.kotlintest.properties.table
import io.kotlintest.specs.StringSpec

import java.io.File

class GradleTest : StringSpec() {
    private val isJava9OrAbove = System.getProperty("java.version").split('.').first().toInt() >= 9
    private val projectDir = File("src/funTest/assets/projects/synthetic/gradle")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private fun patchExpectedResult(filename: String) =
            File(projectDir.parentFile, filename).readText()
                    // project.vcs_processed:
                    .replace("<REPLACE_URL>", normalizeVcsUrl(vcsUrl))
                    .replace("<REPLACE_REVISION>", vcsRevision)

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        try {
            super.interceptSpec(context, spec)
        } finally {
            // Reset the Gradle version in the test project to clean up after the tests.
            Git.run(projectDir, "checkout", ".")
        }
    }

    init {
        "Root project dependencies are detected correctly" {
            val packageFile = File(projectDir, "build.gradle")
            val expectedResult = patchExpectedResult("gradle-expected-output-root.yml")

            val result = Gradle.create().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors shouldBe listOf<String>()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Project dependencies are detected correctly" {
            val packageFile = File(projectDir, "app/build.gradle")
            val expectedResult = patchExpectedResult("gradle-expected-output-app.yml")

            val result = Gradle.create().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors shouldBe listOf<String>()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val packageFile = File(projectDir, "lib/build.gradle")
            val expectedResult = patchExpectedResult("gradle-expected-output-lib.yml")

            val result = Gradle.create().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors shouldBe listOf<String>()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Unresolved dependencies are detected correctly" {
            val packageFile = File(projectDir, "lib-without-repo/build.gradle")
            val expectedResult = patchExpectedResult("gradle-expected-output-lib-without-repo.yml")

            val result = Gradle.create().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors shouldBe listOf<String>()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Fails nicely for Gradle version < 2.14" {
            val packageFile = File("src/funTest/assets/projects/synthetic/gradle-unsupported-version/build.gradle")
            val expectedResult = patchExpectedResult("gradle-expected-output-unsupported-version.yml")

            val result = Gradle.create().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }.config(enabled = false)

        "Is compatible with Gradle >= 2.14" {
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
                    row("3.3", "-3.3"),
                    row("3.2.1", "-3.3"),
                    row("3.2", "-3.3"),
                    row("3.1", "-3.3"),
                    row("3.0", "-3.3"),
                    row("2.14.1", "-3.3"),
                    row("2.14", "-3.3")
            )

            val gradleVersions = if (isJava9OrAbove) {
                gradleVersionsThatSupportJava9
            } else {
                gradleVersionsThatSupportJava9 + gradleVersionsThatDoNotSupportJava9
            }

            val gradleVersionTable = table(headers("version", "resultsFileSuffix"), *gradleVersions)

            forAll(gradleVersionTable) { version, resultsFileSuffix ->
                gradleWrapper(version)

                val packageFile = File(projectDir, "app/build.gradle")
                val expectedResult = patchExpectedResult("gradle-expected-output-app$resultsFileSuffix.yml")

                val result = Gradle.create().resolveDependencies(listOf(packageFile))[packageFile]

                result shouldNotBe null
                result!!.errors shouldBe listOf<String>()
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }.config(tags = setOf(ExpensiveTag), enabled = false)
    }

    private fun gradleWrapper(version: String) {
        println("Installing Gradle wrapper version $version.")

        // When calling Windows batch files directly (without passing them to "cmd" as an argument), Windows requires
        // the absolute path to the batch file to be passed to the underlying ProcessBuilder for some reason.
        val wrapperAbsolutePath = File(projectDir, Gradle.wrapper).absolutePath

        ProcessCapture(projectDir, wrapperAbsolutePath, "wrapper", "--gradle-version", version, "--no-daemon")
                .requireSuccess()
    }
}
