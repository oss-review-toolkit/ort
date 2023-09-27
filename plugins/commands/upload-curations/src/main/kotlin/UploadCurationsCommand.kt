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

import java.net.URI

import org.apache.logging.log4j.kotlin.Logging

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
import org.ossreviewtoolkit.clients.clearlydefined.callBlocking
import org.ossreviewtoolkit.clients.clearlydefined.getDefinitionsChunked
import org.ossreviewtoolkit.clients.clearlydefined.toCoordinates
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.readValueOrDefault
import org.ossreviewtoolkit.model.utils.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.model.utils.toClearlyDefinedSourceLocations
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.utils.inputGroup
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

class UploadCurationsCommand : OrtCommand(
    name = "upload-curations",
    help = "Upload ORT package curations to ClearlyDefined."
) {
    private companion object : Logging

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

    private val service by lazy { ClearlyDefinedService.create(server, OkHttpClientHelper.buildClient()) }

    override fun run() {
        val allCurations = inputFile.readValueOrDefault(emptyList<PackageCuration>())

        val curations = allCurations.groupBy { it.id }.mapValues { (id, pkgCurations) ->
            val mergedData = pkgCurations.fold(PackageCurationData()) { current, other ->
                current.merge(other.data)
            }

            PackageCuration(id, mergedData)
        }.values

        val curationsToCoordinates = curations.mapNotNull { curation ->
            val coordinates = curation.toClearlyDefinedCoordinates()

            if (coordinates.isNotEmpty()) {
                curation to coordinates
            } else {
                logger.warn { "Unable to get ClearlyDefined coordinates for $curation." }

                null
            }
        }.toMap()

        val allCoordinates = curationsToCoordinates.values.flatten()
        val definitions = service.getDefinitionsChunked(allCoordinates)

        val coordinatesByHarvestStatus = allCoordinates.groupBy { coordinates ->
            definitions[coordinates]?.getHarvestStatus() ?: logger.warn {
                "No definition data available for $coordinates, cannot request a harvest or upload curations for it."
            }
        }

        val unharvestedCoordinates = coordinatesByHarvestStatus[HarvestStatus.NOT_HARVESTED].orEmpty()

        unharvestedCoordinates.forEach { coordinates ->
            val definitionUrl = "${server.webUrl}/definitions/$coordinates"

            echo(
                "$coordinates was not harvested until now, but harvesting was requested. Check " +
                    "$definitionUrl for the harvesting status."
            )
        }

        var uploadedCurationsCount = 0
        val uploadableCurations = coordinatesByHarvestStatus[HarvestStatus.HARVESTED].orEmpty() +
            coordinatesByHarvestStatus[HarvestStatus.PARTIALLY_HARVESTED].orEmpty()
        val count = uploadableCurations.size

        uploadableCurations.forEachIndexed { index, curation ->
            val patches = curation.toPatches()

            if (patches.isEmpty()) {
                echo("Unable to convert $curation (${index + 1} of $count) to a contribution patch.")
            } else {
                echo(
                    "Curation ${index + 1} of $count for package '${curation.id.toCoordinates()}' ",
                    trailingNewline = false
                )

                runCatching {
                    patches.map { patch ->
                        val info = ContributionInfo(
                            // The exact values to use here are unclear; use what is mostly used at
                            // https://github.com/clearlydefined/curated-data/pulls.
                            type = ContributionType.OTHER,
                            summary = "Curation for component ${patch.coordinates}.",
                            details = "Imported from curation data of the " +
                                "[OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) via the " +
                                "[clearly-defined](https://github.com/oss-review-toolkit/ort/tree/main/clients/clearly-defined) " +
                                "module.",
                            resolution = curation.data.comment ?: "Unknown, original data contains no comment.",
                            removedDefinitions = false
                        )

                        service.callBlocking { putCuration(ContributionPatch(info, listOf(patch))) }
                    }
                }.onSuccess { summaries ->
                    val urls = summaries.joinToString("\n") { it.url }
                    echo("was uploaded successfully:\n$urls")

                    ++uploadedCurationsCount
                }.onFailure {
                    echo("failed to be uploaded.")
                }
            }
        }

        echo("Successfully uploaded $uploadedCurationsCount of $count curations.")

        if (uploadedCurationsCount != count) {
            echo("At least one curation failed to be uploaded.")
            throw ProgramResult(2)
        }
    }
}

private fun PackageCuration.toPatches(): List<Patch> {
    // In ORT's own PackageCuration format, the Package that the PackageCurationData should apply to is solely
    // identified by the Identifier. That is, there is no PackageProvider information available in PackageCuration for
    // ClearlyDefined to use. So simply construct an empty Package and rely on the default Provider per ComponentType.
    val pkg = Package.EMPTY.copy(id = id)

    // TODO: Find out how to handle VCS curations without a revision.
    return pkg.toClearlyDefinedSourceLocations().map { sourceLocation ->
        val described = CurationDescribed(
            projectWebsite = data.homepageUrl?.let { URI(it) },
            sourceLocation = sourceLocation
        )

        val licenseExpression = data.concludedLicense?.toString()

        val curation = Curation(
            described = described.takeIf { it != CurationDescribed() },
            licensed = licenseExpression?.let { CurationLicensed(declared = it) }
        )

        Patch(
            coordinates = sourceLocation.toCoordinates(),
            revisions = mapOf(id.version to curation)
        )
    }
}
