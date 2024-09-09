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
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.plugins.reporters.aosd.AOSD2.ExternalDependency
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.spdx.SpdxLicense

class Aosd2Reporter : Reporter {
    override val type = "AOSD2"

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        config: PluginConfiguration
    ): List<Result<File>> {
        val reportFiles = input.ortResult.getProjects(omitExcluded = true).map { project ->
            val directDependencies = input.ortResult.getDependencies(project.id, maxLevel = 1, omitExcluded = true)
                .map { it.toCoordinates() }

            val dependencies = input.ortResult.getPackages(omitExcluded = true).map {
                it.metadata.toExternalDependency(input)
            }

            runCatching {
                val model = AOSD2(directDependencies = directDependencies, dependencies = dependencies)
                val projectName = project.id.toPath("-")

                outputDir.resolve("aosd.$projectName.json").writeReport(model)
            }
        }

        return reportFiles
    }
}

private fun Package.toExternalDependency(input: ReporterInput): ExternalDependency =
    ExternalDependency(
        id = id.toCoordinates(),
        name = id.name,
        scmUrl = vcsProcessed.url.takeUnless { it.isEmpty() },
        description = description.takeUnless { it.isEmpty() },
        version = id.version,
        licenses = toLicenses(input),
        deployPackage = binaryArtifact.toDeployPackage(),
        externalDependencies = input.ortResult.getDependencies(id, maxLevel = 1, omitExcluded = true).map {
            it.toCoordinates()
        }
    )

private fun Package.toLicenses(input: ReporterInput): List<AOSD2.License> {
    val licenses = mutableListOf<AOSD2.License>()
    val resolvedLicenseInfo = input.licenseInfoResolver.resolveLicenseInfo(id).filterExcluded()

    fun getLicenses(
        licenseView: LicenseView,
        origin: AOSD2.Origin,
        copyrights: List<String> = emptyList()
    ): List<AOSD2.License> {
        val effectiveLicense = resolvedLicenseInfo.effectiveLicense(
            licenseView,
            input.ortResult.getPackageLicenseChoices(id),
            input.ortResult.getRepositoryLicenseChoices()
        )

        return effectiveLicense?.decompose()?.map { licenseExpression ->
            val name = licenseExpression.toString()
            val text = input.licenseTextProvider.getLicenseText(name)

            AOSD2.License(
                name = name,
                spdxId = SpdxLicense.forId(name)?.id,
                text = text.orEmpty(),
                copyrights = copyrights.takeUnless { it.isEmpty() }?.let { AOSD2.Copyrights(copyrights) },
                origin = origin
            )
        }.orEmpty()
    }

    val copyrights = resolvedLicenseInfo.getCopyrights().toList()

    // Declared licenses come from package management metadata.
    licenses += getLicenses(LicenseView.ONLY_DECLARED, AOSD2.Origin.PACKAGE_MANAGEMENT)

    // Group licenses detected by a scanner by their provenance / origin.
    val provenance = input.ortResult.scanner?.provenances?.find { it.id == id }?.packageProvenance
    when (provenance) {
        is RepositoryProvenance -> licenses += getLicenses(
            LicenseView.ONLY_DETECTED,
            AOSD2.Origin.SCM,
            copyrights
        )

        is ArtifactProvenance -> licenses += getLicenses(
            LicenseView.ONLY_DETECTED,
            AOSD2.Origin.LICENSE_FILE,
            copyrights
        )

        null -> {}
    }

    return licenses
}

private fun RemoteArtifact.toDeployPackage(): AOSD2.DeployPackage =
    AOSD2.DeployPackage(
        name = "default",
        downloadUrl = url.takeUnless { it.isEmpty() },
        checksums = hash.takeIf { it.algorithm.isVerifiable }?.toChecksums()
    )

private fun Hash.toChecksums(): AOSD2.Checksums =
    when (algorithm) {
        HashAlgorithm.MD5 -> AOSD2.Checksums(md5 = value)
        HashAlgorithm.SHA1 -> AOSD2.Checksums(sha1 = value)
        HashAlgorithm.SHA256 -> AOSD2.Checksums(sha256 = value)
        else -> AOSD2.Checksums(integrity = value)
    }
