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

import com.here.ort.analyzer.managers.Maven
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.yamlMapper

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class MavenTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/maven")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private fun patchExpectedResult(filename: String) =
            File(projectDir.parentFile, filename).readText()
                    // project.vcs_processed:
                    .replaceFirst("<REPLACE_URL>", normalizeVcsUrl(vcsUrl))
                    .replaceFirst("<REPLACE_REVISION>", vcsRevision)

    init {
        "jgnash parent dependencies are detected correctly" {
            val projectDir = File("src/funTest/assets/projects/external/jgnash")
            val pomFile = File(projectDir, "pom.xml")
            val expectedResult = File(projectDir.parentFile, "jgnash-expected-output.yml").readText()

            val result = Maven.create().resolveDependencies(listOf(pomFile))[pomFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "jgnash-core dependencies are detected correctly" {
            val projectDir = File("src/funTest/assets/projects/external/jgnash")

            val pomFileCore = File(projectDir, "jgnash-core/pom.xml")
            val pomFileResources = File(projectDir, "jgnash-resources/pom.xml")

            val expectedResult = File(projectDir.parentFile, "jgnash-core-expected-output.yml").readText()

            // jgnash-core depends on jgnash-resources, so we also have to pass the pom.xml of jgnash-resources to
            // resolveDependencies so that it is available in the Maven.projectsByIdentifier cache. Otherwise resolution
            // of transitive dependencies would not work.
            val result = Maven.create()
                    .resolveDependencies(listOf(pomFileCore, pomFileResources))[pomFileCore]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Root project dependencies are detected correctly" {
            val pomFile = File(projectDir, "pom.xml")
            val expectedResult = patchExpectedResult("maven-expected-output-root.yml")

            val result = Maven.create().resolveDependencies(listOf(pomFile))[pomFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Project dependencies are detected correctly" {
            val pomFileApp = File(projectDir, "app/pom.xml")
            val pomFileLib = File(projectDir, "lib/pom.xml")

            val expectedResult = patchExpectedResult("maven-expected-output-app.yml")

            // app depends on lib, so we also have to pass the pom.xml of lib to resolveDependencies so that it is
            // available in the Maven.projectsByIdentifier cache. Otherwise resolution of transitive dependencies would
            // not work.
            val result = Maven.create()
                    .resolveDependencies(listOf(pomFileApp, pomFileLib))[pomFileApp]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val pomFile = File(projectDir, "lib/pom.xml")
            val expectedResult = patchExpectedResult("maven-expected-output-lib.yml")

            val result = Maven.create().resolveDependencies(listOf(pomFile))[pomFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }
    }
}
