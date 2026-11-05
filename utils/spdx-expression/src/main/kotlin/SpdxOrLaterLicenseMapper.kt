/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.spdxexpression

/**
 * A class for determining whether a given single license from the SPDX license list satisfies another. A map
 * associating successor base license IDs with their direct predecessor base license ID. For example, the entry
 * 'GPL-2.0 to GPL-1.0' configures 'GPL-2.0-or-later' and 'GPL-2.0-only' to satisfy 'GPL-1.0-or-later'.
 */
internal class SpdxOrLaterLicenseMapper(successorMap: Map<String, String>) {
    companion object {
        val INSTANCE by lazy {
            val successorMap = javaClass
                .getResource("/or-later-successors-mapping.yaml")!!
                .readText()
                .parseYamlKeyValueLines()
                .entries.associate { (successor, predecessor) ->
                    predecessor.trim() to successor.trim()
                }

            SpdxOrLaterLicenseMapper(successorMap)
        }
    }

    internal val satisfyingIdsForId: Map<String, Set<String>> by lazy {
        (successorMap.keys + successorMap.values).associateWith { id ->
            buildSet {
                var currentId: String? = id

                while (currentId != null && currentId !in this) {
                    add(currentId)
                    currentId = successorMap[currentId]
                }
            }
        }
    }

    /**
     * Return true if the [offered single licenses][offered] satisfies the [required single license].
     */
    fun satisfies(offered: String, required: String): Boolean {
        if (offered == required) return true

        return satisfies(offered.toNormalizedLicense(), required.toNormalizedLicense())
    }

    private fun satisfies(offered: NormalizedLicense, required: NormalizedLicense): Boolean =
        when {
            !required.orLater -> offered == required
            else -> offered.baseId in satisfyingIdsForId[required.baseId].orEmpty()
        }
}

private data class NormalizedLicense(val baseId: String, val orLater: Boolean)

private fun String.toNormalizedLicense(): NormalizedLicense {
    var remaining = trim()
    var orLater = false

    if (remaining.endsWith("+")) {
        orLater = true
        remaining = remaining.removeSuffix("+")
    }

    if (remaining.endsWith("-or-later")) {
        orLater = true
        remaining = remaining.removeSuffix("-or-later")
    } else if (remaining.endsWith("-only")) {
        remaining = remaining.removeSuffix("-only")
    }

    return NormalizedLicense(remaining, orLater)
}
