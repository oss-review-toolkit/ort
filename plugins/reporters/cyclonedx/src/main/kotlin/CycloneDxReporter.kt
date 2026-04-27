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

import java.io.File
import java.util.Date
import java.util.UUID

import org.cyclonedx.Format
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
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginEnumEntry
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ort.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.ort.ORT_VERSION

internal const val DEFAULT_SCHEMA_VERSION_NAME = "1.6" // Version.VERSION_16.versionString
internal val DEFAULT_SCHEMA_VERSION = Version.entries.single { it.versionString == DEFAULT_SCHEMA_VERSION_NAME }

@Suppress("EnumEntryNameCase", "EnumNaming")
enum class SchemaVersion(val version: Version) {
    @OrtPluginEnumEntry(alternativeName = "1.0")
    VERSION_10(Version.VERSION_10),

    @OrtPluginEnumEntry(alternativeName = "1.1")
    VERSION_11(Version.VERSION_11),

    @OrtPluginEnumEntry(alternativeName = "1.2")
    VERSION_12(Version.VERSION_12),

    @OrtPluginEnumEntry(alternativeName = "1.3")
    VERSION_13(Version.VERSION_13),

    @OrtPluginEnumEntry(alternativeName = "1.4")
    VERSION_14(Version.VERSION_14),

    @OrtPluginEnumEntry(alternativeName = "1.5")
    VERSION_15(Version.VERSION_15),

    @OrtPluginEnumEntry(alternativeName = "1.6")
    VERSION_16(Version.VERSION_16)
}

data class CycloneDxReporterConfig(
    /**
     * The CycloneDX schema version to use. Defaults to "1.6".
     */
    @OrtPluginOption(
        defaultValue = DEFAULT_SCHEMA_VERSION_NAME,
        aliases = ["schema.version"]
    )
    val schemaVersion: SchemaVersion,

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
     * Allows overriding the component name in the metadata of the generated report in [singleBom] mode. Per default,
     * the name is derived from a single top-level project (if any) or falls back to the VCS URL. Using this property,
     * an arbitrary name can be set.
     */
    @OrtPluginOption(defaultValue = "")
    val singleBomComponentName: String,

    /**
     * Allows specifying the component type in the metadata of the generated report in [singleBom] mode.
     */
    @OrtPluginOption(defaultValue = "APPLICATION")
    val singleBomComponentType: Component.Type,

    /**
     * A comma-separated list of (case-insensitive) output formats to export to. Supported are XML and JSON.
     */
    @OrtPluginOption(
        defaultValue = "JSON",
        aliases = ["output.file.formats"]
    )
    val outputFileFormats: List<Format>
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

        /**
         * Return a [Component] object to be placed in the metadata of the generated BOM if `singleBom` is enabled.
         * The function tries to find meaningful values for the component's properties based on the current list of
         * [projects] and the given [result] object. This works best for a multi-module project where the single
         * subprojects share common properties. The following properties are set:
         * - If all projects have the same namespace, this is used for the `group` property.
         * - If all projects have the same version, this is used for the `version` property; otherwise, the version is
         *   set to the VCS revision.
         * - To derive the component `name`, the function tries to find a single top-level project and obtains the name
         *   from this project. If this is not possible, it uses the URL from the VCS information.
         *
         * If these default values are not suitable, it is possible to override some of them via the reporter [config].
         */
        internal fun getSingleBomMetadataComponent(
            projects: Collection<Project>,
            result: OrtResult,
            config: CycloneDxReporterConfig
        ): Component =
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

        /**
         * Try to obtain a single top-level project in [projects]. The top-level project is identified based on the
         * number of components in the definition file path. If there are multiple projects with a minimum number of
         * path components, there is no single top-level project, and the function returns *null*.
         */
        private fun findTopLevelProject(projects: Collection<Project>): Project? =
            projects.groupBy { project ->
                project.definitionFilePath.count { it == '/' }
            }.minByOrNull { it.key }?.value?.singleOrNull()
    }

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val reportFileResults = mutableListOf<Result<File>>()

        val projects = input.ortResult.getProjects(omitExcluded = true).sortedBy { it.id }

        val outputFormats = config.outputFileFormats.toSet()

        require(outputFormats.isNotEmpty()) {
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
                    component = getSingleBomMetadataComponent(projects, input.ortResult, config)
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

            input.ortResult.getPackages(omitExcluded = true)
                .map { it.metadata }
                .sortedBy { it.id }
                .forEach { pkg ->
                    val dependencyType = if (pkg.id in allDirectDependencies) "direct" else "transitive"
                    bom.addComponent(input, pkg, dependencyType)
                }

            bom.addDependencies(input, bom.metadata.component.bomRef, allDirectDependencies)

            bom.addVulnerabilities(input.ortResult.getVulnerabilities())

            reportFileResults += bom.writeFormats(
                config.schemaVersion.version,
                outputDir,
                REPORT_BASE_FILENAME,
                outputFormats
            )
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

                val dependencyPackages = input.ortResult.dependencyNavigator
                    .projectDependencies(project, matcher = { !input.ortResult.isExcluded(it.id) })
                    .mapNotNull { input.ortResult.getPackage(it)?.metadata }
                    .sortedBy { it.id }

                val directDependencies = input.ortResult.dependencyNavigator.projectDependencies(project, maxDepth = 1)
                dependencyPackages.forEach { pkg ->
                    val dependencyType = if (pkg.id in directDependencies) "direct" else "transitive"
                    bom.addComponent(input, pkg, dependencyType)
                }

                bom.addDependencies(input, bom.metadata.component.bomRef, directDependencies)

                bom.addVulnerabilities(input.ortResult.getVulnerabilities())

                val reportName = "$REPORT_BASE_FILENAME-${project.id.toPath("-")}"
                reportFileResults += bom.writeFormats(
                    config.schemaVersion.version,
                    outputDir,
                    reportName,
                    outputFormats
                )
            }
        }

        return reportFileResults
    }
}
