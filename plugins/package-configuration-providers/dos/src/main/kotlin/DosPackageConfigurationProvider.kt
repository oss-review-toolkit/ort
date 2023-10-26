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
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.*
import org.ossreviewtoolkit.model.utils.PackageConfigurationProvider
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.utils.common.Options
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
 * A [PackageConfigurationProvider] that loads [PackageConfiguration]s from all given package configuration files.
 * Supports all file formats specified in [FileFormat].
 */
class DosPackageConfigurationProvider(config: DosPackageConfigurationProviderConfig) : PackageConfigurationProvider {
    private companion object : Logging
    private val serverUrl = config.serverUrl
    private val restTimeout = config.restTimeout
    private val service = DOSService.create(serverUrl, config.serverToken, restTimeout)
    private var repository = DOSRepository(service)
    private var packageResults: DOSService.PackageConfigurationResponseBody? = null

    override fun getPackageConfigurations(packageId: Identifier, provenance: Provenance): List<PackageConfiguration> {
        val purl = packageId.toPurl()
        logger.info { "Loading package configuration for ${packageId.toPurl()} from $serverUrl with timeout = $restTimeout" }

        runBlocking {
            packageResults = repository.postPackageConfiguration(purl)
            logger.info { "Configuration results for this package: $packageResults" }
        }

        if (packageResults != null) {
            val packageConfiguration = generatePackageConfiguration(
                id = packageId,
                sourceArtifactUrl = null,
                vcs = null,
                jsonString = packageResults.toString()
            )
            return listOf(packageConfiguration)
        } else {
            logger.info { "No package configuration found for $purl" }
            return emptyList()
        }
    }
}

internal fun generatePackageConfiguration(
    id: Identifier,
    sourceArtifactUrl: String?,
    vcs: VcsMatcher?,
    jsonString: String): PackageConfiguration {

    val mapper = ObjectMapper()
    val result: JsonNode = mapper.readTree(jsonString)
    val pathExcludes = getPathExcludes(result)
    val licenseFindingCurations = getLicenseFindingCurations(result)
    return PackageConfiguration(
        id = id,
        sourceArtifactUrl = sourceArtifactUrl,
        vcs = vcs,
        pathExcludes = pathExcludes,
        licenseFindingCurations = licenseFindingCurations
    )
}

private fun getLicenseFindingCurations(result: JsonNode): List<LicenseFindingCuration> {
    val licenseFindingCurations = mutableListOf<LicenseFindingCuration>()
    // For safety, do an early return when no curations are defined
    val licenseFindingCurationsNode = result["licenseFindingCurations"] ?: return emptyList()

    licenseFindingCurationsNode.forEach { licenseFindingCurationNode ->
        val licenseFindingCuration = LicenseFindingCuration(
            path = licenseFindingCurationNode["path"].asText(),
            startLines = emptyList(),
            detectedLicense = licenseFindingCurationNode["detectedLicenseExpressionSPDX"].asText().let {
                SpdxExpression.Companion.parse(it)
            },
            concludedLicense = licenseFindingCurationNode["concludedLicenseExpressionSPDX"].asText().let {
                SpdxExpression.Companion.parse(it)
            },
            reason = LicenseFindingCurationReason.INCORRECT,
            comment = licenseFindingCurationNode["comment"].asText()
        )
        licenseFindingCurations.add(licenseFindingCuration)
    }
    return licenseFindingCurations
}

private fun getPathExcludes(result: JsonNode): List<PathExclude> {
    val pathExcludes = mutableListOf<PathExclude>()
    // For safety, do an early return when no path excludes are defined
    val pathExcludesNode = result["pathExcludes"] ?: return emptyList()

    pathExcludesNode.forEach { pathExcludeNode ->
        val pathExclude = PathExclude(
            pattern = pathExcludeNode["pattern"].asText(),
            reason = pathExcludeNode["reason"].asText().let {
                PathExcludeReason.valueOf(it)
            },
            comment = pathExcludeNode["comment"].asText()
        )
        pathExcludes.add(pathExclude)
    }
    return pathExcludes
}
