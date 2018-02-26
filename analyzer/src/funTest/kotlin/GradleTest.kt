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
import com.here.ort.utils.ExpensiveTag
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.yamlMapper

import io.kotlintest.Spec
import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.properties.forAll
import io.kotlintest.properties.headers
import io.kotlintest.properties.row
import io.kotlintest.properties.table
import io.kotlintest.specs.StringSpec

import java.io.File

class GradleTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/gradle")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private fun patchExpectedResult(filename: String) =
            File(projectDir.parentFile, filename).readText()
                    // project.vcs_processed:
                    .replaceFirst("<REPLACE_URL>", normalizeVcsUrl(vcsUrl))
                    .replaceFirst("<REPLACE_REVISION>", vcsRevision)

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        // Reset the Gradle version in the test project to the default in case the compatibility test was aborted.
        gradleWrapper("4.5.1")
        try {
            super.interceptSpec(context, spec)
        } finally {
            // Call the Gradle wrapper task again to clean up after the tests.
            gradleWrapper("4.5.1")
        }
    }

    init {
        "Root project dependencies are detected correctly" {
            val packageFile = File(projectDir, "build.gradle")
            val expectedResult = patchExpectedResult("gradle-expected-output-root.yml")

            val result = Gradle.create().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors should beEmpty()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Project dependencies are detected correctly" {
            val packageFile = File(projectDir, "app/build.gradle")
            val expectedResult = patchExpectedResult("gradle-expected-output-app.yml")

            val result = Gradle.create().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors should beEmpty()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val packageFile = File(projectDir, "lib/build.gradle")
            val expectedResult = patchExpectedResult("gradle-expected-output-lib.yml")

            val result = Gradle.create().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors should beEmpty()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Unresolved dependencies are detected correctly" {
            val packageFile = File(projectDir, "lib-without-repo/build.gradle")
            val expectedResult = patchExpectedResult("gradle-expected-output-lib-without-repo.yml")

            val result = Gradle.create().resolveDependencies(listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors should beEmpty()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Is compatible with Gradle >= 3.3" {
            val gradleVersions = table(
                    headers("version", "resultsFileSuffix"),
                    row("3.3", "-3.3"),
                    row("3.4", ""),
                    row("3.4.1", ""),
                    row("3.5", ""),
                    row("3.5.1", ""),
                    row("4.0", ""),
                    row("4.0.1", ""),
                    row("4.0.2", ""),
                    row("4.1", ""),
                    row("4.2", ""),
                    row("4.2.1", ""),
                    row("4.3", ""),
                    row("4.3.1", ""),
                    row("4.4", ""),
                    row("4.4.1", ""),
                    row("4.5", ""),
                    row("4.5.1", "")
            )

            forAll(gradleVersions) { version, resultsFileSuffix ->
                gradleWrapper(version)

                val packageFile = File(projectDir, "app/build.gradle")
                val expectedResult = patchExpectedResult("gradle-expected-output-app$resultsFileSuffix.yml")

                val result = Gradle.create().resolveDependencies(listOf(packageFile))[packageFile]

                result shouldNotBe null
                result!!.errors should beEmpty()
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }.config(tags = setOf(ExpensiveTag))
    }

    private fun gradleWrapper(version: String) {
        println("Installing Gradle wrapper version $version.")
        val gradleWrapper = if (OS.isWindows) Gradle.wrapper else "./${Gradle.wrapper}"
        ProcessCapture(projectDir, gradleWrapper, "wrapper", "--gradle-version=$version", "--distribution-type=ALL")
                .requireSuccess()
    }
}
