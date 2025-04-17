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

package org.ossreviewtoolkit.plugins.packageconfigurationproviders.dos

import java.time.Duration

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.dos.DosClient
import org.ossreviewtoolkit.clients.dos.DosService
import org.ossreviewtoolkit.clients.dos.PackageConfigurationResponseBody
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.LicenseFindingCurationReason
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.VcsMatcher
import org.ossreviewtoolkit.model.utils.associateLicensesWithExceptions
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.model.utils.toPurlExtras
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.api.Secret
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.utils.ort.runBlocking
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

data class DosPackageConfigurationProviderConfig(
    /** The URL where the DOS backend is running. */
    val url: String,

    /** The secret token to use with the DOS backend. */
    val token: Secret,

    /** The timeout for communicating with the DOS backend, in seconds. */
    val timeout: Long?
)

/**
 * A [PackageConfigurationProvider] that loads [PackageConfiguration]s from a Double Open Server instance.
 */
@OrtPlugin(
    id = "DOS",
    displayName = "Double Open Server",
    description = "A package configuration provider that loads package configurations from a Double Open Server " +
        "instance.",
    factory = PackageConfigurationProviderFactory::class
)
class DosPackageConfigurationProvider(
    override val descriptor: PluginDescriptor = DosPackageConfigurationProviderFactory.descriptor,
    config: DosPackageConfigurationProviderConfig
) : PackageConfigurationProvider {
    private val service =
        DosService.create(config.url, config.token.value, config.timeout?.let { Duration.ofSeconds(it) })
    private val client = DosClient(service)

    override fun getPackageConfigurations(packageId: Identifier, provenance: Provenance): List<PackageConfiguration> {
        val purl = packageId.toPurl(provenance.toPurlExtras())

        val packageResults = runBlocking { client.getPackageConfiguration(purl) }
            ?.takeUnless { it.licenseConclusions.isEmpty() && it.pathExclusions.isEmpty() } ?: return emptyList()

        val packageConfiguration = packageResults.toPackageConfiguration(packageId, provenance)

        logger.info { "Found license conclusions for $purl: ${packageResults.licenseConclusions}" }
        logger.info { "Found path exclusions for $purl: ${packageResults.pathExclusions}" }

        return listOf(packageConfiguration)
    }
}

private fun PackageConfigurationResponseBody.toPackageConfiguration(
    id: Identifier,
    provenance: Provenance
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

    val licenseFindingCurations = licenseConclusions.map { licenseConclusion ->
        // The "detectedLicenseExpressionSPDX" comes straight from ScanCode's raw results. As the DOS scanner is
        // currently written in TypeScript and thus cannot make use of ORT's post-processing of ScanCode results,
        // the call of "associateLicensesWithExceptions()" is postponed to here.
        val detected = licenseConclusion.detectedLicenseExpressionSPDX?.takeUnless { it.isEmpty() }
            ?.let { SpdxExpression.parse(it) }
            ?.let { associateLicensesWithExceptions(it) }

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

    val pathExcludes = pathExclusions.map {
        PathExclude(
            pattern = it.pattern,
            reason = it.reason,
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
