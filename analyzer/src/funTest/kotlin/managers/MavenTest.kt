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

package org.ossreviewtoolkit.analyzer.managers

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.normalizeVcsUrl
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldNotBe

import java.io.File

class MavenTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/maven").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "jgnash parent dependencies are detected correctly" {
            val projectDir = File("src/funTest/assets/projects/external/jgnash").absoluteFile
            val pomFile = File(projectDir, "pom.xml")
            val expectedResult = File(projectDir.parentFile, "jgnash-expected-output.yml").readText()

            val result = createMaven().resolveSingleProject(pomFile)

            result.toYaml() shouldBe expectedResult
        }

        "jgnash-core dependencies are detected correctly" {
            val projectDir = File("src/funTest/assets/projects/external/jgnash").absoluteFile

            val pomFileCore = File(projectDir, "jgnash-core/pom.xml")
            val pomFileResources = File(projectDir, "jgnash-resources/pom.xml")

            val expectedResult = File(projectDir.parentFile, "jgnash-core-expected-output.yml").readText()

            // jgnash-core depends on jgnash-resources, so we also have to pass the pom.xml of jgnash-resources to
            // resolveDependencies so that it is available in the Maven.projectsByIdentifier cache. Otherwise resolution
            // of transitive dependencies would not work.
            val result = createMaven().resolveDependencies(listOf(pomFileCore, pomFileResources))[pomFileCore]

            result shouldNotBe null
            result!! should haveSize(1)
            result.single().toYaml() shouldBe expectedResult
        }

        "Root project dependencies are detected correctly" {
            val pomFile = File(projectDir, "pom.xml")
            val expectedResult = patchExpectedResult(
                File(projectDir.parentFile, "maven-expected-output-root.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createMaven().resolveSingleProject(pomFile)

            result.toYaml() shouldBe expectedResult
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
            val result = createMaven().resolveDependencies(listOf(pomFileApp, pomFileLib))[pomFileApp]

            result shouldNotBe null
            result!! should haveSize(1)
            result.single().toYaml() shouldBe expectedResult
        }

        "External dependencies are detected correctly" {
            val pomFile = File(projectDir, "lib/pom.xml")
            val expectedResult = patchExpectedResult(
                File(projectDir.parentFile, "maven-expected-output-lib.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createMaven().resolveSingleProject(pomFile)

            result.toYaml() shouldBe expectedResult
        }

        "Parent POM from Maven central can be resolved" {
            // Delete the parent POM from the local repository to make sure it has to be resolved from Maven central.
            Os.userHomeDirectory
                .resolve(".m2/repository/org/springframework/boot/spring-boot-starter-parent/1.5.3.RELEASE")
                .safeDeleteRecursively(force = true)

            val projectDir = File("src/funTest/assets/projects/synthetic/maven-parent").absoluteFile
            val pomFile = File(projectDir, "pom.xml")
            val expectedResult = patchExpectedResult(
                File(projectDir.parentFile, "maven-parent-expected-output-root.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createMaven().resolveSingleProject(pomFile)

            result.toYaml() shouldBe expectedResult
        }
    }

    private fun createMaven() =
        Maven("Maven", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
