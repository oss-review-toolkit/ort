/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.reporters.cyclonedx

import java.util.Date
import java.util.UUID

import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.Metadata
import org.cyclonedx.model.OrganizationalContact
import org.cyclonedx.model.OrganizationalEntity
import org.cyclonedx.model.license.Expression
import org.cyclonedx.model.metadata.ToolInformation

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ort.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.ort.ORT_VERSION

internal class CycloneDxModelMapper(
    private val input: ReporterInput,
    private val config: CycloneDxReporterConfig
) {
    fun createSingleBom(projects: List<Project>): Bom =
        Bom().apply {
            serialNumber = "urn:uuid:${UUID.randomUUID()}"

            metadata = createBomMetadata().apply {
                component = getSingleBomMetadataComponent(projects, input.ortResult)
            }

            components = mutableListOf()

            // In case of multiple projects it is not always clear for which project to create the BOM:
            //
            // - If a multi-module project only produces a single application that gets distributed, then usually only a
            //   single BOM for that application is generated.
            // - If a multi-module project produces multiple applications (e.g. if there is one module per independent
            //   microservice), then usually for each project a BOM is generated as there are multiple things being
            //   distributed.
            //
            // As this distinction is hard to make programmatically (without additional information about the
            // distributable), just create a single BOM for all projects in that case for now. As there also is no
            // single correct project to pick for adding external references in that case, simply only use the global
            // repository VCS information here.
            val vcs = input.ortResult.repository.vcsProcessed
            addExternalReference(
                ExternalReference.Type.VCS,
                vcs.url,
                "URL to the ${vcs.type} repository of the projects"
            )

            val allDirectDependencies = projects.flatMapTo(mutableSetOf()) { project ->
                input.ortResult.dependencyNavigator.projectDependencies(project, maxDepth = 1)
            }

            input.ortResult.getPackages(omitExcluded = true)
                .map { it.metadata }
                .sortedBy { it.id }
                .forEach { pkg ->
                    val dependencyType = if (pkg.id in allDirectDependencies) "direct" else "transitive"
                    addComponent(input, pkg, dependencyType)
                }

            addDependencies(input, metadata.component.bomRef, allDirectDependencies)

            addVulnerabilities(input.ortResult.getVulnerabilities())
        }

    fun createProjectBom(project: Project): Bom =
        Bom().apply {
            serialNumber = "urn:uuid:${UUID.randomUUID()}"

            metadata = createBomMetadata().apply {
                component = Component().apply {
                    // Actually the project could be a library as well, but there is no automatic way to tell.
                    type = Component.Type.APPLICATION

                    bomRef = project.id.toCoordinates()

                    group = project.id.namespace
                    name = project.id.name
                    version = project.id.version

                    authors = project.authors.map { OrganizationalContact().apply { name = it } }
                    supplier = authors.takeUnless { it.isEmpty() }?.let {
                        OrganizationalEntity().apply { contacts = authors }
                    }

                    description = project.description
                }
            }

            components = mutableListOf()

            // Add information about projects as external references at the BOM level.
            addExternalReference(
                ExternalReference.Type.VCS,
                project.vcsProcessed.url,
                "URL to the project's ${project.vcsProcessed.type} repository"
            )

            addExternalReference(ExternalReference.Type.WEBSITE, project.homepageUrl)

            addExternalReference(ExternalReference.Type.BUILD_SYSTEM, project.id.type)

            addExternalReference(
                ExternalReference.Type.OTHER,
                project.id.toPurl(),
                "Package-URL of the project"
            )

            val dependencyPackages = input.ortResult.dependencyNavigator
                .projectDependencies(project, matcher = { !input.ortResult.isExcluded(it.id) })
                .mapNotNull { input.ortResult.getPackage(it)?.metadata }
                .sortedBy { it.id }

            val directDependencies = input.ortResult.dependencyNavigator.projectDependencies(project, maxDepth = 1)
            dependencyPackages.forEach { pkg ->
                val dependencyType = if (pkg.id in directDependencies) "direct" else "transitive"
                addComponent(input, pkg, dependencyType)
            }

            addDependencies(input, metadata.component.bomRef, directDependencies)

            addVulnerabilities(input.ortResult.getVulnerabilities())
        }

    private fun createBomMetadata(): Metadata =
        Metadata().apply {
            timestamp = Date()
            toolChoice = ToolInformation().apply {
                components = listOf(
                    Component().apply {
                        type = Component.Type.APPLICATION
                        name = ORT_FULL_NAME
                        version = ORT_VERSION
                    }
                )
            }

            licenses = LicenseChoice().apply { expression = Expression(config.dataLicense) }
        }

    /**
     * Return a [Component] which represents the union of the given [projects] as a single component. The component is
     * derived by a heuristic which works best for multi-module projects in which the modules share common properties.
     *
     * If these default values are not suitable, it is possible to override some of them via the reporter [config].
     */
    private fun getSingleBomMetadataComponent(projects: Collection<Project>, result: OrtResult): Component =
        Component().apply {
            type = config.singleBomComponentType

            val namespaces = projects.mapTo(mutableSetOf()) { it.id.namespace }
            val versions = projects.mapTo(mutableSetOf()) { it.id.version }

            with(result.repository.vcsProcessed) {
                bomRef = "$url@$revision"

                group = namespaces.singleOrNull()
                name = config.singleBomComponentName.takeUnless { it.isEmpty() }
                    ?: findTopLevelProject(projects)?.id?.name ?: url
                version = versions.singleOrNull() ?: revision
            }
        }
}

/**
 * Try to obtain a single top-level project in [projects]. The top-level project is identified based on the
 * number of components in the definition file path. If there are multiple projects with a minimum number of
 * path components, there is no single top-level project, and the function returns *null*.
 */
private fun findTopLevelProject(projects: Collection<Project>): Project? =
    projects.groupBy { project ->
        project.definitionFilePath.count { it == '/' }
    }.minByOrNull { it.key }?.value?.singleOrNull()
