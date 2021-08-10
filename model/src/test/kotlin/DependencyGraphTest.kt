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
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.util.SortedSet

class DependencyGraphTest : WordSpec({
    "createScopes()" should {
        "construct a simple tree with scopes" {
            val ids = listOf(
                id("org.apache.commons", "commons-lang3", "3.11"),
                id("org.apache.commons", "commons-collections4", "4.4"),
                id(group = "org.junit", artifact = "junit", version = "5")
            )
            val fragments =
                sortedSetOf(
                    DependencyReference(0),
                    DependencyReference(1),
                    DependencyReference(2)
                )
            val scopeMap = mapOf(
                "p1:scope1" to listOf(RootDependencyIndex(0), RootDependencyIndex(1)),
                "p2:scope2" to listOf(RootDependencyIndex(1), RootDependencyIndex(2))
            )

            val graph = DependencyGraph(ids, fragments, scopeMap)
            val scopes = graph.createScopes()

            scopes.map { it.name } should containExactly("p1:scope1", "p2:scope2")
            scopeDependencies(scopes, "p1:scope1") shouldBe "${ids[1]}${ids[0]}"
            scopeDependencies(scopes, "p2:scope2") shouldBe "${ids[1]}${ids[2]}"
        }

        "support restricting the scopes to a specific set" {
            val scopeName = "theOneAndOnlyScope"
            val qualifier = Identifier("test", "namespace", "name", "version")
            val qualifiedScopeName = DependencyGraph.qualifyScope(qualifier, scopeName)
            val ids = listOf(
                id("org.apache.commons", "commons-lang3", "3.11"),
                id("org.apache.commons", "commons-collections4", "4.4"),
                id("org.junit", "junit", "5")
            )
            val fragments =
                sortedSetOf(
                    DependencyReference(0),
                    DependencyReference(1),
                    DependencyReference(2)
                )
            val scopeMap = mapOf(
                qualifiedScopeName to listOf(RootDependencyIndex(0), RootDependencyIndex(1)),
                DependencyGraph.qualifyScope(qualifier, "scope2") to listOf(
                    RootDependencyIndex(1),
                    RootDependencyIndex(2)
                )
            )

            val graph = DependencyGraph(ids, fragments, scopeMap)
            val scopes = graph.createScopes(setOf(qualifiedScopeName))

            scopes.map { it.name } should containExactly(DependencyGraph.unqualifyScope(scopeName))
            scopeDependencies(scopes, scopeName) shouldBe "${ids[1]}${ids[0]}"
        }

        "construct a tree with multiple levels" {
            val ids = listOf(
                id("org.apache.commons", "commons-lang3", "3.11"),
                id("org.apache.commons", "commons-collections4", "4.4"),
                id("org.apache.commons", "commons-configuration2", "2.8"),
                id("org.apache.commons", "commons-csv", "1.5")
            )
            val refLang = DependencyReference(0)
            val refCollections = DependencyReference(1)
            val refConfig = DependencyReference(2, dependencies = sortedSetOf(refLang, refCollections))
            val refCsv = DependencyReference(3, dependencies = sortedSetOf(refConfig))
            val fragments = sortedSetOf(DependencyGraph.DEPENDENCY_REFERENCE_COMPARATOR, refCsv)
            val scopeMap = mapOf("s" to listOf(RootDependencyIndex(3)))
            val graph = DependencyGraph(ids, fragments, scopeMap)
            val scopes = graph.createScopes()

            scopeDependencies(scopes, "s") shouldBe "${ids[3]}<${ids[2]}<${ids[1]}${ids[0]}>>"
        }

        "construct scopes from different fragments" {
            val ids = listOf(
                id("org.apache.commons", "commons-lang3", "3.11"),
                id("org.apache.commons", "commons-collections4", "4.4"),
                id("org.apache.commons", "commons-configuration2", "2.8"),
                id("org.apache.commons", "commons-logging", "1.3")
            )
            val refLogging = DependencyReference(3)
            val refLang = DependencyReference(0)
            val refCollections1 = DependencyReference(1)
            val refCollections2 = DependencyReference(1, fragment = 1, dependencies = sortedSetOf(refLogging))
            val refConfig1 = DependencyReference(2, dependencies = sortedSetOf(refLang, refCollections1))
            val refConfig2 =
                DependencyReference(2, fragment = 1, dependencies = sortedSetOf(refLang, refCollections2))
            val fragments = sortedSetOf(refConfig1, refConfig2)
            val scopeMap = mapOf(
                "s1" to listOf(RootDependencyIndex(2)),
                "s2" to listOf(RootDependencyIndex(2, fragment = 1))
            )

            val graph = DependencyGraph(ids, fragments, scopeMap)
            val scopes = graph.createScopes()

            scopeDependencies(scopes, "s1") shouldBe "${ids[2]}<${ids[1]}${ids[0]}>"
            scopeDependencies(scopes, "s2") shouldBe "${ids[2]}<${ids[1]}<${ids[3]}>${ids[0]}>"
        }

        "construct scopes from graph nodes and edges" {
            val ids = listOf(
                id("org.apache.commons", "commons-lang3", "3.11"),
                id("org.apache.commons", "commons-collections4", "4.4"),
                id("org.apache.commons", "commons-configuration2", "2.8"),
                id("org.apache.commons", "commons-logging", "1.3")
            )
            val nodeLogging = DependencyGraphNode(3)
            val nodeLang = DependencyGraphNode(0)
            val nodeCollections1 = DependencyGraphNode(1)
            val nodeCollections2 = DependencyGraphNode(1, fragment = 1)
            val nodeConfig1 = DependencyGraphNode(2)
            val nodeConfig2 = DependencyGraphNode(2, fragment = 1)
            val nodes = listOf(nodeLogging, nodeLang, nodeCollections1, nodeCollections2, nodeConfig1, nodeConfig2)
            val edges = listOf(
                DependencyGraphEdge(3, 0),
                DependencyGraphEdge(4, 1),
                DependencyGraphEdge(4, 2),
                DependencyGraphEdge(5, 1),
                DependencyGraphEdge(5, 3)
            )
            val scopeMap = mapOf(
                "s1" to listOf(RootDependencyIndex(2)),
                "s2" to listOf(RootDependencyIndex(2, fragment = 1))
            )

            val graph = DependencyGraph(ids, sortedSetOf(), scopeMap, nodes, edges)
            val scopes = graph.createScopes()

            scopeDependencies(scopes, "s1") shouldBe "${ids[2]}<${ids[1]}${ids[0]}>"
            scopeDependencies(scopes, "s2") shouldBe "${ids[2]}<${ids[1]}<${ids[3]}>${ids[0]}>"
        }

        "deal with attributes of package references" {
            val ids = listOf(
                id("org.apache.commons", "commons-lang3", "3.10"),
                id("org.apache.commons", "commons-collections4", "4.4")
            )
            val issue = OrtIssue(source = "analyzer", message = "Could not analyze :-(")
            val refLang = DependencyReference(0, linkage = PackageLinkage.PROJECT_DYNAMIC)
            val refCol = DependencyReference(1, issues = listOf(issue), dependencies = sortedSetOf(refLang))
            val trees = sortedSetOf(refCol)
            val scopeMap = mapOf("s" to listOf(RootDependencyIndex(1)))

            val graph = DependencyGraph(ids, trees, scopeMap)
            val scopes = graph.createScopes()
            val scope = scopes.first()

            scope.shouldNotBeNull()
            scope.dependencies shouldHaveSize 1
            val pkgRefCol = scope.dependencies.first()
            pkgRefCol.issues should containExactly(issue)
            pkgRefCol.dependencies shouldHaveSize 1

            val pkgRefLang = pkgRefCol.dependencies.first()
            pkgRefLang.linkage shouldBe PackageLinkage.PROJECT_DYNAMIC
        }
    }

    "qualifyScope" should {
        "qualify a scope name with a project identifier" {
            val scopeName = "compile"
            val projectId = Identifier("Maven", "namespace", "name", "version")
            val project = Project(
                id = projectId,
                definitionFilePath = "/some/path/pom.xml",
                declaredLicenses = sortedSetOf(),
                homepageUrl = "https://project.example.org",
                vcs = VcsInfo.EMPTY
            )

            val qualifiedScopeName = DependencyGraph.qualifyScope(project, scopeName)

            qualifiedScopeName shouldBe "namespace:name:version:$scopeName"
        }
    }

    "unqualifyScope" should {
        "remove the project prefix from a qualified scope name" {
            val qualifiedScopeName = "namespace:name:version:scope"

            DependencyGraph.unqualifyScope(qualifiedScopeName) shouldBe "scope"
        }

        "handle a scope name that is not qualified" {
            val unqualifiedScopeName = "justAScope"

            DependencyGraph.unqualifyScope(unqualifiedScopeName) shouldBe unqualifiedScopeName
        }

        "handle a scope name that contains a colon" {
            val qualifiedScopeName = "namespace:name:version:scope:with:colons"

            DependencyGraph.unqualifyScope(qualifiedScopeName) shouldBe "scope:with:colons"
        }
    }
})

/** The name of the dependency manager used by tests. */
private const val MANAGER_NAME = "TestManager"

/**
 * Create an identifier string with the given [group], [artifact] ID and [version].
 */
private fun id(group: String, artifact: String, version: String): Identifier =
    Identifier("$MANAGER_NAME:$group:$artifact:$version")

/**
 * Output the dependency tree of the given scope as a string.
 */
private fun scopeDependencies(scopes: SortedSet<Scope>, name: String): String = buildString {
    scopes.find { it.name == name }?.let { scope ->
        scope.dependencies.forEach { dumpDependencies(it) }
    }
}

/**
 * Transform a dependency tree structure starting at [ref] to a string.
 */
private fun StringBuilder.dumpDependencies(ref: PackageReference) {
    append(ref.id)
    if (ref.dependencies.isNotEmpty()) {
        append('<')
        ref.dependencies.forEach { dumpDependencies(it) }
        append('>')
    }
}
