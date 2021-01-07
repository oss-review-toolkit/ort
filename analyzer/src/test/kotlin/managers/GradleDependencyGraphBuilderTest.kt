/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import Dependency

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository

import org.ossreviewtoolkit.analyzer.managers.utils.MavenSupport
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo

class GradleDependencyGraphBuilderTest : WordSpec({
    "GradleDependencyGraphBuilder" should {
        "collect the direct dependencies of scopes" {
            val scope1 = "compile"
            val scope2 = "test"
            val dep1 = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val dep2 = createDependency("org.apache.commons", "commons-collections4", "4.4")
            val dep3 = createDependency("my-project", "my-module", "1.0", path = "subPath")
            val maven = createMavenSupport()
            val builder = GradleDependencyGraphBuilder(NAME, maven)

            builder.addDependency(scope1, dep1, remoteRepositories)
            builder.addDependency(scope1, dep3, remoteRepositories)
            builder.addDependency(scope2, dep2, remoteRepositories)
            builder.addDependency(scope2, dep1, remoteRepositories)
            val graph = builder.build()

            graph.dependencies shouldHaveSize 3
            val dependencyIds = graph.dependencies.map { ref -> ref.id }
            dependencyIds shouldContainExactlyInAnyOrder listOf(dep1.toId(), dep2.toId(), dep3.toId())
            graph.dependencies.forEach {
                it.dependencies.shouldBeEmpty()
            }

            graph.scopeMapping.keys shouldContainExactlyInAnyOrder setOf(scope1, scope2)
            graph.scopeMapping[scope1] shouldContainExactlyInAnyOrder setOf(dep1.toId(), dep3.toId())
            graph.scopeMapping[scope2] shouldContainExactlyInAnyOrder setOf(dep2.toId(), dep1.toId())
        }

        "collect information about packages" {
            val dep1 = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val dep2 = createDependency("org.apache.commons", "commons-collections4", "4.4")
            val dep3 = createDependency("my-project", "my-module", "1.0", path = "foo")
            val maven = createMavenSupport()
            val builder = GradleDependencyGraphBuilder(NAME, maven)

            builder.addDependency("s1", dep1, remoteRepositories)
            builder.addDependency("s2", dep2, remoteRepositories)
            builder.addDependency("s3", dep3, remoteRepositories)

            val packageIds = builder.packages().map { it.id }
            packageIds shouldContainExactlyInAnyOrder setOf(dep1.toId(), dep2.toId())
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
            val maven = createMavenSupport()
            val builder = GradleDependencyGraphBuilder(NAME, maven)

            builder.addDependency(scope1, dep1, remoteRepositories)
            builder.addDependency(scope2, dep1, remoteRepositories)
            builder.addDependency(scope2, dep2, remoteRepositories)
            builder.addDependency(scope1, dep5, remoteRepositories)
            builder.addDependency(scope1, dep3, remoteRepositories)
            builder.addDependency(scope2, dep4, remoteRepositories)
            val graph = builder.build()

            graph.dependencies shouldHaveSize 2
            val dependencyIds = graph.dependencies.map { ref -> ref.id }
            dependencyIds shouldContainExactlyInAnyOrder listOf(dep4.toId(), dep5.toId())

            val refMapping = mutableMapOf<Identifier, PackageReference>()
            graph.dependencies.forEach {
                traverse(it, refMapping)
            }

            refMapping shouldHaveSize 5
            val refConfig = refMapping[dep3.toId()] ?: fail("Could not resolve dependency.")
            refConfig.dependencies.map { it.id } shouldContainExactlyInAnyOrder setOf(dep2.toId(), dep1.toId())
            val scopeDependencies1 = scopeDependencies(graph, scope1, refMapping)
            scopeDependencies1 shouldContainExactlyInAnyOrder setOf(
                refMapping[dep1.toId()],
                refMapping[dep3.toId()], refMapping[dep5.toId()]
            )
            val scopeDependencies2 = scopeDependencies(graph, scope2, refMapping)
            scopeDependencies2 shouldContainExactlyInAnyOrder setOf(
                refMapping[dep1.toId()],
                refMapping[dep2.toId()],
                refMapping[dep4.toId()]
            )
        }

        "support scopes without dependencies" {
            val scope = "EmptyScope"
            val builder = GradleDependencyGraphBuilder(NAME, createMavenSupport())

            builder.addScope(scope)
            val graph = builder.build()

            graph.dependencies.shouldBeEmpty()
            graph.scopeMapping.keys shouldContainExactly setOf(scope)
            val scopeDependencies = graph.scopeMapping[scope]
            scopeDependencies.shouldNotBeNull()
            scopeDependencies.shouldBeEmpty()
        }

        "not override a scope's dependencies when adding it again" {
            val scope = "compile"
            val dep = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val builder = GradleDependencyGraphBuilder(NAME, createMavenSupport())

            builder.addDependency(scope, dep, remoteRepositories)
            builder.addScope(scope)
            val graph = builder.build()
            val scopeDependencies = graph.scopeMapping[scope]
            scopeDependencies.shouldNotBeNull()
            scopeDependencies shouldContainExactly setOf(dep.toId())
        }
    }
})

/** The name of the package manager. */
private const val NAME = "GradleTest"

/** Remote repositories used by the test. */
private val remoteRepositories = listOf(mockk<RemoteRepository>())

/**
 * Create a mock dependency with the properties provided.
 */
private fun createDependency(
    group: String,
    artifact: String,
    version: String,
    path: String? = null,
    dependencies: List<Dependency> = emptyList()
): Dependency {
    val dependency = mockk<Dependency>()
    every { dependency.groupId } returns group
    every { dependency.artifactId } returns artifact
    every { dependency.version } returns version
    every { dependency.localPath } returns path
    every { dependency.pomFile } returns "pom.xml"
    every { dependency.dependencies } returns dependencies
    every { dependency.warning } returns null
    every { dependency.error } returns null
    every { dependency.classifier } returns "jar"
    every { dependency.extension } returns ""
    return dependency
}

/**
 * Create a [MavenSupport] mock object which is prepared to convert arbitrary artifacts to [Package] objects.
 */
private fun createMavenSupport(): MavenSupport {
    val maven = mockk<MavenSupport>()
    val slotArtifact = slot<DefaultArtifact>()
    every { maven.parsePackage(capture(slotArtifact), remoteRepositories) } answers {
        val artifact = slotArtifact.captured
        val id = Identifier("Maven", artifact.groupId, artifact.artifactId, artifact.version)
        Package(
            id,
            declaredLicenses = sortedSetOf(),
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            homepageUrl = "www.${artifact.artifactId}.org",
            description = "Description of $artifact",
            vcs = VcsInfo.EMPTY
        )
    }

    return maven
}

/**
 * Determine the type of the [Identifier] for this dependency.
 */
private fun Dependency.type(): String =
    if (localPath != null) {
        NAME
    } else {
        "Maven"
    }

/**
 * Returns an [Identifier] for this [Dependency].
 */
private fun Dependency.toId() =
    Identifier(type(), groupId, artifactId, version)

/**
 * Construct a [mapping] of package references to their IDs starting with the given [ref].
 */
private fun traverse(ref: PackageReference, mapping: MutableMap<Identifier, PackageReference>) {
    val validRef = !mapping.containsKey(ref.id) || mapping[ref.id] === ref
    validRef shouldBe true
    mapping[ref.id] = ref
    ref.dependencies.forEach { traverse(it, mapping) }
}

/**
 * Return the package references from the given [graph] associated with the scope with the given [scopeName]
 * using the specified [refMapping].
 */
private fun scopeDependencies(
    graph: DependencyGraph,
    scopeName: String,
    refMapping: Map<Identifier, PackageReference>
): List<PackageReference> =
    graph.scopeMapping[scopeName].orEmpty().mapNotNull { refMapping[it] }
