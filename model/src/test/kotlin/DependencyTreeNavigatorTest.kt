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
import io.kotest.matchers.shouldBe
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

        "packageDependencies" should {
            "return the dependencies of an existing package in a project" {
                val pkgId = Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6")

                val dependencies = navigator.packageDependencies(testProject, pkgId)

                dependencies should containExactlyInAnyOrder(
                    Identifier("Maven:com.typesafe:ssl-config-core_2.12:0.2.2"),
                    Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6"),
                    Identifier("Maven:org.scala-lang.modules:scala-java8-compat_2.12:0.8.0"),
                    Identifier("Maven:org.reactivestreams:reactive-streams:1.0.1")
                )
            }

            "return an empty set for the dependencies of a non-existing package" {
                val pkgId = Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.7")

                val dependencies = navigator.packageDependencies(testProject, pkgId)

                dependencies should beEmpty()
            }

            "support a maxDepth filter" {
                val pkgId = Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6")

                val dependencies = navigator.packageDependencies(testProject, pkgId, maxDepth = 1)

                dependencies should containExactlyInAnyOrder(
                    Identifier("Maven:com.typesafe:ssl-config-core_2.12:0.2.2"),
                    Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6"),
                    Identifier("Maven:org.reactivestreams:reactive-streams:1.0.1")
                )
            }

            "support a DependencyMatcher" {
                val pkgId = Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6")

                val dependencies = navigator.packageDependencies(testProject, pkgId) { node ->
                    node.id.namespace.startsWith("com.typesafe")
                }

                dependencies should containExactlyInAnyOrder(
                    Identifier("Maven:com.typesafe:ssl-config-core_2.12:0.2.2"),
                    Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6")
                )
            }
        }

        "getShortestPaths" should {
            "return the shortest paths for a project" {
                val paths = navigator.getShortestPaths(testProject)

                paths.keys should haveSize(2)

                paths["compile"]!! should org.ossreviewtoolkit.utils.test.containExactly(
                    Identifier("Maven:ch.qos.logback:logback-classic:1.2.3") to emptyList(),
                    Identifier("Maven:ch.qos.logback:logback-core:1.2.3") to listOf(
                        Identifier("Maven:ch.qos.logback:logback-classic:1.2.3")
                    ),
                    Identifier("Maven:com.fasterxml.jackson.core:jackson-annotations:2.8.0") to listOf(
                        Identifier("Maven:net.logstash.logback:logstash-logback-encoder:4.11"),
                        Identifier("Maven:com.fasterxml.jackson.core:jackson-databind:2.8.9")
                    ),
                    Identifier("Maven:com.fasterxml.jackson.core:jackson-core:2.8.9") to listOf(
                        Identifier("Maven:net.logstash.logback:logstash-logback-encoder:4.11"),
                        Identifier("Maven:com.fasterxml.jackson.core:jackson-databind:2.8.9")
                    ),
                    Identifier("Maven:com.fasterxml.jackson.core:jackson-databind:2.8.9") to listOf(
                        Identifier("Maven:net.logstash.logback:logstash-logback-encoder:4.11")
                    ),
                    Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6") to listOf(
                        Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6")
                    ),
                    Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6") to emptyList(),
                    Identifier("Maven:com.typesafe:config:1.3.1") to emptyList(),
                    Identifier("Maven:com.typesafe.scala-logging:scala-logging_2.12:3.7.2") to emptyList(),
                    Identifier("Maven:com.typesafe:ssl-config-core_2.12:0.2.2") to listOf(
                        Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6")
                    ),
                    Identifier("Maven:net.logstash.logback:logstash-logback-encoder:4.11") to emptyList(),
                    Identifier("Maven:org.scala-lang:scala-library:2.12.3") to emptyList(),
                    Identifier("Maven:org.scala-lang:scala-reflect:2.12.2") to listOf(
                        Identifier("Maven:com.typesafe.scala-logging:scala-logging_2.12:3.7.2")
                    ),
                    Identifier("Maven:org.scala-lang.modules:scala-java8-compat_2.12:0.8.0") to listOf(
                        Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6"),
                        Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6")
                    ),
                    Identifier("Maven:org.reactivestreams:reactive-streams:1.0.1") to listOf(
                        Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6")
                    ),
                    Identifier("Maven:org.slf4j:jcl-over-slf4j:1.7.25") to emptyList(),
                    Identifier("Maven:org.slf4j:slf4j-api:1.7.25") to listOf(
                        Identifier("Maven:ch.qos.logback:logback-classic:1.2.3")
                    ),
                )
            }

            "find the shortest paths to each dependency in a scope" {
                // This test case comes from ScopeTest initially. It uses a specific dependency tree to test
                // various corner cases.
                val scope = Scope(
                    name = "test",
                    dependencies = sortedSetOf(
                        pkg("A"),
                        pkg("B") {
                            pkg("A")
                        },
                        pkg("C") {
                            pkg("B") {
                                pkg("A") {
                                    pkg("H")
                                    pkg("I") {
                                        pkg("H")
                                    }
                                }
                            }
                            pkg("D") {
                                pkg("E")
                            }
                        },
                        pkg("F") {
                            pkg("E") {
                                pkg("I")
                            }
                        },
                        pkg("G") {
                            pkg("E")
                        }
                    )
                )

                val project = Project.EMPTY.copy(scopeDependencies = sortedSetOf(scope))
                val paths = navigator.getShortestPaths(project)[scope.name]!!

                paths should org.ossreviewtoolkit.utils.test.containExactly(
                    Identifier("A") to emptyList(),
                    Identifier("B") to emptyList(),
                    Identifier("C") to emptyList(),
                    Identifier("D") to listOf(Identifier("C")),
                    Identifier("E") to listOf(Identifier("F")),
                    Identifier("F") to emptyList(),
                    Identifier("G") to emptyList(),
                    Identifier("H") to listOf(Identifier("C"), Identifier("B"), Identifier("A")),
                    Identifier("I") to listOf(Identifier("F"), Identifier("E"))
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

        "dependencyTreeDepth" should {
            "calculate the dependency tree depth for a project" {
                navigator.dependencyTreeDepth(testProject, "compile") shouldBe 3
                navigator.dependencyTreeDepth(testProject, "test") shouldBe 2
            }

            "return 0 if the scope cannot be resolved" {
                navigator.dependencyTreeDepth(testProject, "unknownScope") shouldBe 0
            }

            "return 0 if the scope does not contain any package" {
                val scope = Scope(name = "test", dependencies = sortedSetOf())
                val project = Project.EMPTY.copy(scopeDependencies = sortedSetOf(scope))

                navigator.dependencyTreeDepth(project, scope.name) shouldBe 0
            }

            "return 1 if the scope contains only direct dependencies" {
                val scope = Scope(
                    name = "test",
                    dependencies = sortedSetOf(
                        PackageReference(id = Identifier("a")),
                        PackageReference(id = Identifier("b"))
                    )
                )
                val project = Project.EMPTY.copy(scopeDependencies = sortedSetOf(scope))

                navigator.dependencyTreeDepth(project, scope.name) shouldBe 1
            }

            "return 2 if the scope contains a tree of height 2" {
                val scope = Scope(
                    name = "test",
                    dependencies = sortedSetOf(
                        pkg("a") {
                            pkg("a1")
                        }
                    )
                )
                val project = Project.EMPTY.copy(scopeDependencies = sortedSetOf(scope))

                navigator.dependencyTreeDepth(project, scope.name) shouldBe 2
            }

            "return 3 if it contains a tree of height 3" {
                val scope = Scope(
                    name = "test",
                    dependencies = sortedSetOf(
                        pkg("a") {
                            pkg("a1") {
                                pkg("a11")
                                pkg("a12")
                            }
                        },
                        pkg("b")
                    )
                )
                val project = Project.EMPTY.copy(scopeDependencies = sortedSetOf(scope))

                navigator.dependencyTreeDepth(project, scope.name) shouldBe 3
            }
        }
    }
}

/** Name of a file with a more complex ORT result that is used by multiple test cases. */
private const val RESULT_FILE =
    "../analyzer/src/funTest/assets/projects/external/sbt-multi-project-example-expected-output.yml"

/** Identifier of the project used by the tests. */
private val PROJECT_ID = Identifier("SBT:com.pbassiner:common_2.12:0.1-SNAPSHOT")

private class PackageRefBuilder(id: String) {
    private val id = Identifier(id)
    private val dependencies = sortedSetOf<PackageReference>()

    fun pkg(id: String, block: PackageRefBuilder.() -> Unit = {}) {
        dependencies += PackageRefBuilder(id).apply { block() }.build()
    }

    fun build(): PackageReference = PackageReference(id = id, dependencies = dependencies)
}

private fun pkg(id: String, block: PackageRefBuilder.() -> Unit = {}): PackageReference =
    PackageRefBuilder(id).apply { block() }.build()
