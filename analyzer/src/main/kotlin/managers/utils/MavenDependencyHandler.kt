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

import org.apache.maven.project.MavenProject

import org.eclipse.aether.graph.DependencyNode

import org.ossreviewtoolkit.analyzer.managers.Maven
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.showStackTrace

/**
 * A specialized [DependencyHandler] implementation for the dependency model of Maven.
 */
class MavenDependencyHandler(
    /** The name of the associated package manager. */
    val managerName: String,

    /** The helper object to invoke Maven-related functionality. */
    val support: MavenSupport,

    /**
     * A map with information about the local projects in the current Maven build. Dependencies pointing to projects
     * sometimes need to be treated in a special way.
     */
    localProjects: Map<String, MavenProject>,

    /**
     * A flag whether [SBT compatibility mode][Maven.enableSbtMode] is enabled.
     */
    private val sbtMode: Boolean
) : DependencyHandler<DependencyNode> {
    /**
     * A set of identifiers that are known to point to local projects. This is updated for packages that are resolved
     * to projects.
     */
    private val localProjectIds = localProjects.keys.toMutableSet()

    override fun identifierFor(dependency: DependencyNode): Identifier =
        Identifier(
            type = if (isLocalProject(dependency.identifier())) managerName else "Maven",
            namespace = dependency.artifact.groupId,
            name = dependency.artifact.artifactId,
            version = dependency.artifact.version
        )

    override fun dependenciesFor(dependency: DependencyNode): Collection<DependencyNode> {
        val childrenWithoutToolDependencies = dependency.children.filterNot { node ->
            TOOL_DEPENDENCIES.any(node.artifact.identifier()::startsWith)
        }

        if (childrenWithoutToolDependencies.size < dependency.children.size) {
            log.info { "Omitting the Java < 1.9 system dependency on 'tools.jar'." }
        }

        return childrenWithoutToolDependencies
    }

    override fun linkageFor(dependency: DependencyNode): PackageLinkage =
        if (isLocalProject(dependency)) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

    /**
     * Create a [Package] representing a [dependency] if possible, recording any [issues]. Inter-project
     * dependencies are skipped.
     */
    override fun createPackage(dependency: DependencyNode, issues: MutableList<OrtIssue>): Package? {
        if (isLocalProject(dependency)) return null

        return runCatching {
            support.parsePackage(dependency.artifact, dependency.repositories, sbtMode = sbtMode).let { pkg ->
                // There is the corner case that a dependency references a project, but in a different version than
                // the one used by the local build. Then, this dependency is actually a package, but Maven's
                // resolution mechanism might prefer using the project. Therefore, the check whether the dependency
                // is a project must be done after the package resolution again.
                if (isLocalProject(pkg.id)) {
                    localProjectIds += dependency.identifier()
                    null
                } else {
                    pkg
                }
            }
        }.onFailure { e ->
            e.showStackTrace()

            issues += createAndLogIssue(
                source = managerName,
                message = "Could not get package information for dependency '" +
                        "${dependency.artifact.identifier()}': ${e.collectMessages()}"
            )
        }.getOrNull()
    }

    /**
     * Return a flag whether the given [dependency] references a project in the same multi-module build.
     */
    private fun isLocalProject(dependency: DependencyNode): Boolean = isLocalProject(dependency.identifier())

    /**
     * Return a flag whether the given [id] references a project in the same multi-module build.
     */
    private fun isLocalProject(id: Identifier): Boolean = isLocalProject("${id.namespace}:${id.name}:${id.version}")

    /**
     * Return a flag whether the given [id] references a project in the same multi-module build.
     */
    private fun isLocalProject(id: String): Boolean = id in localProjectIds
}

/**
 * A list with identifiers referencing 'tools.jar'. Artifacts with identifiers starting with these strings are
 * filtered out by [dependenciesFor()].
 */
private val TOOL_DEPENDENCIES = listOf("com.sun:tools:", "jdk.tools:jdk.tools:")

/**
 * Convenience function to generate the Maven identifier for this [DependencyNode].
 */
private fun DependencyNode.identifier(): String = artifact.identifier()
