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

import org.apache.maven.project.ProjectBuildingException

import org.eclipse.aether.RepositoryException
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.core.showStackTrace

/**
 * A specialized [DependencyHandler] implementation for Gradle's dependency model.
 */
class GradleDependencyHandler(
    /** The name of the associated package manager. */
    val managerName: String,

    /** The helper object to resolve packages via Maven. */
    private val maven: MavenSupport
) : DependencyHandler<Dependency> {
    /**
     * A list with repositories to use when resolving packages. This list must be set before using this handler for
     * constructing the dependency graph of a project. As different projects may use different repositories, this
     * property is writable.
     */
    var repositories = emptyList<RemoteRepository>()

    override fun identifierFor(dependency: Dependency): Identifier =
        Identifier(
            type = dependency.dependencyType(),
            namespace = dependency.groupId,
            name = dependency.artifactId,
            version = dependency.version
        )

    override fun dependenciesFor(dependency: Dependency): Collection<Dependency> = dependency.dependencies

    override fun issuesForDependency(dependency: Dependency): Collection<OrtIssue> =
        listOfNotNull(
            dependency.error?.let {
                createAndLogIssue(
                    source = managerName,
                    message = it,
                    severity = Severity.ERROR
                )
            },

            dependency.warning?.let {
                createAndLogIssue(
                    source = managerName,
                    message = it,
                    severity = Severity.WARNING
                )
            }
        )

    override fun linkageFor(dependency: Dependency): PackageLinkage =
        if (dependency.isProjectDependency()) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

    override fun createPackage(dependency: Dependency, issues: MutableList<OrtIssue>): Package? {
        // Only look for a package if there was no error resolving the dependency and it is no project dependency.
        if (dependency.error != null || dependency.isProjectDependency()) return null

        val artifact = DefaultArtifact(
            dependency.groupId, dependency.artifactId, dependency.classifier,
            dependency.extension, dependency.version
        )

        return runCatching {
            maven.parsePackage(artifact, repositories)
        }.getOrElse { e ->
            when (e) {
                is ProjectBuildingException, is RepositoryException -> {
                    e.showStackTrace()

                    issues += createAndLogIssue(
                        source = managerName,
                        message = "Could not get package information for dependency '${artifact.identifier()}': " +
                                e.collectMessagesAsString()
                    )

                    null
                }

                else -> throw e
            }
        }
    }

    /**
     * Determine the type of this dependency. This manager implementation uses Maven to resolve packages, so
     * the type of dependencies to packages is typically _Maven_ unless no pom is available. Only for module
     * dependencies, the type of this manager is used.
     */
    private fun Dependency.dependencyType(): String =
        if (isProjectDependency()) {
            managerName
        } else {
            pomFile?.let { "Maven" } ?: "Unknown"
        }
}

/**
 * Return a flag whether this dependency references another project in the current build.
 */
private fun Dependency.isProjectDependency() = localPath != null
