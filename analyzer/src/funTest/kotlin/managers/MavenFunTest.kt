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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class MavenFunTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/maven").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Root project dependencies are detected correctly" {
            val pomFile = projectDir.resolve("pom.xml")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("maven-expected-output-root.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createMaven().resolveSingleProject(pomFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "Project dependencies are detected correctly" {
            val pomFileApp = projectDir.resolve("app/pom.xml")
            val pomFileLib = projectDir.resolve("lib/pom.xml")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("maven-expected-output-app.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            // app depends on lib, so we also have to pass the pom.xml of lib to resolveDependencies so that it is
            // available in the Maven.projectsByIdentifier cache. Otherwise, resolution of transitive dependencies would
            // not work.
            val managerResult = createMaven().resolveDependencies(listOf(pomFileApp, pomFileLib), emptyMap())
            val result = managerResult.projectResults[pomFileApp]

            result.shouldNotBeNull()
            result should haveSize(1)
            managerResult.resolveScopes(result.single()).toYaml() shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val pomFile = projectDir.resolve("lib/pom.xml")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("maven-expected-output-lib.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createMaven().resolveSingleProject(pomFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "Parent POM from Maven central can be resolved" {
            // Delete the parent POM from the local repository to make sure it has to be resolved from Maven central.
            Os.userHomeDirectory
                .resolve(".m2/repository/org/springframework/boot/spring-boot-starter-parent/1.5.3.RELEASE")
                .safeDeleteRecursively(force = true)

            val projectDir = File("src/funTest/assets/projects/synthetic/maven-parent").absoluteFile
            val pomFile = projectDir.resolve("pom.xml")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("maven-parent-expected-output-root.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createMaven().resolveSingleProject(pomFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "Maven Wagon extensions can be loaded" {
            val projectDir = File("src/funTest/assets/projects/synthetic/maven-wagon").absoluteFile
            val pomFile = projectDir.resolve("pom.xml")
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("maven-wagon-expected-output.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createMaven().resolveSingleProject(pomFile, resolveScopes = true)

            patchActualResult(result.toYaml(), patchStartAndEndTime = true) shouldBe expectedResult
        }
    }

    private fun createMaven() =
        Maven("Maven", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
