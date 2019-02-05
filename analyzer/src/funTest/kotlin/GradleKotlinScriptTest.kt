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
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.matchers.maps.shouldContainKey
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class GradleKotlinScriptTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/external/multi-kotlin-project")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "root project dependencies are detected correctly" {
            val definitionFile = File(projectDir, "build.gradle.kts")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "multi-kotlin-project-expected-output-root.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val result = createGradle().resolveDependencies(USER_DIR, listOf(definitionFile))

            result shouldContainKey definitionFile
            result[definitionFile]!!.let { resultForDefinitionFile ->
                resultForDefinitionFile.errors shouldBe emptyList()
                yamlMapper.writeValueAsString(resultForDefinitionFile) shouldBe expectedResult
            }
        }

        "core project dependencies are detected correctly" {
            val definitionFile = File(projectDir, "core/build.gradle.kts")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "multi-kotlin-project-expected-output-core.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val result = createGradle().resolveDependencies(USER_DIR, listOf(definitionFile))

            result shouldContainKey definitionFile
            result[definitionFile]!!.let { resultForDefinitionFile ->
                resultForDefinitionFile.errors shouldBe emptyList()
                yamlMapper.writeValueAsString(resultForDefinitionFile) shouldBe expectedResult
            }
        }

        "cli project dependencies are detected correctly" {
            val definitionFile = File(projectDir, "cli/build.gradle.kts")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "multi-kotlin-project-expected-output-cli.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val result = createGradle().resolveDependencies(USER_DIR, listOf(definitionFile))

            result shouldContainKey definitionFile
            result[definitionFile]!!.let { resultForDefinitionFile ->
                resultForDefinitionFile.errors shouldBe emptyList()
                yamlMapper.writeValueAsString(resultForDefinitionFile) shouldBe expectedResult
            }
        }
    }

    private fun createGradle() = Gradle("Gradle", DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
