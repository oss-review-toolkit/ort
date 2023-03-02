/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.sw360

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper

import org.eclipse.sw360.clients.adapter.SW360Connection
import org.eclipse.sw360.clients.adapter.SW360ConnectionFactory
import org.eclipse.sw360.clients.config.SW360ClientConfig
import org.eclipse.sw360.clients.rest.resource.attachments.SW360AttachmentType
import org.eclipse.sw360.clients.rest.resource.licenses.SW360SparseLicense
import org.eclipse.sw360.clients.rest.resource.releases.SW360ClearingState
import org.eclipse.sw360.clients.rest.resource.releases.SW360Release
import org.eclipse.sw360.http.HttpClientFactoryImpl
import org.eclipse.sw360.http.config.HttpClientConfig

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

class Sw360PackageCurationProviderFactory : PackageCurationProviderFactory<Sw360StorageConfiguration> {
    override val type = "SW360"

    override fun create(config: Sw360StorageConfiguration) = Sw360PackageCurationProvider(config)

    override fun parseConfig(config: Map<String, String>) =
        Sw360StorageConfiguration(
            restUrl = config.getValue("restUrl"),
            authUrl = config.getValue("authUrl"),
            username = config.getValue("username"),
            password = config["password"].orEmpty(),
            clientId = config.getValue("clientId"),
            clientPassword = config["clientPassword"].orEmpty(),
            token = config["token"].orEmpty()
        )
}

/**
 * A [PackageCurationProvider] for curated package metadata from the configured SW360 instance using the REST API.
 */
class Sw360PackageCurationProvider(config: Sw360StorageConfiguration) : PackageCurationProvider {
    companion object {
        val JSON_MAPPER: ObjectMapper = jsonMapper.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun createConnection(config: Sw360StorageConfiguration): SW360Connection {
            val httpClientConfig = HttpClientConfig
                .basicConfig()
                .withObjectMapper(JSON_MAPPER)

            val httpClient = HttpClientFactoryImpl().newHttpClient(httpClientConfig)

            val sw360ClientConfig = SW360ClientConfig.createConfig(
                config.restUrl,
                config.authUrl,
                config.username,
                config.password,
                config.clientId,
                config.clientPassword,
                config.token,
                httpClient,
                JSON_MAPPER
            )

            return SW360ConnectionFactory().newConnection(sw360ClientConfig)
        }
    }

    private val connectionFactory = createConnection(config)
    private val releaseClient = connectionFactory.releaseAdapter

    override fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration> =
        packages.flatMapTo(mutableSetOf()) { pkg -> getCurationsFor(pkg.id) }

    private fun getCurationsFor(pkgId: Identifier): Set<PackageCuration> {
        val name = "${pkgId.namespace}/${pkgId.name}"

        return releaseClient.getSparseReleaseByNameAndVersion(name, pkgId.version)
            .flatMap { releaseClient.enrichSparseRelease(it) }
            .filter { it.sw360ClearingState == SW360ClearingState.APPROVED }
            .map { sw360Release ->
                setOf(
                    PackageCuration(
                        id = pkgId,
                        data = PackageCurationData(
                            concludedLicense = sw360Release.embedded?.licenses.orEmpty().toSpdx(),
                            homepageUrl = getHomepageOfRelease(sw360Release).orEmpty(),
                            binaryArtifact = getAttachmentAsRemoteArtifact(sw360Release, SW360AttachmentType.BINARY)
                                .orEmpty(),
                            sourceArtifact = getAttachmentAsRemoteArtifact(sw360Release, SW360AttachmentType.SOURCE)
                                .orEmpty(),
                            vcs = null,
                            comment = "Provided by SW360."
                        )
                    )
                )
            }
            .orElse(emptySet())
    }

    private fun getAttachmentAsRemoteArtifact(release: SW360Release, type: SW360AttachmentType): RemoteArtifact? =
        release.embedded?.attachments?.singleOrNull { it.attachmentType == type }?.let { attachment ->
            return RemoteArtifact(
                url = attachment.links.self.href,
                hash = Hash(
                    value = attachment.sha1,
                    algorithm = HashAlgorithm.SHA1
                )
            )
        }

    private fun getHomepageOfRelease(release: SW360Release): String? =
        connectionFactory.componentAdapter.getComponentById(release.componentId).orElse(null)?.homepage
}

private fun Collection<SW360SparseLicense>.toSpdx(): SpdxExpression? =
    DeclaredLicenseProcessor.process(mapTo(mutableSetOf()) { it.shortName }).spdxExpression
