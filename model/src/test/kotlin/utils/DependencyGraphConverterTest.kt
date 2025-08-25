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

package org.ossreviewtoolkit.model.utils

import io.kotest.assertions.AssertionErrorBuilder
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.beTheSameInstanceAs

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.DependencyGraphNode
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.utils.test.readResourceValue

class DependencyGraphConverterTest : WordSpec({
    "convertToDependencyGraphs" should {
        "return the same result if no conversion is required" {
            val ortResult = readResourceValue<OrtResult>("/analyzer-result-with-dependency-graph.yml")
            val analyzerResult = ortResult.analyzer?.result

            analyzerResult shouldNotBeNull {
                val convertedResult = DependencyGraphConverter.convert(this)

                convertedResult should beTheSameInstanceAs(this)
            }
        }

        "exclude scopes" {
            val project = createProject("Maven", index = 1)

            val result = createAnalyzerResult(project.createResult())

            val scopeExclude = ScopeExclude("test", ScopeExcludeReason.TEST_DEPENDENCY_OF)
            val excludes = Excludes(scopes = listOf(scopeExclude))

            val convertedResult = DependencyGraphConverter.convert(result, excludes)

            convertedResult.projects.single().scopeNames shouldContainOnly listOf("main")
            convertedResult.packages.forAll { it.id.version.drop(2).toInt() shouldBeLessThan 110 }
        }

        "correctly exclude scopes if there are projects using a dependency graph" {
            val ortResult = readResourceValue<OrtResult>("/analyzer-result-with-dependency-graph.yml")
            val graphAnalyzerResult = ortResult.analyzer?.result!!

            val project = createProject("Go", index = 1)
            val projectAnalyzerResult = project.createResult()

            val analyzerResult = graphAnalyzerResult.copy(
                projects = graphAnalyzerResult.projects + project,
                packages = graphAnalyzerResult.packages + projectAnalyzerResult.packages
            )
            val scopeExclude = ScopeExclude("main", ScopeExcludeReason.TEST_DEPENDENCY_OF)
            val excludes = Excludes(scopes = listOf(scopeExclude))

            val convertedResult = DependencyGraphConverter.convert(analyzerResult, excludes)

            val allPackagesTypes = convertedResult.packages.mapTo(mutableSetOf()) { it.id.type }
            allPackagesTypes should containExactlyInAnyOrder("Maven", "Go")
        }

        "convert a result with a partial dependency graph" {
            val gradleProject = createProject("Gradle", index = 1)
            val goProject1 = createProject("GoMod", index = 2)
            val goProject2 = createProject("GoMod", index = 3)

            val orgResult = createAnalyzerResult(gradleProject.createResult())
            val resultWithGraph = DependencyGraphConverter.convert(orgResult)
            val mixedResult = resultWithGraph.copy(
                projects = buildSet {
                    add(goProject1)
                    add(goProject2)
                    addAll(resultWithGraph.projects)
                }
            )

            val convertedResult = DependencyGraphConverter.convert(mixedResult)

            convertedResult.dependencyGraphs.keys should containExactlyInAnyOrder("Gradle", "GoMod")
            convertedResult.getProject(gradleProject.id) should beTheSameInstanceAs(
                resultWithGraph.getProject(gradleProject.id)
            )
        }

        "take the issues of dependencies into account" {
            val mavenProject1 = createProject("Maven", index = 1)

            val result = createAnalyzerResult(mavenProject1.createResult())

            val convertedResult = DependencyGraphConverter.convert(result)

            val graph = convertedResult.dependencyGraphs.getValue("Maven")
            val issues = mutableListOf<Issue>()

            fun collectIssues(node: DependencyGraphNode) {
                issues += node.issues
                graph.dependencies[node]?.forEach(::collectIssues)
            }

            graph.nodes.forEach(::collectIssues)
            issues shouldNot beEmpty()
        }

        "not override a dependency graph for an empty project" {
            val gradleProject = createProject("Gradle", index = 1)
            val gradleEmptyProject = createProject("Gradle", index = 2).copy(scopeDependencies = emptySet())

            val orgResult = createAnalyzerResult(gradleProject.createResult())
            val resultWithGraph = DependencyGraphConverter.convert(orgResult)
            val mixedResult = resultWithGraph.copy(
                projects = buildSet {
                    add(gradleEmptyProject)
                    addAll(resultWithGraph.projects)
                }
            )

            val convertedResult = DependencyGraphConverter.convert(mixedResult)

            convertedResult.dependencyGraphs["Gradle"] shouldNotBeNull {
                nodes shouldNot beEmpty()
                scopes.keys shouldNot beEmpty()
            }
        }
    }
})

/**
 * Create a test project with a (small) dependency tree. Use [managerName] and [index] to generate identifiers.
 */
private fun createProject(managerName: String, index: Int): Project {
    val mainScope = Scope("main", createDependencies(managerName, index * 100, 4))
    val testScope = Scope("test", createDependencies(managerName, index * 100 + 10, 8))

    return Project.EMPTY.copy(
        id = createIdentifier(managerName, index, forProject = true),
        scopeDependencies = setOf(mainScope, testScope)
    )
}

/**
 * Create an identifier for a test project or package based on the given [managerName] and [index] and the
 * [forProject] flag.
 */
private fun createIdentifier(managerName: String, index: Int, forProject: Boolean): Identifier =
    Identifier(
        type = managerName,
        namespace = "test",
        name = if (forProject) "project$index" else "pkg$index",
        version = "1.$index"
    )

/**
 * Create a set of [PackageReference]s with the size of [count] simulating the dependencies of a project. Use
 * [managerName], [startIndex] to generate identifiers.
 */
private fun createDependencies(managerName: String, startIndex: Int, count: Int): Set<PackageReference> =
    (startIndex..(startIndex + count)).mapTo(mutableSetOf()) { index ->
        PackageReference(createIdentifier(managerName, index, forProject = false), issues = createIssues(index))
    }

/**
 * Create a list with issues for a test dependency based on its [index].
 */
private fun createIssues(index: Int): List<Issue> =
    emptyList<Issue>().takeIf { index % 2 == 0 } ?: listOf(
        Issue(source = "test", message = "Test issue $index.")
    )

/**
 * Construct an [AnalyzerResult] from the given sequence of [projectResults].
 */
private fun createAnalyzerResult(vararg projectResults: ProjectAnalyzerResult): AnalyzerResult {
    val projects = projectResults.mapTo(mutableSetOf()) { it.project }
    val packages = projectResults.flatMapTo(mutableSetOf()) { it.packages }

    return AnalyzerResult(projects, packages)
}

/**
 * Create a [ProjectAnalyzerResult] based on this test project.
 */
private fun Project.createResult(): ProjectAnalyzerResult {
    val packages = scopes.flatMap { it.dependencies }.mapTo(mutableSetOf()) { ref ->
        Package.EMPTY.copy(id = ref.id)
    }

    return ProjectAnalyzerResult(this, packages)
}

/**
 * Return the project with the given [id] from this result or fail miserably if it cannot be found.
 */
private fun AnalyzerResult.getProject(id: Identifier): Project {
    val project = projects.find { it.id == id }
    return project ?: AssertionErrorBuilder.fail("Could not find project with ID $id.")
}
