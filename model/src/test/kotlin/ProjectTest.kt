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

package org.ossreviewtoolkit.model

import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder

import java.io.File
import java.time.Instant

private fun readAnalyzerResult(analyzerResultFilename: String): Project =
    File("../analyzer/src/funTest/assets/projects/synthetic")
        .resolve(analyzerResultFilename)
        .readValue<ProjectAnalyzerResult>().project

class ProjectTest : WordSpec({
    "Projects" should {
        "be sorted by type" {
            val npmProject = Project.EMPTY.copy(id = Identifier.EMPTY.copy(type = "NPM"))
            val mavenProject = Project.EMPTY.copy(id = Identifier.EMPTY.copy(type = "Maven"))

            val sortedProjects = listOf(npmProject, mavenProject).sorted()

            sortedProjects should containExactly(mavenProject, npmProject)
        }
    }

    "Projects of the same type" should {
        "be sorted by definition file depth" {
            val nestedProject = Project.EMPTY.copy(
                id = Identifier("Maven", "GroupA", "Artifact", "1.0.0"),
                definitionFilePath = "nested/pom.xml"
            )

            val rootProject = Project.EMPTY.copy(
                id = Identifier("Maven", "GroupB", "Artifact", "1.0.0"),
                definitionFilePath = "pom.xml"
            )

            val sortedProjects = listOf(nestedProject, rootProject).sorted()

            sortedProjects should containExactly(rootProject, nestedProject)
        }
    }

    "collectDependencies" should {
        "get all dependencies by default" {
            val project = readAnalyzerResult("gradle-expected-output-lib.yml")

            val dependencies = project.collectDependencies().map { it.toCoordinates() }

            dependencies should containExactlyInAnyOrder(
                "Maven:junit:junit:4.12",
                "Maven:org.apache.commons:commons-lang3:3.5",
                "Maven:org.apache.commons:commons-text:1.1",
                "Maven:org.apache.struts:struts2-assembly:2.5.14.1",
                "Maven:org.hamcrest:hamcrest-core:1.3"
            )
        }

        "get no dependencies for a depth of 0" {
            val project = readAnalyzerResult("gradle-expected-output-lib.yml")

            val dependencies = project.collectDependencies(maxDepth = 0)

            dependencies should beEmpty()
        }

        "get only direct dependencies for a depth of 1" {
            val project = readAnalyzerResult("gradle-expected-output-lib.yml")

            val dependencies = project.collectDependencies(maxDepth = 1).map { it.toCoordinates() }

            dependencies should containExactlyInAnyOrder(
                "Maven:junit:junit:4.12",
                "Maven:org.apache.commons:commons-text:1.1",
                "Maven:org.apache.struts:struts2-assembly:2.5.14.1"
            )
        }
    }

    "collectIssues" should {
        "find all issues" {
            val project = readAnalyzerResult("gradle-expected-output-lib-without-repo.yml")

            val issues = project.collectIssues()

            issues shouldBe mapOf(
                Identifier("Unknown:org.apache.commons:commons-text:1.1") to setOf(
                    OrtIssue(
                        Instant.EPOCH,
                        "Gradle",
                        "Unresolved: ModuleVersionNotFoundException: Cannot resolve external dependency " +
                                "org.apache.commons:commons-text:1.1 because no repositories are defined."
                    )
                ),
                Identifier("Unknown:junit:junit:4.12") to setOf(
                    OrtIssue(
                        Instant.EPOCH,
                        "Gradle",
                        "Unresolved: ModuleVersionNotFoundException: Cannot resolve external dependency " +
                                "junit:junit:4.12 because no repositories are defined."
                    )
                )
            )
        }
    }
})
