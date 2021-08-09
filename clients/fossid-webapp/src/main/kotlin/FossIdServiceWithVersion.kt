/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.fossid

import kotlinx.coroutines.runBlocking

abstract class FossIdServiceWithVersion(val version: String) : FossIdRestService {
    companion object {
        /**
         * Construct a new instance of [FossIdServiceWithVersion] for given [delegate]. The implementation matching
         * FossID version will be instantiated and returned.
         */
        fun instance(delegate: FossIdRestService): FossIdServiceWithVersion = runBlocking {
            val version = delegate.getFossIdVersion().orEmpty()

            VersionedFossIdService(delegate, version)
        }

        /**
         * Extract the version from the login page.
         * Example: `<link rel='stylesheet' href='style/fossid.css?v=2021.2.2#7936'>`
         */
        private suspend fun FossIdRestService.getFossIdVersion(): String? {
            // TODO: replace with an API call when FossID provides a function (starting at version 21.2).
            val regex = Regex("^.*fossid.css\\?v=([0-9.]+).*\$")

            val response = getLoginPage()

            response.charStream().buffered().useLines { lines ->
                lines.forEach { line ->
                    val matcher = regex.matchEntire(line)
                    if (matcher != null && matcher.groupValues.size == 2) {
                        val version = matcher.groupValues[1]
                        return version
                    }
                }
            }
            return null
        }
    }
}
