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

import com.here.ort.analyzer.managers.Maven
import com.here.ort.util.yamlMapper

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class MavenTest : StringSpec() {
    private val syntheticProjectDir = File("src/funTest/assets/projects/synthetic/maven")

    init {
        "JGnash dependencies are detected correctly" {
            // TODO: test not only root project of JGnash.
            val projectDir = File("src/funTest/assets/projects/external/jgnash")
            val pomFile = File(projectDir, "pom.xml")
            val expectedResult = File(projectDir.parentFile, "jgnash-expected-output.yml").readText()

            val result = Maven.resolveDependencies(projectDir, listOf(pomFile))[pomFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Root project dependencies are detected correctly" {
            val pomFile = File(syntheticProjectDir, "pom.xml")
            val expectedResult = File(syntheticProjectDir.parentFile, "project-maven-expected-output-root.yml").readText()

            val result = Maven.resolveDependencies(syntheticProjectDir, listOf(pomFile))[pomFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Project dependencies are detected correctly" {
            val pomFileApp = File(syntheticProjectDir, "app/pom.xml")
            val pomFileLib = File(syntheticProjectDir, "lib/pom.xml")
            val expectedResult = File(syntheticProjectDir.parentFile, "project-maven-expected-output-app.yml").readText()

            val result = Maven.resolveDependencies(syntheticProjectDir, listOf(pomFileApp, pomFileLib))[pomFileApp]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val pomFile = File(syntheticProjectDir, "lib/pom.xml")
            val expectedResult = File(syntheticProjectDir.parentFile, "project-maven-expected-output-lib.yml").readText()

            val result = Maven.resolveDependencies(syntheticProjectDir, listOf(pomFile))[pomFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }
    }
}
