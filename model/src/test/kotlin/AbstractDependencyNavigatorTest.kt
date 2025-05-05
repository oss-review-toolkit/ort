/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.containExactly as containExactlyEntries
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.sequences.beEmpty as beEmptySequence
import io.kotest.matchers.sequences.containAllInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe

import java.time.Instant

import org.ossreviewtoolkit.model.DependencyNavigator.Companion.MATCH_SUB_PROJECTS
import org.ossreviewtoolkit.utils.test.readOrtResult
import org.ossreviewtoolkit.utils.test.readResourceValue

/**
 * A base class for tests of concrete [DependencyNavigator] implementations.
 *
 * The class is configured with an ORT result file that contains the expected results in a specific format. It then
 * runs tests on the [DependencyNavigator] of this result and checks whether it returns the correct dependency
 * information.
 */
abstract class AbstractDependencyNavigatorTest : WordSpec() {
    /** The name of the result file to be used by all test cases. */
    protected abstract val resultFileName: String

    /**
     * The name of the file with a result that contains issues. This is used by tests of the collectIssues() function.
     */
    protected abstract val resultWithIssuesFileName: String

    private val testResult by lazy { readOrtResult(resultFileName) }

    private val testProject by lazy { testResult.getProject(PROJECT_ID)!! }

    protected val navigator by lazy { testResult.dependencyNavigator }

    init {
        "scopeNames" should {
            "return the scope names of a project" {
                navigator.scopeNames(testProject) should containExactlyInAnyOrder("compile", "test")
            }
        }

        "directDependencies" should {
            "return the direct dependencies of a project" {
                val scopeDependencies = navigator.directDependencies(testProject, "test").map { it.id }

                scopeDependencies should containAllInAnyOrder(
                    Identifier("Maven:org.scalacheck:scalacheck_2.12:1.13.5"),
                    Identifier("Maven:org.scalatest:scalatest_2.12:3.0.4")
                )
            }

            "return an empty sequence for an unknown scope" {
                navigator.directDependencies(testProject, "unknownScope") should beEmptySequence()
            }
        }

        "projectDependencies" should {
            "return all dependencies from all scopes for a project" {
                val scopeDependencies = navigator.projectDependencies(testProject)

                scopeDependencies should haveSize(23)
                scopeDependencies should containAll(
                    Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6"),
                    Identifier("Maven:org.scala-lang:scala-reflect:2.12.2"),
                    Identifier("Maven:org.scala-lang:scala-library:2.12.15"),
                    Identifier("Maven:org.scalacheck:scalacheck_2.12:1.13.5"),
                    Identifier("Maven:org.scalactic:scalactic_2.12:3.0.4")
                )
            }

            "return direct dependencies from all scopes by using maxDepth = 1" {
                val scopeDependencies = navigator.projectDependencies(testProject, maxDepth = 1)

                scopeDependencies should haveSize(9)
                scopeDependencies shouldNotContain Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6")
            }

            "return all dependencies from all scopes up to a given maxDepth" {
                val scopeDependencies = navigator.projectDependencies(testProject, maxDepth = 2)

                scopeDependencies should haveSize(20)
                scopeDependencies shouldNotContain
                    Identifier("Maven:org.scala-lang.modules:scala-java8-compat_2.12:0.8.0")
            }

            "return dependencies from all scopes that match a filter criteria" {
                val matchedIds = mutableSetOf<Identifier>()
                val scopeDependencies = navigator.projectDependencies(testProject) { node ->
                    matchedIds += node.id
                    node.id.namespace == "com.typesafe.akka"
                }

                matchedIds should haveSize(23)
                scopeDependencies should containExactlyInAnyOrder(
                    Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6"),
                    Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6")
                )
            }

            "return the combination of scope dependencies of a project" {
                val projectDependencies = navigator.projectDependencies(testProject)
                val expectedDependencies = navigator.scopeDependencies(testProject, "compile") +
                    navigator.scopeDependencies(testProject, "test")

                projectDependencies should containExactlyInAnyOrder(expectedDependencies)
            }

            "support filtering the direct dependencies of a project" {
                val dependencies = navigator.projectDependencies(testProject, maxDepth = 1) {
                    it.linkage != PackageLinkage.PROJECT_DYNAMIC
                }

                dependencies should containExactlyInAnyOrder(
                    Identifier("Maven:ch.qos.logback:logback-classic:1.2.3"),
                    Identifier("Maven:com.typesafe:config:1.3.1"),
                    Identifier("Maven:com.typesafe.akka:akka-stream_2.12:2.5.6"),
                    Identifier("Maven:com.typesafe.scala-logging:scala-logging_2.12:3.7.2"),
                    Identifier("Maven:net.logstash.logback:logstash-logback-encoder:4.11"),
                    Identifier("Maven:org.scala-lang:scala-library:2.12.15"),
                    Identifier("Maven:org.slf4j:jcl-over-slf4j:1.7.25"),
                    Identifier("Maven:org.scalacheck:scalacheck_2.12:1.13.5"),
                    Identifier("Maven:org.scalatest:scalatest_2.12:3.0.4")
                )
            }

            "return no dependencies for a maxDepth of 0" {
                val dependencies = navigator.projectDependencies(testProject, 0)

                dependencies should beEmpty()
            }
        }

        "scopeDependencies" should {
            "return an empty set for an unknown scope" {
                navigator.scopeDependencies(testProject, "unknownScope") should beEmpty()
            }

            "return the dependencies of a specific scope" {
                val compileDependencies = navigator.scopeDependencies(testProject, "compile")

                compileDependencies should haveSize(17)
                compileDependencies should containAll(
                    Identifier("Maven:com.typesafe.akka:akka-actor_2.12:2.5.6"),
                    Identifier("Maven:org.scala-lang:scala-reflect:2.12.2"),
                    Identifier("Maven:org.scala-lang:scala-library:2.12.15")
                )
            }

            "return the dependencies of a specific scope up to a given maxDepth" {
                val compileDependencies = navigator.scopeDependencies(testProject, "compile", maxDepth = 2)

                compileDependencies should haveSize(14)
                compileDependencies shouldNot contain(
                    Identifier("Maven:org.scala-lang.modules:scala-java8-compat_2.12:0.8.0")
                )
            }

            "return the dependencies of a specific scope with filter criteria" {
                val akkaDependencies = navigator.scopeDependencies(testProject, "compile") { node ->
                    "akka" in node.id.namespace
                }

                akkaDependencies should containExactlyInAnyOrder(
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

            "find all the sub-projects of a project" {
                val projectId = Identifier("SBT:com.pbassiner:multi1_2.12:0.1.0-SNAPSHOT")
                testResult.getProject(projectId) shouldNotBeNull {
                    val subProjectIds = navigator.projectDependencies(this, matcher = MATCH_SUB_PROJECTS)

                    subProjectIds should containExactly(PROJECT_ID)
                }
            }
        }

        "getShortestPaths" should {
            "return the shortest paths for a project" {
                val paths = navigator.getShortestPaths(testProject)

                paths.keys should haveSize(2)

                paths["compile"] shouldNotBeNull {
                    this should containExactlyEntries(
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
                        Identifier("Maven:org.scala-lang:scala-library:2.12.15") to emptyList(),
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
                        )
                    )
                }
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
        }

        "projectIssues" should {
            "return the issues of a project" {
                val ortResultWithIssues = readResourceValue<OrtResult>(resultWithIssuesFileName)
                val navigator = ortResultWithIssues.dependencyNavigator
                val projectIdWithIssues = Identifier("SBT:com.pbassiner:common_2.12:0.1-SNAPSHOT")
                val project = ortResultWithIssues.getProject(projectIdWithIssues)

                project shouldNotBeNull {
                    val issues = navigator.projectIssues(this)

                    issues should containExactlyEntries(
                        Identifier("Maven:org.scala-lang.modules:scala-java8-compat_2.12:0.8.0") to setOf(
                            Issue(
                                Instant.EPOCH,
                                "Gradle",
                                "Test issue 1"
                            )
                        ),
                        Identifier("Maven:org.scalactic:scalactic_2.12:3.0.4") to setOf(
                            Issue(
                                Instant.EPOCH,
                                "Gradle",
                                "Test issue 2"
                            )
                        )
                    )
                }
            }
        }

        "DependencyNode.equals" should {
            "be reflexive" {
                val node = navigator.directDependencies(testProject, "compile").iterator().next()

                node shouldBe node
            }

            "return false for a different node" {
                val iterator = navigator.directDependencies(testProject, "compile").iterator()
                val node1 = iterator.next().getStableReference()
                val node2 = iterator.next()

                node1 shouldNotBe node2
            }

            "return true the same node" {
                val node1 = navigator.directDependencies(testProject, "compile").iterator().next()
                    .getStableReference()
                val node2 = navigator.directDependencies(testProject, "compile").iterator().next()
                    .getStableReference()

                node1 shouldBe node2
                node1.hashCode() shouldBe node2.hashCode()
            }

            "return false for another object" {
                val node = navigator.directDependencies(testProject, "compile").iterator().next()

                node shouldNotBe this
            }
        }
    }
}

/** Identifier of the project used by the tests. */
private val PROJECT_ID = Identifier("SBT:com.pbassiner:common_2.12:0.1.0-SNAPSHOT")
