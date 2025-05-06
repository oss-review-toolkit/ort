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

package org.ossreviewtoolkit.plugins.commands.uploadcurations

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.Theme

import java.net.URI

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionInfo
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionPatch
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.clients.clearlydefined.ContributionType
import org.ossreviewtoolkit.clients.clearlydefined.Curation
import org.ossreviewtoolkit.clients.clearlydefined.CurationDescribed
import org.ossreviewtoolkit.clients.clearlydefined.CurationLicensed
import org.ossreviewtoolkit.clients.clearlydefined.HarvestStatus
import org.ossreviewtoolkit.clients.clearlydefined.Patch
import org.ossreviewtoolkit.clients.clearlydefined.getDefinitionsChunked
import org.ossreviewtoolkit.clients.clearlydefined.toCoordinates
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.readValueOrDefault
import org.ossreviewtoolkit.model.utils.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.model.utils.toClearlyDefinedSourceLocation
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.OrtCommandFactory
import org.ossreviewtoolkit.plugins.commands.api.utils.inputGroup
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.okHttpClient
import org.ossreviewtoolkit.utils.ort.runBlocking

@OrtPlugin(
    displayName = "Upload Curations",
    description = "Upload ORT package curations to ClearlyDefined.",
    factory = OrtCommandFactory::class
)
class UploadCurationsCommand(
    descriptor: PluginDescriptor = UploadCurationsCommandFactory.descriptor
) : OrtCommand(descriptor) {
    private val inputFile by option(
        "--input-file", "-i",
        help = "The file with package curations to upload."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
        .inputGroup()

    private val server by option(
        "--server", "-s",
        help = "The ClearlyDefined server to upload to. Must be one of ${Server.entries.map { it.name }}."
    ).enum<Server>().default(Server.DEVELOPMENT)

    private val service by lazy { ClearlyDefinedService.create(server, okHttpClient) }

    override fun run() {
        val allCurations = inputFile.readValueOrDefault(emptyList<PackageCuration>())

        val curations = allCurations.groupBy { it.id }.mapValues { (id, pkgCurations) ->
            val mergedData = pkgCurations.fold(PackageCurationData()) { current, other ->
                current.merge(other.data)
            }

            PackageCuration(id, mergedData)
        }.values

        val curationsToCoordinates = curations.mapNotNull { curation ->
            val pkg = Package.EMPTY.copy(id = curation.id)
            pkg.toClearlyDefinedCoordinates()?.let { curation to it }
        }.toMap()

        val definitions = runBlocking { service.getDefinitionsChunked(curationsToCoordinates.values) }

        val curationsByHarvestStatus = curations.groupBy { curation ->
            definitions[curationsToCoordinates[curation]]?.getHarvestStatus() ?: logger.warn {
                "No definition data available for package '${curation.id.toCoordinates()}', cannot request a harvest " +
                    "or upload curations for it."
            }
        }

        val unharvestedCurations = curationsByHarvestStatus[HarvestStatus.NOT_HARVESTED].orEmpty()

        unharvestedCurations.forEach { curation ->
            val definitionUrl = "${server.webUrl}/definitions/${curationsToCoordinates[curation]}"

            echo(
                "Package '${curation.id.toCoordinates()}' was not harvested until now, but harvesting was requested. " +
                    "Check $definitionUrl for the harvesting status."
            )
        }

        var uploadedCurationsCount = 0
        val uploadableCurations = curationsByHarvestStatus[HarvestStatus.HARVESTED].orEmpty() +
            curationsByHarvestStatus[HarvestStatus.PARTIALLY_HARVESTED].orEmpty()
        val count = uploadableCurations.size

        uploadableCurations.forEachIndexed { index, curation ->
            val patch = curation.toContributionPatch()

            if (patch == null) {
                echo("Unable to convert $curation (${index + 1} of $count) to a contribution patch.")
            } else {
                echo(
                    "Curation ${index + 1} of $count for package '${curation.id.toCoordinates()}' ",
                    trailingNewline = false
                )

                runCatching {
                    runBlocking { service.putCuration(patch) }
                }.onSuccess {
                    echo("was uploaded successfully:\n${it.url}")
                    ++uploadedCurationsCount
                }.onFailure {
                    echo("failed to be uploaded.")
                }
            }
        }

        echo("Successfully uploaded $uploadedCurationsCount of $count curations.")

        if (uploadedCurationsCount != count) {
            echo(Theme.Default.danger("At least one curation failed to be uploaded."))
            throw ProgramResult(2)
        }
    }
}

private fun PackageCuration.toContributionPatch(): ContributionPatch? {
    // In ORT's own PackageCuration format, the Package that the PackageCurationData should apply to is solely
    // identified by the Identifier. That is, there is no PackageProvider information available in PackageCuration for
    // ClearlyDefined to use. So simply construct an empty Package and rely on the default Provider per ComponentType.
    val pkg = Package.EMPTY.copy(id = id)

    val sourceLocation = pkg.toClearlyDefinedSourceLocation() ?: return null
    val coordinates = sourceLocation.toCoordinates()

    val info = ContributionInfo(
        // The exact values to use here are unclear; use what is mostly used at
        // https://github.com/clearlydefined/curated-data/pulls.
        type = ContributionType.OTHER,
        summary = "Curation for component $coordinates.",
        details = "Imported from curation data of the " +
            "[OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) via the " +
            "[clearly-defined](https://github.com/oss-review-toolkit/ort/tree/main/clients/clearly-defined) " +
            "module.",
        resolution = data.comment ?: "Unknown, original data contains no comment.",
        removedDefinitions = false
    )

    val licenseExpression = data.concludedLicense?.toString()

    val described = CurationDescribed(
        projectWebsite = data.homepageUrl?.let { URI(it) },
        sourceLocation = sourceLocation
    )

    val curation = Curation(
        described = described.takeIf { it != CurationDescribed() },
        licensed = licenseExpression?.let { CurationLicensed(declared = it) }
    )

    val patch = Patch(
        coordinates = coordinates,
        revisions = mapOf(id.version to curation)
    )

    return ContributionPatch(info, listOf(patch))
}
