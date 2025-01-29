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

package org.ossreviewtoolkit.plugins.advisors.blackduck

import com.blackduck.integration.bdio.model.externalid.ExternalId
import com.blackduck.integration.blackduck.api.core.BlackDuckPath
import com.blackduck.integration.blackduck.api.core.response.LinkMultipleResponses
import com.blackduck.integration.blackduck.api.generated.discovery.ApiDiscovery
import com.blackduck.integration.blackduck.api.generated.response.ComponentsView
import com.blackduck.integration.blackduck.api.generated.view.OriginView
import com.blackduck.integration.blackduck.api.generated.view.VulnerabilityView
import com.blackduck.integration.blackduck.configuration.BlackDuckServerConfigBuilder
import com.blackduck.integration.blackduck.configuration.BlackDuckServerConfigKeys.KEYS
import com.blackduck.integration.blackduck.http.BlackDuckRequestBuilder
import com.blackduck.integration.blackduck.service.BlackDuckApiClient
import com.blackduck.integration.blackduck.service.BlackDuckServicesFactory
import com.blackduck.integration.blackduck.service.dataservice.ComponentService
import com.blackduck.integration.log.IntLogger
import com.blackduck.integration.log.SilentIntLogger
import com.blackduck.integration.rest.HttpUrl
import com.blackduck.integration.util.IntEnvironmentVariables

import java.util.concurrent.Executors

// Parameter for BlackDuck services factory, see also
// https://github.com/blackducksoftware/blackduck-common/blob/67.0.2/src/main/java/com/blackduck/integration/blackduck/service/BlackDuckServicesFactory.java#L82-L84
private const val BLACK_DUCK_SERVICES_THREAD_POOL_SIZE = 30

private val KB_COMPONENTS_SEARCH_PATH = BlackDuckPath(
    "/api/search/kb-purl-component",
    // Use ComponentsView even though SearchKbPurlComponentView is probably the class dedicated to this result,
    // to avoid any conversion to the needed ComponentsView.
    ComponentsView::class.java,
    /* isMultiple = */ true
)

/**
 * This class adds a couple of functions which are missing in the super class.
 */
internal class ExtendedComponentService(
    blackDuckApiClient: BlackDuckApiClient,
    apiDiscovery: ApiDiscovery,
    logger: IntLogger
) : ComponentService(blackDuckApiClient, apiDiscovery, logger), ComponentServiceClient {
    companion object {
        fun create(serverUrl: String, apiToken: String): ExtendedComponentService {
            val logger = SilentIntLogger()
            val factory = createBlackDuckServicesFactory(serverUrl, apiToken, logger)
            return ExtendedComponentService(factory.blackDuckApiClient, factory.apiDiscovery, factory.logger)
        }
    }

    override fun searchKbComponentsByPurl(purl: String): List<ComponentsView> {
        // See https://community.blackduck.com/s/article/Searching-Black-Duck-KnowledgeBase-using-Package-URLs.
        val responses = apiDiscovery.metaMultipleResponses(KB_COMPONENTS_SEARCH_PATH)

        val request = BlackDuckRequestBuilder()
            .commonGet()
            .addQueryParameter("purl", purl)
            .buildBlackDuckRequest(responses)

        return blackDuckApiClient.getAllResponses(request)
    }

    override fun searchKbComponentsByExternalId(externalId: ExternalId): List<ComponentsView> =
        getAllSearchResults(externalId)

    override fun getOriginView(searchResult: ComponentsView): OriginView? {
        if (searchResult.variant.isNullOrBlank()) return null

        val url = HttpUrl(searchResult.variant)
        return blackDuckApiClient.getResponse(url, OriginView::class.java)
    }

    override fun getVulnerabilities(originView: OriginView): List<VulnerabilityView> {
        val link = LinkMultipleResponses("vulnerabilities", VulnerabilityView::class.java)
        val metaVulnerabilitiesLinked = originView.metaMultipleResponses(link)

        return blackDuckApiClient.getAllResponses(metaVulnerabilitiesLinked)
    }
}

private fun createBlackDuckServicesFactory(
    serverUrl: String,
    apiToken: String,
    logger: IntLogger
): BlackDuckServicesFactory {
    val serverConfig = BlackDuckServerConfigBuilder(KEYS.apiToken).apply {
        url = serverUrl
        this.apiToken = apiToken
    }.build()

    val httpClient = serverConfig.createBlackDuckHttpClient(logger)
    val environmentVariables = IntEnvironmentVariables.empty()
    val executorService = Executors.newFixedThreadPool(BLACK_DUCK_SERVICES_THREAD_POOL_SIZE)

    return BlackDuckServicesFactory(environmentVariables, executorService, logger, httpClient)
}
