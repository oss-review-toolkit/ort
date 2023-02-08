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

package org.ossreviewtoolkit.cli.commands

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.net.URI

import org.ossreviewtoolkit.cli.OrtCommand
import org.ossreviewtoolkit.cli.utils.inputGroup
import org.ossreviewtoolkit.cli.utils.logger
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
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.readValueOrDefault
import org.ossreviewtoolkit.model.utils.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.model.utils.toClearlyDefinedSourceLocation
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

class UploadCurationsCommand : OrtCommand(
    name = "upload-curations",
    help = "Upload ORT package curations to ClearlyDefined."
) {
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
        help = "The ClearlyDefined server to upload to."
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
            val pkg = Package.EMPTY.copy(id = curation.id)
            pkg.toClearlyDefinedCoordinates()?.let { curation to it }
        }.toMap()

        val definitions = service.getDefinitionsChunked(curationsToCoordinates.values)

        val curationsByHarvestStatus = curations.groupBy { curation ->
            definitions[curationsToCoordinates[curation]]?.getHarvestStatus() ?: logger.warn {
                "No definition data available for package '${curation.id.toCoordinates()}', cannot request a harvest " +
                        "or upload curations for it."
            }
        }

        val unharvestedCurations = curationsByHarvestStatus[HarvestStatus.NOT_HARVESTED].orEmpty()

        unharvestedCurations.forEach { curation ->
            val definitionUrl = "${server.webUrl}/definitions/${curationsToCoordinates[curation]}"

            println(
                "Package '${curation.id.toCoordinates()}' was not harvested until now, but harvesting was requested. " +
                        "Check $definitionUrl for the harvesting status."
            )
        }

        var uploadedCurationsCount = 0
        val uploadableCurations = curationsByHarvestStatus[HarvestStatus.HARVESTED].orEmpty() +
                curationsByHarvestStatus[HarvestStatus.PARTIALLY_HARVESTED].orEmpty()

        uploadableCurations.forEachIndexed { index, curation ->
            val patch = curation.toContributionPatch()

            if (patch == null) {
                println(
                    "Unable to convert $curation (${index + 1} of ${uploadableCurations.size}) to a contribution patch."
                )
            } else {
                print(
                    "Curation ${index + 1} of ${uploadableCurations.size} for package '${curation.id.toCoordinates()}' "
                )

                runCatching {
                    service.callBlocking { putCuration(patch) }
                }.onSuccess {
                    println("was uploaded successfully:\n${it.url}")
                    ++uploadedCurationsCount
                }.onFailure {
                    println("failed to be uploaded.")
                }
            }
        }

        println("Successfully uploaded $uploadedCurationsCount of ${uploadableCurations.size} curations.")

        if (uploadedCurationsCount != uploadableCurations.size) {
            println("At least one curation failed to be uploaded.")
            throw ProgramResult(2)
        }
    }
}

private fun PackageCuration.toContributionPatch(): ContributionPatch? {
    val pkg = Package.EMPTY.copy(id = id)
    val coordinates = pkg.toClearlyDefinedCoordinates() ?: return null

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
        sourceLocation = pkg.toClearlyDefinedSourceLocation(data.vcs, data.sourceArtifact)
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
