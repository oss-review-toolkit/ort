/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.contain
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

import org.ossreviewtoolkit.utils.test.readOrtResult

class DependencyTreeNavigatorTest : WordSpec() {
    private val testResult = readOrtResult(RESULT_FILE)

    private val testProject = testResult.getProject(PROJECT_ID)!!

    private val navigator = testResult.dependencyNavigator

    init {
        "scopeNames" should {
            "return the scope names of a project" {
                navigator.scopeNames(testProject) should containExactlyInAnyOrder("compile", "test")
            }
        }

        "directDependencies" should {
            "return the direct dependencies of a project" {
                navigator.directDependencies(testProject, "test")
                    .map { it.id }.toList() should containExactly(
                    Identifier("Maven:org.scalacheck:scalacheck_2.12:1.13.5"),
                    Identifier("Maven:org.scalatest:scalatest_2.12:3.0.4")
                )
            }

            "return an empty sequence for an unknown scope" {
                navigator.directDependencies(testProject, "unknownScope").toList() should beEmpty()
            }
        }

        "scopeDependencies" should {
            "return a map with scopes and their dependencies for a project" {
                val scopeDependencies = navigator.scopeDependencies(testProject)

                scopeDependencies.keys should containExactlyInAnyOrder("compile", "test")

                val compileDependencies = scopeDependencies["compile"].orEmpty()
                compileDependencies should haveSize(17)
                compileDependencies should containAll(
                    Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6"),
                    Identifier("Maven:org.scala-lang:scala-reflect:2.12.2"),
                    Identifier("Maven:org.scala-lang:scala-library:2.12.3")
                )

                val testDependencies = scopeDependencies["test"].orEmpty()
                testDependencies should haveSize(6)
                testDependencies should containAll(
                    Identifier("Maven:org.scalacheck:scalacheck_2.12:1.13.5"),
                    Identifier("Maven:org.scalactic:scalactic_2.12:3.0.4")
                )
            }

            "return a map with scopes and their direct dependencies by using maxDepth = 1" {
                val scopeDependencies = navigator.scopeDependencies(testProject, maxDepth = 1)

                val compileDependencies = scopeDependencies["compile"].orEmpty()
                compileDependencies should haveSize(7)
                compileDependencies shouldNot contain(Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6"))
            }

            "return a map with scopes and their dependencies up to a given maxDepth" {
                val scopeDependencies = navigator.scopeDependencies(testProject, maxDepth = 2)

                val compileDependencies = scopeDependencies["compile"].orEmpty()
                compileDependencies should haveSize(14)
                compileDependencies shouldNot contain(
                    Identifier("Maven:org.scala-lang.modules:scala-java8-compat_2.12:0.8.0")
                )
            }

            "return a map with scopes and their dependencies with filter criteria" {
                val id1 = Identifier("Maven:test:dependency1:1")
                val id2 = Identifier("Maven:test:dependency2:2")
                val ref1 = PackageReference(id1)
                val ref2 = PackageReference(
                    id2,
                    linkage = PackageLinkage.PROJECT_STATIC,
                    issues = listOf(OrtIssue(source = "test", message = "test message"))
                )
                val scope = Scope("test", sortedSetOf(ref1, ref2))
                val project = Project.EMPTY.copy(scopeDependencies = sortedSetOf(scope))

                val matchedIds = mutableSetOf<Identifier>()
                val scopeDependencies = navigator.scopeDependencies(project) { node ->
                    matchedIds += node.id
                    node.linkage == PackageLinkage.PROJECT_STATIC && node.issues.isNotEmpty()
                }

                scopeDependencies["test"].orEmpty() should containExactly(id2)
                matchedIds should containExactlyInAnyOrder(id1, id2)
            }
        }

        "dependenciesForScope" should {
            "return an empty set for an unknown scope" {
                navigator.dependenciesForScope(testProject, "unknownScope") should beEmpty()
            }

            "return the dependencies of a specific scope" {
                val compileDependencies = navigator.dependenciesForScope(testProject, "compile")

                compileDependencies should haveSize(17)
                compileDependencies should containAll(
                    Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6"),
                    Identifier("Maven:org.scala-lang:scala-reflect:2.12.2"),
                    Identifier("Maven:org.scala-lang:scala-library:2.12.3")
                )
            }

            "return the dependencies of a specific scope up to a given maxDepth" {
                val compileDependencies = navigator.dependenciesForScope(testProject, "compile", maxDepth = 2)

                compileDependencies should haveSize(14)
                compileDependencies shouldNot contain(
                    Identifier("Maven:org.scala-lang.modules:scala-java8-compat_2.12:0.8.0")
                )
            }

            "return the dependencies of a specific scope with filter criteria" {
                val akkaDependencies = navigator.dependenciesForScope(testProject, "compile") { node ->
                    node.id.namespace.contains("akka")
                }

                akkaDependencies.shouldContainExactlyInAnyOrder(
                    Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6"),
                    Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6")
                )
            }
        }

        "collectSubProjects" should {
            "find all the sub projects of a project" {
                val projectId = Identifier("SBT:com.pbassiner:multi1_2.12:0.1-SNAPSHOT")
                val project = testResult.getProject(projectId)!!

                val subProjectIds = navigator.collectSubProjects(project)

                subProjectIds should containExactly(PROJECT_ID)
            }
        }
    }
}

/** Name of a file with a more complex ORT result that is used by multiple test cases. */
private const val RESULT_FILE =
    "../analyzer/src/funTest/assets/projects/external/sbt-multi-project-example-expected-output.yml"

/** Identifier of the project used by the tests. */
private val PROJECT_ID = Identifier("SBT:com.pbassiner:common_2.12:0.1-SNAPSHOT")
