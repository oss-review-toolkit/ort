/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beTheSameInstanceAs

import io.mockk.mockk

import java.io.File

private fun readAnalyzerResult(analyzerResultFilename: String): Project =
    File("../analyzer/src/funTest/assets/projects/synthetic")
        .resolve(analyzerResultFilename)
        .readValue<ProjectAnalyzerResult>().project

private const val MANAGER = "MyManager"

private val projectId = Identifier("$MANAGER:my.example.org:my-project:1.0.0")
private val exampleId = Identifier("$MANAGER:org.ossreviewtoolkit.gradle.example:lib:1.0.0")
private val textId = Identifier("$MANAGER:org.apache.commons:commons-text:1.1")
private val langId = Identifier("$MANAGER:org.apache.commons:commons-lang3:3.5")
private val strutsId = Identifier("$MANAGER:org.apache.struts:struts2-assembly:2.5.14.1")
private val csvId = Identifier("$MANAGER:org.apache.commons:commons-csv:1.4")

/**
 * Create a [DependencyGraph] containing some test dependencies, optionally with [qualified] scope names.
 */
private fun createDependencyGraph(qualified: Boolean = false): DependencyGraph {
    val dependencies = listOf(
        langId,
        textId,
        strutsId,
        csvId,
        exampleId
    )
    val langRef = DependencyReference(0)
    val textRef = DependencyReference(1, dependencies = sortedSetOf(langRef))
    val strutsRef = DependencyReference(2)
    val csvRef = DependencyReference(3, dependencies = sortedSetOf(langRef))
    val exampleRef = DependencyReference(4, dependencies = sortedSetOf(textRef, strutsRef))

    val plainScopeMapping = mapOf(
        "default" to listOf(RootDependencyIndex(4)),
        "compile" to listOf(RootDependencyIndex(4)),
        "test" to listOf(RootDependencyIndex(4), RootDependencyIndex(3)),
        "partial" to listOf(RootDependencyIndex(1))
    )
    val scopeMapping = if (qualified) plainScopeMapping.mapKeys { DependencyGraph.qualifyScope(projectId, it.key) }
    else plainScopeMapping

    return DependencyGraph(dependencies, sortedSetOf(exampleRef, csvRef), scopeMapping)
}

class ProjectTest : WordSpec({
    "init" should {
        "fail if both scopeDependencies and scopeNames are provided" {
            shouldThrow<IllegalArgumentException> {
                Project.EMPTY.copy(
                    scopeDependencies = sortedSetOf(mockk()),
                    scopeNames = sortedSetOf("test", "compile", "other")
                )
            }
        }
    }

    "scopes" should {
        "be initialized from scope dependencies" {
            val project = readAnalyzerResult("maven-expected-output-app.yml")

            project.scopes shouldBe project.scopeDependencies
        }

        "be initialized to an empty set if no information is available" {
            val project = Project(
                id = projectId,
                definitionFilePath = "/some/path",
                declaredLicenses = sortedSetOf(),
                vcs = VcsInfo.EMPTY,
                homepageUrl = "https//www.test-project.org",
            )

            project.scopes.shouldBeEmpty()
        }
    }

    "withResolvedScopes" should {
        "return the same instance if scope dependencies are available" {
            val project = readAnalyzerResult("maven-expected-output-app.yml")

            val resolvedProject = project.withResolvedScopes(createDependencyGraph())

            resolvedProject should beTheSameInstanceAs(project)
        }

        "return an instance with scope information extracted from a sub graph of a shared dependency graph" {
            val project = Project.EMPTY.copy(
                id = projectId,
                definitionFilePath = "/some/path",
                homepageUrl = "https//www.test-project.org",
                scopeDependencies = null,
                scopeNames = sortedSetOf("partial")
            )

            val graph = createDependencyGraph(qualified = true)

            val resolvedProject = project.withResolvedScopes(graph)

            resolvedProject.scopeNames should beNull()
            resolvedProject.scopes shouldHaveSize 1
            resolvedProject.scopes.find { it.name == "partial" } ?: fail("Could not resolve scope ${"partial"}.")
        }
    }
})
