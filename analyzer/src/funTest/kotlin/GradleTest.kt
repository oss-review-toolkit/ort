/*
 * Copyright (c) 2017 HERE Europe B.V.
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
import com.here.ort.util.yamlMapper
import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class GradleTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/gradle")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsRevision = vcsDir.getRevision()
    private val vcsUrl = vcsDir.getRemoteUrl()

    private val unresolvableConfigurations = listOf(
            "apiElements",
            "implementation",
            "runtimeElements",
            "runtimeOnly",
            "testImplementation",
            "testRuntimeOnly"
    ).map { "Configuration '$it' cannot be resolved." }

    private fun patchExpectedResult(filename: String) =
            File(projectDir.parentFile, filename)
                    .readText()
                    .replaceFirst("vcs_url: \"\"", "vcs_url: \"$vcsUrl\"")
                    .replaceFirst("vcs_revision: \"\"", "vcs_revision: \"$vcsRevision\"")

    init {
        "Root project dependencies are detected correctly" {
            val packageFile = File(projectDir, "build.gradle")
            val expectedResult = patchExpectedResult("project-gradle-expected-output-root.yml")

            val result = Gradle.create().resolveDependencies(projectDir, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors should beEmpty()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Project dependencies are detected correctly" {
            val packageFile = File(projectDir, "app/build.gradle")
            val expectedResult = patchExpectedResult("project-gradle-expected-output-app.yml")

            val result = Gradle.create().resolveDependencies(projectDir, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors.joinToString("\n") shouldBe unresolvableConfigurations.joinToString("\n")
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val packageFile = File(projectDir, "lib/build.gradle")
            val expectedResult = patchExpectedResult("project-gradle-expected-output-lib.yml")

            val result = Gradle.create().resolveDependencies(projectDir, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors.joinToString("\n") shouldBe unresolvableConfigurations.joinToString("\n")
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Unresolved dependencies are detected correctly" {
            val packageFile = File(projectDir, "lib-without-repo/build.gradle")
            val expectedResult = patchExpectedResult("project-gradle-expected-output-lib-without-repo.yml")

            val result = Gradle.create().resolveDependencies(projectDir, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors.joinToString("\n") shouldBe unresolvableConfigurations.joinToString("\n")
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }
    }
}
