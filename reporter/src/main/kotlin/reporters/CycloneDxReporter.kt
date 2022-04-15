/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.reporter.reporters

import java.io.File
import java.util.Base64
import java.util.SortedSet
import java.util.UUID

import org.cyclonedx.BomGeneratorFactory
import org.cyclonedx.CycloneDxSchema
import org.cyclonedx.model.AttachmentText
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExtensibleType
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.Hash
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.isFalse
import org.ossreviewtoolkit.utils.core.ORT_NAME
import org.ossreviewtoolkit.utils.spdx.SpdxLicense

/**
 * A [Reporter] that creates software bills of materials (SBOM) in the [CycloneDX][1] format. For each [Project]
 * contained in the ORT result a separate SBOM is created.
 *
 * This reporter supports the following options:
 * - *single.bom*: If true (the default), a single SBOM for all projects is created; if set to false, separate SBOMs are
 *                 created for each project.
 * - *output.file.formats*: A comma-separated list of (case-insensitive) output formats to export to. Supported are XML
 *                          and JSON.
 * [1]: https://cyclonedx.org
 */
class CycloneDxReporter : Reporter {
    companion object {
        val DEFAULT_SCHEMA_VERSION = CycloneDxSchema.Version.VERSION_13

        const val REPORT_BASE_FILENAME = "bom.cyclonedx"

        const val OPTION_SCHEMA_VERSION = "schema.version"
        const val OPTION_SINGLE_BOM = "single.bom"
        const val OPTION_OUTPUT_FILE_FORMATS = "output.file.formats"
    }

    override val reporterName = "CycloneDx"

    private val base64Encoder = Base64.getEncoder()

    // Ensure that JSON comes last due to a work-around in writeBom() below.
    private val supportedOutputFileFormats = listOf(FileFormat.XML, FileFormat.JSON)

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
        enumValues<Hash.Algorithm>().find { it.spec == hash.algorithm.toString() }?.let { Hash(it, hash.value) }

    private fun mapLicenseNamesToObjects(licenseNames: Collection<String>, origin: String, input: ReporterInput) =
        licenseNames.map { licenseName ->
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
                            encoding = "base64"
                            text = base64Encoder.encodeToString(licenseText.toByteArray())
                        }
                    )
                }
            }
        }

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        val outputFiles = mutableListOf<File>()
        val projects = input.ortResult.getProjects(omitExcluded = true)

        val schemaVersion = enumValues<CycloneDxSchema.Version>().find {
            it.versionString == options[OPTION_SCHEMA_VERSION]
        } ?: DEFAULT_SCHEMA_VERSION

        val createSingleBom = !options[OPTION_SINGLE_BOM].isFalse()

        val outputFileFormats = options[OPTION_OUTPUT_FILE_FORMATS]
            ?.split(",")
            ?.mapTo(mutableSetOf()) { FileFormat.valueOf(it.uppercase()) }
            ?: setOf(FileFormat.XML)

        if (createSingleBom) {
            val bom = Bom().apply { serialNumber = "urn:uuid:${UUID.randomUUID()}" }

            // In case of multiple projects it is not always clear for which project to create the BOM:
            //
            // - If a multi-module project only produces a single application that gets distributed, then usually only a
            //   single BOM for that application is generated.
            // - If a multi-module project produces multiple applications (e.g. if there is one module per independent
            //   micro-service), then usually for each project a BOM is generated as there are multiple things being
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

            input.ortResult.getPackages().forEach { (pkg, _) ->
                val dependencyType = if (pkg.id in allDirectDependencies) "direct" else "transitive"
                addPackageToBom(input, pkg, bom, dependencyType)
            }

            outputFiles += writeBom(bom, schemaVersion, outputDir, REPORT_BASE_FILENAME, outputFileFormats)
        } else {
            projects.forEach { project ->
                val bom = Bom().apply { serialNumber = "urn:uuid:${UUID.randomUUID()}" }

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
                val packages = input.ortResult.getPackages().mapNotNull { (pkg, _) ->
                    pkg.takeIf { it.id in dependencies }
                }

                val directDependencies =
                    input.ortResult.dependencyNavigator.projectDependencies(project, maxDepth = 1)
                packages.forEach { pkg ->
                    val dependencyType = if (pkg.id in directDependencies) "direct" else "transitive"
                    addPackageToBom(input, pkg, bom, dependencyType)
                }

                val reportName = "$REPORT_BASE_FILENAME-${project.id.toPath("-")}"
                outputFiles += writeBom(bom, schemaVersion, outputDir, reportName, outputFileFormats)
            }
        }

        return outputFiles
    }

    private fun addPackageToBom(input: ReporterInput, pkg: Package, bom: Bom, dependencyType: String) {
        val resolvedLicenseInfo = input.licenseInfoResolver.resolveLicenseInfo(pkg.id).filterExcluded()

        val concludedLicenseNames = resolvedLicenseInfo.getLicenseNames(LicenseSource.CONCLUDED)
        val declaredLicenseNames = resolvedLicenseInfo.getLicenseNames(LicenseSource.DECLARED)
        val detectedLicenseNames = resolvedLicenseInfo.getLicenseNames(LicenseSource.DETECTED)

        // Get all licenses, but note down their origins inside of an extensible type.
        val licenseObjects = mapLicenseNamesToObjects(concludedLicenseNames, "concluded license", input) +
                mapLicenseNamesToObjects(declaredLicenseNames, "declared license", input) +
                mapLicenseNamesToObjects(detectedLicenseNames, "detected license", input)

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

            // TODO: Map package-manager-specific OPTIONAL scopes.
            scope = if (input.ortResult.isExcluded(pkg.id)) {
                Component.Scope.EXCLUDED
            } else {
                Component.Scope.REQUIRED
            }

            hashes = listOfNotNull(hash)

            // TODO: Support license expressions once we have fully converted to them.
            licenseChoice = LicenseChoice().apply { licenses = licenseObjects }

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
        schemaVersion: CycloneDxSchema.Version,
        outputDir: File,
        outputName: String,
        requestedOutputFileFormats: Set<FileFormat>
    ): List<File> {
        val writtenFiles = mutableListOf<File>()
        val outputFileFormats = supportedOutputFileFormats.filter { it in requestedOutputFileFormats }

        outputFileFormats.forEach { fileFormat ->
            val outputFile = outputDir.resolve("$outputName.${fileFormat.fileExtension}")

            val bomGenerator = when (fileFormat) {
                // Note that the BomXmlGenerator and BomJsonGenerator interfaces do not share a common base interface.
                FileFormat.XML -> BomGeneratorFactory.createXml(schemaVersion, bom) as Any
                FileFormat.JSON -> {
                    // JSON output cannot handle extensible types (see [1]), so simply remove them. As JSON output is
                    // guaranteed to be the last format serialized, it is okay to modify the BOM here without doing a
                    // deep copy first.
                    //
                    // [1] https://github.com/CycloneDX/cyclonedx-core-java/issues/99.
                    val bomWithoutExtensibleTypes = bom.apply {
                        components.forEach { component ->
                            component.extensibleTypes = null
                            component.licenseChoice.licenses.forEach { license ->
                                license.extensibleTypes = null
                            }
                        }
                    }

                    BomGeneratorFactory.createJson(schemaVersion, bomWithoutExtensibleTypes) as Any
                }
                else -> throw IllegalArgumentException("Unsupported CycloneDX file format '$fileFormat'.")
            }

            outputFile.bufferedWriter().use { it.write(bomGenerator.toString()) }
            writtenFiles += outputFile
        }

        return writtenFiles
    }
}

/**
 * Return the license names of all licenses that have any of the given [sources] disregarding the excluded state.
 */
private fun ResolvedLicenseInfo.getLicenseNames(vararg sources: LicenseSource): SortedSet<String> =
    licenses.filter { license -> sources.any { it in license.sources } }.mapTo(sortedSetOf()) { it.license.toString() }
