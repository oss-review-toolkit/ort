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

package org.ossreviewtoolkit.plugins.packagemanagers.gradle

import OrtDependency

import org.apache.maven.project.ProjectBuildingException

import org.eclipse.aether.RepositoryException
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.plugins.packagemanagers.gradlemodel.getIdentifierType
import org.ossreviewtoolkit.plugins.packagemanagers.gradlemodel.isProjectDependency
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.MavenSupport
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.identifier
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.showStackTrace

/**
 * A specialized [DependencyHandler] implementation for Gradle's dependency model.
 */
internal class GradleDependencyHandler(
    /** The type of projects to handle. */
    private val projectType: String,

    /** The helper object to resolve packages via Maven. */
    private val maven: MavenSupport
) : DependencyHandler<OrtDependency> {
    /**
     * A list with repositories to use when resolving packages. This list must be set before using this handler for
     * constructing the dependency graph of a project. As different projects may use different repositories, this
     * property is writable.
     */
    var repositories = emptyList<RemoteRepository>()

    override fun identifierFor(dependency: OrtDependency): Identifier =
        with(dependency) { Identifier(getIdentifierType(projectType), groupId, artifactId, version) }

    override fun dependenciesFor(dependency: OrtDependency): List<OrtDependency> = dependency.dependencies

    override fun issuesFor(dependency: OrtDependency): List<Issue> =
        listOfNotNull(
            dependency.error?.let {
                createAndLogIssue(
                    source = GradleFactory.descriptor.displayName,
                    message = it,
                    severity = Severity.ERROR
                )
            },

            dependency.warning?.let {
                createAndLogIssue(
                    source = GradleFactory.descriptor.displayName,
                    message = it,
                    severity = Severity.WARNING
                )
            }
        )

    override fun linkageFor(dependency: OrtDependency): PackageLinkage =
        if (dependency.isProjectDependency) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

    override fun createPackage(dependency: OrtDependency, issues: MutableCollection<Issue>): Package? {
        // Only look for a package if there was no error resolving the dependency and it is no project dependency.
        if (dependency.error != null || dependency.isProjectDependency) return null

        val artifact = DefaultArtifact(
            dependency.groupId, dependency.artifactId, dependency.classifier,
            dependency.extension, dependency.version
        )

        return runCatching {
            maven.parsePackage(artifact, repositories, useReposFromDependencies = false)
        }.getOrElse { e ->
            when (e) {
                is ProjectBuildingException, is RepositoryException -> {
                    e.showStackTrace()

                    issues += createAndLogIssue(
                        source = GradleFactory.descriptor.displayName,
                        message = "Could not get package information for dependency '${artifact.identifier()}': " +
                            e.collectMessages()
                    )

                    null
                }

                else -> throw e
            }
        }
    }
}
