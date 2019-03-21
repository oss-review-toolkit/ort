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

package com.here.ort.model

import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration

import io.kotlintest.matchers.haveSize
import io.kotlintest.matchers.match
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec

import java.io.File
import java.lang.IllegalArgumentException

class OrtResultTest : WordSpec({
    "collectDependencies" should {
        "be able to get all direct dependencies of a package" {
            val expectedDependencies = listOf(
                "Maven:com.typesafe.akka:akka-actor_2.12:2.5.6",
                "Maven:com.typesafe:ssl-config-core_2.12:0.2.2",
                "Maven:org.reactivestreams:reactive-streams:1.0.1"
            )

            val projectsDir = File("../analyzer/src/funTest/assets/projects")
            val resultFile = projectsDir.resolve("external/sbt-multi-project-example-expected-output.yml")
            val result = resultFile.readValue<OrtResult>()

            val id = Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6")
            result.collectDependencies(id, 1).map { it.id.toCoordinates() } shouldBe expectedDependencies
        }
    }

    "collectProjectsAndPackages" should {
        "be able to get all ids except for ones for sub-projects" {
            val projectsDir = File("../analyzer/src/funTest/assets/projects")
            val resultFile = projectsDir.resolve("synthetic/gradle-all-dependencies-expected-result.yml")
            val result = resultFile.readValue<OrtResult>()

            val ids = result.collectProjectsAndPackages()
            val idsWithoutSubProjects = result.collectProjectsAndPackages(false)

            val actualIds = ids - idsWithoutSubProjects
            val expectedIds = sortedSetOf(Identifier("Gradle:com.here.ort.gradle.example:lib:1.0.0"))

            ids should haveSize(9)
            idsWithoutSubProjects should haveSize(8)

            actualIds shouldBe expectedIds
        }
    }

    "getDefinitionFilePathRelativeToAnalyzerRoot" should {
        "use the correct vcs" {
            val vcs = VcsInfo(type = "Git", url = "https://example.com/git", revision = "")
            val nestedVcs1 = VcsInfo(type = "Git", url = "https://example.com/git1", revision = "")
            val nestedVcs2 = VcsInfo(type = "Git", url = "https://example.com/git2", revision = "")
            val project1 = Project.EMPTY.copy(
                id = Identifier("Gradle:com.here:project1:1.0"),
                definitionFilePath = "project1/build.gradle",
                vcs = vcs,
                vcsProcessed = vcs.normalize()
            )
            val project2 = Project.EMPTY.copy(
                id = Identifier("Gradle:com.here:project1:1.0"),
                definitionFilePath = "project2/build.gradle",
                vcs = nestedVcs1,
                vcsProcessed = nestedVcs1.normalize()
            )
            val project3 = Project.EMPTY.copy(
                id = Identifier("Gradle:com.here:project1:1.0"),
                definitionFilePath = "project3/build.gradle",
                vcs = nestedVcs2,
                vcsProcessed = nestedVcs2.normalize()
            )
            val ortResult = OrtResult(
                Repository(
                    vcs = vcs,
                    vcsProcessed = vcs.normalize(),
                    nestedRepositories = mapOf(
                        "path/1" to nestedVcs1,
                        "path/2" to nestedVcs2
                    ),
                    config = RepositoryConfiguration()
                ),
                AnalyzerRun(
                    environment = Environment(),
                    config = AnalyzerConfiguration(ignoreToolVersions = true, allowDynamicVersions = true),
                    result = AnalyzerResult.EMPTY
                )
            )

            ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project1) shouldBe "project1/build.gradle"
            ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project2) shouldBe "path/1/project2/build.gradle"
            ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project3) shouldBe "path/2/project3/build.gradle"
        }

        "fail if no vcs matches" {
            val vcs = VcsInfo(type = "Git", url = "https://example.com/git", revision = "")
            val nestedVcs1 = VcsInfo(type = "Git", url = "https://example.com/git1", revision = "")
            val nestedVcs2 = VcsInfo(type = "Git", url = "https://example.com/git2", revision = "")
            val project = Project.EMPTY.copy(
                id = Identifier("Gradle:com.here:project1:1.0"),
                definitionFilePath = "build.gradle",
                vcs = nestedVcs2,
                vcsProcessed = nestedVcs2.normalize()
            )
            val ortResult = OrtResult(
                Repository(
                    vcs = vcs,
                    vcsProcessed = vcs.normalize(),
                    nestedRepositories = mapOf(
                        "path/1" to nestedVcs1
                    ),
                    config = RepositoryConfiguration()
                ),
                AnalyzerRun(
                    environment = Environment(),
                    config = AnalyzerConfiguration(ignoreToolVersions = true, allowDynamicVersions = true),
                    result = AnalyzerResult.EMPTY
                )
            )

            val e = shouldThrow<IllegalArgumentException> {
                ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project) shouldBe "project1/build.gradle"
            }

            e.message should match("The .* of project .* cannot be found in .*")
        }
    }
})
