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

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf

import io.mockk.every
import io.mockk.mockk

import java.util.SortedSet

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.utils.ort.Environment

class CompatibilityDependencyNavigatorTest : WordSpec() {
    private val treeProject = createProject("tree", scopes = sortedSetOf(createScope("scope")))
    private val graphProject = createProject("graph", scopeNames = sortedSetOf("scope"))

    init {
        "create" should {
            "return a DependencyTreeNavigator if all projects use the tree format" {
                val project1 = createProject("test1", scopes = sortedSetOf(createScope("scope1")))
                val project2 = createProject("test2", scopes = sortedSetOf(createScope("scope2")))
                val result = createOrtResult(project1, project2)

                val navigator = CompatibilityDependencyNavigator.create(result)

                navigator shouldBe DependencyTreeNavigator
            }

            "return a DependencyGraphNavigator if all projects use the graph format" {
                val project1 = createProject("test1", scopeNames = sortedSetOf("scope1"))
                val project2 = createProject("test2", scopeNames = sortedSetOf("scope2", "scope3"))
                val result = createOrtResult(project1, project2)

                val navigator = CompatibilityDependencyNavigator.create(result)

                navigator shouldBe instanceOf<DependencyGraphNavigator>()
            }

            "return a CompatibilityGraphNavigator if mixed representations are used" {
                val project1 = createProject("test1", scopes = sortedSetOf(createScope("scope1")))
                val project2 = createProject("test2", scopeNames = sortedSetOf("scope2", "scope3"))
                val result = createOrtResult(project1, project2)

                when (val navigator = CompatibilityDependencyNavigator.create(result)) {
                    is CompatibilityDependencyNavigator -> {
                        navigator.treeNavigator shouldBe DependencyTreeNavigator
                        navigator.graphNavigator shouldBe instanceOf<DependencyGraphNavigator>()
                    }

                    else -> fail("Unexpected dependency navigator: $navigator.")
                }
            }

            "return a DependencyTreeNavigator for an empty result" {
                val navigator = CompatibilityDependencyNavigator.create(OrtResult.EMPTY)

                navigator shouldBe DependencyTreeNavigator
            }

            "return a DependencyTreeNavigator for a result that does not contain any dependency graphs" {
                val project = createProject("dummy", scopeNames = sortedSetOf())

                val analyzerResult = AnalyzerResult(
                    projects = sortedSetOf(project),
                    packages = sortedSetOf(),
                    dependencyGraphs = mapOf()
                )
                val analyzerRun = AnalyzerRun(
                    result = analyzerResult,
                    environment = Environment(),
                    config = AnalyzerConfiguration()
                )
                val result = OrtResult.EMPTY.copy(analyzer = analyzerRun)

                val navigator = CompatibilityDependencyNavigator.create(result)

                navigator shouldBe DependencyTreeNavigator
            }
        }

        "scopeNames" should {
            "return the scope names of a dependency tree project" {
                val scopeNames = setOf("the", "set", "of", "scope", "names")
                val treeNavigator = mockk<DependencyNavigator>()
                val graphNavigator = mockk<DependencyNavigator>()
                every { treeNavigator.scopeNames(treeProject) } returns scopeNames

                val navigator = CompatibilityDependencyNavigator(graphNavigator, treeNavigator)

                navigator.scopeNames(treeProject) shouldBe scopeNames
            }

            "return the scope names of a dependency graph project" {
                val scopeNames = setOf("scopes", "in", "the", "dependency", "graph")
                val graphNavigator = mockk<DependencyNavigator>()
                val treeNavigator = mockk<DependencyNavigator>()
                every { graphNavigator.scopeNames(graphProject) } returns scopeNames

                val navigator = CompatibilityDependencyNavigator(graphNavigator, treeNavigator)

                navigator.scopeNames(graphProject) shouldBe scopeNames
            }
        }

        "directDependencies" should {
            "return the dependencies of a dependency tree project" {
                val scopeName = "someScope"
                val dependencies = sequenceOf(mockk<DependencyNode>(), mockk())
                val treeNavigator = mockk<DependencyNavigator>()
                val graphNavigator = mockk<DependencyNavigator>()
                every { treeNavigator.directDependencies(treeProject, scopeName) } returns dependencies

                val navigator = CompatibilityDependencyNavigator(graphNavigator, treeNavigator)

                navigator.directDependencies(treeProject, scopeName) shouldBe dependencies
            }

            "return the dependencies of a dependency graph project" {
                val scopeName = "someScope"
                val dependencies = sequenceOf(mockk<DependencyNode>(), mockk())
                val graphNavigator = mockk<DependencyNavigator>()
                val treeNavigator = mockk<DependencyNavigator>()
                every { graphNavigator.directDependencies(graphProject, scopeName) } returns dependencies

                val navigator = CompatibilityDependencyNavigator(graphNavigator, treeNavigator)

                navigator.directDependencies(graphProject, scopeName) shouldBe dependencies
            }
        }

        "dependenciesForScope" should {
            "return the dependencies of a dependency tree project" {
                val scopeName = "testScope"
                val maxDepth = 42
                val matcher = mockk<DependencyMatcher>()
                val dependencies = setOf(Identifier.EMPTY.copy(name = "id1"), Identifier.EMPTY.copy(name = "id2"))
                val treeNavigator = mockk<DependencyNavigator>()
                val graphNavigator = mockk<DependencyNavigator>()
                every {
                    treeNavigator.dependenciesForScope(
                        treeProject,
                        scopeName,
                        maxDepth,
                        matcher
                    )
                } returns dependencies

                val navigator = CompatibilityDependencyNavigator(graphNavigator, treeNavigator)

                navigator.dependenciesForScope(treeProject, scopeName, maxDepth, matcher) shouldBe dependencies
            }

            "return the dependencies of a dependency graph project" {
                val scopeName = "testScope"
                val maxDepth = 42
                val matcher = mockk<DependencyMatcher>()
                val dependencies = setOf(Identifier.EMPTY.copy(name = "id1"), Identifier.EMPTY.copy(name = "id2"))
                val treeNavigator = mockk<DependencyNavigator>()
                val graphNavigator = mockk<DependencyNavigator>()
                every {
                    graphNavigator.dependenciesForScope(
                        graphProject,
                        scopeName,
                        maxDepth,
                        matcher
                    )
                } returns dependencies

                val navigator = CompatibilityDependencyNavigator(graphNavigator, treeNavigator)

                navigator.dependenciesForScope(graphProject, scopeName, maxDepth, matcher) shouldBe dependencies
            }
        }

        "packageDependencies" should {
            "return the dependencies of a dependency tree project" {
                val pkgId = Identifier.EMPTY.copy(name = "testPackage")
                val maxDepth = 42
                val matcher = mockk<DependencyMatcher>()
                val packageDependencies =
                    setOf(Identifier.EMPTY.copy(name = "id1"), Identifier.EMPTY.copy(name = "id2"))
                val treeNavigator = mockk<DependencyNavigator>()
                val graphNavigator = mockk<DependencyNavigator>()
                every {
                    treeNavigator.packageDependencies(treeProject, pkgId, maxDepth, matcher)
                } returns packageDependencies

                val navigator = CompatibilityDependencyNavigator(graphNavigator, treeNavigator)

                navigator.packageDependencies(treeProject, pkgId, maxDepth, matcher) shouldBe packageDependencies
            }

            "return the dependencies of a dependency graph project" {
                val pkgId = Identifier.EMPTY.copy(name = "testPackage")
                val maxDepth = 42
                val matcher = mockk<DependencyMatcher>()
                val packageDependencies =
                    setOf(Identifier.EMPTY.copy(name = "id1"), Identifier.EMPTY.copy(name = "id2"))
                val graphNavigator = mockk<DependencyNavigator>()
                val treeNavigator = mockk<DependencyNavigator>()
                every {
                    graphNavigator.packageDependencies(graphProject, pkgId, maxDepth, matcher)
                } returns packageDependencies

                val navigator = CompatibilityDependencyNavigator(graphNavigator, treeNavigator)

                navigator.packageDependencies(graphProject, pkgId, maxDepth, matcher) shouldBe packageDependencies
            }
        }
    }
}

/**
 * Construct an [OrtResult] that contains the given [projects].
 */
private fun createOrtResult(vararg projects: Project): OrtResult {
    val graph = DependencyGraph(packages = emptyList(), scopeRoots = sortedSetOf(), scopes = emptyMap())

    val analyzerResult = AnalyzerResult(
        projects = sortedSetOf(*projects),
        packages = sortedSetOf(),
        dependencyGraphs = mapOf("test" to graph)
    )

    val analyzerRun = AnalyzerRun(
        result = analyzerResult,
        environment = Environment(),
        config = AnalyzerConfiguration()
    )

    return OrtResult.EMPTY.copy(analyzer = analyzerRun)
}

/**
 * Create a [Project] with an identifier derived from the given [name] that has the dependency information passed as
 * [scopes] or [scopeNames].
 */
private fun createProject(
    name: String,
    scopes: SortedSet<Scope>? = null,
    scopeNames: SortedSet<String>? = null
): Project {
    val id = Identifier.EMPTY.copy(name = name)
    return Project.EMPTY.copy(id = id, scopeDependencies = scopes, scopeNames = scopeNames)
}

/**
 * Create a [Scope] with the given [name] and a synthetic dependency.
 */
private fun createScope(name: String): Scope {
    val dependencies = sortedSetOf(PackageReference(Identifier.EMPTY.copy(name = "dep$name")))
    return Scope(name, dependencies)
}
