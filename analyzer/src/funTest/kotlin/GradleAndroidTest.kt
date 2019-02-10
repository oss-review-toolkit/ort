/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.test.AndroidTag
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class GradleAndroidTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/gradle-android")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Root project dependencies are detected correctly".config(tags = setOf(AndroidTag)) {
            val packageFile = File(projectDir, "build.gradle")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "gradle-android-expected-output-root.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val result = createGradle().resolveDependencies(USER_DIR, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors shouldBe emptyList()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Project dependencies are detected correctly".config(tags = setOf(AndroidTag)) {
            val packageFile = File(projectDir, "app/build.gradle")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "gradle-android-expected-output-app.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val result = createGradle().resolveDependencies(USER_DIR, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors shouldBe emptyList()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "External dependencies are detected correctly".config(tags = setOf(AndroidTag)) {
            val packageFile = File(projectDir, "lib/build.gradle")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "gradle-android-expected-output-lib.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val result = createGradle().resolveDependencies(USER_DIR, listOf(packageFile))[packageFile]

            result shouldNotBe null
            result!!.errors shouldBe emptyList()
            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }
    }

    private fun createGradle() = Gradle("Gradle", DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
