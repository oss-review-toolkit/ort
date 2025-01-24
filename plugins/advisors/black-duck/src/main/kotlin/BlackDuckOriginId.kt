/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.blackduck.integration.bdio.model.Forge
import com.blackduck.integration.bdio.model.externalid.ExternalId

/**
 * A unique identifier of a Black Duck Origin, see also
 * https://community.blackduck.com/s/article/What-is-an-Origin-and-Origin-ID-in-Blackduck.
 * Note: While the term "origin" is still current, the properties `originType` and`originId`  haven been deprecated in
 * favor of `externalNamespace` and `externalId`.
 */
internal data class BlackDuckOriginId(
    /**
     * The namespace such as 'maven', 'pypi' or 'github'.
     */
    val externalNamespace: String,

    /**
     * The component's identifier within the external namespace.
     */
    val externalId: String
) {
    fun toExternalId(): ExternalId {
        val forge = requireNotNull(KNOWN_FORGES[externalNamespace]) {
            "Unknown forge for namespace: '$externalNamespace'."
        }

        return ExternalId.createFromExternalId(forge, externalId, null, null)
    }

    companion object {
        fun parse(coordinates: String): BlackDuckOriginId {
            val parts = coordinates.split(':', limit = 2)
            require(parts.size == 2) {
                "Could not parse originId '$coordinates'. Missing ':' separator ."
            }

            return BlackDuckOriginId(externalNamespace = parts[0], externalId = parts[1])
        }
    }
}

// A replacement for `Forge.getKnownForges()`, because the latter does not contain entries for all forges and entries
// which do not work and need to be overridden.
private val KNOWN_FORGES = Forge.getKnownForges() + listOf(
    Forge(":", "conan"), // See https://github.com/blackducksoftware/integration-bdio/pull/40.
    Forge(":", Forge.LONG_TAIL.name) // See https://github.com/blackducksoftware/integration-bdio/issues/41.
).associateBy { it.name }
