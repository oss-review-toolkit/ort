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
import java.util.SortedSet
import java.util.UUID

import org.apache.logging.log4j.kotlin.logger

import org.cyclonedx.Format
import org.cyclonedx.Version
import org.cyclonedx.generators.BomGeneratorFactory
import org.cyclonedx.model.AttachmentText
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExtensibleType
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.Hash
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.Metadata
import org.cyclonedx.model.license.Expression
import org.cyclonedx.model.metadata.ToolInformation

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.alsoIfNull
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.ort.ORT_NAME
import org.ossreviewtoolkit.utils.spdx.SpdxLicense

internal const val DEFAULT_SCHEMA_VERSION_NAME = "1.5" // Version.VERSION_15.versionString
internal val DEFAULT_SCHEMA_VERSION = Version.entries.single { it.versionString == DEFAULT_SCHEMA_VERSION_NAME }

data class CycloneDxReporterConfig(
    /**
     * The CycloneDX schema version to use. Defaults to "1.5".
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
        defaultValue = "XML",
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
    displayName = "CycloneDX Reporter",
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

    private fun Bom.addExternalReference(type: ExternalReference.Type, url: String, comment: String? = null) {
        if (url.isBlank()) return

        addExternalReference(
            ExternalReference().also { ref ->
                ref.type = type
                ref.url = url
                ref.comment = comment?.takeUnless { it.isBlank() }
            }
        )
    }

    private fun Component.addExternalReference(type: ExternalReference.Type, url: String, comment: String? = null) {
        if (url.isBlank()) return

        addExternalReference(
            ExternalReference().also { ref ->
                ref.type = type
                ref.url = url
                ref.comment = comment?.takeUnless { it.isBlank() }
            }
        )
    }

    private fun mapHash(hash: org.ossreviewtoolkit.model.Hash): Hash? =
        Hash.Algorithm.entries.find { it.spec == hash.algorithm.toString() }?.let { Hash(it, hash.value) }

    private fun Collection<String>.mapNamesToLicenses(origin: String, input: ReporterInput): List<License> =
        map { licenseName ->
            val spdxId = SpdxLicense.forId(licenseName)?.id
            val licenseText = input.licenseTextProvider.getLicenseText(licenseName)

            // Prefer to set the id in case of an SPDX "core" license and only use the name as a fallback, also
            // see https://github.com/CycloneDX/cyclonedx-core-java/issues/8.
            License().apply {
                id = spdxId
                name = licenseName.takeIf { spdxId == null }
                extensibleTypes = listOf(ExtensibleType(ORT_NAME, "origin", origin))

                if (licenseText != null) {
                    setLicenseText(
                        AttachmentText().apply {
                            contentType = "plain/text"
                            text = licenseText
                        }
                    )
                }
            }
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
                        version = Environment.ORT_VERSION
                    }
                )
            }

            licenses = LicenseChoice().apply { expression = Expression(config.dataLicense) }
        }

        if (config.singleBom) {
            val bom = Bom().apply {
                serialNumber = "urn:uuid:${UUID.randomUUID()}"
                this.metadata = metadata
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
                addPackageToBom(input, pkg, bom, dependencyType)
            }

            addVulnerabilitiesToBom(input.ortResult.getVulnerabilities(), bom)

            reportFileResults += writeBom(bom, schemaVersion, outputDir, REPORT_BASE_FILENAME, outputFileExtensions)
        } else {
            projects.forEach { project ->
                val bom = Bom().apply {
                    serialNumber = "urn:uuid:${UUID.randomUUID()}"
                    this.metadata = metadata
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
                    addPackageToBom(input, pkg, bom, dependencyType)
                }

                addVulnerabilitiesToBom(input.ortResult.getVulnerabilities(), bom)

                val reportName = "$REPORT_BASE_FILENAME-${project.id.toPath("-")}"
                reportFileResults += writeBom(bom, schemaVersion, outputDir, reportName, outputFileExtensions)
            }
        }

        return reportFileResults
    }

    private fun addVulnerabilitiesToBom(advisorVulnerabilities: Map<Identifier, List<Vulnerability>>, bom: Bom) {
        val vulnerabilities = mutableListOf<org.cyclonedx.model.vulnerability.Vulnerability>()
        advisorVulnerabilities.forEach {
            val vulnerabilityBomRef = it.key.toCoordinates()
            it.value.forEach {
                val vulnerability = org.cyclonedx.model.vulnerability.Vulnerability().apply {
                    id = it.id
                    description = it.description
                    detail = it.summary
                    ratings = it.references.map { reference ->
                        org.cyclonedx.model.vulnerability.Vulnerability.Rating().apply {
                            source = org.cyclonedx.model.vulnerability.Vulnerability.Source()
                                .apply { url = reference.url.toString() }
                            severity = org.cyclonedx.model.vulnerability.Vulnerability.Rating.Severity
                                .fromString(reference.severity?.lowercase())
                            score = reference.score?.toDouble()
                            method = org.cyclonedx.model.vulnerability.Vulnerability.Rating.Method
                                .fromString(reference.scoringSystem)
                        }
                    }

                    affects = mutableListOf(
                        org.cyclonedx.model.vulnerability.Vulnerability.Affect()
                            .apply { ref = vulnerabilityBomRef }
                    )
                }

                vulnerabilities.add(vulnerability)
            }

            bom.vulnerabilities = vulnerabilities
        }
    }

    private fun addPackageToBom(input: ReporterInput, pkg: Package, bom: Bom, dependencyType: String) {
        val resolvedLicenseInfo = input.licenseInfoResolver.resolveLicenseInfo(pkg.id).filterExcluded()
            .applyChoices(input.ortResult.getPackageLicenseChoices(pkg.id))
            .applyChoices(input.ortResult.getRepositoryLicenseChoices())

        val concludedLicenseNames = resolvedLicenseInfo.getLicenseNames(LicenseSource.CONCLUDED)
        val declaredLicenseNames = resolvedLicenseInfo.getLicenseNames(LicenseSource.DECLARED)
        val detectedLicenseNames = resolvedLicenseInfo.getLicenseNames(LicenseSource.DETECTED)

        // Get all licenses, but note down their origins inside an extensible type.
        val licenseObjects = concludedLicenseNames.mapNamesToLicenses("concluded license", input) +
            declaredLicenseNames.mapNamesToLicenses("declared license", input) +
            detectedLicenseNames.mapNamesToLicenses("detected license", input)

        val binaryHash = mapHash(pkg.binaryArtifact.hash)
        val sourceHash = mapHash(pkg.sourceArtifact.hash)

        val (hash, purlQualifier) = if (binaryHash == null && sourceHash != null) {
            Pair(sourceHash, "?classifier=sources")
        } else {
            Pair(binaryHash, "")
        }

        val component = Component().apply {
            group = pkg.id.namespace
            name = pkg.id.name
            version = pkg.id.version
            description = pkg.description
            bomRef = pkg.id.toCoordinates()

            // TODO: Map package-manager-specific OPTIONAL scopes.
            scope = if (input.ortResult.isExcluded(pkg.id)) {
                Component.Scope.EXCLUDED
            } else {
                Component.Scope.REQUIRED
            }

            hashes = listOfNotNull(hash)

            if (licenseObjects.isNotEmpty()) licenses = LicenseChoice().apply { licenses = licenseObjects }

            // TODO: Find a way to associate copyrights to the license they belong to, see
            //       https://github.com/CycloneDX/cyclonedx-core-java/issues/58
            copyright = resolvedLicenseInfo.getCopyrights().joinToString().takeUnless { it.isEmpty() }

            purl = pkg.purl + purlQualifier
            isModified = pkg.isModified

            // See https://github.com/CycloneDX/specification/issues/17 for how this differs from FRAMEWORK.
            type = Component.Type.LIBRARY

            extensibleTypes = listOf(ExtensibleType(ORT_NAME, "dependencyType", dependencyType))
        }

        component.addExternalReference(ExternalReference.Type.WEBSITE, pkg.homepageUrl)

        bom.addComponent(component)
    }

    private fun writeBom(
        bom: Bom,
        schemaVersion: Version,
        outputDir: File,
        outputName: String,
        outputFormats: Set<Format>
    ): List<Result<File>> =
        outputFormats.map { format ->
            runCatching {
                val bomString = generateBom(bom, schemaVersion, format)

                outputDir.resolve("$outputName.${format.extension}").apply {
                    bufferedWriter().use { it.write(bomString) }
                }
            }
        }
}

/**
 * Return the CycloneDX [Format] for the given extension as a [String], or null if there is no match.
 */
private fun String.toFormat(): Format? = Format.entries.find { this == it.extension }

/**
 * Return the license names of all licenses that have any of the given [sources] disregarding the excluded state.
 */
private fun ResolvedLicenseInfo.getLicenseNames(vararg sources: LicenseSource): SortedSet<String> =
    licenses.filter { license -> sources.any { it in license.sources } }.mapTo(sortedSetOf()) { it.license.toString() }

/**
 * Return the string representation for the given [bom], [schemaVersion] and [format].
 */
private fun generateBom(bom: Bom, schemaVersion: Version, format: Format): String =
    when (format) {
        Format.XML -> BomGeneratorFactory.createXml(schemaVersion, bom).toXmlString()
        Format.JSON -> {
            // JSON output cannot handle extensible types (see [1]), so simply remove them. As JSON output is guaranteed
            // to be the last format serialized, it is okay to modify the BOM here without doing a deep copy first.
            //
            // [1] https://github.com/CycloneDX/cyclonedx-core-java/issues/99.
            val bomWithoutExtensibleTypes = bom.apply {
                components.forEach { component ->
                    // Clear the "dependencyType".
                    component.extensibleTypes = null

                    if (component.licenses?.licenses != null) {
                        component.licenses.licenses.forEach { license ->
                            // Clear the "origin".
                            license.extensibleTypes = null
                        }

                        // Remove duplicates that may occur due to clearing the distinguishing extensive type.
                        component.licenses.licenses = component.licenses.licenses.distinct()
                    }
                }
            }

            BomGeneratorFactory.createJson(schemaVersion, bomWithoutExtensibleTypes).toJsonString()
        }
    }
