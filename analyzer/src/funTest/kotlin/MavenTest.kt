/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class MavenTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/maven")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "jgnash parent dependencies are detected correctly" {
            val projectDir = File("src/funTest/assets/projects/external/jgnash")
            val pomFile = File(projectDir, "pom.xml")
            val expectedResult = File(projectDir.parentFile, "jgnash-expected-output.yml").readText()

            val config = AnalyzerConfiguration(false, false)
            val result = Maven.create(config).resolveDependencies(USER_DIR, listOf(pomFile))[pomFile]

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
            val config = AnalyzerConfiguration(false, false)
            val result = Maven.create(config)
                    .resolveDependencies(USER_DIR, listOf(pomFileCore, pomFileResources))[pomFileCore]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Root project dependencies are detected correctly" {
            val pomFile = File(projectDir, "pom.xml")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "maven-expected-output-root.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val config = AnalyzerConfiguration(false, false)
            val result = Maven.create(config).resolveDependencies(USER_DIR, listOf(pomFile))[pomFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Project dependencies are detected correctly" {
            val pomFileApp = File(projectDir, "app/pom.xml")
            val pomFileLib = File(projectDir, "lib/pom.xml")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "maven-expected-output-app.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            // app depends on lib, so we also have to pass the pom.xml of lib to resolveDependencies so that it is
            // available in the Maven.projectsByIdentifier cache. Otherwise resolution of transitive dependencies would
            // not work.
            val config = AnalyzerConfiguration(false, false)
            val result = Maven.create(config)
                    .resolveDependencies(USER_DIR, listOf(pomFileApp, pomFileLib))[pomFileApp]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val pomFile = File(projectDir, "lib/pom.xml")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "maven-expected-output-lib.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val config = AnalyzerConfiguration(false, false)
            val result = Maven.create(config).resolveDependencies(USER_DIR, listOf(pomFile))[pomFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }

        "Parent POM from Maven central can be resolved" {
            // Delete the parent POM from the local repository to make sure it has to be resolved from Maven central.
            val userHome = File(System.getProperty("user.home"))
            File(userHome, ".m2/repository/org/springframework/boot/spring-boot-starter-parent/1.5.3.RELEASE")
                    .safeDeleteRecursively()

            val projectDir = File("src/funTest/assets/projects/synthetic/maven-parent")
            val pomFile = File(projectDir, "pom.xml")
            val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "maven-parent-expected-output-root.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision
            )

            val config = AnalyzerConfiguration(false, false)
            val result = Maven.create(config).resolveDependencies(USER_DIR, listOf(pomFile))[pomFile]

            yamlMapper.writeValueAsString(result) shouldBe expectedResult
        }
    }
}
