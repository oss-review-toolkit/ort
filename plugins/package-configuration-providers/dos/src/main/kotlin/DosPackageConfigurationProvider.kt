/*
 * SPDX-FileCopyrightText: 2023 Double Open Oy
 *
 * SPDX-License-Identifier: MIT
 */
package org.ossreviewtoolkit.plugins.packageconfigurationproviders.dos

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

import org.apache.logging.log4j.kotlin.Logging

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.clients.dos.*
import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.*
import org.ossreviewtoolkit.model.utils.PackageConfigurationProvider
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.utils.common.Options
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.NONE
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

data class DosPackageConfigurationProviderConfig(
    /** The URL where the DOS service is running. */
    val serverUrl: String,

    /** Backend REST messaging timeout **/
    val restTimeout: Int,

    /** The secret token to use with the DOS service **/
    val serverToken: String
)

open class DosPackageConfigurationProviderFactory :
    PackageConfigurationProviderFactory<DosPackageConfigurationProviderConfig> {
    override val type = "DOS"
    
    override fun create(config: DosPackageConfigurationProviderConfig) = DosPackageConfigurationProvider(config)

    override fun parseConfig(options: Options, secrets: Options) =
        DosPackageConfigurationProviderConfig(
            serverUrl = options.getValue("serverUrl").toString(),
            restTimeout = options["restTimeout"]?.toInt() ?: 60,
            serverToken = System.getenv("SERVER_TOKEN") ?: throw Exception("SERVER_TOKEN not set")
        )
}
/**
 * A [PackageConfigurationProvider] that loads [PackageConfiguration]s from a DOS service.
 */
class DosPackageConfigurationProvider(config: DosPackageConfigurationProviderConfig) : PackageConfigurationProvider {
    private companion object : Logging
    private val serverUrl = config.serverUrl
    private val restTimeout = config.restTimeout
    private val service = DOSService.create(serverUrl, config.serverToken, restTimeout)
    private var repository = DOSRepository(service)

    override fun getPackageConfigurations(packageId: Identifier, provenance: Provenance): List<PackageConfiguration> {
        val purl = packageId.toPurl()
        var packageResults: DOSService.PackageConfigurationResponseBody?

        runBlocking {
            packageResults = repository.postPackageConfiguration(purl)
            if (packageResults == null) {
                logger.info { "Could not request package configurations for this package" }
                return@runBlocking
            }
        }

        return if (packageResults?.licenseConclusions?.isEmpty() == true && packageResults?.pathExclusions?.isEmpty() == true) {
            logger.info { "No package configuration found for $purl" }
            emptyList()
        } else {
            val packageConfiguration = generatePackageConfiguration(
                id = packageId,
                provenance = provenance,
                packageResults = packageResults!!
            )
            logger.info { "Found package configuration for $purl: $packageConfiguration" }
            listOf(packageConfiguration)
        }
    }
}

internal fun generatePackageConfiguration(
    id: Identifier,
    provenance: Provenance,
    packageResults: DOSService.PackageConfigurationResponseBody): PackageConfiguration {

    val sourceArtifactUrl = if (provenance is ArtifactProvenance) {
        provenance.sourceArtifact.url
    } else {
        null
    }
    val vcs = if (provenance is RepositoryProvenance) {
        VcsMatcher(
            type = provenance.vcsInfo.type,
            url = provenance.vcsInfo.url,
            revision = provenance.resolvedRevision
        )
    } else {
        null
    }

    val licenseFindingCurations = packageResults.licenseConclusions.map {
        val detected = if (it.detectedLicenseExpressionSPDX == "") {
            SpdxExpression.parse("NONE")
        } else {
            SpdxExpression.parse(it.detectedLicenseExpressionSPDX!!)
        }
        LicenseFindingCuration(
            path = it.path,
            startLines = emptyList(),
            detectedLicense = detected,
            concludedLicense = SpdxExpression.parse(it.concludedLicenseExpressionSPDX ?: NONE),
            reason = LicenseFindingCurationReason.INCORRECT,
            comment = it.comment ?: ""
        )
    }
    val pathExcludes = packageResults.pathExclusions.map {
        PathExclude(
            pattern = it.pattern,
            reason = PathExcludeReason.valueOf(it.reason),
            comment = it.comment ?: ""
        )
    }
    return PackageConfiguration(
        id = id,
        sourceArtifactUrl = sourceArtifactUrl,
        vcs = vcs,
        pathExcludes = pathExcludes,
        licenseFindingCurations = licenseFindingCurations
    )
}
