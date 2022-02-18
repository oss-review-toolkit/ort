/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import com.fasterxml.jackson.module.kotlin.readValue

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.io.IOException
import java.net.URI

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.cli.utils.inputGroup
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionInfo
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionPatch
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.clients.clearlydefined.ContributionType
import org.ossreviewtoolkit.clients.clearlydefined.Curation
import org.ossreviewtoolkit.clients.clearlydefined.CurationDescribed
import org.ossreviewtoolkit.clients.clearlydefined.CurationLicensed
import org.ossreviewtoolkit.clients.clearlydefined.ErrorResponse
import org.ossreviewtoolkit.clients.clearlydefined.HarvestStatus
import org.ossreviewtoolkit.clients.clearlydefined.Patch
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.readValueOrDefault
import org.ossreviewtoolkit.model.utils.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.model.utils.toClearlyDefinedSourceLocation
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.log

import retrofit2.HttpException

class UploadCurationsCommand : CliktCommand(
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

    private fun <S, T> S.call(block: suspend S.() -> T): T =
        try {
            runBlocking { block() }
        } catch (e: HttpException) {
            val errorMessage = e.response()?.errorBody()?.let {
                val errorResponse = jsonMapper.readValue<ErrorResponse>(it.string())
                val innerError = errorResponse.error.innererror

                log.debug { innerError.stack }

                "The HTTP service call failed with: ${innerError.message}"
            } ?: "The HTTP service call failed with code ${e.code()}: ${e.message()}"

            throw IOException(errorMessage, e)
        }

    override fun run() {
        val allCurations = inputFile.readValueOrDefault(emptyList<PackageCuration>())

        val curations = allCurations.groupBy { it.id }.mapValues { (id, pkgCurations) ->
            val mergedData = pkgCurations.fold(PackageCurationData()) { current, other ->
                current.merge(other.data)
            }

            PackageCuration(id, mergedData)
        }.values

        val curationsToCoordinates = curations.mapNotNull { curation ->
            curation.id.toClearlyDefinedCoordinates()?.let { coordinates ->
                curation to coordinates
            }
        }.toMap()

        val definitions = service.call { getDefinitions(curationsToCoordinates.values) }

        val curationsByHarvestStatus = curations.groupBy { curation ->
            definitions[curationsToCoordinates[curation]]?.getHarvestStatus() ?: log.warn {
                "No definition data available for package '${curation.id.toCoordinates()}', cannot request a harvest " +
                        "or upload curations for it."
            }
        }

        val unharvestedCurations = curationsByHarvestStatus[HarvestStatus.NOT_HARVESTED].orEmpty()

        unharvestedCurations.forEach { curation ->
            val webServerUrl = server.url.replaceFirst("dev-api.", "dev.").replaceFirst("api.", "")
            val definitionUrl = "$webServerUrl/definitions/${curationsToCoordinates[curation]}"

            println(
                "Package '${curation.id.toCoordinates()}' was not harvested until now, but harvesting was requested. " +
                        "Check $definitionUrl for the harvesting status."
            )
        }

        var uploadedCurationsCount = 0
        val uploadableCurations = curationsByHarvestStatus[HarvestStatus.HARVESTED].orEmpty() +
                curationsByHarvestStatus[HarvestStatus.PARTIALLY_HARVESTED].orEmpty()

        uploadableCurations.forEachIndexed { index, curation ->
            print("Curation ${index + 1} of ${uploadableCurations.size} for package '${curation.id.toCoordinates()}' ")

            when (val summary = curation.toContributionPatch()?.let { service.call { putCuration(it) } }) {
                null -> println("failed to be uploaded.")
                else -> {
                    println("was uploaded successfully:\n${summary.url}")

                    ++uploadedCurationsCount
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
    val coordinates = id.toClearlyDefinedCoordinates() ?: return null

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
        sourceLocation = id.toClearlyDefinedSourceLocation(data.vcs, data.sourceArtifact)
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
