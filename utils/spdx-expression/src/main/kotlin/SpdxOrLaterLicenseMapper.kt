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

    /**
     * A map which has base license IDs as key, and all base license IDs which satisfy the key as values, if the key is
     * used with "or-later" semantics. For example: GPL-1.0 -> [GPL-1.0, GPL-2.0, GPL-3.0].
     */
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
     * Return true if [offered] satisfies the [required]. Note that, in case [offered] differs from [required] in terms
     * of any license exception, [offered] does not satisfy [required].
     */
    fun satisfies(offered: SpdxSingleLicenseExpression, required: SpdxSingleLicenseExpression): Boolean =
        satisfies(offered.toNormalizedLicense(), required.toNormalizedLicense())

    private fun satisfies(offered: NormalizedLicense, required: NormalizedLicense): Boolean =
        when {
            offered.exceptionId != required.exceptionId -> false
            !required.orLater -> offered == required
            offered.baseId == required.baseId -> !offered.orLater || required.orLater
            else -> offered.baseId in satisfyingIdsForId[required.baseId].orEmpty()
        }
}

private data class NormalizedLicense(val baseId: String, val orLater: Boolean, val exceptionId: String?)

private fun SpdxSingleLicenseExpression.toNormalizedLicense(): NormalizedLicense {
    val license = simpleLicense()
    val exception = exception()

    var remaining = license.trim()
    var orLater = (this as? SpdxLicenseIdExpression)?.orLaterVersion ?: false

    // Do not solely rely on SpdxSingleLicenseExpression.orLater, to not introduce a limitation to SPDX license list
    // licenses. This way the code could be used as-is for LicenseRef-* licenses, and tests may (consistently) use
    // compact IDs such as A+ OR B-only.
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

    return NormalizedLicense(remaining, orLater, exception)
}
