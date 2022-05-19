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

package org.ossreviewtoolkit.model.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.util.SortedSet

import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.DependencyGraphEdge
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.RootDependencyIndex
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class DependencyGraphBuilderTest : WordSpec({
    "DependencyGraphBuilder" should {
        "collect the direct dependencies of scopes" {
            val scope1 = "compile"
            val scope2 = "test"
            val dep1 = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val dep2 = createDependency("org.apache.commons", "commons-collections4", "4.4")
            val dep3 = createDependency("my-project", "my-module", "1.0")

            val graph = createGraphBuilder()
                .addDependency(scope1, dep1)
                .addDependency(scope1, dep3)
                .addDependency(scope2, dep2)
                .addDependency(scope2, dep1)
                .build()

            graph.scopeRoots should beEmpty()
            graph.nodes shouldNotBeNull {
                this shouldHaveSize 3
            }

            graph.edges shouldNotBeNull {
                this should beEmpty()
            }

            val scopes = graph.createScopes()

            scopes.map { it.name } should containExactlyInAnyOrder(scope1, scope2)
            scopeDependencies(scopes, scope1) shouldBe setOf(dep1, dep3)
            scopeDependencies(scopes, scope2) shouldBe setOf(dep1, dep2)
        }

        "order the scopes, their dependencies, and packages" {
            val scope1 = "compile"
            val scope2 = "test"
            val dep1 = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val dep2 = createDependency("org.apache.commons", "commons-collections4", "4.4")
            val dep3 = createDependency("my-project", "my-module", "1.0")

            val graph = createGraphBuilder()
                .addDependency(scope2, dep1)
                .addDependency(scope2, dep2)
                .addDependency(scope1, dep3)
                .addDependency(scope1, dep1)
                .build()

            graph.scopes.keys shouldBe setOf("compile", "test")
            graph.scopes.getValue("compile") should containExactly(
                RootDependencyIndex(0),
                RootDependencyIndex(2)
            )

            graph.packages should containExactly(dep3.id, dep2.id, dep1.id)
        }

        "collect information about packages" {
            val dep1 = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val dep2 = createDependency("org.apache.commons", "commons-collections4", "4.4")
            val dep3 = createDependency("my-project", "my-module", "1.0")

            val packageIds = createGraphBuilder()
                .addDependency("s1", dep1)
                .addDependency("s2", dep2)
                .addDependency("s3", dep3)
                .packages()
                .map { it.id }

            packageIds should containExactlyInAnyOrder(dep1.id, dep2.id, dep3.id)
        }

        "deal with transitive dependencies correctly" {
            val scope1 = "compile"
            val scope2 = "test"
            val dep1 = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val dep2 = createDependency("org.apache.commons", "commons-collections4", "4.4")
            val dep3 = createDependency(
                "org.apache.commons",
                "commons-configuration2",
                "2.7",
                dependencies = listOf(dep1, dep2)
            )
            val dep4 = createDependency("org.apache.commons", "commons-csv", "1.5", dependencies = listOf(dep1))
            val dep5 = createDependency("com.acme", "dep", "0.7", dependencies = listOf(dep3))

            val graph = createGraphBuilder()
                .addDependency(scope1, dep1)
                .addDependency(scope2, dep1)
                .addDependency(scope2, dep2)
                .addDependency(scope1, dep5)
                .addDependency(scope1, dep3)
                .addDependency(scope2, dep4)
                .build()
            val scopes = graph.createScopes()

            graph.nodes shouldNotBeNull {
                this shouldHaveSize 5
                all { it.fragment == 0 } shouldBe true
            }

            scopeDependencies(scopes, scope1) shouldBe setOf(dep1, dep3, dep5)
            scopeDependencies(scopes, scope2) shouldBe setOf(dep1, dep2, dep4)
        }

        "deal with packages that have different dependencies in the same scope" {
            val scope = "TheScope"
            val depLang = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val depLog = createDependency("commons-logging", "commons-logging", "1.2")
            val depConfig1 = createDependency(
                "org.apache.commons", "commons-configuration2", "2.7",
                dependencies = listOf(depLog, depLang)
            )
            val depConfig2 = createDependency(
                "org.apache.commons", "commons-configuration2", "2.7",
                dependencies = listOf(depLang)
            )
            val depAcme = createDependency(
                "com.acme", "lib-full", "1.0",
                dependencies = listOf(depConfig1, depLang)
            )
            val depAcmeExclude = createDependency(
                "com.acme", "lib-exclude", "1.1",
                dependencies = listOf(depConfig2)
            )
            val depLib = createDependency("com.business", "lib", "1", dependencies = listOf(depConfig1, depAcmeExclude))

            val graph = createGraphBuilder()
                .addDependency(scope, depAcme)
                .addDependency(scope, depLib)
                .build()

            scopeDependencies(graph.createScopes(), scope) shouldBe setOf(depAcme, depLib)
        }

        "deal with packages that have different dependencies in different scopes" {
            val scope1 = "OneScope"
            val scope2 = "AnotherScope"
            val depLang = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val depLog = createDependency("commons-logging", "commons-logging", "1.2")
            val depConfig1 = createDependency(
                "org.apache.commons", "commons-configuration2", "2.7",
                dependencies = listOf(depLog, depLang)
            )
            val depConfig2 = createDependency(
                "org.apache.commons", "commons-configuration2", "2.7",
                dependencies = listOf(depLang)
            )
            val depAcmeExclude = createDependency(
                "com.acme", "lib-exclude", "1.1",
                dependencies = listOf(depConfig2)
            )

            val graph = createGraphBuilder()
                .addDependency(scope1, depLog)
                .addDependency(scope1, depConfig1)
                .addDependency(scope2, depAcmeExclude)
                .build()
            val scopes = graph.createScopes()

            scopeDependencies(scopes, scope1) shouldBe setOf(depConfig1, depLog)
            scopeDependencies(scopes, scope2) shouldBe setOf(depAcmeExclude)
        }

        "deal with cycles in dependencies" {
            val scope = "CyclicScope"
            val depCyc1 = createDependency("org.cyclic", "cyclic", "77.7")
            val depFoo = createDependency("org.foo", "foo", "1.2.0", dependencies = listOf(depCyc1))
            val depCyc2 = createDependency("org.cyclic", "cyclic", "77.7", dependencies = listOf(depFoo))

            val graph = createGraphBuilder()
                .addDependency(scope, depCyc2)
                .build()
            val scopes = graph.createScopes()

            scopeDependencies(scopes, scope) shouldContainExactly listOf(depCyc2)

            graph.nodes.shouldNotBeNull {
                this shouldHaveSize 3
            }
        }

        "check for illegal references when building the graph" {
            val depLang = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val depNoPkg = createDependency(NO_PACKAGE_NAMESPACE, "invalid", "1.2")
            val depLog = createDependency("commons-logging", "commons-logging", "1.2", dependencies = listOf(depNoPkg))

            val exception = shouldThrow<IllegalArgumentException> {
                createGraphBuilder()
                    .addDependency("someScope", depLang)
                    .addDependency("someScope", depLog)
                    .build()
            }

            exception.message shouldContain depNoPkg.id.toString()
        }

        "take issues into account when checking for illegal references" {
            val depLang = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val depIssuesPkg = createDependency(NO_PACKAGE_NAMESPACE, "errors", "1.2").copy(
                issues = listOf(OrtIssue(source = "test", message = "test issue"))
            )
            val depLog =
                createDependency("commons-logging", "commons-logging", "1.2", dependencies = listOf(depIssuesPkg))

            createGraphBuilder()
                .addDependency("s", depLang)
                .addDependency("s", depLog)
                .build()
        }

        "take the linkage into account when checking for illegal references" {
            val depLang = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val depProjectStaticPkg = createDependency(NO_PACKAGE_NAMESPACE, "static", "1.2").copy(
                linkage = PackageLinkage.PROJECT_STATIC
            )
            val depProjectDynamicPkg = createDependency(NO_PACKAGE_NAMESPACE, "dynamic", "1.2").copy(
                linkage = PackageLinkage.PROJECT_DYNAMIC
            )
            val depLog = createDependency(
                "commons-logging",
                "commons-logging",
                "1.2",
                dependencies = listOf(depProjectDynamicPkg, depProjectStaticPkg)
            )

            createGraphBuilder()
                .addDependency("s", depLang)
                .addDependency("s", depLog)
                .build()
        }

        "allow disabling the check for illegal references" {
            val depLang = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val depNoPkg = createDependency(NO_PACKAGE_NAMESPACE, "invalid", "1.2")
            val depLog = createDependency("commons-logging", "commons-logging", "1.2", dependencies = listOf(depNoPkg))

            createGraphBuilder()
                .addDependency("s", depLang)
                .addDependency("s", depLog)
                .build(checkReferences = false)
        }

        "collect information about scopes of projects" {
            val projectId = Identifier("test", "test", "project1", "1.0")
            val scope1 = DependencyGraph.qualifyScope(projectId, "test")
            val scope2 = DependencyGraph.qualifyScope(projectId, "compile")
            val builder = createGraphBuilder()

            builder.addDependency(scope1, createDependency("org.apache.commons", "commons-lang3", "3.11"))
                .addDependency(scope2, createDependency("org.apache.commons", "commons-collections4", "4.4"))
                .addDependency(scope1, createDependency("g1", "a1", "1"))
                .addDependency("anotherScope", createDependency("g2", "a2", "2"))

            builder.scopesFor(projectId) shouldBe sortedSetOf("compile", "test")
        }

        "collect information about qualified scopes of projects" {
            val projectId = Identifier("test", "test", "project1", "1.0")
            val scope1 = DependencyGraph.qualifyScope(projectId, "test")
            val scope2 = DependencyGraph.qualifyScope(projectId, "compile")
            val builder = createGraphBuilder()

            builder.addDependency(scope1, createDependency("org.apache.commons", "commons-lang3", "3.11"))
                .addDependency(scope2, createDependency("org.apache.commons", "commons-collections4", "4.4"))
                .addDependency(scope1, createDependency("g1", "a1", "1"))
                .addDependency("anotherScope", createDependency("g2", "a2", "2"))

            builder.scopesFor(projectId, unqualify = false) shouldBe sortedSetOf(scope2, scope1)
        }
    }

    "breakCycles()" should {
        "not break undirected cycles" {
            val edges = listOf(
                1 to 2,
                2 to 3,
                3 to 4,
                1 to 4
            ).map { DependencyGraphEdge(it.first, it.second) }

            breakCycles(edges) shouldContainExactlyInAnyOrder edges
        }

        "break a directed cycle with a single node" {
            val edges = listOf(DependencyGraphEdge(1, 1))

            breakCycles(edges) should beEmpty()
        }

        "break directed cycles involving multiple nodes" {
            val edges = listOf(
                1 to 2,
                2 to 3,
                3 to 4,
                4 to 1
            ).map { DependencyGraphEdge(it.first, it.second) }

            val result = breakCycles(edges)

            result.intersect(edges) shouldHaveSize 3
        }
    }
})

/**
 * A special namespace used by dependencies, for which [PackageRefDependencyHandler] should not create a package.
 */
private const val NO_PACKAGE_NAMESPACE = "no-package"

/**
 * A [DependencyHandler] implementation that operates on [PackageReference] objects. This is used to handle the
 * dependencies added to the builder under test.
 */
private object PackageRefDependencyHandler : DependencyHandler<PackageReference> {
    override fun identifierFor(dependency: PackageReference): Identifier = dependency.id

    override fun dependenciesFor(dependency: PackageReference): Collection<PackageReference> =
        dependency.dependencies

    override fun linkageFor(dependency: PackageReference): PackageLinkage = dependency.linkage

    override fun createPackage(dependency: PackageReference, issues: MutableList<OrtIssue>): Package? =
        Package.EMPTY.copy(id = dependency.id).takeUnless { dependency.id.namespace == NO_PACKAGE_NAMESPACE }

    override fun issuesForDependency(dependency: PackageReference): Collection<OrtIssue> =
        dependency.issues
}

/**
 * Create a [DependencyGraphBuilder] equipped with a [PackageRefDependencyHandler] that is used by the test cases in
 * this class.
 */
private fun createGraphBuilder(): DependencyGraphBuilder<PackageReference> =
    DependencyGraphBuilder(PackageRefDependencyHandler)

/**
 * Create a test dependency with the properties provided.
 */
private fun createDependency(
    group: String,
    artifact: String,
    version: String,
    dependencies: List<PackageReference> = emptyList()
): PackageReference {
    val id = Identifier("test", group, artifact, version)
    return PackageReference(id, dependencies = dependencies.toSortedSet())
}

/**
 * Return the package references from the given [scopes] associated with the scope with the given [scopeName].
 */
private fun scopeDependencies(
    scopes: SortedSet<Scope>,
    scopeName: String
): Set<PackageReference> =
    scopes.find { it.name == scopeName }?.dependencies.orEmpty()
