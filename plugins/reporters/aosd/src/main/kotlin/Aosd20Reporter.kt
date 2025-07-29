/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.aosd

import java.io.File

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.reporters.aosd.AOSD20.ExternalDependency
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.spdx.SpdxLicense

@OrtPlugin(
    id = "AOSD2.0",
    displayName = "Audi Open Source Diagnostics 2.0",
    description = "A reporter for the Audi Open Source Diagnostics (AOSD) 2.0 format.",
    factory = ReporterFactory::class
)
class Aosd20Reporter(override val descriptor: PluginDescriptor = Aosd20ReporterFactory.descriptor) : Reporter {
    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val reportFiles = input.ortResult.getProjects(omitExcluded = true).map { project ->
            val directDependencies = input.ortResult.getDependencies(project.id, maxLevel = 1, omitExcluded = true)
                .map { it.toCoordinates() }

            val dependencies = input.ortResult.getPackages(omitExcluded = true).map {
                it.metadata.toExternalDependency(input)
            }

            runCatching {
                val model = AOSD20(directDependencies = directDependencies, dependencies = dependencies)
                val projectName = project.id.toPath("-")

                outputDir.resolve("aosd20.$projectName.json").writeReport(model)
            }
        }

        return reportFiles
    }
}

private fun Package.toExternalDependency(input: ReporterInput): ExternalDependency {
    val licenses = toLicenses(input)

    // There has to be at least one part. As nothing is known about the logical layout of the external dependency,
    // assume that there are no separate parts and always create a default one.
    val defaultPart = AOSD20.Part(
        name = "default",
        providers = listOf(AOSD20.Provider(additionalLicenses = licenses))
    )

    return ExternalDependency(
        id = id.toCoordinates(),
        name = id.name,
        scmUrl = vcsProcessed.url.takeUnless { it.isEmpty() },
        description = description.takeUnless { it.isEmpty() },
        version = id.version,
        licenses = licenses,
        parts = listOf(defaultPart),
        deployPackage = binaryArtifact.toDeployPackage(),
        externalDependencies = input.ortResult.getDependencies(id, maxLevel = 1, omitExcluded = true).map {
            it.toCoordinates()
        }
    )
}

private fun Package.toLicenses(input: ReporterInput): List<AOSD20.License> {
    val licenses = mutableListOf<AOSD20.License>()
    val resolvedLicenseInfo = input.licenseInfoResolver.resolveLicenseInfo(id).filterExcluded()

    fun getLicenses(
        licenseView: LicenseView,
        origin: AOSD20.Origin,
        copyrights: List<String> = emptyList()
    ): List<AOSD20.License> {
        val effectiveLicense = resolvedLicenseInfo.effectiveLicense(
            licenseView,
            input.ortResult.getPackageLicenseChoices(id),
            input.ortResult.getRepositoryLicenseChoices()
        )

        return effectiveLicense?.decompose()?.map { licenseExpression ->
            val name = licenseExpression.toString()
            val text = input.licenseFactProvider.getLicenseText(name)

            AOSD20.License(
                name = name,
                spdxId = SpdxLicense.forId(name)?.id,
                text = text.orEmpty(),
                copyrights = copyrights.takeUnless { it.isEmpty() }?.let { AOSD20.Copyrights(copyrights) },
                origin = origin
            )
        }.orEmpty()
    }

    val copyrights = resolvedLicenseInfo.getCopyrights().toList()

    // Declared licenses come from package management metadata.
    licenses += getLicenses(LicenseView.ONLY_DECLARED, AOSD20.Origin.PACKAGE_MANAGEMENT)

    // Group licenses detected by a scanner by their provenance / origin.
    val provenance = input.ortResult.scanner?.provenances?.find { it.id == id }?.packageProvenance
    when (provenance) {
        is RepositoryProvenance -> licenses += getLicenses(
            LicenseView.ONLY_DETECTED,
            AOSD20.Origin.SCM,
            copyrights
        )

        is ArtifactProvenance -> licenses += getLicenses(
            LicenseView.ONLY_DETECTED,
            AOSD20.Origin.LICENSE_FILE,
            copyrights
        )

        null -> {}
    }

    return licenses
}

private fun RemoteArtifact.toDeployPackage(): AOSD20.DeployPackage =
    AOSD20.DeployPackage(
        name = "default",
        downloadUrl = url.takeUnless { it.isEmpty() },
        checksums = hash.takeIf { it.algorithm.isVerifiable }?.toChecksums()
    )

private fun Hash.toChecksums(): AOSD20.Checksums =
    when (algorithm) {
        // Other algorithms than SHA256 create an error message when importing.
        HashAlgorithm.SHA256 -> AOSD20.Checksums(sha256 = value)
        else -> AOSD20.Checksums(integrity = value)
    }
