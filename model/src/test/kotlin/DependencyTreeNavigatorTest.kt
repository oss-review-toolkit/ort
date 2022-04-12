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

import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class DependencyTreeNavigatorTest : AbstractDependencyNavigatorTest() {
    override val resultFileName: String = RESULT_FILE

    override val resultWithIssuesFileName: String = RESULT_WITH_ISSUES_FILE

    init {
        "getShortestPaths" should {
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

                paths should containExactly(
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

        "dependencyTreeDepth" should {
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

/** Name of the file with a result that contains some issues. */
private const val RESULT_WITH_ISSUES_FILE = "src/test/assets/result-with-issues-scopes.yml"

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
