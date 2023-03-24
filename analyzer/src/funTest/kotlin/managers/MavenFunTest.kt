/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.toYaml

class MavenFunTest : StringSpec() {
    private val projectDir = getAssetFile("projects/synthetic/maven")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Root project dependencies are detected correctly" {
            val pomFile = projectDir.resolve("pom.xml")
            val expectedResult = patchExpectedResult(
                projectDir.resolveSibling("maven-expected-output-root.yml"),
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
                projectDir.resolveSibling("maven-expected-output-app.yml"),
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
                projectDir.resolveSibling("maven-expected-output-lib.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createMaven().resolveSingleProject(pomFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "Scopes can be excluded" {
            val pomFile = projectDir.resolve("lib/pom.xml")
            val expectedResult = patchExpectedResult(
                projectDir.resolveSibling("maven-expected-output-scope-excludes.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val analyzerConfig = AnalyzerConfiguration(skipExcluded = true)
            val scopeExclude = ScopeExclude("test.*", ScopeExcludeReason.TEST_DEPENDENCY_OF)
            val repoConfig = RepositoryConfiguration(excludes = Excludes(scopes = listOf(scopeExclude)))

            val result = createMaven(analyzerConfig, repoConfig)
                .resolveSingleProject(pomFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "Parent POM from Maven central can be resolved" {
            // Delete the parent POM from the local repository to make sure it has to be resolved from Maven central.
            Os.userHomeDirectory
                .resolve(".m2/repository/org/springframework/boot/spring-boot-starter-parent/1.5.3.RELEASE")
                .safeDeleteRecursively(force = true)

            val projectDir = getAssetFile("projects/synthetic/maven-parent")
            val pomFile = projectDir.resolve("pom.xml")
            val expectedResult = patchExpectedResult(
                projectDir.resolveSibling("maven-parent-expected-output-root.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createMaven().resolveSingleProject(pomFile, resolveScopes = true)

            result.toYaml() shouldBe expectedResult
        }

        "Maven Wagon extensions can be loaded" {
            val projectDir = getAssetFile("projects/synthetic/maven-wagon")
            val pomFile = projectDir.resolve("pom.xml")
            val expectedResult = patchExpectedResult(
                projectDir.resolveSibling("maven-wagon-expected-output.yml"),
                url = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val result = createMaven().resolveSingleProject(pomFile, resolveScopes = true)

            patchActualResult(result.toYaml(), patchStartAndEndTime = true) shouldBe expectedResult
        }
    }

    private fun createMaven(
        analyzerConfig: AnalyzerConfiguration = AnalyzerConfiguration(),
        repositoryConfig: RepositoryConfiguration = RepositoryConfiguration()
    ) =
        Maven("Maven", USER_DIR, analyzerConfig, repositoryConfig)
}
