/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.utils

import java.net.URI
import java.util.SortedMap

/**
 * Defines a mapping for VCS URLs which can be used to derive the original VCS URL from a VCS URL for a repository
 * mirror.
 */
internal data class VcsUrlMapping(
    /**
     * Map the hostname of a VCS mirror to the hostname of the original VCS.
     */
    val hostnames: SortedMap<String, String> = sortedMapOf()
) {
    /**
     * Applies the mapping defined by this [VcsUrlMapping].
     */
    fun map(vcsUrl: String): String {
        val uri = URI(vcsUrl)

        return hostnames[uri.host]?.let { uri.replaceHost(it) } ?: vcsUrl
    }
}

internal fun VcsUrlMapping?.orEmpty() = this ?: VcsUrlMapping()

private fun URI.replaceHost(host: String) = URI(scheme, userInfo, host, port, path, query, fragment).toString()
