/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import java.io.File
import java.util.Date
import java.util.UUID

import org.apache.logging.log4j.kotlin.logger

import org.cyclonedx.Version
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.Metadata
import org.cyclonedx.model.OrganizationalContact
import org.cyclonedx.model.OrganizationalEntity
import org.cyclonedx.model.license.Expression
import org.cyclonedx.model.metadata.ToolInformation

import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.alsoIfNull
import org.ossreviewtoolkit.utils.ort.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.ort.ORT_VERSION

internal const val DEFAULT_SCHEMA_VERSION_NAME = "1.6" // Version.VERSION_16.versionString
internal val DEFAULT_SCHEMA_VERSION = Version.entries.single { it.versionString == DEFAULT_SCHEMA_VERSION_NAME }

data class CycloneDxReporterConfig(
    /**
     * The CycloneDX schema version to use. Defaults to "1.6".
     */
    @OrtPluginOption(
        defaultValue = DEFAULT_SCHEMA_VERSION_NAME,
        aliases = ["schema.version"]
    )
    val schemaVersion: String,

    /**
     * The license for the data contained in the report. Defaults to "CC0-1.0".
     */
    @OrtPluginOption(
        defaultValue = "CC0-1.0",
        aliases = ["data.license"]
    )
    val dataLicense: String,

    /**
     * If true (the default), a single SBOM for all projects is created; if set to false, separate SBOMs are created for
     * each project.
     */
    @OrtPluginOption(
        defaultValue = "true",
        aliases = ["single.bom"]
    )
    val singleBom: Boolean,

    /**
     * A comma-separated list of (case-insensitive) output formats to export to. Supported are XML and JSON.
     */
    @OrtPluginOption(
        defaultValue = "JSON",
        aliases = ["output.file.formats"]
    )
    val outputFileFormats: List<String>
)

/**
 * A [Reporter] that creates software bills of materials (SBOM) in the [CycloneDX](https://cyclonedx.org) format. For
 * each [Project] contained in the ORT result a separate SBOM is created.
 */
@OrtPlugin(
    id = "CycloneDX",
    displayName = "CycloneDX SBOM",
    description = "Creates software bills of materials (SBOM) in the CycloneDX format.",
    factory = ReporterFactory::class
)
class CycloneDxReporter(
    override val descriptor: PluginDescriptor = CycloneDxReporterFactory.descriptor,
    private val config: CycloneDxReporterConfig
) : Reporter {
    companion object {
        const val REPORT_BASE_FILENAME = "bom.cyclonedx"
    }

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val reportFileResults = mutableListOf<Result<File>>()

        val projects = input.ortResult.getProjects(omitExcluded = true).sortedBy { it.id }
        val packages = input.ortResult.getPackages(omitExcluded = true).sortedBy { it.metadata.id }

        val schemaVersion = Version.entries.find {
            it.versionString == config.schemaVersion
        } ?: throw IllegalArgumentException("Unsupported CycloneDX schema version '${config.schemaVersion}'.")

        val outputFileExtensions = config.outputFileFormats.mapNotNullTo(mutableSetOf()) {
            val extension = it.trim().lowercase()
            extension.toFormat().alsoIfNull {
                logger.warn { "No CycloneDX format supports the '$extension' extension." }
            }
        }

        require(outputFileExtensions.isNotEmpty()) {
            "No valid CycloneDX output formats specified."
        }

        val metadata = Metadata().apply {
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

        if (config.singleBom) {
            val bom = Bom().apply {
                serialNumber = "urn:uuid:${UUID.randomUUID()}"

                this.metadata = metadata.apply {
                    component = Component().apply {
                        // There is no component type for repositories.
                        type = Component.Type.FILE

                        with(input.ortResult.repository.vcsProcessed) {
                            bomRef = "$url@$revision"

                            name = url
                            version = revision
                        }
                    }
                }

                components = mutableListOf()
            }

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
            bom.addExternalReference(
                ExternalReference.Type.VCS,
                vcs.url,
                "URL to the ${vcs.type} repository of the projects"
            )

            val allDirectDependencies = projects.flatMapTo(mutableSetOf()) { project ->
                input.ortResult.dependencyNavigator.projectDependencies(project, maxDepth = 1)
            }

            packages.forEach { (pkg, _) ->
                val dependencyType = if (pkg.id in allDirectDependencies) "direct" else "transitive"
                bom.addComponent(input, pkg, dependencyType)
            }

            bom.addDependencies(input, bom.metadata.component.bomRef, allDirectDependencies)

            bom.addVulnerabilities(input.ortResult.getVulnerabilities())

            reportFileResults += bom.writeFormats(schemaVersion, outputDir, REPORT_BASE_FILENAME, outputFileExtensions)
        } else {
            projects.forEach { project ->
                val bom = Bom().apply {
                    serialNumber = "urn:uuid:${UUID.randomUUID()}"

                    this.metadata = metadata.apply {
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
                }

                // Add information about projects as external references at the BOM level.
                bom.addExternalReference(
                    ExternalReference.Type.VCS,
                    project.vcsProcessed.url,
                    "URL to the project's ${project.vcsProcessed.type} repository"
                )

                bom.addExternalReference(ExternalReference.Type.WEBSITE, project.homepageUrl)

                val licenseNames = input.licenseInfoResolver.resolveLicenseInfo(project.id).filterExcluded()
                    .getLicenseNames(LicenseSource.DECLARED, LicenseSource.DETECTED)

                bom.addExternalReference(ExternalReference.Type.LICENSE, licenseNames.joinToString())

                bom.addExternalReference(ExternalReference.Type.BUILD_SYSTEM, project.id.type)

                bom.addExternalReference(
                    ExternalReference.Type.OTHER,
                    project.id.toPurl(),
                    "Package-URL of the project"
                )

                val dependencies = input.ortResult.dependencyNavigator.projectDependencies(project)
                val dependencyPackages = packages.mapNotNull { (pkg, _) ->
                    pkg.takeIf { it.id in dependencies }
                }

                val directDependencies = input.ortResult.dependencyNavigator.projectDependencies(project, maxDepth = 1)
                dependencyPackages.forEach { pkg ->
                    val dependencyType = if (pkg.id in directDependencies) "direct" else "transitive"
                    bom.addComponent(input, pkg, dependencyType)
                }

                bom.addDependencies(input, bom.metadata.component.bomRef, directDependencies)

                bom.addVulnerabilities(input.ortResult.getVulnerabilities())

                val reportName = "$REPORT_BASE_FILENAME-${project.id.toPath("-")}"
                reportFileResults += bom.writeFormats(schemaVersion, outputDir, reportName, outputFileExtensions)
            }
        }

        return reportFileResults
    }
}
