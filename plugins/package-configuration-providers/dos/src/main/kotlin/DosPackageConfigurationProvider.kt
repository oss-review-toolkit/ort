/*
 * SPDX-FileCopyrightText: 2023 Double Open Oy
 *
 * SPDX-License-Identifier: MIT
 */
package org.ossreviewtoolkit.plugins.packageconfigurationproviders.dos

import kotlinx.coroutines.runBlocking

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.dos.*
import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.*
import org.ossreviewtoolkit.model.utils.PackageConfigurationProvider
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.model.utils.vcsPath
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
            serverUrl = options.getValue("serverUrl"),
            restTimeout = options["restTimeout"]?.toIntOrNull() ?: 60,
            serverToken = secrets.getValue("serverToken")
        )
}
/**
 * A [PackageConfigurationProvider] that loads [PackageConfiguration]s from a DOS service.
 */
class DosPackageConfigurationProvider(config: DosPackageConfigurationProviderConfig) : PackageConfigurationProvider {
    private val serverUrl = config.serverUrl
    private val restTimeout = config.restTimeout
    private val service = DOSService.create(serverUrl, config.serverToken, restTimeout)
    private var repository = DOSRepository(service)

    override fun getPackageConfigurations(packageId: Identifier, provenance: Provenance): List<PackageConfiguration> {
        val purl = packageId.toPurl(subpath = provenance.vcsPath)

        val packageResults = runBlocking { repository.postPackageConfiguration(purl) }
        if (packageResults == null) {
            logger.info { "Package $purl was scanned as part of another package" }
            return emptyList()
        }

        return if (packageResults.licenseConclusions.isEmpty() && packageResults.pathExclusions.isEmpty()) {
            emptyList()
        } else {
            val packageConfiguration = generatePackageConfiguration(
                id = packageId,
                provenance = provenance,
                packageResults = packageResults
            )
            logger.info { "Found package configuration for $purl:" }
            logger.info { "License conclusions: ${packageResults.licenseConclusions}" }
            logger.info { "Path exclusions: ${packageResults.pathExclusions}" }
            listOf(packageConfiguration)
        }
    }
}

internal fun generatePackageConfiguration(
    id: Identifier,
    provenance: Provenance,
    packageResults: DOSService.PackageConfigurationResponseBody
): PackageConfiguration {
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

    val licenseFindingCurations = packageResults.licenseConclusions.map { licenseConclusion ->
        val detected = licenseConclusion.detectedLicenseExpressionSPDX?.takeUnless { it.isEmpty() }
            ?.let { SpdxExpression.parse(it) }

        check(licenseConclusion.concludedLicenseExpressionSPDX.isNotEmpty())
        val concluded = SpdxExpression.parse(licenseConclusion.concludedLicenseExpressionSPDX)

        LicenseFindingCuration(
            path = licenseConclusion.path,
            detectedLicense = detected,
            concludedLicense = concluded,
            reason = LicenseFindingCurationReason.INCORRECT,
            comment = licenseConclusion.comment.orEmpty()
        )
    }
    val pathExcludes = packageResults.pathExclusions.map {
        PathExclude(
            pattern = it.pattern,
            reason = PathExcludeReason.valueOf(it.reason),
            comment = it.comment.orEmpty()
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
