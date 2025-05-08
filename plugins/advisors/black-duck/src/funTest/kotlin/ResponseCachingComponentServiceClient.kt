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
import com.blackduck.integration.blackduck.api.generated.response.ComponentsView
import com.blackduck.integration.blackduck.api.generated.view.OriginView
import com.blackduck.integration.blackduck.api.generated.view.VulnerabilityView

import com.google.gson.GsonBuilder

import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * This [ComponentServiceClient] uses a cache for responses. The cache is initialized with the content from a preceding
 * run in the given overrideFile.
 *
 * Note: In case the cache initially contains all responses for a particular test, an instance of this class can be used
 * as a fake [ComponentServiceClient].
 */
internal class ResponseCachingComponentServiceClient(
    private val overrideUrl: URL?,
    private val serverUrl: String?,
    apiToken: String?
) : ComponentServiceClient {
    // The Black Duck library uses GSON to serialize its POJOs. So use GSON, too, because this is the simplest option.
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val cache = runCatching {
        // The URL may be null during development when the resource file was deleted in order to record responses anew.
        requireNotNull(overrideUrl)

        gson.fromJson(overrideUrl.readText(), ResponseCache::class.java)
    }.getOrDefault(ResponseCache())

    private val delegate = if (serverUrl != null && apiToken != null) {
        ExtendedComponentService.create(serverUrl, apiToken)
    } else {
        null
    }

    override fun searchKbComponentsByPurl(purl: String): List<ComponentsView> =
        cache.componentsViewsForPurl.getOrPut(purl) {
            delegate?.searchKbComponentsByPurl(purl).orEmpty()
        }

    override fun searchKbComponentsByExternalId(externalId: ExternalId): List<ComponentsView> =
        cache.componentsViewsForExternalId.getOrPut(externalId.createExternalId()) {
            delegate?.searchKbComponentsByExternalId(externalId).orEmpty()
        }

    override fun getOriginView(searchResult: ComponentsView): OriginView? =
        cache.originViewForComponentsViewKey.getOrPut(searchResult.key) {
            delegate?.getOriginView(searchResult)
        }

    override fun getVulnerabilities(originView: OriginView): List<VulnerabilityView> =
        cache.vulnerabilityViewsForOriginViewKey.getOrPut(originView.key) {
            delegate?.getVulnerabilities(originView).orEmpty()
        }

    fun flush() {
        // Skip writing the override file if it is a resource embedded into a JAR.
        val overrideFile = overrideUrl?.takeIf { it.protocol == "file" }?.let { File(it.path) } ?: return

        if (delegate != null) {
            val json = gson.toJson(cache).patchServerUrl(serverUrl)
            overrideFile.writeText(json)
        }
    }
}

private class ResponseCache {
    val componentsViewsForExternalId = ConcurrentHashMap<String, List<ComponentsView>>()
    val componentsViewsForPurl = ConcurrentHashMap<String, List<ComponentsView>>()
    val originViewForComponentsViewKey = ConcurrentHashMap<String, OriginView?>()
    val vulnerabilityViewsForOriginViewKey = ConcurrentHashMap<String, List<VulnerabilityView>>()
}

private val OriginView.key: String get() = "$externalNamespace:$externalId"
private val ComponentsView.key: String
    // Only take the UUID of the version and variant, to avoid including the server URL into the key, to avoid
    // complexities related to the replacement of the server URL.
    get() = variant.substringAfter("/versions/")
