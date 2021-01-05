/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.commands

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
import java.net.URL

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionInfo
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionPatch
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Curation
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Described
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Licensed
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Patch
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.clients.clearlydefined.ContributionType
import org.ossreviewtoolkit.clients.clearlydefined.ErrorResponse
import org.ossreviewtoolkit.clients.clearlydefined.HarvestStatus
import org.ossreviewtoolkit.clients.clearlydefined.string
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.model.utils.toClearlyDefinedSourceLocation
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.hasNonNullProperty
import org.ossreviewtoolkit.utils.log

import retrofit2.Call

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

    private fun <T> executeApiCall(call: Call<T>): Result<T> {
        log.debug {
            val request = call.request()
            "Going to execute API call at ${request.url} with body:\n${request.body?.string()}"
        }

        val response = call.execute()

        return when {
            response.isSuccessful -> when (val body = response.body()) {
                null -> Result.failure(IOException("The REST API call succeeded but no response body was returned."))
                else -> Result.success(body)
            }

            else -> when (val errorBody = response.errorBody()) {
                null -> Result.failure(IOException("The REST API call failed with code ${response.code()}."))

                else -> {
                    val errorResponse = jsonMapper.readValue<ErrorResponse>(errorBody.string())
                    val innerError = errorResponse.error.innererror

                    log.debug { innerError.stack }

                    Result.failure(IOException("The REST API call failed with: ${innerError.message}"))
                }
            }
        }
    }

    private fun getDefinitions(coordinates: Collection<String>): Map<String, ClearlyDefinedService.Defined> =
        executeApiCall(service.getDefinitions(coordinates)).getOrElse {
            log.error { it.collectMessagesAsString() }
            emptyMap()
        }

    private fun putCuration(curation: PackageCuration): ClearlyDefinedService.ContributionSummary? =
        curation.toContributionPatch()?.let { patch ->
            executeApiCall(service.putCuration(patch)).getOrElse {
                log.error { it.collectMessagesAsString() }
                null
            }
        }

    override fun run() {
        val curations = inputFile.readValue<List<PackageCuration>>()
        val curationsToCoordinates = curations.associateWith { it.id.toClearlyDefinedCoordinates().toString() }
        val definitions = getDefinitions(curationsToCoordinates.values)

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

            when (val summary = putCuration(curation)) {
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
                "[clearly-defined](https://github.com/oss-review-toolkit/ort/tree/master/clearly-defined) " +
                "module.",
        resolution = data.comment ?: "Unknown, original data contains no comment.",
        removedDefinitions = false
    )

    val licenseExpression = data.concludedLicense?.toString() ?: data.declaredLicenses?.joinToString(" AND ")

    val described = Described(
        projectWebsite = data.homepageUrl?.let { URL(it) },
        sourceLocation = id.toClearlyDefinedSourceLocation(data.vcs, data.sourceArtifact)
    )

    val curation = Curation(
        described = described.takeIf { it.hasNonNullProperty() },
        licensed = licenseExpression?.let { Licensed(declared = it) }
    )

    val patch = Patch(
        coordinates = coordinates,
        revisions = mapOf(id.version to curation)
    )

    return ContributionPatch(info, listOf(patch))
}
