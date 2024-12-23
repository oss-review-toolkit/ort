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

import org.ossreviewtoolkit.utils.common.withoutPrefix

// TODO: Check if this code should go to common PURL code.
internal data class Purl(
    val type: String,
    val namespace: String?,
    val name: String,
    val version: String?
) {
    companion object {
        fun parse(s: String): Purl? {
            // TODO: Adhere to decoding / encoding.
            var remaining = s.withoutPrefix("pkg:")
                ?.substringBefore("?") // drop qualifiers
                ?: return null

            val type = remaining.substringBefore("/")
            remaining = remaining.withoutPrefix("$type/")!!

            val version = remaining.substringAfter("@", "").takeUnless { it.isBlank() }
            remaining = remaining.substringBefore("@")

            val name = remaining.substringAfterLast("/")
            val namespace = remaining.substringBeforeLast("/", "").takeUnless { it.isBlank() }

            return Purl(type, namespace, name, version)
        }

        fun isValid(s: String): Boolean = !parse(s)?.name.orEmpty().isNullOrBlank()
    }
}
