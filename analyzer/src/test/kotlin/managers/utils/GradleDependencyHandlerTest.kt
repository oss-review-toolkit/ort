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

package org.ossreviewtoolkit.analyzer.managers.utils

import Dependency

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain
import io.kotest.matchers.types.beTheSameInstanceAs
import io.kotest.matchers.types.shouldBeSameInstanceAs

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

import java.io.IOException
import java.lang.IllegalArgumentException
import java.util.SortedSet

import org.apache.maven.project.ProjectBuildingException

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

/**
 * A test class to test the integration of the [Gradle] package manager with [DependencyGraphBuilder]. This class
 * not only tests the dependency handler implementation itself but also the logic of the
 */
class GradleDependencyHandlerTest : WordSpec({
    "DependencyGraphBuilder" should {
        "collect the direct dependencies of scopes" {
            val scope1 = "compile"
            val scope2 = "test"
            val dep1 = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val dep2 = createDependency("org.apache.commons", "commons-collections4", "4.4")
            val dep3 = createDependency("my-project", "my-module", "1.0", path = "subPath")

            val graph = createGraphBuilder()
                .addDependency(scope1, dep1)
                .addDependency(scope1, dep3)
                .addDependency(scope2, dep2)
                .addDependency(scope2, dep1)
                .build()

            graph.nodes shouldNotBeNull {
                this shouldHaveSize 3
            }

            val scopes = graph.createScopes()
            scopes.map { it.name } should containExactlyInAnyOrder(scope1, scope2)

            val scope1Dependencies = scopeDependencies(scopes, scope1)
            scope1Dependencies.all { it.dependencies.isEmpty() } shouldBe true
            scope1Dependencies.identifiers() should containExactlyInAnyOrder(dep1.toId(), dep3.toId())

            val scope2Dependencies = scopeDependencies(scopes, scope2)
            scope2Dependencies.identifiers() should containExactlyInAnyOrder(dep2.toId(), dep1.toId())
        }

        "collect a dependency of type Maven" {
            val scope = "TheScope"
            val dep = createDependency("org.apache.commons", "commons-lang3", "3.10")

            val graph = createGraphBuilder()
                .addDependency(scope, dep)
                .build()

            val scopes = graph.createScopes()

            scopeDependencies(scopes, scope).single { it.id.type == "Maven" }
        }

        "collect a dependency of type Unknown" {
            val scope = "TheScope"
            val dep = createDependency("org.apache.commons", "commons-lang3", "3.10")
            every { dep.pomFile } returns null

            val graph = createGraphBuilder()
                .addDependency(scope, dep)
                .build()

            val scopes = graph.createScopes()

            scopeDependencies(scopes, scope).single { it.id.type == "Unknown" }
        }

        "collect a project dependency" {
            val scope = "TheScope"
            val dep = createDependency("a-project", "a-module", "1.0", path = "p")

            val graph = createGraphBuilder()
                .addDependency(scope, dep)
                .build()

            val scopes = graph.createScopes()

            scopeDependencies(scopes, scope).single { it.id.type == NAME }
        }

        "collect information about packages" {
            val dep1 = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val dep2 = createDependency("org.apache.commons", "commons-collections4", "4.4")
            val dep3 = createDependency("my-project", "my-module", "1.0", path = "foo")

            val packageIds = createGraphBuilder()
                .addDependency("s1", dep1)
                .addDependency("s2", dep2)
                .addDependency("s3", dep3)
                .packages()
                .map { it.id }

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

            val graph = createGraphBuilder()
                .addDependency(scope1, dep1)
                .addDependency(scope2, dep1)
                .addDependency(scope2, dep2)
                .addDependency(scope1, dep5)
                .addDependency(scope1, dep3)
                .addDependency(scope2, dep4)
                .build()

            graph.scopeRoots should beEmpty()
            graph.nodes shouldNotBeNull {
                this shouldHaveSize 5
                all { it.fragment == 0 } shouldBe true
            }

            val scopes = graph.createScopes()
            val scopeDependencies1 = scopeDependencies(scopes, scope1)
            scopeDependencies1.identifiers() should containExactly(dep5.toId(), dep3.toId(), dep1.toId())
            val dep5Pkg = scopeDependencies1.findDependency(dep5)
            val dep3Pkg = dep5Pkg.checkDependencies(dep3).findDependency(dep3)
            dep3Pkg.checkDependencies(dep1, dep2)
            scopeDependencies1.findDependency(dep3) should beTheSameInstanceAs(dep3Pkg)

            val scopeDependencies2 = scopeDependencies(scopes, scope2)
            scopeDependencies2.identifiers() should containExactlyInAnyOrder(dep1.toId(), dep2.toId(), dep4.toId())
            dep3Pkg.dependencies.findDependency(dep2) should beTheSameInstanceAs(
                scopeDependencies2.findDependency(dep2)
            )
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

            val scopeDependencies = scopeDependencies(graph.createScopes(), scope)

            scopeDependencies.identifiers() should containExactly(depAcme.toId(), depLib.toId())
            val refLib = scopeDependencies.findDependency(depLib)
            refLib.checkDependencies(depConfig1, depAcmeExclude)
            val refConfigFull = refLib.dependencies.findDependency(depConfig1)
            refConfigFull.checkDependencies(depLang, depLog)
            val refAcmeExclude = refLib.dependencies.findDependency(depAcmeExclude)
            refAcmeExclude.checkDependencies(depConfig1)
            val refConfigExclude = refAcmeExclude.dependencies.findDependency(depConfig1)
            refConfigExclude.checkDependencies(depLang)
            val refAcme = scopeDependencies.findDependency(depAcme)
            refAcme.checkDependencies(depLang, depConfig1)
            refAcme.dependencies.findDependency(depConfig1) shouldBeSameInstanceAs refConfigFull
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

            val scope1Dependencies = scopeDependencies(scopes, scope1)
            scope1Dependencies.identifiers() should containExactly(depLog.toId(), depConfig1.toId())
            val scope2Dependencies = scopeDependencies(scopes, scope2)
            scope2Dependencies.identifiers() should containExactly(depAcmeExclude.toId())
            val refConfigFull = scope1Dependencies.findDependency(depConfig1)
            refConfigFull.checkDependencies(depLang, depLog)
            refConfigFull.dependencies.findDependency(depLog) shouldBeSameInstanceAs scope1Dependencies
                .findDependency(depLog)
            val refAcme = scope2Dependencies.findDependency(depAcmeExclude)
            refAcme.checkDependencies(depConfig2)
            val refConfigExclude = refAcme.dependencies.findDependency(depConfig2)
            refConfigExclude.checkDependencies(depLang)
            refConfigExclude.dependencies.findDependency(depLang) shouldBeSameInstanceAs refConfigFull
                .dependencies.findDependency(depLang)
        }

        "support adding packages directly" {
            val identifiers = listOf(
                Identifier("Maven:org.apache.commons:commons-lang3:3.11"),
                Identifier("Maven:commons-logging:commons-logging:1.2"),
                Identifier("Maven:org.apache.commons:commons-configuration2:2.7")
            )
            val packages = identifiers.map { Package.EMPTY.copy(id = it) }
            val builder = createGraphBuilder()

            builder.addPackages(packages)

            builder.packages() should containExactly(packages)
        }

        "use packages that have been added directly rather than creating them anew" {
            val id = Identifier("Maven:org.apache.commons:commons-lang3:3.11")
            val dependency = createDependency(id.namespace, id.name, id.version)
            val pkg = Package.EMPTY.copy(id = id)
            val builder = createGraphBuilder()

            builder.addPackages(listOf(pkg))
            builder.addDependency("compile", dependency)

            builder.packages() should containExactly(pkg)
        }
    }

    "GradleDependencyHandler" should {
        "handle an exception when resolving a package" {
            val maven = mockk<MavenSupport>()
            val exception = ProjectBuildingException("project", "Could not build.", IOException("Download exception"))
            val dep = createDependency("org.apache.commons", "commons-lang3", "3.11")
            val issues = mutableListOf<OrtIssue>()

            every { maven.parsePackage(any(), any(), any()) } throws exception
            val handler = GradleDependencyHandler(NAME, maven)

            handler.createPackage(dep, issues) should beNull()

            issues should haveSize(1)
            with(issues.first()) {
                source shouldBe NAME
                severity shouldBe Severity.ERROR
                message should contain("${dep.groupId}:${dep.artifactId}:${dep.version}")
            }
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
 * Create a [DependencyGraphBuilder] equipped with a [GradleDependencyHandler] that is used by the test cases in
 * this class.
 */
private fun createGraphBuilder(): DependencyGraphBuilder<Dependency> {
    val dependencyHandler = GradleDependencyHandler(NAME, createMavenSupport())
    dependencyHandler.repositories = remoteRepositories
    return DependencyGraphBuilder(dependencyHandler)
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
private fun Dependency.toId() = Identifier(type(), groupId, artifactId, version)

/**
 * Return the package references from the given [scopes] associated with the scope with the given [scopeName].
 */
private fun scopeDependencies(
    scopes: SortedSet<Scope>,
    scopeName: String
): Set<PackageReference> =
    scopes.find { it.name == scopeName }?.dependencies.orEmpty()

/**
 * Extract the identifiers from a collection of package references.
 */
private fun Collection<PackageReference>.identifiers(): List<Identifier> = map { it.id }

/**
 * Find the package corresponding to the given [dependency] in this collection.
 */
private fun Collection<PackageReference>.findDependency(dependency: Dependency): PackageReference =
    findId(dependency.toId())

/**
 * Find a package with the given [id] in this collection.
 */
private fun Collection<PackageReference>.findId(id: Identifier): PackageReference =
    find { it.id == id } ?: throw IllegalArgumentException("Package with id $id is not contained in $this.")

/**
 * Check whether this [PackageReference] contains exactly the given [dependencies][expectedDependencies].
 */
private fun PackageReference.checkDependencies(vararg expectedDependencies: Dependency): Set<PackageReference> {
    val ids = expectedDependencies.map { it.toId() }
    dependencies.identifiers() should containExactlyInAnyOrder(ids)
    return dependencies
}
