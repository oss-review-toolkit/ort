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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import java.io.IOException

import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuildingException

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.graph.DependencyNode

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.Severity

class MavenDependencyHandlerTest : WordSpec({
    beforeSpec {
        mockkStatic(Artifact::identifier)
    }

    afterSpec {
        unmockkAll()
    }

    "identifierFor" should {
        "return the identifier for an external dependency" {
            val dependency = createDependency(PACKAGE_ID_SUFFIX)

            val handler = createHandler()

            handler.identifierFor(dependency) shouldBe Identifier("$PACKAGE_TYPE:$PACKAGE_ID_SUFFIX")
        }

        "return the identifier for an inter-project dependency" {
            val dependency = createDependency(PROJECT_ID_SUFFIX)

            val handler = createHandler()

            handler.identifierFor(dependency) shouldBe Identifier("$PROJECT_TYPE:$PROJECT_ID_SUFFIX")
        }
    }

    "dependenciesFor" should {
        "return the dependencies of a dependency node" {
            val dependency = createDependency(PACKAGE_ID_SUFFIX)
            val child1 = createDependency("child:dep:1")
            val child2 = createDependency("child:dep:2")

            every { dependency.children } returns listOf(child1, child2)

            val handler = createHandler()

            handler.dependenciesFor(dependency) should containExactly(child1, child2)
        }

        "filter out the dependency to com.sun:tools" {
            val dependency = createDependency(PACKAGE_ID_SUFFIX)
            val toolDep = createDependency("com.sun:tools:test-17")
            val child = createDependency("child:dep:1")

            every { dependency.children } returns listOf(toolDep, child)

            val handler = createHandler()

            handler.dependenciesFor(dependency) should containExactly(child)
        }

        "filter out the dependency to jdk.tools:jdk.tools" {
            val dependency = createDependency(PACKAGE_ID_SUFFIX)
            val toolDep = createDependency("jdk.tools:jdk.tools:17-beta")
            val child = createDependency("child:dep:1")

            every { dependency.children } returns listOf(child, toolDep)

            val handler = createHandler()

            handler.dependenciesFor(dependency) should containExactly(child)
        }
    }

    "linkageFor" should {
        "return PackageLinkage.DYNAMIC for an external dependency" {
            val dependency = createDependency(PACKAGE_ID_SUFFIX)

            val handler = createHandler()

            handler.linkageFor(dependency) shouldBe PackageLinkage.DYNAMIC
        }

        "return PackageLinkage.PROJECT_DYNAMIC for an inter-project dependency" {
            val dependency = createDependency(PROJECT_ID_SUFFIX)

            val handler = createHandler()

            handler.linkageFor(dependency) shouldBe PackageLinkage.PROJECT_DYNAMIC
        }
    }

    "createPackage" should {
        "create a package for a dependency" {
            val dependency = createDependency(PACKAGE_ID_SUFFIX)
            val pkg = createPackage(PACKAGE_ID_SUFFIX)
            val issues = mutableListOf<Issue>()

            val handler = createHandler { node ->
                node shouldBe dependency
                pkg
            }

            handler.createPackage(dependency, issues) shouldBe pkg
            issues should beEmpty()
        }

        "return null for a project dependency" {
            val projectDependency = createDependency(PROJECT_ID_SUFFIX)
            val issues = mutableListOf<Issue>()

            val handler = createHandler()

            handler.createPackage(projectDependency, issues) should beNull()
            issues should beEmpty()
        }

        "return null for a package that is resolved to a project dependency" {
            val dependency = createDependency(PACKAGE_ID_SUFFIX)
            val pkg = createPackage(PROJECT_ID_SUFFIX)

            val handler = createHandler { pkg }

            handler.createPackage(dependency, mutableListOf()) should beNull()
        }

        "report the correct linkage for a package that is resolved to a project dependency" {
            val dependency = createDependency(PACKAGE_ID_SUFFIX)
            val pkg = createPackage(PROJECT_ID_SUFFIX)

            val handler = createHandler { pkg }

            handler.createPackage(dependency, mutableListOf())

            handler.linkageFor(dependency) shouldBe PackageLinkage.PROJECT_DYNAMIC
        }

        "handle an exception from MavenSupport" {
            val exception = ProjectBuildingException(
                "BrokenProject", "Cannot parse pom.",
                IOException("General failure when reading hard disk.")
            )
            val dependency = createDependency(PACKAGE_ID_SUFFIX)
            val issues = mutableListOf<Issue>()

            val handler = createHandler { throw exception }

            handler.createPackage(dependency, issues) should beNull()

            issues should haveSize(1)
            with(issues[0]) {
                severity shouldBe Severity.ERROR
                source shouldBe MANAGER_NAME
                message should contain(PACKAGE_ID_SUFFIX)
                message should contain(exception.message!!)
            }
        }
    }
})

private const val MANAGER_NAME = "MavenTest"
private const val PROJECT_TYPE = "MavenProject"
private const val PACKAGE_TYPE = "Maven"
private const val PACKAGE_ID_SUFFIX = "org.apache.commons:commons-lang2:3.12"

/**
 * A map simulating local projects. This is required by [MavenSupport] when parsing packages.
 */
private val LOCAL_PROJECTS = mapOf(
    "namespace:project1:1.0" to createProject(),
    "namespace:project2:2.0" to createProject(),
    "namespace:project3:3.0" to createProject()
)

/** ID of an inter-project dependency. */
private val PROJECT_ID_SUFFIX = LOCAL_PROJECTS.keys.first()

/** Constant for a resolver function that can be used if no interaction with the resolver is expected. */
private val unusedPackageResolverFun: PackageResolverFun = { throw NotImplementedError() }

/**
 * Return an [Identifier] from the given [mavenId]. The identifiers used by Maven internally are very close to
 * ORT's identifiers; only the type component is missing.
 */
private fun toOrtIdentifier(mavenId: String): Identifier = Identifier("$PACKAGE_TYPE:$mavenId")

/**
 * Return a mock [Artifact] that is prepared to return the given [identifier][id]. Note: For this to work, the
 * `identifier()` extension function must have been mocked statically.
 */
private fun createArtifact(id: String): Artifact {
    val parts = id.split(':')
    val artifact = mockk<Artifact>()

    every { artifact.identifier() } returns id
    every { artifact.groupId } returns parts.getOrElse(0) { "" }
    every { artifact.artifactId } returns parts.getOrElse(1) { "" }
    every { artifact.version } returns parts.getOrElse(2) { "" }
    every { artifact.identifier() } returns id

    return artifact
}

/**
 * Return a mock [DependencyNode] with an [Artifact] that has the given [identifier][id].
 */
private fun createDependency(id: String): DependencyNode {
    val artifact = createArtifact(id)
    val node = mockk<DependencyNode>()
    every { node.artifact } returns artifact
    return node
}

/**
 * Create the [MavenDependencyHandler] instance to be tested with default parameters and the given
 * [packageResolverFun].
 */
private fun createHandler(packageResolverFun: PackageResolverFun = unusedPackageResolverFun): MavenDependencyHandler =
    MavenDependencyHandler(MANAGER_NAME, PROJECT_TYPE, LOCAL_PROJECTS, packageResolverFun)

/**
 * Convenience function to create a mock [MavenProject]. Note: This function solves some nasty false-positive
 * warnings about explicit type arguments.
 */
private fun createProject(): MavenProject = mockk()

/**
 * Create a mock for a [Package] that is prepared to return an [Identifier] based on [mavenId].
 */
private fun createPackage(mavenId: String): Package =
    mockk<Package>().apply {
        every { id } returns toOrtIdentifier(mavenId)
    }
